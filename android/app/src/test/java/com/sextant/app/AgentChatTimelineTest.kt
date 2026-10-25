package com.sextant.app

import com.sextant.app.data.AgentDraft
import com.sextant.app.data.AgentMessage
import com.sextant.app.ui.screens.agentDraftsByMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentChatTimelineTest {
  private fun draft(id: String, run: String, status: String = "pending") =
    AgentDraft(id, run, "update", "transaction-$id", status = status)

  @Test fun oldCardsStayBeforeLaterTurnsAndReceipts() {
    val first = draft("first", "run-1", "applied")
    val second = draft("second", "run-2")
    val messages = listOf(
      AgentMessage("user-1", "user", "修改", "run-1"),
      AgentMessage("answer-1", "assistant", "请确认", "run-1"),
      AgentMessage("receipt", "receipt", "已保存修改"),
      AgentMessage("user-2", "user", "再记一笔", "run-2"),
      AgentMessage("answer-2", "assistant", "请确认", "run-2"),
    )
    val cards = agentDraftsByMessage(messages, listOf(first, second))
    val timeline = messages.flatMap { message -> listOf(message.id) + cards[message.id].orEmpty().map { it.id } }
    assertEquals(listOf("user-1", "answer-1", "first", "receipt", "user-2", "answer-2", "second"), timeline)
  }

  @Test fun runningAndUndoCardsStayWithTheirRunInDraftOrder() {
    val original = draft("original", "run-1", "undo_prepared")
    val inverse = draft("inverse", "run-1")
    val pending = draft("pending", "run-2")
    val messages = listOf(
      AgentMessage("answer-1", "assistant", "完成", "run-1"),
      AgentMessage("user-2", "user", "记一笔", "run-2"),
    )
    val cards = agentDraftsByMessage(messages, listOf(original, pending, inverse))
    assertEquals(listOf(original, inverse), cards["answer-1"])
    assertEquals(listOf(pending), cards["user-2"])
  }

  @Test fun legacyCardsAreRetainedAndSupersededCardsHidden() {
    val legacy = draft("legacy", "missing-run", "rejected")
    val superseded = draft("superseded", "missing-run", "superseded")
    val cards = agentDraftsByMessage(emptyList(), listOf(legacy, superseded))
    assertEquals(listOf(legacy), cards[null])
    assertFalse(cards.values.flatten().contains(superseded))
  }
}
