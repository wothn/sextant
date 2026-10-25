package com.sextant.app.ui.screens

import com.sextant.app.data.AgentDraft
import com.sextant.app.data.AgentMessage

/** Keep each run's cards with its last message, including when later turns arrive. */
internal fun agentDraftsByMessage(messages: List<AgentMessage>, drafts: List<AgentDraft>): Map<String?, List<AgentDraft>> {
  val anchorByRun = messages.filter { it.runId != null }.associate { it.runId to it.id }
  // Retain legacy cards even if their originating message is absent.
  return drafts.filter { it.status != "superseded" }.groupBy { anchorByRun[it.runId] }
}
