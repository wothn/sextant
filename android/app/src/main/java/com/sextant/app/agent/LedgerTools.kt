package com.sextant.app.agent

import com.sextant.app.data.*
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.typeToken
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.json.JSONObject

@Serializable
data class SchemaArgs(val includeSchema: Boolean = true)
@Serializable
data class DetailArgs(val title: String, val startDate: String, val endDateInclusive: String, val type: String, val categoryId: String? = null)
@Serializable
data class QueryArgs(val sql: String)
@Serializable
data class ChangeTarget(val transactionId: String, val expectedUpdatedAt: Long)
@Serializable
data class ChangeArgs(
  val action: String,
  val transactionId: String? = null,
  val type: String? = null,
  val amountCents: Long? = null,
  val categoryId: String? = null,
  val paymentMethodId: String? = null,
  val description: String? = null,
  val date: String? = null,
  val clearCategory: Boolean = false,
  val clearPaymentMethod: Boolean = false,
  val expectedUpdatedAt: Long? = null,
  val candidates: List<ChangeTarget> = emptyList(),
)

private suspend fun toolResult(summary: (String) -> String, block: suspend () -> String): AgentToolResult = try {
  val content = block()
  AgentToolResult(content, summary(content))
}
catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) {
  val message = error.message?.take(400) ?: "工具执行失败"
  AgentToolResult(JSONObject().put("status", "error").put("message", message).toString(), message, failed = true)
}

internal suspend fun prepareChange(
  repository: SextantRepository, store: AgentConversationStore, runId: String, args: ChangeArgs,
  ledger: AgentLedgerQuery, attemptId: String? = null,
): String {
  require(args.action in setOf("create", "update", "delete")) { "action 必须是 create/update/delete" }
  val choice = args.candidates.isNotEmpty()
  if (choice) {
    require(args.action != "create" && args.transactionId == null && args.candidates.size in 2..8 &&
      args.candidates.distinctBy { it.transactionId }.size == args.candidates.size) { "请提供 2 至 8 笔不同的候选流水" }
  }
  val group = if (choice) UUID.randomUUID().toString() else null
  val proposals = if (choice) args.candidates.map { target ->
    args.copy(transactionId = target.transactionId, expectedUpdatedAt = target.expectedUpdatedAt, candidates = emptyList())
  } else listOf(args)
  val drafts = proposals.map { proposal -> buildDraft(repository, ledger, runId, proposal).copy(
    choiceGroup = group, status = if (choice) "candidate" else "pending") }
  store.update { state ->
    require(state.ownsRun(runId, attemptId)) { "任务已停止" }
    val targets = drafts.map { it.transactionId }.toSet()
    val replacedGroups = state.drafts.filter { it.status == "candidate" && it.transactionId in targets }.mapNotNull { it.choiceGroup }
    val retained = state.drafts.map {
      if (it.awaitingInput && (it.transactionId in targets || it.choiceGroup in replacedGroups)) it.copy(status = "superseded") else it
    }
    require(retained.count { it.runId == runId && it.awaitingInput } + drafts.size <= 20) { "一次最多准备20项变更" }
    state.copy(drafts = retained + drafts)
  }
  return if (choice) JSONObject().put("status", "awaiting_selection").put("message", "候选已展示，请用户点选；仍需确认。").toString()
    else JSONObject().put("status", "awaiting_confirmation").put("draftId", drafts.single().id)
      .put("message", "草稿已展示给用户，尚未修改账本。用户可在卡片中确认或拒绝。").toString()
}

private suspend fun buildDraft(repository: SextantRepository, ledger: AgentLedgerQuery, runId: String, args: ChangeArgs): AgentDraft {
  val before = if (args.action == "create") null else {
    val id = requireNotNull(args.transactionId) { "需要交易ID" }
    val observedVersion = ledger.transactionVersion(id) ?: error("快照中没有这笔记录，请重新分析")
    require(args.expectedUpdatedAt == observedVersion) { "需要提供查询得到的 updatedAt；记录可能已变化，请重新查询" }
    val current = repository.findTransaction(id)?.transaction ?: error("这笔记录已不存在")
    require(current.updatedAt == observedVersion) { "这笔记录在查询后已被修改，请重新分析" }
    current
  }
  val type = args.type ?: before?.type?.storageValue() ?: error("需要流水方向")
  require(type in setOf("expense", "income", "transfer")) { "无效的流水方向" }
  val amount = args.amountCents ?: before?.amountCents ?: error("需要金额（整数分）")
  require(amount > 0) { "金额必须大于 0" }
  val date = args.date?.let { value ->
    if (value.length == 10) LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    else OffsetDateTime.parse(value).toInstant().toEpochMilli()
  } ?: before?.transactionDate ?: error("需要日期")
  return AgentDraft(UUID.randomUUID().toString(), runId, args.action, before?.id ?: UUID.randomUUID().toString(),
    type, amount, if (args.clearCategory) null else args.categoryId ?: before?.categoryId,
    if (args.clearPaymentMethod) null else args.paymentMethodId ?: before?.paymentMethodId,
    args.description ?: before?.description.orEmpty(), date, before?.updatedAt, before)
}

internal fun ledgerTools(repository: SextantRepository, store: AgentConversationStore, runId: String, attemptId: String?, ledger: AgentLedgerQuery, trace: AgentTraceRecorder): ToolRegistry {
  val schemaTool = object : SimpleTool<SchemaArgs>(typeToken<SchemaArgs>(), "inspect_ledger_schema", "查看可查询的账本表结构、字段、时间和消费统计口径。查询前先查看结构。") {
    override suspend fun execute(args: SchemaArgs): String = trace.tool("inspect_ledger_schema", Json.encodeToString(SchemaArgs.serializer(), args), "正在查看账本结构…") {
      toolResult({ "账本结构与统计口径已读取" }) { ledger.schema() }
    }
  }
  val queryTool = object : SimpleTool<QueryArgs>(typeToken<QueryArgs>(), "query_ledger", "在一致的只读账本快照上执行自由 SQLite SELECT/WITH。支持 JOIN、聚合、子查询、窗口函数。最多返回100行/24000字符，超限请聚合或分批。不能访问内部设置与密钥。") {
    override suspend fun execute(args: QueryArgs): String = trace.tool("query_ledger", Json.encodeToString(QueryArgs.serializer(), args), "正在查询账本…") {
      toolResult({ content ->
        val result = JSONObject(content)
        "返回 ${result.getJSONArray("rows").length()} 行" + if (result.optBoolean("truncated")) "，结果已截断" else ""
      }) { ledger.query(args.sql) }
    }
  }
  val changeTool = object : SimpleTool<ChangeArgs>(typeToken<ChangeArgs>(), "prepare_transaction_change", "生成待用户确认的新增(create)、修改(update)、删除(delete)草稿。不会入账。修改/删除必须先查询并提供确切 transactionId 和查询得到的 expectedUpdatedAt（updatedAt）；新增需提供 type(expense/income/transfer)、正整数分 amountCents 和 ISO date(yyyy-MM-dd 或带偏移量时间)。分类/来源使用查询得到的ID。未提供的修改字段保留原值。目标有多笔可能时用 candidates 传入每笔的 transactionId、expectedUpdatedAt，共用同一组修改字段，省略顶层 transactionId。缺少修改内容时先问用户。") {
    override suspend fun execute(args: ChangeArgs): String = trace.tool("prepare_transaction_change", Json.encodeToString(ChangeArgs.serializer(), args), "正在整理待确认的变更…") {
      toolResult({ if (args.candidates.isEmpty()) "草稿已生成，等待确认" else "候选流水已列出，等待选择" }) { prepareChange(repository, store, runId, args, ledger, attemptId) }
    }
  }
  val detailTool = object : SimpleTool<DetailArgs>(typeToken<DetailArgs>(), "show_ledger_details",
    "按时间、方向和可选分类查合计并在本条回答附上查看明细入口。日期为 yyyy-MM-dd，起止日均包含；type 为 expense/income/transfer。支出按消费统计口径，分类包含子分类。比较两期分别调用。") {
    override suspend fun execute(args: DetailArgs): String = trace.tool("show_ledger_details", Json.encodeToString(args), "正在整理明细…") {
      toolResult({ "已关联账本明细" }) {
        val type = TransactionType.entries.singleOrNull { it.storageValue() == args.type } ?: error("无效的流水方向")
        val detail = ledger.detail(args.title, LocalDate.parse(args.startDate).toStartOfDayMillis(),
          LocalDate.parse(args.endDateInclusive).plusDays(1).toStartOfDayMillis(), type, args.categoryId)
        Json.encodeToString(detail)
      }
    }
  }
  return ToolRegistry { tool(schemaTool); tool(queryTool); tool(changeTool); tool(detailTool) }
}
