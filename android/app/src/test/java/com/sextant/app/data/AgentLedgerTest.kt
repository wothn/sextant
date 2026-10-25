package com.sextant.app.data

import android.content.Context
import com.sextant.app.agent.ChangeArgs
import com.sextant.app.agent.prepareChange
import com.sextant.app.agent.AgentTraceRecorder
import com.sextant.app.agent.AgentToolResult
import com.sextant.app.agent.SchemaArgs
import com.sextant.app.agent.generateConversationTitle
import com.sextant.app.agent.normalizeConversationTitle
import com.sextant.app.agent.streamingLedgerStrategy
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.serialization.typeToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.time.LocalDate
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentLedgerTest {
  private lateinit var context: Context
  private lateinit var repository: SextantRepository
  private lateinit var zone: TimeZone
  private val date = LocalDate.of(2026, 10, 25)

  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    File(context.noBackupFilesDir, "agent-test.json").delete()
    File(context.noBackupFilesDir, "agent-test.json.bak").delete()
    zone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    repository = SextantRepository(context)
  }

  @After fun tearDown() {
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    TimeZone.setDefault(zone)
  }

  private fun draft() = AgentDraft(UUID.randomUUID().toString(), "run", "create", UUID.randomUUID().toString(),
    amountCents = 3500, description = "午饭", transactionDate = date.toStartOfDayMillis())

  private suspend fun prepare(store: AgentConversationStore, args: ChangeArgs): String {
    val version = args.transactionId?.let { repository.findTransaction(it)?.transaction?.updatedAt }
    return AgentLedgerQuery.capture(context, repository).use {
      prepareChange(repository, store, "run", args.copy(expectedUpdatedAt = version), it)
    }
  }
  @Test fun legacyConversationRemainsAvailableAfterCreatingAndReopeningHistory() = runBlocking {
    File(context.noBackupFilesDir, "agent-test.json").writeText("""{
      "messages":[{"id":"m1","role":"user","text":"上个月花了多少","runId":"run"}],
      "drafts":[{"id":"d1","runId":"run","action":"create","transactionId":"tx","amountCents":3500}],
      "trace":[{"id":"t1","runId":"run","kind":"model","title":"思考","status":"completed","reasoning":"先查询账本"}],
      "status":"awaiting_confirmation","runId":"run"
    }""")
    val store = AgentConversationStore(context, "agent-test.json")
    val legacy = store.load()
    val legacyId = requireNotNull(store.activeId.value)
    assertEquals("上个月花了多少", store.history.value.single().title)
    store.newConversation()
    assertEquals(AgentConversation(), store.load())
    assertEquals(1, store.history.value.size)
    val reloaded = AgentConversationStore(context, "agent-test.json")
    assertEquals(AgentConversation(), reloaded.load())
    reloaded.openConversation(legacyId)
    assertEquals(legacy, reloaded.load())
    assertEquals(legacy, AgentConversationStore(context, "agent-test.json").load())
  }

  @Test fun conversationUpdatesStayInTheSelectedHistoryAndEmptyChatsAreReused() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.load()
    val firstId = requireNotNull(store.activeId.value)
    store.update { it.copy(messages = listOf(AgentMessage("m1", "user", "第一段对话"))) }
    store.newConversation()
    val secondId = requireNotNull(store.activeId.value)
    store.newConversation()
    assertEquals(secondId, store.activeId.value)
    store.update { it.copy(messages = listOf(AgentMessage("m2", "user", "第二段对话"))) }
    store.openConversation(firstId)
    store.update { it.copy(messages = it.messages + AgentMessage("m3", "assistant", "第一段的回答")) }
    assertEquals(2, store.history.value.size)
    assertEquals("第一段的回答", store.history.value.first { it.id == firstId }.preview)
    val reloaded = AgentConversationStore(context, "agent-test.json")
    assertEquals(2, reloaded.load().messages.size)
    reloaded.openConversation(secondId)
    assertEquals(listOf(AgentMessage("m2", "user", "第二段对话")), reloaded.load().messages)
    reloaded.newConversation()
    val emptyId = reloaded.activeId.value
    reloaded.openConversation(firstId)
    reloaded.newConversation()
    assertEquals(emptyId, reloaded.activeId.value)
    assertEquals(2, reloaded.history.value.size)
  }

  @Test fun generatedTitlePersistsWithoutOverwritingOrSwitchingConversations() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(messages = listOf(AgentMessage("m1", "user", "看看餐饮开销"))) }
    val firstId = requireNotNull(store.activeId.value)
    val firstUpdatedAt = store.history.value.single().updatedAt
    store.newConversation()
    store.update { it.copy(messages = listOf(AgentMessage("m2", "user", "第二段对话"))) }
    val secondId = requireNotNull(store.activeId.value)

    store.setTitleIfAbsent(firstId, "月度餐饮分析")
    assertEquals(secondId, store.activeId.value)
    assertEquals("月度餐饮分析", store.history.value.first { it.id == firstId }.title)
    assertEquals(firstUpdatedAt, store.history.value.first { it.id == firstId }.updatedAt)
    store.setTitleIfAbsent(firstId, "不应覆盖原标题")

    val reloaded = AgentConversationStore(context, "agent-test.json")
    reloaded.load()
    assertEquals("月度餐饮分析", reloaded.history.value.first { it.id == firstId }.title)
  }

  @Test fun runningConversationCannotBeReplacedOrSwitched() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(messages = listOf(AgentMessage("m1", "user", "历史"))) }
    val firstId = requireNotNull(store.activeId.value)
    store.newConversation()
    val running = store.update { it.copy(status = "running", runId = "live") }
    assertEquals("请先停止当前任务", runCatching { store.newConversation() }.exceptionOrNull()?.message)
    assertEquals("请先停止当前任务", runCatching { store.openConversation(firstId) }.exceptionOrNull()?.message)
    assertEquals(running, store.load())
    assertEquals(running, AgentConversationStore(context, "agent-test.json").load())
  }

  @Test fun failedHistoryWriteAndUnknownIdLeaveCurrentConversationIntact() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    val original = store.update { it.copy(messages = listOf(AgentMessage("m1", "user", "保留这段对话"))) }
    val originalId = store.activeId.value
    assertEquals("这段对话已不存在", runCatching { store.openConversation("missing") }.exceptionOrNull()?.message)
    val pending = File(context.noBackupFilesDir, "agent-test.json.pending")
    pending.mkdirs()
    val blocker = File(pending, "blocker").apply { writeText("prevent replacement") }
    try {
      assertTrue(runCatching { store.newConversation() }.isFailure)
      assertEquals(originalId, store.activeId.value)
      assertEquals(original, store.load())
      assertEquals(original, AgentConversationStore(context, "agent-test.json").load())
    } finally {
      blocker.delete()
      pending.delete()
    }
  }

  @Test fun repeatedApplyReturnsReceiptAndCreatesOnlyOneTransaction() = runBlocking {
    val operation = draft()
    val first = repository.applyAgentDraft(operation)
    assertEquals(first, repository.applyAgentDraft(operation))
    assertEquals(1, repository.loadState().transactions.size)
    assertEquals(3500L, repository.findTransaction(operation.transactionId)!!.transaction.amountCents)
    assertFalse(repository.database.appSettingDao().listBackupSettings().any { it.key.startsWith("agent_operation_") })
  }

  @Test fun staleUpdateAndDeleteLeaveLedgerAndReceiptUnchanged() = runBlocking {
    val operation = draft()
    repository.applyAgentDraft(operation)
    val before = repository.findTransaction(operation.transactionId)!!.transaction
    repository.updateTransaction(before.id, before.type, 4000, null, null, "已改", before.transactionDate, expectedUpdatedAt = before.updatedAt)
    for (action in listOf("update", "delete")) {
      val stale = operation.copy(id = UUID.randomUUID().toString(), action = action, amountCents = 1000, expectedUpdatedAt = before.updatedAt)
      val error = runCatching { repository.applyAgentDraft(stale) }.exceptionOrNull()
      assertEquals("这笔记录已被修改，请重新生成变更", error?.message)
      assertNull(repository.database.appSettingDao().findValue("agent_operation_${stale.id}"))
    }
    assertEquals(4000L, repository.findTransaction(before.id)!!.transaction.amountCents)
  }

  @Test fun invalidReferencesAndAmountsRollBack() = runBlocking {
    for (operation in listOf(draft().copy(categoryId = "missing"), draft().copy(paymentMethodId = "missing"), draft().copy(amountCents = 0))) {
      assertTrue(runCatching { repository.applyAgentDraft(operation) }.isFailure)
      assertNull(repository.findTransaction(operation.transactionId))
      assertNull(repository.database.appSettingDao().findValue("agent_operation_${operation.id}"))
    }
  }

  @Test fun sqlCanJoinAndAggregateButCannotSeeSettingsOrModifyLedger() = runBlocking {
    val operation = draft()
    repository.applyAgentDraft(operation)
    AgentLedgerQuery.capture(context, repository).use { query ->
      val result = JSONObject(query.query("WITH totals AS (SELECT type, SUM(amountCents) AS total FROM transactions GROUP BY type) SELECT total FROM totals WHERE type='expense'"))
      assertEquals(3500L, result.getJSONArray("rows").getJSONArray(0).getLong(0))
      assertFalse(result.getBoolean("truncated"))
      assertTrue(runCatching { query.query("SELECT * FROM app_settings") }.isFailure)
      assertTrue(runCatching { query.query("DELETE FROM transactions") }.isFailure)
      assertTrue(runCatching { query.query("WITH x AS (SELECT 1) DELETE FROM transactions") }.isFailure)
      assertTrue(runCatching { query.query("ATTACH DATABASE ':memory:' AS private") }.isFailure)
      assertEquals(1, repository.loadState().transactions.size)
    }
  }

  @Test fun snapshotStaysConsistentAndLargeResultsDeclareTruncation() = runBlocking {
    repository.applyAgentDraft(draft())
    AgentLedgerQuery.capture(context, repository).use { query ->
      repository.applyAgentDraft(draft())
      val count = JSONObject(query.query("SELECT COUNT(*) FROM transactions")).getJSONArray("rows").getJSONArray(0).getInt(0)
      assertEquals(1, count)
      val large = JSONObject(query.query("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<150) SELECT x FROM n"))
      assertTrue(large.getBoolean("truncated"))
      assertEquals(100, large.getJSONArray("rows").length())
    }
  }

  @Test fun preparePersistsDraftWithoutPostingAndNewStoreRestoresIt() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(runId = "run", status = "running") }
    val result = JSONObject(prepare(store, ChangeArgs("create", type = "expense", amountCents = 3500,
      description = "午饭", date = "2026-10-25")))
    assertEquals("awaiting_confirmation", result.getString("status"))
    assertTrue(repository.loadState().transactions.isEmpty())
    assertEquals(1, store.state.value.drafts.size)
    val savedFile = File(context.noBackupFilesDir, "agent-test.json")
    assertTrue("File missing: $savedFile", savedFile.exists())
    assertTrue(savedFile.readText().contains("storageVersion"))
    val restored = AgentConversationStore(context, "agent-test.json").load()
    assertEquals(3500L, restored.drafts.single().amountCents)
    assertEquals(date.toStartOfDayMillis(), restored.drafts.single().transactionDate)
    repository.applyAgentDraft(restored.drafts.single())
    assertEquals(1, repository.loadState().transactions.size)
  }

  @Test fun preparingAnEditKeepsUnspecifiedFieldsAndReplacesEarlierDraft() = runBlocking {
    val operation = draft()
    repository.applyAgentDraft(operation)
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(runId = "run", status = "running") }
    prepare(store, ChangeArgs("update", transactionId = operation.transactionId, amountCents = 3200))
    prepare(store, ChangeArgs("update", transactionId = operation.transactionId, amountCents = 3000))
    val pending = store.load().drafts.filter { it.status == "pending" }
    assertEquals(1, pending.size)
    assertEquals("午饭", pending.single().description)
    assertEquals(3000L, pending.single().amountCents)
    assertEquals(3500L, repository.findTransaction(operation.transactionId)!!.transaction.amountCents)
  }

  @Test fun cancelledRunCannotPrepareChanges() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(runId = "run", status = "cancelled") }
    assertTrue(runCatching {
      prepare(store, ChangeArgs("create", type = "expense", amountCents = 100, date = "2026-10-25"))
    }.isFailure)
    assertTrue(store.load().drafts.isEmpty())
  }

  @Test fun reasoningStreamsInMemoryAndFinalResponsePersistsWithoutDuplicatingDeltas() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(runId = "run", status = "running") }
    var time = 0L
    val recorder = AgentTraceRecorder(store, "run") { time }
    recorder.startModel()
    time = 200
    recorder.reasoning("先核对统计范围。")
    assertEquals("先核对统计范围。", store.state.value.trace.single().reasoning)
    // 高频片段只刷新界面，完成时再确保落盘。
    assertEquals("", AgentConversationStore(context, "agent-test.json").load().trace.single().reasoning)
    time = 400
    recorder.reasoning("然后查询。")
    recorder.text("结果")
    time = 600
    recorder.text("为零。")
    recorder.finishModel()
    val entry = AgentConversationStore(context, "agent-test.json").load().trace.single()
    assertEquals("先核对统计范围。然后查询。", entry.reasoning)
    assertEquals("结果为零。", entry.text)
    assertEquals("completed", entry.status)
    assertEquals(600L, entry.elapsedMillis)
  }

  @Test fun toolTraceDistinguishesLogicalErrorsFromSuccessfulExecution() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(runId = "run", status = "running") }
    val recorder = AgentTraceRecorder(store, "run")
    recorder.tool("query_ledger", "{\"sql\":\"SELECT 1\"}", "正在查询账本…") {
      AgentToolResult("{\"rows\":[[1]],\"truncated\":false}", "返回 1 行")
    }
    recorder.tool("query_ledger", "{\"sql\":\"SELECT missing\"}", "正在查询账本…") {
      AgentToolResult("{\"status\":\"error\",\"message\":\"字段不存在\"}", "字段不存在", failed = true)
    }
    val entries = AgentConversationStore(context, "agent-test.json").load().trace
    assertEquals(listOf("completed", "failed"), entries.map { it.status })
    assertEquals("返回 1 行", entries.first().summary)
    assertTrue(entries.first().arguments.contains("SELECT 1"))
    assertEquals("字段不存在", entries.last().summary)
  }

  @Test fun traceDisplayLimitsDoNotTruncateTheActualToolResult() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(runId = "run", status = "running") }
    val recorder = AgentTraceRecorder(store, "run")
    val full = "x".repeat(20000)
    val actual = recorder.tool("query_ledger", "{}", "正在查询账本…") { AgentToolResult(full, "完整结果") }
    assertEquals(full, actual)
    assertEquals(16000, store.state.value.trace.single().result.length)
    assertTrue(store.state.value.trace.single().truncated)
  }

  @Test fun lateStreamFramesCannotOverwriteAnotherRun() = runBlocking {
    val store = AgentConversationStore(context, "agent-test.json")
    store.update { it.copy(runId = "run", status = "running") }
    val recorder = AgentTraceRecorder(store, "run")
    recorder.startModel()
    store.update { it.copy(runId = "new-run", trace = emptyList()) }
    recorder.reasoning("来自旧请求的内容")
    recorder.finishModel()
    assertTrue(store.state.value.trace.isEmpty())
    assertEquals("new-run", AgentConversationStore(context, "agent-test.json").load().runId)
  }

  @Test fun conversationTitleRequestReturnsCleanShortTitle() = runBlocking {
    var body: JSONObject? = null
    val engine = MockEngine { request ->
      body = JSONObject((request.body as OutgoingContent.ByteArrayContent).bytes().toString(Charsets.UTF_8))
      respond("""{"id":"title","object":"chat.completion","created":1,"model":"deepseek-flash","system_fingerprint":"test","choices":[{"index":0,"message":{"role":"assistant","content":"标题：“月度餐饮分析”。\n多余说明"},"finish_reason":"stop"}]}""",
        headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val http = HttpClient(engine)
    val client = DeepSeekLLMClient(apiKey = "test-key", httpClientFactory = KtorKoogHttpClient.Factory(http))
    try {
      val title = generateConversationTitle(client, "deepseek-flash", "分析这个月餐饮花了多少", "本月餐饮共支出1200元")
      assertEquals("月度餐饮分析", title)
      val request = requireNotNull(body)
      assertEquals("deepseek-flash", request.getString("model"))
      assertTrue(request.getJSONArray("messages").getJSONObject(0).getString("content").contains("只输出标题本身"))
      assertTrue(request.getJSONArray("messages").getJSONObject(1).getString("content").contains("本月餐饮共支出1200元"))
      assertEquals("出行账单", normalizeConversationTitle("# **出行账单。**"))
      assertNull(normalizeConversationTitle(" \n "))
    } finally {
      client.close()
      http.close()
    }
  }

  @Test fun streamingToolContinuationIncludesAssistantCallAndOriginalReasoning() = runBlocking {
    var requests = 0
    var toolExecutions = 0
    val attachment = MessagePart.Attachment(AttachmentSource.Image(AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3)), "jpg", "image/jpeg"))
    val executor = object : PromptExecutor() {
      override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant = error("必须使用流式调用")
      override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("未使用内容审核")
      override fun close() = Unit
      override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = flow {
        requests++
        val users = prompt.messages.filterIsInstance<Message.User>()
        val user = users.single { message -> message.parts.any { it is MessagePart.Attachment } }
        assertEquals(1, users.sumOf { it.parts.filterIsInstance<MessagePart.Attachment>().size })
        assertEquals(attachment, user.parts.filterIsInstance<MessagePart.Attachment>().single())
        val response = if (requests == 1) {
          Message.Assistant(parts = listOf(
            MessagePart.Reasoning(id = "thought-1", content = listOf("先查询，再回答。")),
            MessagePart.Tool.Call(id = "call-1", tool = "inspect_ledger_schema", args = buildJsonObject { }),
          ), metaInfo = ResponseMetaInfo.Empty, finishReason = "tool_calls")
        } else {
          val previous = prompt.messages.filterIsInstance<Message.Assistant>().single()
          assertEquals("call-1", previous.parts.filterIsInstance<MessagePart.Tool.Call>().single().id)
          assertEquals(listOf("先查询，再回答。"), previous.parts.filterIsInstance<MessagePart.Reasoning>().single().content)
          assertTrue(prompt.messages.last().toString().contains("call-1"))
          Message.Assistant(parts = listOf(MessagePart.Text("查询完成")), metaInfo = ResponseMetaInfo.Empty, finishReason = "stop")
        }
        response.toStreamFrames().forEach { emit(it) }
      }
    }
    val tool = object : SimpleTool<SchemaArgs>(typeToken<SchemaArgs>(), "inspect_ledger_schema", "测试查询") {
      override suspend fun execute(args: SchemaArgs): String { toolExecutions++; return "{}" }
    }
    val agent = AIAgent(promptExecutor = executor, llmModel = DeepSeekModels.DeepSeekV4Flash,
      strategy = streamingLedgerStrategy(listOf(MessagePart.Text("查询账本"), attachment)), toolRegistry = ToolRegistry { tool(tool) })
    try { assertEquals("查询完成", agent.run("查询账本")) } finally { agent.close() }
    assertEquals(2, requests)
    assertEquals(1, toolExecutions)
  }
}
