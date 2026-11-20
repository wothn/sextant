package com.sextant.app.agent

import com.sextant.app.data.*
import java.util.UUID

/** 用户确认与撤销属于账本用例，不向模型暴露写入能力。 */
internal class AgentDraftActions(private val repository: SextantRepository, private val store: AgentConversationStore) {
  suspend fun adjust(id: String, amount: Long, categoryId: String?, description: String) {
    require(amount > 0 && description.length <= 1000) { "请检查金额和备注" }
    require(repository.agentOperationVersion(id) == null) { "这笔变更已经入账，请重新生成草稿" }
    val categories = repository.listCategories()
    store.update { state ->
      val draft = state.drafts.singleOrNull { it.id == id && it.status == "pending" } ?: error("草稿已失效")
      require(!state.running && draft.action != "delete") { "当前草稿无法调整" }
      require(categoryId == null || categories.any { it.id == categoryId && it.type.storageValue() == draft.type }) { "分类已停用或与流水方向不符" }
      state.copy(drafts = state.drafts.map { if (it.id == id) it.copy(amountCents = amount, categoryId = categoryId, description = description.trim()) else it })
    }
  }

  suspend fun choose(id: String) {
    val draft = store.load().drafts.singleOrNull { it.id == id && it.status == "candidate" } ?: error("候选记录已失效")
    val current = repository.findTransaction(draft.transactionId)?.transaction
    require(current != null && current.updatedAt == draft.expectedUpdatedAt) { "这笔记录已变化，请重新查询" }
    store.update { state ->
      require(!state.running && draft in state.drafts && draft.choiceGroup != null) { "候选记录已失效" }
      state.copy(drafts = state.drafts.map {
        when {
          it.id == id -> it.copy(status = "pending")
          it.status == "candidate" && it.choiceGroup == draft.choiceGroup -> it.copy(status = "superseded")
          else -> it
        }
      })
    }
  }

  suspend fun decide(id: String, approve: Boolean) {
    val saved = store.load()
    require(!saved.running) { "请等待分析结束后再确认" }
    val draft = saved.drafts.firstOrNull { it.id == id && it.status == "pending" } ?: error("这项变更已处理")
    val receipt = if (approve) repository.applyAgentDraft(draft) else "已拒绝这项变更，账本未修改"
    val appliedVersion = if (approve) repository.agentOperationVersion(draft.id) else null
    val contextReceipt = if (approve && draft.action != "delete") {
      "$receipt（transactionId=${draft.transactionId}，amountCents=${draft.amountCents}，categoryId=${draft.categoryId}）"
    } else receipt
    store.update { current ->
      val drafts = current.drafts.map { if (it.id == id) it.copy(status = if (approve) "applied" else "rejected", result = receipt, appliedUpdatedAt = appliedVersion) else it }
      current.copy(drafts = drafts, status = if (drafts.any { it.awaitingInput }) "awaiting_confirmation" else "completed",
        messages = current.messages + AgentMessage(UUID.randomUUID().toString(), "receipt", contextReceipt), activity = "")
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
