package com.sextant.app.agent

import com.sextant.app.data.AgentConversation
import com.sextant.app.data.AgentConversationStore
import com.sextant.app.data.AgentDocument
import com.sextant.app.data.AgentDocumentStore
import com.sextant.app.data.AgentDocumentUpload
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class AgentDocumentException(message: String) : IOException(message)

internal class DeepSeekDocuments(
  private val documents: AgentDocumentStore,
  private val store: AgentConversationStore,
  private val connect: () -> HttpClient = {
    HttpClient(OkHttp) {
      followRedirects = false
      install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 180_000
        requestTimeoutMillis = 270_000
      }
    }
  },
) {
  suspend fun prepare(conversation: AgentConversation, userMessageId: String, apiKey: String): AgentConversation {
    val latest = conversation.messages.single { it.id == userMessageId }
    val keyFingerprint = documentKeyFingerprint(apiKey)
    val pending = latest.documents.filter { it.activeFileId(keyFingerprint) == null }
    if (pending.isEmpty()) return conversation
    val runId = requireNotNull(conversation.runId)
    val attemptId = conversation.attemptId
    var current = conversation
    connect().use { client ->
      for (document in pending) {
        store.update { state ->
          check(state.ownsRun(runId, attemptId)) { "任务已停止或已重试" }
          state.copy(activity = "正在上传并解析 ${document.name}…")
        }
        val upload = upload(client, apiKey, keyFingerprint, document)
        current = store.update { state ->
          check(state.ownsRun(runId, attemptId)) { "任务已停止或已重试" }
          state.copy(messages = state.messages.map { message ->
            if (message.id != userMessageId) message else message.copy(documents = message.documents.map {
              if (it.id == document.id) it.copy(upload = upload) else it
            })
          })
        }
      }
    }
    return current
  }

  private suspend fun upload(client: HttpClient, apiKey: String, keyFingerprint: String, document: AgentDocument): AgentDocumentUpload =
    withContext(Dispatchers.IO) {
      try {
        val source = documents.source(document)
        val startedAt = System.currentTimeMillis() / 1000
        val response = client.post("https://api.deepseek.com/files") {
          bearerAuth(apiKey)
          setBody(MultiPartFormDataContent(formData {
            append("purpose", AgentDocumentStore.purpose(document.name))
            append("expires_after[anchor]", "created_at")
            append("expires_after[seconds]", FILE_LIFETIME_SECONDS.toString())
            val filename = document.name.replace(Regex("[\\\\\"/]"), "_")
            append("file", InputProvider(source.length()) { source.inputStream().asSource().buffered() }, Headers.build {
              append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
              append(HttpHeaders.ContentType, "application/octet-stream")
            })
          }))
        }
        val detail = when (response.status) {
          HttpStatusCode.OK, HttpStatusCode.Created -> null
          HttpStatusCode.BadRequest -> "请检查附件格式、密码保护和页数"
          HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> "请检查 DeepSeek API Key"
          HttpStatusCode.PaymentRequired -> "请检查 DeepSeek 账户余额"
          HttpStatusCode.PayloadTooLarge -> "文件超过 DeepSeek 大小限制"
          HttpStatusCode.TooManyRequests -> "上传请求过多，请稍后重试"
          else -> "请稍后重试"
        }
        if (detail != null) throw AgentDocumentException("附件上传失败：$detail")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val id = body["id"]?.jsonPrimitive?.contentOrNull
        if (id == null || !id.startsWith("file-api-")) throw AgentDocumentException("DeepSeek 未返回有效的附件编号，请重试")
        val expiresAt = body["expires_at"]?.jsonPrimitive?.longOrNull ?: (startedAt + FILE_LIFETIME_SECONDS)
        AgentDocumentUpload(id, expiresAt, keyFingerprint)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: AgentDocumentException) {
        throw error
      } catch (_: Exception) {
        throw AgentDocumentException("无法上传 ${document.name}，请检查文件和网络后重试")
      }
    }

  private companion object {
    const val FILE_LIFETIME_SECONDS = 7 * 24 * 60 * 60L
  }
}

internal fun documentKeyFingerprint(apiKey: String): String =
  MessageDigest.getInstance("SHA-256").digest(apiKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun AgentDocument.activeFileId(keyFingerprint: String): String? = upload
  ?.takeIf { it.keyFingerprint == keyFingerprint && it.expiresAt > System.currentTimeMillis() / 1000 }
  ?.fileId
