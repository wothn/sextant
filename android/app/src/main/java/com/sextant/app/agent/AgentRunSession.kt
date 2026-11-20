package com.sextant.app.agent

import ai.koog.prompt.message.Message
import com.sextant.app.data.AgentConversationStore
import com.sextant.app.data.AgentTurnTranscript

/** 协议检查点和它引用的草稿一起提交；旧 attempt 无权回写。 */
internal class AgentRunSession(
  private val store: AgentConversationStore,
  private val runId: String,
  private val attemptId: String?,
  private val userMessageId: String,
  private val previous: List<Message>,
) {
  suspend fun checkpoint(messages: List<Message>, completed: Boolean) {
    val encoded = AgentTranscriptCodec.encode(previous + messages)
    store.update { state ->
      check(state.ownsRun(runId, attemptId)) { "任务已停止或已重试" }
      val turn = AgentTurnTranscript(userMessageId, runId, encoded,
        state.drafts.filter { it.runId == runId && it.awaitingInput }.map { it.id }, completed)
      state.copy(transcript = state.transcript.filterNot { it.userMessageId == userMessageId } + turn)
    }
  }
}
