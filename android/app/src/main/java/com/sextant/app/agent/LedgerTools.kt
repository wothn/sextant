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
data class QueryArgs(val sql: String)
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
  val draft = AgentDraft(UUID.randomUUID().toString(), runId, args.action, before?.id ?: UUID.randomUUID().toString(),
    type, amount, if (args.clearCategory) null else args.categoryId ?: before?.categoryId,
    if (args.clearPaymentMethod) null else args.paymentMethodId ?: before?.paymentMethodId,
    args.description ?: before?.description.orEmpty(), date, before?.updatedAt, before)
  store.update { state ->
    require(state.ownsRun(runId, attemptId)) { "任务已停止" }
    require(state.drafts.count { it.runId == runId && it.status == "pending" } < 20) { "一次最多准备20项变更" }
    state.copy(drafts = state.drafts.map {
      if (before != null && it.transactionId == before.id && it.status == "pending") it.copy(status = "superseded") else it
    } + draft)
  }
  return JSONObject().put("status", "awaiting_confirmation").put("draftId", draft.id)
    .put("message", "草稿已展示给用户，尚未修改账本。用户可在卡片中确认或拒绝。").toString()
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
  val changeTool = object : SimpleTool<ChangeArgs>(typeToken<ChangeArgs>(), "prepare_transaction_change", "生成待用户确认的新增(create)、修改(update)、删除(delete)草稿。不会入账。修改/删除必须先查询并提供确切 transactionId 和查询得到的 expectedUpdatedAt（updatedAt）；新增需提供 type(expense/income/transfer)、正整数分 amountCents 和 ISO date(yyyy-MM-dd 或带偏移量时间)。分类/来源使用查询得到的ID。未提供的修改字段保留原值。缺少关键信息应先问用户。") {
    override suspend fun execute(args: ChangeArgs): String = trace.tool("prepare_transaction_change", Json.encodeToString(ChangeArgs.serializer(), args), "正在整理待确认的变更…") {
      toolResult({ "草稿已生成，等待确认，尚未修改账本" }) { prepareChange(repository, store, runId, args, ledger, attemptId) }
    }
  }
  return ToolRegistry { tool(schemaTool); tool(queryTool); tool(changeTool) }
}
