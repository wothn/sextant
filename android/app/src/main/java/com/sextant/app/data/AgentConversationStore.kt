package com.sextant.app.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonElement

@Serializable
data class AgentMessage(val id: String, val role: String, val text: String, val runId: String? = null,
  val images: List<String> = emptyList(), val documents: List<AgentDocument> = emptyList(),
  val sources: List<AgentWebSource> = emptyList())

@Serializable
data class AgentWebSource(val url: String, val title: String)

@Serializable
data class AgentTraceEntry(
  val id: String,
  val runId: String,
  val kind: String,
  val title: String,
  val status: String = "running",
  val reasoning: String = "",
  val text: String = "",
  val arguments: String = "",
  val result: String = "",
  val summary: String = "",
  val elapsedMillis: Long = 0,
  val truncated: Boolean = false,
)

@Serializable
data class AgentDraft(
  val id: String,
  val runId: String,
  val action: String,
  val transactionId: String,
  val type: String = "expense",
  val amountCents: Long = 0,
  val categoryId: String? = null,
  val paymentMethodId: String? = null,
  val description: String = "",
  val transactionDate: Long = 0,
  val expectedUpdatedAt: Long? = null,
  val before: Transaction? = null,
  val status: String = "pending",
  val result: String? = null,
  val restoreOriginal: Boolean = false,
  val appliedUpdatedAt: Long? = null,
)

@Serializable
data class AgentTurnTranscript(
  val userMessageId: String,
  val runId: String,
  val messages: List<JsonElement> = emptyList(),
  val draftIds: List<String> = emptyList(),
  val completed: Boolean = false,
  @Required val formatVersion: Int = 1,
)

@Serializable
data class AgentConversation(
  val messages: List<AgentMessage> = emptyList(),
  val drafts: List<AgentDraft> = emptyList(),
  val runId: String? = null,
  val status: String = "idle",
  val activity: String = "",
  val error: String? = null,
  val trace: List<AgentTraceEntry> = emptyList(),
  val transcript: List<AgentTurnTranscript> = emptyList(),
  val attemptId: String? = null,
) {
  val running: Boolean get() = status == "running"
  fun ownsRun(run: String, attempt: String?): Boolean = runId == run && attemptId == attempt && running
}

data class AgentConversationSummary(val id: String, val title: String, val preview: String, val updatedAt: Long)

@Serializable
private data class SavedAgentConversation(
  val id: String,
  val updatedAt: Long,
  val conversation: AgentConversation = AgentConversation(),
  val title: String? = null,
)

@Serializable
private data class AgentConversationArchive(val activeId: String, val conversations: List<SavedAgentConversation>)

@Serializable
private data class AgentConversationRef(val id: String, val updatedAt: Long, val title: String?, val revision: String)

@Serializable
private data class AgentConversationIndex(val storageVersion: Int, val activeId: String, val conversations: List<AgentConversationRef>)

/** 正文按会话写入不可变版本，原子替换索引作为提交点；失败时保留旧版本。 */
class AgentConversationStore(context: Context, fileName: String = "agent-conversation.json") {
  private val file = AgentAtomicFile(File(context.noBackupFilesDir, fileName))
  private val directory = File(context.noBackupFilesDir, "$fileName.sessions")
  private var revisions = emptyMap<String, String>()
  private var persistedBodies = emptyMap<String, AgentConversation>()
  private val mutex = Mutex()
  private val json = Json { ignoreUnknownKeys = true }
  private val mutable = MutableStateFlow(AgentConversation())
  val state = mutable.asStateFlow()
  private val mutableHistory = MutableStateFlow<List<AgentConversationSummary>>(emptyList())
  val history = mutableHistory.asStateFlow()
  private val mutableActiveId = MutableStateFlow<String?>(null)
  val activeId = mutableActiveId.asStateFlow()
  private var archive: AgentConversationArchive? = null
  private var loaded = false

  suspend fun load(): AgentConversation = mutex.withLock {
    withContext(Dispatchers.IO) {
      if (!loaded) {
        val saved = if (file.exists()) {
          val source = String(file.read(), Charsets.UTF_8)
          if ("storageVersion" in json.parseToJsonElement(source).jsonObject) readIndex(source)
          else if ("activeId" in json.parseToJsonElement(source).jsonObject) json.decodeFromString<AgentConversationArchive>(source)
          else newArchive(json.decodeFromString<AgentConversation>(source))
        } else newArchive(AgentConversation())
        publish(saved)
        loaded = true
      }
      mutable.value
    }
  }

  suspend fun update(persist: Boolean = true, change: (AgentConversation) -> AgentConversation): AgentConversation {
    load()
    return mutex.withLock {
      withContext(Dispatchers.IO) {
        val next = change(mutable.value)
        if (next == mutable.value) return@withContext next
        val current = requireNotNull(archive)
        val saved = current.copy(conversations = current.conversations.map {
          if (it.id == current.activeId) it.copy(conversation = next,
            updatedAt = if (persist) System.currentTimeMillis() else it.updatedAt) else it
        })
        if (persist) write(saved)
        if (persist) publish(saved) else {
          archive = saved
          mutable.value = next
        }
        next
      }
    }
  }

  suspend fun newConversation() {
    load()
    mutex.withLock {
      withContext(Dispatchers.IO) {
        require(!mutable.value.running) { "请先停止当前任务" }
        if (mutable.value == AgentConversation()) return@withContext
        val current = requireNotNull(archive)
        val empty = current.conversations.firstOrNull { it.conversation == AgentConversation() }
          ?: SavedAgentConversation(UUID.randomUUID().toString(), System.currentTimeMillis())
        val saved = current.copy(activeId = empty.id,
          conversations = if (empty in current.conversations) current.conversations else current.conversations + empty)
        write(saved)
        publish(saved)
      }
    }
  }

  suspend fun openConversation(id: String) {
    load()
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val current = requireNotNull(archive)
        if (id == current.activeId) return@withContext
        require(!mutable.value.running) { "请先停止当前任务" }
        require(current.conversations.any { it.id == id }) { "这段对话已不存在" }
        val saved = current.copy(activeId = id)
        write(saved)
        publish(saved)
      }
    }
  }

  suspend fun containsImage(id: String): Boolean {
    load()
    return mutex.withLock {
      requireNotNull(archive).conversations.any { saved -> saved.conversation.messages.any { id in it.images } }
    }
  }

  suspend fun containsDocument(id: String): Boolean {
    load()
    return mutex.withLock {
      requireNotNull(archive).conversations.any { saved ->
        saved.conversation.messages.any { message -> message.documents.any { it.id == id } }
      }
    }
  }

  /** 标题生成可能晚于会话切换，必须按 ID 回写，且不能覆盖已有标题或改变历史排序。 */
  suspend fun setTitleIfAbsent(conversationId: String, title: String) {
    load()
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val current = requireNotNull(archive)
        val target = current.conversations.firstOrNull { it.id == conversationId } ?: return@withContext
        if (!target.title.isNullOrBlank()) return@withContext
        val saved = current.copy(conversations = current.conversations.map {
          if (it.id == conversationId) it.copy(title = title) else it
        })
        write(saved)
        publish(saved)
      }
    }
  }

  private fun newArchive(conversation: AgentConversation): AgentConversationArchive {
    val saved = SavedAgentConversation(UUID.randomUUID().toString(), System.currentTimeMillis(), conversation)
    return AgentConversationArchive(saved.id, listOf(saved))
  }

  private fun write(saved: AgentConversationArchive) {
    val created = mutableListOf<File>()
    try {
      val refs = saved.conversations.map { entry ->
        val revision = revisions[entry.id]?.takeIf { persistedBodies[entry.id] == entry.conversation } ?: run {
          val name = "${UUID.randomUUID()}.json"
          val body = File(directory, name)
          created += body
          AgentAtomicFile(body).write(json.encodeToString(AgentConversation.serializer(), entry.conversation).toByteArray(Charsets.UTF_8))
          name
        }
        AgentConversationRef(entry.id, entry.updatedAt, entry.title, revision)
      }
      val index = AgentConversationIndex(2, saved.activeId, refs)
      file.write(json.encodeToString(AgentConversationIndex.serializer(), index).toByteArray(Charsets.UTF_8))
      val old = revisions.values.toSet()
      revisions = refs.associate { it.id to it.revision }
      persistedBodies = saved.conversations.associate { it.id to it.conversation }
      (old - revisions.values.toSet()).forEach { runCatching { File(directory, it).delete() } }
    } catch (error: Exception) {
      created.forEach { it.delete() }
      throw error
    }
  }

  private fun readIndex(source: String): AgentConversationArchive {
    val index = json.decodeFromString<AgentConversationIndex>(source)
    require(index.storageVersion == 2) { "对话存储版本不受支持" }
    val conversations = index.conversations.map { ref ->
      require(Regex("[0-9a-f-]{36}\\.json").matches(ref.revision)) { "无效的对话文件" }
      val body = json.decodeFromString<AgentConversation>(File(directory, ref.revision).readText(Charsets.UTF_8))
      SavedAgentConversation(ref.id, ref.updatedAt, body, ref.title)
    }
    revisions = index.conversations.associate { it.id to it.revision }
    persistedBodies = conversations.associate { it.id to it.conversation }
    // 进程可能在写完正文、替换索引之前退出；完整读取索引后再清理这些未提交版本。
    directory.listFiles()?.filter { Regex("[0-9a-f-]{36}\\.json").matches(it.name) && it.name !in revisions.values }
      ?.forEach { runCatching { it.delete() } }
    return AgentConversationArchive(index.activeId, conversations)
  }

  private fun publish(saved: AgentConversationArchive) {
    val active = saved.conversations.single { it.id == saved.activeId }
    archive = saved
    mutable.value = active.conversation
    mutableActiveId.value = saved.activeId
    mutableHistory.value = saved.conversations.filter { it.conversation != AgentConversation() }
      .sortedByDescending { it.updatedAt }.map { entry ->
        val messages = entry.conversation.messages
        fun label(message: AgentMessage?): String = message?.text?.takeIf { it.isNotBlank() }?.take(120)
          ?: message?.documents?.firstOrNull()?.name
          ?: if (message?.images?.isNotEmpty() == true) "[图片]" else "未命名对话"
        val fallbackTitle = label(messages.firstOrNull { it.role == "user" }).take(80)
        AgentConversationSummary(entry.id, entry.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
          label(messages.lastOrNull()), entry.updatedAt)
      }
  }
}
