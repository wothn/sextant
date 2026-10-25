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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
data class AgentMessage(val id: String, val role: String, val text: String, val runId: String? = null,
  val images: List<String> = emptyList())

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
data class AgentConversation(
  val messages: List<AgentMessage> = emptyList(),
  val drafts: List<AgentDraft> = emptyList(),
  val runId: String? = null,
  val status: String = "idle",
  val activity: String = "",
  val error: String? = null,
  val trace: List<AgentTraceEntry> = emptyList(),
) {
  val running: Boolean get() = status == "running"
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

/** 对话不进入账本备份；活动会话和历史在同一文件中原子提交。 */
class AgentConversationStore(context: Context, fileName: String = "agent-conversation.json") {
  private val file = AgentAtomicFile(File(context.noBackupFilesDir, fileName))
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
          if ("activeId" in json.parseToJsonElement(source).jsonObject) json.decodeFromString<AgentConversationArchive>(source)
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
        val current = requireNotNull(archive)
        val saved = current.copy(conversations = current.conversations.map {
          if (it.id == current.activeId) it.copy(conversation = next,
            updatedAt = if (persist) System.currentTimeMillis() else it.updatedAt) else it
        })
        if (persist) write(saved)
        publish(saved)
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
    file.write(json.encodeToString(AgentConversationArchive.serializer(), saved).toByteArray(Charsets.UTF_8))
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
          ?: if (message?.images?.isNotEmpty() == true) "[图片]" else "未命名对话"
        val fallbackTitle = label(messages.firstOrNull { it.role == "user" }).take(80)
        AgentConversationSummary(entry.id, entry.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
          label(messages.lastOrNull()), entry.updatedAt)
      }
  }
}
