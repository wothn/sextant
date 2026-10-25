package com.sextant.app.agent

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import com.sextant.app.data.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
) {
  val store = AgentConversationStore(context)
  val settings = AgentSettingsStore(context)
  val images = AgentImageStore(context)
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
        if (saved.running) store.update { it.copy(status = "interrupted", activity = "上次任务中断，可重新分析；待确认草稿尚未入账。",
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
    require(config.model != "deepseek-v4-pro" ||
      (imageIds.isEmpty() && store.load().messages.takeLast(39).none { it.images.isNotEmpty() })) {
      "当前模型不支持图片，请在设置中切换为 deepseek-flash"
    }
    imageIds.forEach { images.read(it) }
    val id = UUID.randomUUID().toString()
    store.update {
      require(!it.running) { "请等待当前任务结束，或先停止任务" }
      it.copy(messages = it.messages + AgentMessage(UUID.randomUUID().toString(), "user", text.trim(), id, imageIds.toList()),
        drafts = it.drafts.map { draft -> if (draft.status == "pending") draft.copy(status = "superseded") else draft },
        runId = id, status = "running", activity = "准备连接 DeepSeek…", error = null)
    }
    withContext(Dispatchers.Main) { onAccepted() }
    start(id)
  }

  fun retry() = command {
    val saved = store.load()
    require(!saved.running && saved.runId != null) { "没有可重试的任务" }
    require(saved.status in setOf("failed", "interrupted", "cancelled")) { "这次任务已经完成" }
    store.update { it.copy(status = "running", activity = "正在重新分析…", error = null,
      drafts = it.drafts.map { draft -> if (draft.runId == saved.runId && draft.status == "pending") draft.copy(status = "superseded") else draft }) }
    start(saved.runId)
  }

  private suspend fun start(id: String) {
    try { ContextCompat.startForegroundService(context, Intent(context, AgentRunService::class.java).putExtra("runId", id)) }
    catch (error: Exception) {
      store.update { it.copy(status = "failed", error = "无法启动后台任务，请保持应用打开后重试。", activity = "") }
      throw IllegalStateException("后台任务启动失败，请重试", error)
    }
  }

  fun stop() = command {
    fail("cancelled", "任务已停止。已确认的记账不会撤销。")
    context.startService(Intent(context, AgentRunService::class.java).setAction(AgentRunService.STOP))
  }

  internal suspend fun recordStopped(runId: String?) {
    fail("cancelled", "任务已停止。已确认的记账不会撤销。", runId)
  }

  fun decide(id: String, approve: Boolean) = command {
    val saved = store.load()
    require(!saved.running) { "请等待分析结束后再确认" }
    val draft = saved.drafts.firstOrNull { it.id == id && it.status == "pending" } ?: error("这项变更已处理")
    val receipt = if (approve) repository.applyAgentDraft(draft) else "已拒绝这项变更，账本未修改"
    val appliedVersion = if (approve) repository.agentOperationVersion(draft.id) else null
    store.update { current ->
      val drafts = current.drafts.map { if (it.id == id) it.copy(status = if (approve) "applied" else "rejected", result = receipt, appliedUpdatedAt = appliedVersion) else it }
      current.copy(drafts = drafts, status = if (drafts.any { it.status == "pending" }) "awaiting_confirmation" else "completed",
        messages = current.messages + AgentMessage(UUID.randomUUID().toString(), "receipt", receipt), activity = "")
    }
  }

  fun newConversation() = command {
    require(mutableReady.value) { "对话正在加载" }
    store.newConversation()
  }

  fun openConversation(id: String, onOpened: () -> Unit) = command {
    require(mutableReady.value) { "对话正在加载" }
    store.openConversation(id)
    withContext(Dispatchers.Main) { onOpened() }
  }

  fun undo(id: String) = command {
    val saved = store.load()
    require(!saved.running) { "请先停止当前任务" }
    val applied = saved.drafts.firstOrNull { it.id == id && it.status == "applied" } ?: error("没有可撤销的操作")
    val current = repository.findTransaction(applied.transactionId)?.transaction
    val original = applied.before
    val inverse = when (applied.action) {
      "create" -> {
        require(current != null) { "这笔记录已不存在" }
        // 撤销不能删掉用户随后在其他入口修改过的记录。
        require(current.updatedAt == applied.appliedUpdatedAt && current.amountCents == applied.amountCents && current.type.storageValue() == applied.type &&
          current.categoryId == applied.categoryId && current.paymentMethodId == applied.paymentMethodId &&
          current.description == applied.description.trim() && current.transactionDate == applied.transactionDate) { "记录已变化，请通过对话重新生成变更" }
        applied.copy(id = UUID.randomUUID().toString(), action = "delete", expectedUpdatedAt = current.updatedAt, before = current, status = "pending", result = null)
      }
      "update" -> {
        require(current != null && original != null) { "原记录不可恢复" }
        require(current.updatedAt == applied.appliedUpdatedAt && current.amountCents == applied.amountCents && current.type.storageValue() == applied.type &&
          current.categoryId == applied.categoryId && current.paymentMethodId == applied.paymentMethodId &&
          current.description == applied.description.trim() && current.transactionDate == applied.transactionDate) { "记录已变化，请通过对话重新生成变更" }
        AgentDraft(UUID.randomUUID().toString(), applied.runId, "update", current.id, original.type.storageValue(), original.amountCents,
          original.categoryId, original.paymentMethodId, original.description, original.transactionDate, current.updatedAt, current)
      }
      else -> {
        require(current == null && original != null) { "记录已经存在，无法恢复" }
        AgentDraft(UUID.randomUUID().toString(), applied.runId, "create", original.id, original.type.storageValue(), original.amountCents,
          original.categoryId, original.paymentMethodId, original.description, original.transactionDate, before = original, restoreOriginal = true)
      }
    }
    store.update { it.copy(drafts = it.drafts.map { draft -> if (draft.id == id) draft.copy(status = "undo_prepared") else draft } + inverse,
      status = "awaiting_confirmation", activity = "") }
  }

  /** 系统重投递只重新分析，不会自动兑现任何变更草稿。 */
  suspend fun execute(id: String) = execution.withLock {
    initialized.await()
    val saved = store.load()
    val conversationId = requireNotNull(store.activeId.value)
    if (saved.runId != id || saved.status !in setOf("running", "interrupted")) return@withLock
    val active = store.update {
      if (it.runId != id || it.status !in setOf("running", "interrupted")) it
      else it.copy(status = "running", error = null, drafts = it.drafts.filterNot { draft -> draft.runId == id && draft.status == "pending" })
    }
    if (active.runId != id || !active.running) return@withLock
    try {
      val config = settings.read()
      require(config.apiKey.isNotBlank()) { "请配置 DeepSeek API Key" }
      val result = LedgerAgent(context, repository, store).run(id, config)
      store.update { current -> if (!current.running || current.runId != id) current else current.copy(
        messages = current.messages + AgentMessage(UUID.randomUUID().toString(), "assistant", result, id),
        status = if (current.drafts.any { it.status == "pending" }) "awaiting_confirmation" else "completed",
        activity = "", error = null,
      ) }
      val userMessage = saved.messages.lastOrNull { it.role == "user" && it.runId == id }?.text.orEmpty()
      if (userMessage.isNotBlank()) generateTitleAsync(conversationId, userMessage, result, config)
    } catch (image: AgentImageUnavailableException) {
      fail("failed", requireNotNull(image.message), id)
    } catch (timeout: TimeoutCancellationException) {
      withContext(NonCancellable) { fail("failed", "任务超过15分钟，已停止。可重新分析，账本未自动修改。", id) }
    } catch (cancelled: CancellationException) {
      withContext(NonCancellable) { fail("cancelled", "任务已停止。已确认的记账不会撤销。", id) }
      throw cancelled
    } catch (error: Exception) {
      // 不把网络异常原文（可能包含请求或服务返回内容）持久化或显示。
      fail("failed", "连接或分析失败。请检查网络、API Key、余额和模型名称后重试。", id)
    }
  }

  private fun generateTitleAsync(conversationId: String, userMessage: String, assistantMessage: String, config: AgentSettings) {
    scope.launch {
      try {
        withTimeout(30_000) {
          val http = HttpClient(OkHttp)
          val client = DeepSeekLLMClient(apiKey = config.apiKey, httpClientFactory = KtorKoogHttpClient.Factory(http))
          try {
            val title = generateConversationTitle(client, config.model, userMessage, assistantMessage)
            if (title != null) store.setTitleIfAbsent(conversationId, title)
          } finally {
            client.close()
            http.close()
          }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        // 标题是辅助信息；失败时继续使用首条用户消息，不影响已完成的回答。
      }
    }
  }

  private suspend fun fail(status: String, message: String, runId: String? = null) {
    store.update { if (runId != null && it.runId != runId) it else it.copy(status = status, error = message, activity = "",
      trace = it.trace.map { entry -> if (entry.status == "running") entry.copy(status = if (status == "failed") "failed" else "cancelled") else entry },
      drafts = it.drafts.map { draft -> if (draft.status == "pending") draft.copy(status = "superseded") else draft }) }
  }
}
