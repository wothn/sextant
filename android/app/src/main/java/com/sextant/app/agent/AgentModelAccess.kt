package com.sextant.app.agent

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.Closeable
import kotlinx.serialization.json.Json

/** 模型能力与客户端生命周期集中在供应商适配层。 */
internal interface AgentModelConnection : Closeable {
  val executor: PromptExecutor
  fun configureExtensions(documentIds: Map<String, String>, onWebActivity: suspend (String) -> Unit) = Unit
  fun prepareRequest(messages: List<Message>) = Unit
  fun completeResponse(message: Message.Assistant): Message.Assistant = message
}

internal class AgentModelAccess(apiKey: String, private val http: HttpClient = HttpClient(OkHttp)) : AgentModelConnection {
  private val extensions = DeepSeekChatExtensions()
  private val factory = KtorKoogHttpClient.Factory(http)
  val client = DeepSeekLLMClient(apiKey = apiKey, httpClientFactory = object : KoogHttpClient.Factory {
    override fun create(
      clientName: String, baseUrl: String, headers: Map<String, String>, queryParameters: Map<String, String>,
      requestTimeoutMillis: Long, connectTimeoutMillis: Long, socketTimeoutMillis: Long, json: Json,
    ): KoogHttpClient = extensions.wrap(factory.create(clientName, baseUrl, headers, queryParameters,
      requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis, json))
  })
  override val executor = MultiLLMPromptExecutor(client)

  override fun configureExtensions(documentIds: Map<String, String>, onWebActivity: suspend (String) -> Unit) {
    extensions.documentIds = documentIds
    extensions.onWebActivity = onWebActivity
  }

  override fun prepareRequest(messages: List<Message>) = extensions.prepare(messages)
  override fun completeResponse(message: Message.Assistant) = extensions.decorate(message)
  override fun close() { try { executor.close() } finally { http.close() } }

  companion object {
    fun supportsImages(model: String): Boolean = model != "deepseek-v4-pro"
    fun supportsExtendedChat(model: String): Boolean = model == "deepseek-flash"
    fun model(id: String, withImages: Boolean = false) =
      (if (withImages) DeepSeekModels.DeepSeekV4FlashVisionExp else DeepSeekModels.DeepSeekV4Flash).copy(id = id)
  }
}
