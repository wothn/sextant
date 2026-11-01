package com.sextant.app.data

import android.content.Context
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import com.sextant.app.agent.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.File
import java.time.LocalDate
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class AgentSessionTest {
  private lateinit var context: Context
  private lateinit var repository: SextantRepository
  private lateinit var zone: TimeZone
  private lateinit var fileName: String
  private val longReasoning = "完整思考".repeat(6000)
  private val longResult = "完整结果".repeat(6000)

  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    repository = SextantRepository(context)
    zone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    fileName = "session-${UUID.randomUUID()}.json"
  }

  @After fun tearDown() {
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    TimeZone.setDefault(zone)
    File(context.noBackupFilesDir, fileName).delete()
    File(context.noBackupFilesDir, "$fileName.sessions").listFiles()?.forEach { it.delete() }
    File(context.noBackupFilesDir, "$fileName.sessions").delete()
  }

  private fun assistant(text: String, reasoning: String = "继续分析") = Message.Assistant(
    listOf(MessagePart.Reasoning(content = listOf(reasoning)), MessagePart.Text(text)), ResponseMetaInfo.Empty, "stop")

  private fun call(id: String, sql: String) = MessagePart.Tool.Call(id, "query_ledger", buildJsonObject { put("sql", sql) })

  private fun executor(block: suspend (Prompt) -> Message.Assistant) = object : PromptExecutor() {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant = error("必须使用流式调用")
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("未使用")
    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = flow {
      block(prompt).toStreamFrames().forEach { emit(it) }
    }
    override fun close() = Unit
  }

  private fun agent(store: AgentConversationStore, executor: PromptExecutor) = LedgerAgent(context, repository, store,
    models = { object : AgentModelConnection {
      override val executor = executor
      override fun close() = Unit
    } })

  private suspend fun begin(store: AgentConversationStore, run: String, attempt: String = run) {
    store.update { it.copy(runId = run, attemptId = attempt, status = "running",
      messages = it.messages + AgentMessage("user-$run", "user", "分析$run", run)) }
  }

  @Test fun laterTurnsPreserveTheEntireRequestPrefixAndPersistTheirOwnContext() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    begin(store, "first")
    var firstRequest = emptyList<Message>()
    agent(store, executor { request ->
      firstRequest = request.messages.toList()
      assertEquals(1, request.messages.filterIsInstance<Message.System>().size)
      assertFalse(request.messages.first().textContent().contains("现在是"))
      assertEquals("分析first", request.messages[1].textContent())
      assertTrue(request.messages.last().textContent().contains("【应用运行上下文】"))
      // 请求发送之前已经落盘，网络错误或进程退出也不会丢失运行上下文。
      assertEquals(request.messages.last(), AgentTranscriptCodec.decode(store.load().transcript.single()).single())
      assistant("第一轮完成")
    }).run("first", AgentSettings("test-key"), "first")
    val firstProtocol = AgentTranscriptCodec.decode(store.load().transcript.single())
    store.update { it.copy(status = "completed") }
    val restored = AgentConversationStore(context, fileName)
    begin(restored, "second")
    agent(restored, executor { request ->
      assertEquals(firstRequest, request.messages.take(firstRequest.size))
      assertEquals(firstProtocol.last(), request.messages[firstRequest.size])
      assertEquals("分析second", request.messages[request.messages.lastIndex - 1].textContent())
      assertTrue(request.messages.last().textContent().contains("【应用运行上下文】"))
      assistant("第二轮完成")
    }).run("second", AgentSettings("test-key"), "second")
    assertEquals(firstProtocol, AgentTranscriptCodec.decode(restored.load().transcript.first()))
  }

  @Test fun retryAfterNetworkFailureAppendsFreshContextWithoutRewritingSavedInput() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    begin(store, "run", "old")
    var failedRequest = emptyList<Message>()
    assertTrue(runCatching {
      agent(store, executor { request ->
        failedRequest = request.messages.toList()
        error("模拟网络错误")
      }).run("run", AgentSettings("test-key"), "old")
    }.isFailure)
    val restored = AgentConversationStore(context, fileName)
    restored.update { it.copy(attemptId = "new") }
    agent(restored, executor { request ->
      assertEquals(failedRequest, request.messages.take(failedRequest.size))
      assertEquals(failedRequest.size + 1, request.messages.size)
      assertTrue(request.messages.last().textContent().contains("【应用运行上下文】"))
      assistant("恢复完成")
    }).run("run", AgentSettings("test-key"), "new")
    assertEquals(2, AgentTranscriptCodec.decode(restored.load().transcript.single()).count {
      it.textContent().contains("【应用运行上下文】")
    })
  }

  @Test fun secondTurnAfterRestartReplaysReasoningCallsResultsAndReceiptsOnTheWire() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    begin(store, "first")
    var requests = 0
    val first = executor { request ->
      requests++
      if (requests == 1) Message.Assistant(listOf(
        MessagePart.Reasoning(id = "reasoning-original", content = listOf(longReasoning)),
        call("call-first", "SELECT 35 AS amount"), call("call-second", "SELECT 42 AS amount")), ResponseMetaInfo.Empty, "tool_calls")
      else {
        val results = request.messages.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>()
        assertEquals(listOf("call-first", "call-second"), results.map { it.id })
        assistant("第一轮完成", "最终回答也有思考")
      }
    }
    assertEquals("第一轮完成", agent(store, first).run("first", AgentSettings("test-key"), "first"))
    store.update { it.copy(status = "completed", messages = it.messages + AgentMessage("answer-first", "assistant", "第一轮完成", "first") +
      AgentMessage("receipt", "receipt", "已保存修改")) }
    val reloaded = AgentConversationStore(context, fileName)
    begin(reloaded, "second")
    var wire: JSONObject? = null
    val engine = MockEngine { request ->
      wire = JSONObject((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
      respond("""{"id":"response","object":"chat.completion","created":1,"model":"deepseek-flash","system_fingerprint":"test","choices":[{"index":0,"message":{"role":"assistant","content":"完成","reasoning_content":"新的思考"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}""",
        headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val http = HttpClient(engine)
    val client = DeepSeekLLMClient("test-key", httpClientFactory = KtorKoogHttpClient.Factory(http))
    try {
      val history = AgentContextBuilder(AgentImageStore(context)).build(reloaded.load())
      client.execute(prompt("wire-test") { history.forEach { message(it) } }, DeepSeekModels.DeepSeekV4Flash,
        listOf(ToolDescriptor("query_ledger", "查询账本")))
      val messages = requireNotNull(wire).getJSONArray("messages")
      val assistants = (0 until messages.length()).map { messages.getJSONObject(it) }.filter { it.getString("role") == "assistant" }
      assertEquals(2, assistants.size)
      assertEquals(longReasoning, assistants[0].getString("reasoning_content"))
      assertEquals("call-first", assistants[0].getJSONArray("tool_calls").getJSONObject(0).getString("id"))
      assertEquals("call-second", assistants[0].getJSONArray("tool_calls").getJSONObject(1).getString("id"))
      assertEquals("最终回答也有思考", assistants[1].getString("reasoning_content"))
      val tools = (0 until messages.length()).map { messages.getJSONObject(it) }.filter { it.getString("role") == "tool" }
      assertEquals(listOf("call-first", "call-second"), tools.map { it.getString("tool_call_id") })
      assertTrue(tools[0].getString("content").contains("35"))
      assertTrue(messages.toString().contains("应用操作回执：已保存修改"))
      assertEquals(1, (0 until messages.length()).count { messages.getJSONObject(it).optString("content") == "分析second" })
    } finally { client.close(); http.close() }
    val second = executor { request ->
      assertEquals(2, request.messages.filterIsInstance<Message.Assistant>().size)
      assistant("第二轮完成")
    }
    assertEquals("第二轮完成", agent(reloaded, second).run("second", AgentSettings("test-key"), "second"))
    assertEquals(2, reloaded.load().transcript.size)
  }

  @Test fun interruptedToolBatchResumesOnlyMissingResultsAndPreservesUntruncatedData() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    begin(store, "run", "old")
    val firstCall = call("done", "SELECT 1")
    val missingCall = call("missing", "SELECT 2")
    val messages = listOf<Message>(Message.Assistant(listOf(MessagePart.Reasoning(content = listOf(longReasoning)),
      firstCall, missingCall), ResponseMetaInfo.Empty, "tool_calls"),
      Message.User(listOf(MessagePart.Tool.Result("done", "query_ledger", longResult)), RequestMetaInfo.Empty))
    AgentRunSession(store, "run", "old", "user-run", emptyList()).checkpoint(messages, false)
    val reloaded = AgentConversationStore(context, fileName)
    reloaded.update { it.copy(attemptId = "new") }
    val resumed = executor { request ->
      val assistants = request.messages.filterIsInstance<Message.Assistant>()
      assertEquals(1, assistants.size)
      assertEquals(longReasoning, assistants.single().parts.filterIsInstance<MessagePart.Reasoning>().single().content.single())
      val results = request.messages.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>()
      assertEquals(listOf("done", "missing"), results.map { it.id })
      assertEquals(longResult, results.first().output)
      assertTrue(results.last().output.contains("2"))
      assistant("恢复完成")
    }
    assertEquals("恢复完成", agent(reloaded, resumed).run("run", AgentSettings("test-key"), "new"))
    val saved = AgentConversationStore(context, fileName).load().transcript.single()
    assertTrue(saved.completed)
    assertEquals(2, AgentTranscriptCodec.decode(saved).flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>().size)
    // 同一实例上的旧回调也必须被新 attempt 拒绝。
    val before = reloaded.load()
    assertTrue(runCatching { AgentRunSession(reloaded, "run", "old", "user-run", emptyList()).checkpoint(messages, false) }.isFailure)
    val recorder = AgentTraceRecorder(reloaded, "run", attemptId = "old")
    recorder.startModel(); recorder.text("过期片段"); recorder.finishModel()
    assertEquals(before, reloaded.load())
  }

  @Test fun finalizedCheckpointSurvivesCrashBeforeUiCompletionWithoutAnotherModelRequest() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    begin(store, "run")
    AgentRunSession(store, "run", "run", "user-run", emptyList()).checkpoint(listOf(assistant("已经完成")), true)
    val restored = AgentConversationStore(context, fileName)
    assertEquals("已经完成", agent(restored, executor { error("不应再次请求模型") }).run("run", AgentSettings("test-key"), "run"))
  }

  @Test fun abandoningAnInterruptedRunClosesMissingCallsWithoutInventingSuccess() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    begin(store, "first")
    AgentRunSession(store, "first", "first", "user-first", emptyList()).checkpoint(listOf(
      Message.Assistant(listOf(call("unfinished", "SELECT 1")), ResponseMetaInfo.Empty, "tool_calls")), false)
    begin(store, "second")
    val history = AgentContextBuilder(AgentImageStore(context)).build(store.load())
    val failure = history.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>().single()
    assertEquals("unfinished", failure.id)
    assertTrue(failure.isError)
    assertTrue(failure.output.contains("中断"))
    assertEquals("分析second", history.last().textContent())
  }

  @Test fun orphanDraftIsReplacedWhenInterruptedToolHasNoSavedResult() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    begin(store, "run")
    val call = MessagePart.Tool.Call("prepare", "prepare_transaction_change", buildJsonObject {
      put("action", "create"); put("type", "expense"); put("amountCents", 3500); put("date", "2026-10-25")
    })
    AgentRunSession(store, "run", "run", "user-run", emptyList()).checkpoint(listOf(
      Message.Assistant(listOf(MessagePart.Reasoning(content = listOf("准备草稿")), call), ResponseMetaInfo.Empty, "tool_calls")), false)
    // 模拟工具生成草稿后，结果检查点写入之前进程退出。
    AgentLedgerQuery.capture(context, repository).use { ledger ->
      prepareChange(repository, store, "run", ChangeArgs("create", type = "expense", amountCents = 3500, date = "2026-10-25"), ledger, "run")
    }
    val orphanId = store.load().drafts.single().id
    val restored = AgentConversationStore(context, fileName)
    val resumed = executor { request ->
      val result = request.messages.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>().single()
      assertEquals("prepare", result.id)
      assistant("请确认草稿")
    }
    assertEquals("请确认草稿", agent(restored, resumed).run("run", AgentSettings("test-key"), "run"))
    val saved = restored.load()
    assertEquals(1, saved.drafts.size)
    assertNotEquals(orphanId, saved.drafts.single().id)
    assertEquals(listOf(saved.drafts.single().id), saved.transcript.single().draftIds)
    assertTrue(repository.loadState().transactions.isEmpty())
  }

  @Test fun staleStopCannotCancelNewAttemptOfTheSameRun() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val coordinator = AgentCoordinator(context, repository, scope, store = store)
    try {
      withTimeout(10000) { coordinator.ready.first { it } }
      begin(store, "run", "new")
      val before = store.load()
      coordinator.recordStopped("run", "old")
      assertEquals(before, store.load())
      coordinator.recordStopped("run", "new")
      assertEquals("cancelled", store.load().status)
    } finally { scope.cancel() }
  }

  @Test fun legacyAssistantIsExplicitReferenceAndDoesNotInventMissingReasoning() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    store.update { it.copy(messages = listOf(AgentMessage("old-user", "user", "旧问题", "old"),
      AgentMessage("old-answer", "assistant", "旧回答", "old"))) }
    begin(store, "new")
    val restored = AgentConversationStore(context, fileName)
    val history = AgentContextBuilder(AgentImageStore(context)).build(restored.load())
    assertTrue(history.none { it is Message.Assistant })
    assertTrue(history.any { it.textContent().contains("原始工具协议已缺失") && it.textContent().contains("旧回答") })
    assertEquals("旧回答", restored.load().messages[1].text)
  }

  @Test fun snapshotVersionConflictsAreRejectedBeforePreparingAndAgainAtConfirmation() = runBlocking {
    val operation = AgentDraft("create", "run", "create", "tx", amountCents = 10000,
      transactionDate = LocalDate.of(2026, 10, 25).toStartOfDayMillis())
    repository.applyAgentDraft(operation)
    val original = repository.findTransaction("tx")!!.transaction
    val store = AgentConversationStore(context, fileName)
    begin(store, "run")
    AgentLedgerQuery.capture(context, repository).use { snapshot ->
      repository.updateTransaction("tx", original.type, 20000, null, null, "其他入口修改", original.transactionDate, expectedUpdatedAt = original.updatedAt)
      for (action in listOf("update", "delete")) {
        assertEquals("这笔记录在查询后已被修改，请重新分析", runCatching {
          prepareChange(repository, store, "run", ChangeArgs(action, transactionId = "tx", amountCents = 5000,
            expectedUpdatedAt = original.updatedAt), snapshot, "run")
        }.exceptionOrNull()?.message)
      }
      assertTrue(store.load().drafts.isEmpty())
    }
    val current = repository.findTransaction("tx")!!.transaction
    AgentLedgerQuery.capture(context, repository).use { snapshot ->
      assertTrue(runCatching { prepareChange(repository, store, "run", ChangeArgs("delete", transactionId = "tx"), snapshot, "run") }.isFailure)
      prepareChange(repository, store, "run", ChangeArgs("update", transactionId = "tx", amountCents = 5000,
        expectedUpdatedAt = current.updatedAt), snapshot, "run")
    }
    repository.updateTransaction("tx", current.type, 30000, null, null, "又修改了", current.transactionDate, expectedUpdatedAt = current.updatedAt)
    assertTrue(runCatching { repository.applyAgentDraft(store.load().drafts.single()) }.isFailure)
    assertEquals(30000L, repository.findTransaction("tx")!!.transaction.amountCents)
  }

  @Test fun ledgerCommitWithFailedSessionWriteCanBeConfirmedAgainWithoutDuplicatePosting() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    val draft = AgentDraft("operation", "run", "create", "tx", amountCents = 3500,
      transactionDate = LocalDate.of(2026, 10, 25).toStartOfDayMillis())
    store.update { it.copy(status = "awaiting_confirmation", drafts = listOf(draft)) }
    val pending = File(context.noBackupFilesDir, "$fileName.pending").apply { mkdirs() }
    val blocker = File(pending, "blocker").apply { writeText("block") }
    try {
      assertTrue(runCatching { AgentDraftActions(repository, store).decide(draft.id, true) }.isFailure)
      assertEquals("pending", store.load().drafts.single().status)
      assertNotNull(repository.findTransaction("tx"))
    } finally { blocker.delete(); pending.delete() }
    val restored = AgentConversationStore(context, fileName)
    AgentDraftActions(repository, restored).decide(draft.id, true)
    assertEquals(1, repository.loadState().transactions.size)
    assertEquals("applied", restored.load().drafts.single().status)
    assertNotNull(restored.load().drafts.single().appliedUpdatedAt)
    assertEquals(1, restored.load().messages.count { it.role == "receipt" })
  }

  @Test fun writesReplaceOnlyTheActiveConversationBodyAndIndexFailurePreservesCommittedHistory() = runBlocking {
    val store = AgentConversationStore(context, fileName)
    begin(store, "first")
    store.update { it.copy(status = "completed") }
    val firstId = store.activeId.value
    store.newConversation()
    begin(store, "second")
    fun revision(id: String): String {
      val index = JSONObject(File(context.noBackupFilesDir, fileName).readText()).getJSONArray("conversations")
      return (0 until index.length()).map { index.getJSONObject(it) }.single { it.getString("id") == id }.getString("revision")
    }
    val firstRevision = revision(requireNotNull(firstId))
    val before = store.load()
    store.update { it.copy(activity = "变化") }
    assertEquals(firstRevision, revision(firstId))
    val stable = store.load()
    val pending = File(context.noBackupFilesDir, "$fileName.pending").apply { mkdirs() }
    val blocker = File(pending, "blocker").apply { writeText("block") }
    try {
      assertTrue(runCatching { store.update { it.copy(activity = "失败更新") } }.isFailure)
      assertEquals(stable, store.load())
      val restored = AgentConversationStore(context, fileName)
      assertEquals(stable, restored.load())
      assertEquals(2, restored.history.value.size)
      assertNotEquals(before.activity, stable.activity)
    } finally { blocker.delete(); pending.delete() }
  }

  @Test fun previousArchiveMigratesAllConversationsAtomicallyWithoutLosingDraftsOrTrace() = runBlocking {
    val legacy = """{"activeId":"old","conversations":[
      {"id":"old","updatedAt":100,"conversation":{"messages":[{"id":"u1","role":"user","text":"旧问题","runId":"old-run"}],
        "drafts":[{"id":"draft","runId":"old-run","action":"create","transactionId":"tx","amountCents":3500}],
        "trace":[{"id":"trace","runId":"old-run","kind":"model","title":"思考","reasoning":"旧思考"}],"status":"awaiting_confirmation"}},
      {"id":"other","updatedAt":50,"title":"保留标题","conversation":{"messages":[{"id":"u2","role":"user","text":"另一段对话"}]}}
    ]}"""
    val file = File(context.noBackupFilesDir, fileName).apply { writeText(legacy) }
    val store = AgentConversationStore(context, fileName)
    val before = store.load()
    val pending = File(context.noBackupFilesDir, "$fileName.pending").apply { mkdirs() }
    val blocker = File(pending, "blocker").apply { writeText("block") }
    try {
      assertTrue(runCatching { store.update { it.copy(activity = "迁移") } }.isFailure)
      assertEquals(legacy, file.readText())
      assertEquals(before, store.load())
    } finally { blocker.delete(); pending.delete() }
    store.update { it.copy(activity = "迁移") }
    val restored = AgentConversationStore(context, fileName)
    val migrated = restored.load()
    assertEquals(before.messages, migrated.messages)
    assertEquals(before.drafts, migrated.drafts)
    assertEquals(before.trace, migrated.trace)
    assertTrue(migrated.transcript.isEmpty())
    assertEquals(2, restored.history.value.size)
    restored.openConversation("other")
    assertEquals("另一段对话", restored.load().messages.single().text)
    assertEquals("保留标题", restored.history.value.single { it.id == "other" }.title)
  }
}
