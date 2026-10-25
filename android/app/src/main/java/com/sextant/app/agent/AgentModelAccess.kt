package com.sextant.app.agent

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.Closeable

/** 模型能力与客户端生命周期集中在供应商适配层。 */
internal interface AgentModelConnection : Closeable {
  val executor: PromptExecutor
}

internal class AgentModelAccess(apiKey: String) : AgentModelConnection {
  private val http = HttpClient(OkHttp)
  val client = DeepSeekLLMClient(apiKey = apiKey, httpClientFactory = KtorKoogHttpClient.Factory(http))
  override val executor = MultiLLMPromptExecutor(client)
  override fun close() { try { executor.close() } finally { http.close() } }

  companion object {
    fun supportsImages(model: String): Boolean = model != "deepseek-v4-pro"
    fun model(id: String, withImages: Boolean = false) =
      (if (withImages) DeepSeekModels.DeepSeekV4FlashVisionExp else DeepSeekModels.DeepSeekV4Flash).copy(id = id)
  }
}
