package com.sextant.app.data

import androidx.room.withTransaction

/** 操作回执和账本变更同事务提交，UI 回执丢失后重试不会重复写入。 */
suspend fun SextantRepository.applyAgentDraft(draft: AgentDraft): String {
  require(draft.status == "pending") { "这项操作已经处理" }
  val result = database.withTransaction {
    val receiptKey = "agent_operation_${draft.id}"
    database.appSettingDao().findValue(receiptKey)?.let { return@withTransaction it.substringAfter('|') }
    val dao = database.transactionDao()
    val current = dao.findById(draft.transactionId)
    if (draft.action != "create") {
      require(current != null) { "这笔记录已不存在" }
      require(draft.expectedUpdatedAt != null && current.updatedAt == draft.expectedUpdatedAt) { "这笔记录已被修改，请重新生成变更" }
    } else require(current == null) { "记录已存在，请重新生成变更" }
    val now = maxOf(System.currentTimeMillis(), (current?.updatedAt ?: 0) + 1)
    if (draft.action == "delete") {
      dao.deleteById(draft.transactionId)
    } else {
      require(draft.action == "create" || draft.action == "update") { "不支持的操作" }
      require(draft.amountCents > 0) { "流水金额必须大于 0" }
      require(draft.description.length <= 1000) { "备注不能超过 1000 字" }
      require(draft.transactionDate > 0) { "请选择有效日期" }
      val type = when (draft.type) {
        "expense" -> TransactionType.Expense
        "income" -> TransactionType.Income
        "transfer" -> TransactionType.Transfer
        else -> error("无效的流水方向")
      }
      draft.categoryId?.let { categoryId ->
        require(database.categoryDao().listActive().any { it.id == categoryId && it.type == type }) { "分类已停用或与流水方向不符" }
      }
      draft.paymentMethodId?.let { methodId ->
        require(database.paymentMethodDao().listWithUsage().any { it.method.id == methodId && it.method.isActive }) { "支付来源已停用" }
      }
      if (current == null) {
        val original = draft.before?.takeIf { draft.restoreOriginal }
        dao.insert(TransactionEntity(draft.transactionId, draft.categoryId, draft.paymentMethodId, draft.amountCents,
          type, draft.description.trim(), draft.transactionDate, original?.createdAt ?: now, now,
          original?.entryOrigin ?: EntryOrigin.Manual, original?.automationKind, null,
          original?.isUserEdited ?: false, original?.merchantName))
      } else {
        dao.update(draft.transactionId, type, draft.amountCents, draft.categoryId, draft.paymentMethodId,
          draft.description.trim(), draft.transactionDate, now)
      }
    }
    val receipt = when (draft.action) { "create" -> "已记录这笔交易"; "update" -> "已保存修改"; else -> "已删除这笔交易" } +
      draft.description.takeIf { it.isNotBlank() }?.let { "：${it.take(60)}" }.orEmpty()
    database.appSettingDao().upsert(AppSettingEntity(receiptKey, "$now|$receipt"))
    receipt
  }
  notifyChanged()
  return result
}

suspend fun SextantRepository.agentOperationVersion(id: String): Long? =
  database.appSettingDao().findValue("agent_operation_$id")?.substringBefore('|')?.toLongOrNull()
