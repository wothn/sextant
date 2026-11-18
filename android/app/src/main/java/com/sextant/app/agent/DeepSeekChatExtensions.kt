package com.sextant.app.agent

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.message.Message
import com.sextant.app.data.AgentWebSource
import java.io.IOException
import java.net.URI
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/** Bridges DeepSeek's Chat Completions extensions while Koog handles standard tool calls and streaming. */
internal class DeepSeekChatExtensions {
  private var assistantHistory: List<JsonObject?> = emptyList()
  var documentIds: Map<String, String> = emptyMap()
  var onWebActivity: suspend (String) -> Unit = {}
  private var response: JsonObject? = null

  fun prepare(messages: List<Message>) {
    assistantHistory = messages.filterIsInstance<Message.Assistant>().map { it.rawResponse?.obj(WEB_RESPONSE) }
    response = null
  }

  fun decorate(message: Message.Assistant): Message.Assistant {
    val extension = response ?: return message
    response = null
    return message.copy(rawResponse = JsonObject(message.rawResponse.orEmpty() + (WEB_RESPONSE to extension)))
  }

  fun wrap(client: KoogHttpClient): KoogHttpClient = object : KoogHttpClient by client {
    override fun <T : Any, R : Any, O : Any> sse(
      path: String, requestBody: T, requestBodyType: KClass<T>, dataFilter: (String?) -> Boolean,
      decodeStreamingResponse: (String) -> R, processStreamingChunk: (R) -> O?,
      parameters: Map<String, String>, headers: Map<String, String>,
    ): Flow<O> {
      val request = if (requestBody is String && path.endsWith("chat/completions")) Json.parseToJsonElement(requestBody).jsonObject else null
      if (request == null || !AgentModelAccess.supportsExtendedChat(request.string("model").orEmpty())) {
        return client.sse(path, requestBody, requestBodyType, dataFilter, decodeStreamingResponse, processStreamingChunk, parameters, headers)
      }
      val body = extendRequest(request)
      return flow {
        val reasoning = StringBuilder()
        var activity: String? = null
        client.sse(path, body, String::class, dataFilter, { data ->
          val chunk = Json.parseToJsonElement(data).jsonObject
          if (chunk.obj("error") != null) throw IOException("DeepSeek 流式响应中断")
          chunk.array("choices")?.firstOrNull()?.let { it as? JsonObject }?.let { choice ->
            val delta = choice.obj("delta")
            delta?.string("reasoning_content")?.let(reasoning::append)
            choice.obj("server_tool_event")?.let { event ->
              when (event.string("type")) {
                "step_reasoning_done" -> reasoning.clear()
                "call_started" -> activity = when (event.obj("call")?.obj("action")?.string("type")) {
                  "read_link", "open_page", "find_in_page" -> "正在读取网页…"
                  else -> "正在联网搜索…"
                }
              }
            }
            val calls = delta?.array("server_tool_calls")?.takeIf { it.isNotEmpty() }
            val annotations = delta?.array("annotations")?.takeIf { it.isNotEmpty() }
            if (calls != null || annotations != null) response = buildJsonObject {
              calls?.let {
                put("server_tool_calls", it)
                put("reasoning_content", reasoning.toString())
              }
              annotations?.let { put("annotations", it) }
            }
            if (choice.string("finish_reason") == "content_filter") throw IOException("DeepSeek 未能返回回复")
          }
          // Koog requires a fingerprint, while the service can omit it on web tool events.
          val compatible = if (chunk.string("system_fingerprint") == null) {
            JsonObject(chunk + ("system_fingerprint" to JsonPrimitive(""))).toString()
          } else data
          decodeStreamingResponse(compatible)
        }, processStreamingChunk, parameters, headers).collect { frame ->
          activity?.let { onWebActivity(it) }
          activity = null
          emit(frame)
        }
      }
    }
  }

  private fun extendRequest(request: JsonObject): String {
    var assistantIndex = 0
    val messages = requireNotNull(request.array("messages"))
    val replayWeb = assistantHistory.any { it != null }
    if (replayWeb) check(messages.count { (it as? JsonObject)?.string("role") == "assistant" } == assistantHistory.size) {
      "联网历史与当前模型请求不匹配"
    }
    val extended = messages.map { item ->
      val message = item.jsonObject
      when (message.string("role")) {
        "assistant" -> {
          val web = assistantHistory.getOrNull(assistantIndex++)
          val calls = web?.array("server_tool_calls")
          if (web == null) message else buildJsonObject {
            message.forEach(::put)
            if (calls != null) {
              put("server_tool_calls", calls)
              put("reasoning_content", web.string("reasoning_content").orEmpty())
            }
            web.array("annotations")?.let { put("annotations", it) }
          }
        }
        "user" -> addDocuments(message)
        else -> message
      }
    }
    return buildJsonObject {
      request.forEach(::put)
      put("messages", JsonArray(extended))
      put("tools", buildJsonArray {
        request.array("tools")?.forEach { add(it) }
        add(buildJsonObject { put("type", "deepseek_web_search") })
        add(buildJsonObject { put("type", "deepseek_read_url") })
      })
    }.toString()
  }

  private fun addDocuments(message: JsonObject): JsonObject {
    if (documentIds.isEmpty()) return message
    val content = message["content"]
    val parts = when (content) {
      is JsonArray -> content
      is JsonPrimitive -> listOf(buildJsonObject { put("type", "text"); put("text", content) })
      else -> return message
    }
    val fileIds = mutableListOf<String>()
    val adjusted = parts.mapNotNull { part ->
      val block = part as? JsonObject ?: return@mapNotNull part
      var text = block.string("text") ?: return@mapNotNull block
      for ((marker, id) in documentIds) {
        if (marker in text) {
          fileIds += id
          text = text.replace(marker, "")
        }
      }
      block.takeUnless { text.isBlank() }?.let { JsonObject(it + ("text" to JsonPrimitive(text.trimEnd()))) }
    }
    if (fileIds.isEmpty()) return message
    val fileParts = fileIds.map { id -> buildJsonObject { put("type", "file"); put("file_id", id) } }
    return JsonObject(message + ("content" to JsonArray(adjusted + fileParts)))
  }
}

private const val WEB_RESPONSE = "sextant_deepseek_web"
private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.array(key: String) = this[key] as? JsonArray
private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun deepSeekWebSources(messages: List<Message>): List<AgentWebSource> = messages.filterIsInstance<Message.Assistant>()
  .flatMap { it.rawResponse?.obj(WEB_RESPONSE)?.array("annotations").orEmpty() }
  .mapNotNull { annotation ->
    val citation = (annotation as? JsonObject)?.obj("url_citation") ?: return@mapNotNull null
    val url = citation.string("url")?.takeIf { value ->
      val uri = runCatching { URI(value) }.getOrNull()
      uri?.scheme?.lowercase() in setOf("https", "http") && uri?.host != null && uri.userInfo == null
    } ?: return@mapNotNull null
    AgentWebSource(url, citation.string("title")?.takeIf { it.isNotBlank() } ?: url)
  }.distinctBy { it.url }
