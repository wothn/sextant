package com.sextant.app.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.sextant.app.ui.components.SextantButton
import com.sextant.app.ui.components.SextantCard
import com.sextant.app.ui.components.SextantIconButton
import com.sextant.app.ui.components.SextantOutlinedButton
import com.sextant.app.ui.components.SextantSwitch
import com.sextant.app.ui.components.SectionTitle

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.sextant.app.ui.components.rememberSaveAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sextant.app.SextantApplication
import com.sextant.app.automation.ALIPAY_PACKAGE
import com.sextant.app.automation.ListenerConnectionState
import com.sextant.app.automation.WECHAT_PACKAGE
import com.sextant.app.data.AppState
import com.sextant.app.data.AutomationEventStatus
import com.sextant.app.data.SextantRepository
import com.sextant.app.ui.theme.SextantColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun AutomationSettingsScreen(
  state: AppState,
  repository: SextantRepository,

  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  var draft by remember(state.automationSettings) { mutableStateOf(state.automationSettings) }
  var message by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val saveAction = rememberSaveAction("automation") { message = "自动记账设置已保存。" }
  val connection = repository.notificationListenerConnection
  val listenerStatus by connection.status.collectAsState()
  val listenerEnabled = listenerStatus.authorized
  val listenerConnected = listenerStatus.connection == ListenerConnectionState.Connected

  val notificationAccessLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) {
    scope.launch { connection.rebind(context) }

  }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted -> message = if (granted) "已允许显示自动记账结果通知。" else "未允许结果通知，自动入账仍会继续。" }

  // 从系统设置返回时（onResume）重新确认授权状态，并顺手尝试恢复一次绑定。
  // 首次进入页面也主动查一次，因为状态是进程级的，可能还是上一次会话的旧值。
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, connection) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) scope.launch { connection.rebind(context) }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        SextantIconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
        SectionTitle("自动记账")
      }
    }
    item {
      SextantCard() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("通知读取授权", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text(
            "Sextant 只处理微信和支付宝中包含金额与支付关键词的通知。普通聊天不会保存或上传。功能默认关闭，可随时撤销系统授权。",
            color = SextantColors.TextMuted,
          )
          Text(
            text = when {
              !listenerEnabled -> "状态：未授权，不会记录任何通知"
              listenerConnected -> "状态：监听中"
              else -> "状态：已授权，但监听服务当前没有运行"
            },
            color = when {
              !listenerEnabled -> SextantColors.Danger
              listenerConnected -> SextantColors.Success
              else -> SextantColors.Danger
            },
          )
          (listenerStatus.lastCapturedAt)?.let { capturedAt ->
            Text(
              "最近捕获支付通知：${SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(capturedAt))}",
              color = SextantColors.TextMuted,
              style = MaterialTheme.typography.bodySmall,
            )
          }
          SextantOutlinedButton(onClick = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            runCatching { notificationAccessLauncher.launch(intent) }
              .onFailure { message = "当前系统无法打开通知使用权设置。" }
          }) { Text(if (listenerEnabled) "管理系统授权" else "前往授权") }
        }
      }
    }
    item {
      SettingsSwitchCard(
        title = "启用自动记账",
        description = if (listenerEnabled) "监听新的支付通知并自动生成有效流水" else "请先授予通知读取权限",
        checked = draft.enabled,
        enabled = listenerEnabled,
      ) { draft = draft.copy(enabled = it) }
    }
    item {
      SextantCard() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("支付应用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          InlineSwitch("微信", WECHAT_PACKAGE, draft.wechatEnabled) { draft = draft.copy(wechatEnabled = it) }
          InlineSwitch("支付宝", ALIPAY_PACKAGE, draft.alipayEnabled) { draft = draft.copy(alipayEnabled = it) }
        }
      }
    }
    item {
      SextantCard() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          InlineSwitch("入账结果通知", "可撤销或打开编辑", draft.resultNotificationsEnabled) {
            draft = draft.copy(resultNotificationsEnabled = it)
          }
          if (draft.resultNotificationsEnabled && Build.VERSION.SDK_INT >= 33) {
            SextantOutlinedButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
              Text("允许结果通知")
            }
          }
        }
      }
    }
    item {
      SextantButton(onClick = {
          val submitted = draft
          saveAction.launch { repository.updateAutomationSettings(submitted) }
        },
        modifier = Modifier.fillMaxWidth(),
      ) { Text("保存设置") }
    }
    message?.let { value -> item { Text(value, color = SextantColors.TextMuted) } }
    item { Text("最近处理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
    if (state.automationEvents.isEmpty()) {
      item { Text("暂无自动记账事件。", color = SextantColors.TextMuted) }
    } else {
      items(state.automationEvents.size) { index ->
        val event = state.automationEvents[index]
        SextantCard() {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${event.sourcePackage.sourceName()} · ${event.status.label()}", fontWeight = FontWeight.SemiBold)
            Text(SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(event.postedAt)), color = SextantColors.TextMuted)
            event.parsedSummary?.let { Text(it, color = SextantColors.TextMuted) }
            if (event.status == AutomationEventStatus.Failed) {
              event.failureCode?.let { Text("原因：${it.failureLabel()}", color = SextantColors.Danger) }
            }
            if (event.status == AutomationEventStatus.Failed) {
              SextantOutlinedButton(onClick = {
                scope.launch {
                  val queued = (context.applicationContext as SextantApplication).automationCoordinator.retry(event.id)
                  message = if (queued) "已重新加入处理队列。" else "原通知已过期，无法重试。"

                }
              }) { Text("重试") }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsSwitchCard(
  title: String,
  description: String,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
) {
  SextantCard() {
    InlineSwitch(title, description, checked, enabled, onCheckedChange)
  }
}

@Composable
private fun InlineSwitch(
  title: String,
  description: String,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(description, color = SextantColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
    SextantSwitch(
      modifier = Modifier.semantics { contentDescription = title },
      checked = checked,
      enabled = enabled,
      onCheckedChange = onCheckedChange,
    )
  }
}

private fun String.sourceName() = when (this) {
  WECHAT_PACKAGE -> "微信"

  ALIPAY_PACKAGE -> "支付宝"

  else -> this
}

private fun AutomationEventStatus.label() = when (this) {
  AutomationEventStatus.Captured -> "等待处理"
  AutomationEventStatus.Posted -> "已入账"
  AutomationEventStatus.Duplicate -> "已去重"
  AutomationEventStatus.Failed -> "处理失败"
  AutomationEventStatus.Undone -> "已撤销"
}

private fun String.failureLabel() = when (this) {
  "amount_missing" -> "通知未包含明确金额"
  "amount_ambiguous" -> "通知包含多个可能的金额"
  "direction_missing" -> "无法确定收支方向"
  else -> this
}
