package com.sextant.app.agent

import com.sextant.app.data.*
import java.util.UUID

/** 用户确认与撤销属于账本用例，不向模型暴露写入能力。 */
internal class AgentDraftActions(private val repository: SextantRepository, private val store: AgentConversationStore) {
  suspend fun decide(id: String, approve: Boolean) {
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

  suspend fun undo(id: String) {
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

}
