package com.sextant.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import com.sextant.app.agent.AgentCoordinator
import com.sextant.app.agent.AgentSpeech
import com.sextant.app.agent.AgentSpeechException
import com.sextant.app.data.*
import com.sextant.app.ui.components.LocalBottomBarInset
import com.sextant.app.ui.components.LocalSnackbarHostState
import com.sextant.app.ui.components.SextantAlertDialog
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.agentDraftDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Composable
fun AgentChatScreen(coordinator: AgentCoordinator, repository: SextantRepository) {
  val conversation by coordinator.state.collectAsState()
  val commandError by coordinator.error.collectAsState()
  val ready by coordinator.ready.collectAsState()
  val conversationId by coordinator.conversationId.collectAsState()
  val history by coordinator.history.collectAsState()
  var input by rememberSaveable(conversationId) { mutableStateOf("") }
  var pendingImages by rememberSaveable(conversationId) { mutableStateOf(emptyList<String>()) }
  var pendingDocuments by rememberSaveable(conversationId, stateSaver = documentListSaver) { mutableStateOf(emptyList()) }
  var attachmentError by remember(conversationId) { mutableStateOf<String?>(null) }
  var preparingAttachment by remember { mutableStateOf<String?>(null) }
  var showAttachments by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val speech = rememberAgentSpeech(coordinator.settings, conversationId)
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AgentImageStore.MAX_IMAGES)) { uris ->
    if (uris.isNotEmpty()) {
      if (pendingImages.size + uris.size > AgentImageStore.MAX_IMAGES) attachmentError = "每条消息最多4张图片"
      else scope.launch {
        val targetId = conversationId
        val imported = mutableListOf<String>()
        var retained = false
        preparingAttachment = "正在准备图片…"
        attachmentError = null
        try {
          uris.forEach { imported += coordinator.images.import(it) }
          if (targetId == coordinator.conversationId.value) {
            pendingImages = pendingImages + imported
            retained = true
          }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { attachmentError = error.message ?: "图片读取失败，请重新选择" }
        finally {
          if (!retained) withContext(NonCancellable) { imported.forEach { coordinator.images.removePending(it) } }
          preparingAttachment = null
        }
      }
    }
  }
  val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isEmpty()) return@rememberLauncherForActivityResult
    if (pendingDocuments.size + uris.size > AgentDocumentStore.MAX_FILES) {
      attachmentError = "每条消息最多4个文件"
    } else scope.launch {
      val targetId = conversationId
      val imported = mutableListOf<AgentDocument>()
      var retained = false
      preparingAttachment = "正在准备文件…"
      attachmentError = null
      try {
        uris.forEach { imported += coordinator.documents.import(it) }
        if (targetId == coordinator.conversationId.value) {
          pendingDocuments += imported
          retained = true
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: IllegalArgumentException) {
        attachmentError = error.message ?: "文件无法读取，请重新选择"
      } catch (_: Exception) {
        attachmentError = "文件无法读取，请重新选择"
      } finally {
        if (!retained) withContext(NonCancellable) { imported.forEach { coordinator.documents.removePending(it.id) } }
        preparingAttachment = null
      }
    }
  }
  val importing = preparingAttachment != null
  var showSettings by rememberSaveable { mutableStateOf(false) }
  var showHistory by rememberSaveable { mutableStateOf(false) }
  val traceByRun = conversation.trace.groupBy { it.runId }
  val draftsByMessage = agentDraftsByMessage(conversation.messages, conversation.drafts)
  val chatScroll = rememberAgentChatScrollState(conversationId)
  val list = chatScroll.list
  var ledger by remember { mutableStateOf(AppState()) }
  LaunchedEffect(conversation.drafts, ready) {
    if (ready) ledger = repository.loadState(projection = StateProjection.Settings)
  }
  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  Column(Modifier.fillMaxSize().padding(bottom = LocalBottomBarInset.current)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Spacer(Modifier.weight(1f))
      IconButton(onClick = { showHistory = true }, enabled = ready && !importing) { Icon(Icons.Outlined.History, "对话历史") }
      IconButton(onClick = { coordinator.newConversation(discardDocuments = pendingDocuments.map { it.id }) }, enabled = ready && !conversation.running && !importing) {
        Icon(Icons.Outlined.AddComment, "新对话")
      }
      IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, "模型与 API Key") }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
    LazyColumn(Modifier.fillMaxSize().nestedScroll(chatScroll.userScroll), state = list, reverseLayout = true,
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Emit in reverse chronological order so index zero remains the bottom anchor.
      item(key = "chat-bottom-anchor") { Spacer(Modifier.height(1.dp)) }
      if (conversation.running && conversation.activity.isNotBlank()) item(key = "chat-activity") {
        Text(conversation.activity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      commandError?.let { error -> item(key = "chat-command-error") {
        Text(error, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = coordinator::dismissError) { Text("知道了") }
      } }
      if (conversation.status in setOf("failed", "interrupted", "cancelled")) item(key = "chat-status") {
        Text(conversation.error ?: conversation.activity, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = coordinator::retry) { Text("继续分析") }
      }
      items(draftsByMessage[null].orEmpty().asReversed(), key = { it.id }) { draft ->
        DraftCard(draft, ledger, !conversation.running, { coordinator.decide(draft.id, true) }, { coordinator.decide(draft.id, false) }, { coordinator.undo(draft.id) })
      }
      conversation.messages.asReversed().forEach { message ->
        items(draftsByMessage[message.id].orEmpty().asReversed(), key = { it.id }) { draft ->
          DraftCard(draft, ledger, !conversation.running, { coordinator.decide(draft.id, true) }, { coordinator.decide(draft.id, false) }, { coordinator.undo(draft.id) })
        }
        if (message.role == "user" && message.runId != null) {
          val entries = traceByRun[message.runId].orEmpty()
          if (entries.isNotEmpty()) {
            val liveText = entries.lastOrNull { it.kind == "model" }?.text.orEmpty()
            if (conversation.running && conversation.runId == message.runId && liveText.isNotBlank()) item(key = "stream-${message.id}") {
              AgentMessageItem(AgentMessage("stream-${message.id}", "assistant", liveText), coordinator.images, streaming = true)
            }
            item(key = "process-${message.id}") {
              AgentProcess(entries, conversation.running && conversation.runId == message.runId,
                if (conversation.runId == message.runId) conversation.status else when {
                  entries.any { it.status == "interrupted" } -> "interrupted"
                  entries.any { it.status == "cancelled" } -> "cancelled"
                  entries.lastOrNull()?.status == "failed" -> "failed"
                  else -> "completed"
                })
            }
          }
        }
        item(key = message.id) {
          AgentMessageItem(message, coordinator.images)
          if (message.role == "assistant" && message.text.isNotBlank()) {
            val speaking = speech.activeMessageId == message.id
            IconButton(onClick = { speech.toggle(message) }) {
              Icon(if (speaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = if (speaking) "停止朗读" else "朗读回复")
            }
          }
        }
      }
    }
    if (list.canScrollBackward) {
      SmallFloatingActionButton(
        onClick = chatScroll::requestBottom,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 12.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ) {
        Icon(Icons.Outlined.ArrowDownward, contentDescription = "回到底部")
      }
    }
    }
    if (pendingImages.isNotEmpty()) Box(Modifier.padding(horizontal = 20.dp)) {
      AgentMessageImages(pendingImages, coordinator.images, onRemove = if (conversation.running || importing) null else { id ->
        pendingImages = pendingImages - id
        coordinator.discardPendingImage(id)
      })
    }
    if (pendingDocuments.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp)) {
      pendingDocuments.forEach { document ->
        AgentDocumentRow(document, onRemove = if (conversation.running || importing) null else {
          {
            pendingDocuments = pendingDocuments.filterNot { it.id == document.id }
            coordinator.discardPendingDocument(document.id)
          }
        })
      }
    }
    if (importing || attachmentError != null) Text(attachmentError ?: preparingAttachment.orEmpty(),
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
      color = if (attachmentError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box {
        IconButton(modifier = Modifier.size(48.dp).padding(bottom = 4.dp),
          enabled = ready && !conversation.running && !importing, onClick = { showAttachments = true }) {
          Icon(Icons.Outlined.AttachFile, "添加附件")
        }
        DropdownMenu(expanded = showAttachments, onDismissRequest = { showAttachments = false }) {
          DropdownMenuItem(text = { Text("添加图片") }, leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
            enabled = pendingImages.size < AgentImageStore.MAX_IMAGES, onClick = {
              showAttachments = false
              picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
          DropdownMenuItem(text = { Text("添加文件") }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
            enabled = pendingDocuments.size < AgentDocumentStore.MAX_FILES, onClick = {
              showAttachments = false
              documentPicker.launch(arrayOf("*/*"))
            })
        }
      }
      OutlinedTextField(value = input, onValueChange = { if (it.length <= 6000) input = it },
        modifier = Modifier.weight(1f), placeholder = { Text(if (pendingImages.isEmpty() && pendingDocuments.isEmpty())
          "说说想记或想了解的…" else "补充说明，或直接发送附件") },
        maxLines = 5, enabled = ready && !conversation.running)
      FilledIconButton(modifier = Modifier.size(56.dp),
        enabled = ready && (conversation.running || (!importing && (input.isNotBlank() || pendingImages.isNotEmpty() || pendingDocuments.isNotEmpty()))), onClick = {
          if (conversation.running) coordinator.stop()
          else {
            chatScroll.requestBottom()
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            coordinator.send(input, pendingImages, pendingDocuments) {
              input = ""
              pendingImages = emptyList()
              pendingDocuments = emptyList()
              attachmentError = null
            }
          }
        }) {
        Icon(if (conversation.running) Icons.Outlined.Pause else Icons.AutoMirrored.Outlined.Send,
          contentDescription = if (conversation.running) "停止任务" else "发送")
      }
    }
  }
  if (showSettings) AgentSettingsDialog(coordinator, onDismiss = { showSettings = false })
  if (showHistory) AgentHistorySheet(history, conversationId, conversation.running,
    onDismiss = { showHistory = false },
    onSelect = { id -> coordinator.openConversation(id, discardDocuments = pendingDocuments.map { it.id }) { showHistory = false } })
}

private val documentListSaver = Saver<List<AgentDocument>, String>(
  save = { Json.encodeToString(it) }, restore = { Json.decodeFromString<List<AgentDocument>>(it) },
)

@Composable
private fun AgentDocumentRow(document: AgentDocument, onRemove: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
    Text(document.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "移除 ${document.name}") }
  }
}

@Composable
private fun rememberAgentSpeech(settings: AgentSettingsStore, conversationId: String?): AgentSpeechControls {
  val context = LocalContext.current.applicationContext
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val scope = rememberCoroutineScope()
  val snackbar = LocalSnackbarHostState.current
  val controls = remember(context, settings, snackbar) {
    AgentSpeechControls(AgentSpeech(context, settings), scope, snackbar)
  }
  DisposableEffect(lifecycle, conversationId, controls) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_STOP) controls.stop()
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      controls.stop()
    }
  }
  return controls
}

private class AgentSpeechControls(
  private val speech: AgentSpeech,
  private val scope: CoroutineScope,
  private val snackbar: SnackbarHostState,
) {
  var activeMessageId by mutableStateOf<String?>(null)
    private set
  private var playback: Job? = null
  private val playbackLock = Mutex()

  fun toggle(message: AgentMessage) {
    val wasActive = activeMessageId == message.id
    stop()
    if (wasActive) return

    activeMessageId = message.id
    playback = scope.launch(start = CoroutineStart.LAZY) {
      try {
        playbackLock.withLock { speech.readAloud(message.text) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        val feedback = (error as? AgentSpeechException)?.message ?: "朗读失败，请检查网络后重试"
        scope.launch { snackbar.showSnackbar(feedback) }
      } finally {
        if (playback === coroutineContext.job) {
          activeMessageId = null
          playback = null
        }
      }
    }
    playback?.start()
  }

  fun stop() {
    activeMessageId = null
    playback?.cancel()
  }
}

@Composable
private fun AgentMessageItem(message: AgentMessage, images: AgentImageStore, streaming: Boolean = false) {
  when (message.role) {
    "user" -> BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
      Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.widthIn(max = maxWidth * 0.85f)) {
        AgentMessageContent(message, images, streaming, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
      }
    }
    "receipt" -> Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth()) {
      AgentMessageContent(message, images, streaming, Modifier.padding(16.dp))
    }
    else -> AgentMessageContent(message, images, streaming, Modifier.fillMaxWidth().padding(vertical = 4.dp))
  }
}

@Composable
private fun AgentMessageContent(message: AgentMessage, images: AgentImageStore, streaming: Boolean, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (message.role != "user") {
      Text(when {
        streaming -> "Sextant · 正在回答"
        message.role == "receipt" -> "账本回执"
        else -> "Sextant"
      }, style = MaterialTheme.typography.labelMedium)
    }
    if (message.images.isNotEmpty()) AgentMessageImages(message.images, images)
    message.documents.forEach { AgentDocumentRow(it) }
    if (message.text.isNotBlank()) SelectionContainer {
      if (message.role == "assistant") Markdown(
        // Restoring a list offset against an empty, asynchronously parsed answer makes
        // LazyColumn clamp that offset to a different message before the text appears.
        markdownState = rememberMarkdownState(message.text, retainState = true, immediate = true),
        modifier = Modifier.fillMaxWidth(),
        typography = markdownTypography(
          h1 = MaterialTheme.typography.headlineSmall,
          h2 = MaterialTheme.typography.titleLarge,
          h3 = MaterialTheme.typography.titleMedium,
          h4 = MaterialTheme.typography.titleSmall,
          h5 = MaterialTheme.typography.titleSmall,
          h6 = MaterialTheme.typography.titleSmall,
        ),
        error = { Text(message.text, style = MaterialTheme.typography.bodyLarge) },
      ) else Text(message.text, style = MaterialTheme.typography.bodyLarge)
    }
    if (message.sources.isNotEmpty()) {
      val handler = LocalUriHandler.current
      Text("来源", style = MaterialTheme.typography.labelMedium)
      message.sources.forEach { source ->
        TextButton(onClick = { runCatching { handler.openUri(source.url) } }, contentPadding = PaddingValues(horizontal = 4.dp)) {
          Text(source.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun DraftCard(draft: AgentDraft, ledger: AppState, canDecide: Boolean, approve: () -> Unit, reject: () -> Unit, undo: () -> Unit) {
  Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(when (draft.action) { "create" -> "新增记录"; "update" -> "修改记录"; else -> "删除记录" }, style = MaterialTheme.typography.titleMedium)
      Text("${when (draft.type) { "expense" -> "支出"; "income" -> "收入"; else -> "转移" }} · ${formatCurrency(draft.amountCents)} · ${agentDraftDate(draft.transactionDate)}")
      Text("${ledger.categories.firstOrNull { it.id == draft.categoryId }?.name ?: "未分类"} · ${ledger.allPaymentMethods.firstOrNull { it.id == draft.paymentMethodId }?.name ?: "未设置来源"}")
      if (draft.description.isNotBlank()) Text(draft.description)
      draft.before?.let { before ->
        Text("原记录：${formatCurrency(before.amountCents)} · ${agentDraftDate(before.transactionDate)} · ${before.description}", style = MaterialTheme.typography.bodySmall)
        Text("原分类：${ledger.categories.firstOrNull { it.id == before.categoryId }?.name ?: "未分类"} · 原来源：${ledger.allPaymentMethods.firstOrNull { it.id == before.paymentMethodId }?.name ?: "未设置"}", style = MaterialTheme.typography.bodySmall)
      }
      if (draft.status == "pending") {
        Text("尚未入账；如需调整，可直接发消息说明。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = approve, enabled = canDecide) { Text(if (draft.action == "delete") "确认删除" else "确认保存") }
          TextButton(onClick = reject, enabled = canDecide) { Text("拒绝") }
        }
      } else {
        Text(draft.result ?: "已处理", style = MaterialTheme.typography.bodySmall)
        if (draft.status == "applied") TextButton(onClick = undo, enabled = canDecide) { Text("准备撤销") }
      }
    }
  }
}

@Composable
private fun AgentSettingsDialog(coordinator: AgentCoordinator, onDismiss: () -> Unit) {
  val scope = rememberCoroutineScope()
  var key by remember { mutableStateOf("") }
  var model by remember { mutableStateOf("deepseek-flash") }
  var loaded by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) {
    try { val settings = coordinator.settings.read(); key = settings.apiKey; model = settings.model; loaded = true }
    catch (_: Exception) { error = "读取模型设置失败，重新填写可覆盖旧配置。"; loaded = true }
  }
  SextantAlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("连接 DeepSeek") },
    text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("使用你自己的 API Key，费用由 DeepSeek 账户承担。Key 加密保存在本机，不进入账本备份。")
      OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), enabled = loaded && !saving)
      OutlinedTextField(model, { model = it }, label = { Text("模型名称") }, singleLine = true, enabled = loaded && !saving)
      Text("默认 deepseek-flash，支持图片、文件和联网搜索。也可填写你账户可用的模型名称。", style = MaterialTheme.typography.bodySmall)
      error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } },
    confirmButton = { TextButton(enabled = loaded && !saving && model.isNotBlank(), onClick = {
      saving = true
      scope.launch {
        try { coordinator.settings.save(AgentSettings(key, model)); onDismiss() }
        catch (_: Exception) { error = "保存失败，请重试" }
        finally { saving = false }
      }
    }) { Text(if (saving) "保存中…" else "保存") } },
    dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } })
}
