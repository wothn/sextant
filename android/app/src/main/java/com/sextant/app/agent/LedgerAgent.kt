package com.sextant.app.agent

import android.content.Context
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import com.sextant.app.data.*
import java.util.UUID
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** 装配运行依赖；工具、上下文、协议检查点与供应商接入各有独立边界。 */
internal class LedgerAgent(
  private val context: Context,
  private val repository: SextantRepository,
  private val store: AgentConversationStore,
  private val images: AgentImageStore = AgentImageStore(context),
  private val models: (AgentSettings) -> AgentModelConnection = { AgentModelAccess(it.apiKey) },
) {
  suspend fun run(runId: String, settings: AgentSettings, attemptId: String? = null): String = withTimeout(15 * 60 * 1000L) {
    val conversation = store.load()
    check(conversation.ownsRun(runId, attemptId)) { "当前任务已停止或切换" }
    val latest = conversation.messages.last { it.role == "user" && it.runId == runId }
    val turn = conversation.transcript.firstOrNull { it.userMessageId == latest.id }
    val previous = turn?.let { AgentTranscriptCodec.decode(it) }.orEmpty()
    // 最终协议已落盘、UI 完成状态尚未落盘时，直接兑现回答，不重复调用模型或工具。
    if (turn?.completed == true) return@withTimeout (previous.last() as Message.Assistant).textContent()
    val cleared = store.update { state ->
      check(state.ownsRun(runId, attemptId)) { "当前任务已停止或切换" }
      state.copy(drafts = state.drafts.filterNot {
        it.runId == runId && it.awaitingInput && it.id !in turn?.draftIds.orEmpty()
      })
    }
    val supportsDocuments = AgentModelAccess.supportsExtendedChat(settings.model)
    if (!supportsDocuments && latest.documents.isNotEmpty()) throw AgentDocumentException("附件需要使用 deepseek-flash，请在设置中切换模型")
    val active = DeepSeekDocuments(AgentDocumentStore(context), store).prepare(cleared, latest.id, settings.apiKey)
    val keyFingerprint = documentKeyFingerprint(settings.apiKey)
    val documentIds = if (supportsDocuments) active.messages.flatMap { it.documents }
      .mapNotNull { document -> document.activeFileId(keyFingerprint)?.let { document.id to it } }
      .takeLast(20).toMap() else emptyMap()
    val markers = documentIds.mapValues { "[sextant-file-${UUID.randomUUID()}]" }
    val history = AgentContextBuilder(images).build(active, markers)
    val hasImages = active.messages.any { it.images.isNotEmpty() }
    require(!hasImages || AgentModelAccess.supportsImages(settings.model)) { "当前模型不支持图片，请切换模型" }
    val session = AgentRunSession(store, runId, attemptId, latest.id, previous)
    AgentLedgerQuery.capture(context, repository).use { ledger ->
      val trace = AgentTraceRecorder(store, runId, attemptId = attemptId)
      val registry = ledgerTools(repository, store, runId, attemptId, ledger, trace)
      models(settings).use { access ->
        access.configureExtensions(markers.entries.associate { (id, marker) -> marker to documentIds.getValue(id) }) { activity ->
          store.update(persist = false) { state ->
            if (state.ownsRun(runId, attemptId)) state.copy(activity = activity) else state
          }
        }
        val config = AIAgentConfig(
          model = AgentModelAccess.model(settings.model, hasImages),
          prompt = prompt("sextant-chat", params = LLMParams(maxTokens = 4096)) {
            message(Message.System(ledgerInstructions(), RequestMetaInfo.Empty))
            history.forEach { message(it) }
          },
          maxAgentIterations = 40,
        )
        val agent = AIAgent(promptExecutor = access.executor, agentConfig = config,
          strategy = streamingLedgerStrategy(appendUser = false,
            runContext = ledgerRunContext(ledger.capturedAt, active.drafts.joinToString { "${it.id}=${it.status}" }),
            pendingCalls = AgentTranscriptCodec.pendingCalls(previous), checkpoint = session::checkpoint,
            prepareRequest = access::prepareRequest, completeResponse = access::completeResponse), toolRegistry = registry) {
          handleEvents {
            onLLMStreamingStarting { trace.startModel() }
            onLLMStreamingFrameReceived { event ->
              when (val frame = event.streamFrame) {
                is StreamFrame.ReasoningDelta -> trace.reasoning(frame.text ?: frame.summary.orEmpty())
                is StreamFrame.TextDelta -> trace.text(frame.text)
                else -> Unit
              }
            }
            onLLMStreamingCompleted { trace.finishModel() }
            onToolValidationFailed { event -> trace.invalidTool(event.toolName, event.toolArgs.toString(), event.message) }
            onLLMStreamingFailed { event ->
              val http = generateSequence(event.error) { it.cause }.filterIsInstance<KoogHttpClientException>().firstOrNull()
              val detail = http?.errorBody?.let { body -> runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull() }
                ?: event.error.javaClass.simpleName
              trace.finishModel("failed", ("${http?.statusCode?.let { "HTTP $it · " }.orEmpty()}$detail")
                .replace(settings.apiKey, "[Key 已隐藏]").take(1000))
            }
          }
        }
        try { agent.run(latest.text) } finally { agent.close() }
      }
    }
  }
}
