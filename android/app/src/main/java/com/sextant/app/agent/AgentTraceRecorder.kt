package com.sextant.app.agent

import com.sextant.app.data.AgentConversationStore
import com.sextant.app.data.AgentTraceEntry
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class AgentToolResult(val content: String, val summary: String, val failed: Boolean = false)

/** 只记录供应商真实返回的内容；流式更新节流，避免每个 token 都写磁盘。 */
internal class AgentTraceRecorder(
  private val store: AgentConversationStore,
  private val runId: String,
  private val attemptId: String? = null,
  private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
  private var model: AgentTraceEntry? = null
  private var modelStartedAt = 0L
  private var lastPublish = 0L
  private var lastPersist = 0L
  private var modelNumber = 0

  suspend fun startModel() {
    if (modelNumber == 0) modelNumber = store.load().trace.count { it.runId == runId && it.kind == "model" }
    modelStartedAt = clock()
    model = AgentTraceEntry(UUID.randomUUID().toString(), runId, "model", "DeepSeek · 第${++modelNumber}次响应")
    publish(requireNotNull(model), true)
    store.update { if (it.ownsRun(runId, attemptId)) it.copy(activity = "正在思考与分析…") else it }
  }

  suspend fun reasoning(delta: String) = append(delta, isReasoning = true)
  suspend fun text(delta: String) = append(delta, isReasoning = false)

  private suspend fun append(delta: String, isReasoning: Boolean) {
    val current = model ?: return
    if (delta.isEmpty()) return
    val combined = (if (isReasoning) current.reasoning else current.text) + delta
    model = if (isReasoning) current.copy(reasoning = combined.take(DISPLAY_LIMIT), truncated = current.truncated || combined.length > DISPLAY_LIMIT)
      else current.copy(text = combined.take(DISPLAY_LIMIT), truncated = current.truncated || combined.length > DISPLAY_LIMIT)
    val now = clock()
    if (now - lastPublish >= 150) {
      val persist = now - lastPersist >= 1000
      publish(requireNotNull(model).copy(elapsedMillis = (now - modelStartedAt).coerceAtLeast(0)), persist)
      lastPublish = now
      if (persist) lastPersist = now
    }
  }

  suspend fun finishModel(status: String = "completed", summary: String = "") {
    val current = model ?: return
    publish(current.copy(status = status, summary = summary, elapsedMillis = (clock() - modelStartedAt).coerceAtLeast(0)), true)
    model = null
  }

  suspend fun invalidTool(name: String, arguments: String, message: String) {
    publish(AgentTraceEntry(UUID.randomUUID().toString(), runId, "tool", name, status = "failed",
      arguments = arguments.take(DISPLAY_LIMIT), summary = "参数校验失败：${message.take(400)}", truncated = arguments.length > DISPLAY_LIMIT), true)
  }

  suspend fun tool(name: String, arguments: String, activity: String, block: suspend () -> AgentToolResult): String {
    val entry = AgentTraceEntry(UUID.randomUUID().toString(), runId, "tool", name, arguments = arguments.take(DISPLAY_LIMIT), truncated = arguments.length > DISPLAY_LIMIT)
    val started = clock()
    publish(entry, true)
    store.update { if (it.ownsRun(runId, attemptId)) it.copy(activity = activity) else it }
    try {
      val result = block()
      publish(entry.copy(status = if (result.failed) "failed" else "completed", result = result.content.take(DISPLAY_LIMIT), summary = result.summary,
        elapsedMillis = (clock() - started).coerceAtLeast(0), truncated = entry.truncated || result.content.length > DISPLAY_LIMIT), true)
      return result.content
    } catch (cancelled: CancellationException) {
      withContext(NonCancellable) { publish(entry.copy(status = "cancelled", summary = "已停止", elapsedMillis = (clock() - started).coerceAtLeast(0)), true) }
      throw cancelled
    } catch (error: Exception) {
      publish(entry.copy(status = "failed", summary = error.message?.take(400) ?: "工具执行失败", elapsedMillis = (clock() - started).coerceAtLeast(0)), true)
      throw error
    }
  }

  private suspend fun publish(entry: AgentTraceEntry, persist: Boolean) {
    store.update(persist = persist) { state ->
      if (!state.ownsRun(runId, attemptId)) state else state.copy(trace =
        if (state.trace.any { it.id == entry.id }) state.trace.map { if (it.id == entry.id) entry else it }
        else state.trace + entry)
    }
  }

  private companion object { const val DISPLAY_LIMIT = 16000 }
}
