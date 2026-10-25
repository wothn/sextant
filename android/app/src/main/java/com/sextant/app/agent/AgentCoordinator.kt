package com.sextant.app.agent

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sextant.app.data.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentCoordinator(
  private val context: Context,
  private val repository: SextantRepository,
  private val scope: CoroutineScope,
  private val store: AgentConversationStore = AgentConversationStore(context),
  val settings: AgentSettingsStore = AgentSettingsStore(context),
  val images: AgentImageStore = AgentImageStore(context),
) {
  private val draftActions = AgentDraftActions(repository, store)
  val state = store.state
  val history = store.history
  val conversationId = store.activeId
  private val commands = Mutex()
  private val execution = Mutex()
  private val mutableError = MutableStateFlow<String?>(null)
  val error = mutableError.asStateFlow()
  private val mutableReady = MutableStateFlow(false)
  val ready = mutableReady.asStateFlow()
  private val initialized = CompletableDeferred<Unit>()

  init {
    scope.launch {
      try {
        val saved = store.load()
        if (saved.running) store.update { it.copy(status = "interrupted", activity = "上次任务中断，可继续分析；待确认草稿尚未入账。",
          trace = it.trace.map { entry -> if (entry.status == "running") entry.copy(status = "interrupted") else entry }) }
        mutableReady.value = true
        initialized.complete(Unit)
      } catch (error: Exception) {
        mutableError.value = "读取对话失败，请重启后重试；原文件已保留。"
        initialized.completeExceptionally(error)
      }
    }
  }

  private fun command(block: suspend () -> Unit) { scope.launch {
    commands.withLock {
      try { mutableError.value = null; block() }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (error: Exception) { mutableError.value = error.message ?: "操作失败，请重试" }
    }
  } }

  fun dismissError() { mutableError.value = null }

  fun discardPendingImage(id: String) = command {
    if (!store.containsImage(id)) images.removePending(id)
  }
  fun send(text: String, imageIds: List<String> = emptyList(), onAccepted: () -> Unit = {}) = command {
    require(mutableReady.value) { "对话正在加载" }
    require((text.isNotBlank() || imageIds.isNotEmpty()) && text.length <= 6000) { "请填写消息或选择图片，文字最多6000字" }
    require(imageIds.size <= AgentImageStore.MAX_IMAGES && imageIds.distinct().size == imageIds.size) { "每条消息最多4张图片" }
    val config = settings.read()
    require(config.apiKey.isNotBlank()) { "请先在右上角配置 DeepSeek API Key" }
    require(AgentModelAccess.supportsImages(config.model) ||
      (imageIds.isEmpty() && store.load().messages.none { it.images.isNotEmpty() })) {
      "当前模型不支持图片，请在设置中切换为 deepseek-flash"
    }
    imageIds.forEach { images.read(it) }
    val id = UUID.randomUUID().toString()
    val attempt = UUID.randomUUID().toString()
    store.update {
      require(!it.running) { "请等待当前任务结束，或先停止任务" }
      it.copy(messages = it.messages + AgentMessage(UUID.randomUUID().toString(), "user", text.trim(), id, imageIds.toList()),
        drafts = it.drafts.map { draft -> if (draft.status == "pending") draft.copy(status = "superseded") else draft },
        runId = id, attemptId = attempt, status = "running", activity = "准备连接 DeepSeek…", error = null)
    }
    withContext(Dispatchers.Main) { onAccepted() }
    start(id, attempt)
  }

  fun retry() = command {
    val saved = store.load()
    require(!saved.running && saved.runId != null) { "没有可重试的任务" }
    require(saved.status in setOf("failed", "interrupted", "cancelled")) { "这次任务已经完成" }
    val attempt = UUID.randomUUID().toString()
    store.update { it.copy(status = "running", attemptId = attempt, activity = "正在恢复分析…", error = null) }
    start(saved.runId, attempt)
  }

  private suspend fun start(id: String, attempt: String) {
    try { ContextCompat.startForegroundService(context, Intent(context, AgentRunService::class.java).putExtra("runId", id).putExtra("attemptId", attempt)) }
    catch (error: Exception) {
      store.update { it.copy(status = "failed", error = "无法启动后台任务，请保持应用打开后重试。", activity = "") }
      throw IllegalStateException("后台任务启动失败，请重试", error)
    }
  }

  fun stop() = command {
    val saved = store.load()
    if (!saved.running) return@command
    fail("cancelled", "任务已停止。已确认的记账不会撤销。", saved.runId, saved.attemptId)
    context.startService(Intent(context, AgentRunService::class.java).setAction(AgentRunService.STOP)
      .putExtra("runId", saved.runId).putExtra("attemptId", saved.attemptId))
  }

  internal suspend fun recordStopped(runId: String, attemptId: String?) {
    fail("cancelled", "任务已停止。已确认的记账不会撤销。", runId, attemptId)
  }

  fun decide(id: String, approve: Boolean) = command { draftActions.decide(id, approve) }

  fun newConversation() = command {
    require(mutableReady.value) { "对话正在加载" }
    store.newConversation()
  }

  fun openConversation(id: String, onOpened: () -> Unit) = command {
    require(mutableReady.value) { "对话正在加载" }
    store.openConversation(id)
    withContext(Dispatchers.Main) { onOpened() }
  }

  fun undo(id: String) = command { draftActions.undo(id) }

  /** 系统重投递从已保存的协议检查点继续，不会自动兑现任何变更草稿。 */
  suspend fun execute(id: String, attemptId: String? = null) = execution.withLock {
    initialized.await()
    val saved = store.load()
    val conversationId = requireNotNull(store.activeId.value)
    if (saved.runId != id || saved.attemptId != attemptId || saved.status !in setOf("running", "interrupted")) return@withLock
    val active = store.update {
      if (it.runId != id || it.attemptId != attemptId || it.status !in setOf("running", "interrupted")) it
      else it.copy(status = "running", error = null)
    }
    if (!active.ownsRun(id, attemptId)) return@withLock
    try {
      val config = settings.read()
      require(config.apiKey.isNotBlank()) { "请配置 DeepSeek API Key" }
      val result = LedgerAgent(context, repository, store, images).run(id, config, attemptId)
      store.update { current -> if (!current.ownsRun(id, attemptId)) current else current.copy(
        messages = current.messages + AgentMessage(UUID.randomUUID().toString(), "assistant", result, id),
        status = if (current.drafts.any { it.status == "pending" }) "awaiting_confirmation" else "completed",
        activity = "", error = null,
      ) }
      val userMessage = saved.messages.lastOrNull { it.role == "user" && it.runId == id }?.text.orEmpty()
      if (userMessage.isNotBlank()) generateTitleAsync(conversationId, userMessage, result, config)
    } catch (image: AgentImageUnavailableException) {
      fail("failed", requireNotNull(image.message), id, attemptId)
    } catch (timeout: TimeoutCancellationException) {
      withContext(NonCancellable) { fail("failed", "任务超过15分钟，已停止。可继续分析，账本未自动修改。", id, attemptId) }
    } catch (cancelled: CancellationException) {
      withContext(NonCancellable) { fail("cancelled", "任务已停止。已确认的记账不会撤销。", id, attemptId) }
      throw cancelled
    } catch (error: Exception) {
      // 不把网络异常原文（可能包含请求或服务返回内容）持久化或显示。
      fail("failed", "连接或分析失败。请检查网络、API Key、余额和模型名称后重试。", id, attemptId)
    }
  }

  private fun generateTitleAsync(conversationId: String, userMessage: String, assistantMessage: String, config: AgentSettings) {
    scope.launch {
      try {
        withTimeout(30_000) {
          AgentModelAccess(config.apiKey).use { access ->
            val title = generateConversationTitle(access.client, config.model, userMessage, assistantMessage)
            if (title != null) store.setTitleIfAbsent(conversationId, title)
          }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        // 标题是辅助信息；失败时继续使用首条用户消息，不影响已完成的回答。
      }
    }
  }

  private suspend fun fail(status: String, message: String, runId: String?, attemptId: String?) {
    store.update { if (runId == null || !it.ownsRun(runId, attemptId)) it else it.copy(status = status, error = message, activity = "",
      trace = it.trace.map { entry -> if (entry.status == "running") entry.copy(status = if (status == "failed") "failed" else "cancelled") else entry },
      drafts = it.drafts.map { draft ->
        if (draft.status == "pending" && draft.id !in it.transcript.flatMap { turn -> turn.draftIds }) draft.copy(status = "superseded") else draft
      }) }
  }
}
