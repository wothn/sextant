package com.sextant.app.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.sextant.app.ui.components.SextantSurface
import com.sextant.app.ui.components.SextantTextButton
import com.sextant.app.ui.components.SextantTonalButton
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.components.LocalBottomBarInset
import com.sextant.app.ui.components.SextantIconButton
import com.sextant.app.ui.components.SextantSwitch
import com.sextant.app.ui.components.SectionTitle

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.sextant.app.ui.components.rememberAutosave
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.sextant.app.ui.components.SextantPageContent
import com.sextant.app.ui.components.SextantDetailTile
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sextant.app.SextantApplication
import com.sextant.app.automation.ALIPAY_PACKAGE
import com.sextant.app.automation.ListenerConnectionState
import com.sextant.app.automation.WECHAT_PACKAGE
import com.sextant.app.automation.isPaymentPageServiceEnabled
import com.sextant.app.data.AppState
import com.sextant.app.data.AutomationEventStatus
import com.sextant.app.data.AutomationEventSummary
import com.sextant.app.data.AutomationSettings
import com.sextant.app.data.SextantRepository
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.timestampLabel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@Composable
fun AutomationSettingsScreen(
  state: AppState,
  repository: SextantRepository,

  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  var merchantRulesOpen by rememberSaveable { mutableStateOf(false) }
  val pages = rememberSaveableStateHolder()
  val listState = rememberLazyListState()
  var draft by remember(state.automationSettings) { mutableStateOf(state.automationSettings) }
  var message by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  // 这些开关改一下就生效，不该再要求点一次「保存设置」。开关自己就是草稿，改动由这里
  // 合并后落库；界面对成功保持沉默，只有失败才出声。
  val autosave = rememberAutosave(
    spec = draft,
    load = { repository.loadAutomationSettings() },
    save = { repository.updateAutomationSettings(it as AutomationSettings) },
  )
  val connection = repository.notificationListenerConnection
  val listenerStatus by connection.status.collectAsState()
  val listenerEnabled = listenerStatus.authorized
  val listenerConnected = listenerStatus.connection == ListenerConnectionState.Connected
  var pageAuthorized by remember { mutableStateOf(isPaymentPageServiceEnabled(context)) }
  var resultNotificationAuthorized by remember {
    mutableStateOf(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
  }
  val pageAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    pageAuthorized = isPaymentPageServiceEnabled(context)
  }

  val notificationAccessLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) {
    scope.launch { connection.rebind(context) }

  }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    resultNotificationAuthorized = granted
    message = if (granted) null else "未允许结果通知，自动入账仍会继续。"
  }

  // 从系统设置返回时（onResume）重新确认授权状态，并顺手尝试恢复一次绑定。
  // 首次进入页面也主动查一次，因为状态是进程级的，可能还是上一次会话的旧值。
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, connection) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        pageAuthorized = isPaymentPageServiceEnabled(context)
        resultNotificationAuthorized = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        scope.launch { connection.rebind(context) }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  BackHandler(enabled = merchantRulesOpen) { merchantRulesOpen = false }
  SextantPageContent(targetState = merchantRulesOpen, depth = { if (it) 1 else 0 }, modifier = Modifier.fillMaxSize()) { rulesOpen ->
    pages.SaveableStateProvider(rulesOpen) {
      if (rulesOpen) {
        MerchantCategoryRulesScreen(state, repository, onNavigateBack = { merchantRulesOpen = false })
      } else {
        Column(Modifier.fillMaxSize()) {
          Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SextantIconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
            }
            SectionTitle("自动记账")
          }
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(24.dp),
          ) {
            item(key = "enabled") {
              SextantSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                InlineSwitch(
                  title = "启用自动记账",
                  description = when {
                    !listenerEnabled && !pageAuthorized -> "先授权下方任一读取方式，再开启记账"
                    !draft.enabled -> "开启后，根据已授权的支付信息生成流水"
                    !draft.wechatEnabled && !draft.alipayEnabled -> "尚未启用支付应用，请在下方选择"
                    else -> "记账开关已开启，读取状态见下方"
                  },
                  checked = draft.enabled,
                  enabled = listenerEnabled || pageAuthorized || draft.enabled,
                ) { draft = draft.copy(enabled = it) }
              }
            }
            autosave.failure?.let { reason ->
              item(key = "saveError") {
                Text("$reason 再改一次开关会重试。", color = SextantColors.Danger, style = MaterialTheme.typography.bodyMedium)
              }
            }
            message?.let { value -> item(key = "message") { Text(value, color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium) } }
            item(key = "access") {
              AutomationSection("读取方式") {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                  AuthorizationSection(
                    title = "支付通知",
                    status = when {
                      !listenerEnabled -> "未授权"
                      listenerConnected -> "已授权 · 已连接"
                      else -> "已授权 · 未连接"
                    },
                    ready = listenerConnected && listenerEnabled,
                    needsAttention = listenerEnabled && !listenerConnected,
                    description = "仅处理微信、支付宝的支付通知，普通聊天不会保存或上传。",
                    action = if (listenerEnabled) "管理授权" else "授权通知读取",
                    onClick = {
                      runCatching { notificationAccessLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        .onFailure { message = "当前系统无法打开通知使用权设置。" }
                    },
                  )
                  listenerStatus.lastCapturedAt?.let { capturedAt ->
                    Text("最近捕获：${timestampLabel(capturedAt)}", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                  }
                  AuthorizationSection(
                    title = "支付页面",
                    status = if (pageAuthorized) "已授权" else "未授权",
                    ready = pageAuthorized,
                    description = "通过无障碍服务读取支付信息，仅在本机解析，不保存或上传原始页面，也不会自动操作支付。",
                    action = if (pageAuthorized) "管理授权" else "授权页面读取",
                    onClick = {
                      runCatching { pageAccessLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        .onFailure { message = "当前系统无法打开无障碍设置。" }
                    },
                  )
                }
              }
            }
            item(key = "options") {
              AutomationSection("记账偏好") {
                Column {
                  InlineSwitch("微信", "识别微信支付信息", draft.wechatEnabled) { draft = draft.copy(wechatEnabled = it) }
                  InlineSwitch("支付宝", "识别支付宝支付信息", draft.alipayEnabled) { draft = draft.copy(alipayEnabled = it) }
                  InlineSwitch("识别商户并补全记账", "从支付页面补全商户信息，保留手动填写的备注", draft.paymentPageEnabled) {
                    draft = draft.copy(paymentPageEnabled = it)
                  }
                  InlineSwitch("入账结果通知", "入账后可从通知撤销或打开编辑", draft.resultNotificationsEnabled) {
                    draft = draft.copy(resultNotificationsEnabled = it)
                  }
                  if (draft.resultNotificationsEnabled && !resultNotificationAuthorized && Build.VERSION.SDK_INT >= 33) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text("系统尚未允许结果通知，自动入账仍可继续。", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                      SextantTonalButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("允许结果通知") }
                    }
                  }
                }
              }
            }
            item(key = "merchants") {
              SextantDetailTile(
                label = "商户分类",
                value = if (state.merchantCategoryRules.isEmpty()) "管理商户的自动分类" else "已记住 ${state.merchantCategoryRules.size} 个商户",
                icon = Icons.Outlined.Storefront,
                modifier = Modifier.fillMaxWidth(),
                onClick = { merchantRulesOpen = true },
              )
            }
            item(key = "help") { AutomationHelp() }
            item(key = "eventsHeader") {
              FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SextantHeading("最近处理")
                Text("${state.automationEvents.size} 条", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
              }
            }
            if (state.automationEvents.isEmpty()) {
              item(key = "eventsEmpty") { EmptyCard("还没有处理记录", "收到支付通知或识别支付页面后，可在这里查看入账结果。") }
            } else {
              items(state.automationEvents, key = { "event:${it.id}" }) { event ->
                AutomationEventRow(event, scope) {
                  val queued = (context.applicationContext as SextantApplication).automationCoordinator.retry(event.id)
                  if (queued) "已重新加入处理队列。" else "原始信息不可用，请检查账本后手动补记。"
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AutomationSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = SextantColors.TextSubtle)
    SextantSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Column(content = content) }
  }
}

@Composable
private fun AuthorizationSection(
  title: String,
  status: String,
  ready: Boolean,
  needsAttention: Boolean = false,
  description: String,
  action: String,
  onClick: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val color = when {
          ready -> SextantColors.Success
          needsAttention -> SextantColors.Warning
          else -> SextantColors.TextMuted
        }
        Icon(if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Info, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(status, style = MaterialTheme.typography.labelLarge, color = color)
      }
    }
    Text(description, color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    if (needsAttention) {
      Text("监听服务当前未连接，可检查系统授权。", color = SextantColors.Warning, style = MaterialTheme.typography.bodyMedium)
    }
    SextantTonalButton(onClick = onClick) { Text(action) }
  }
}

@Composable
private fun AutomationHelp() {
  var expanded by rememberSaveable { mutableStateOf(false) }
  SextantSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SextantTextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics {
        stateDescription = if (expanded) "已展开" else "已收起"
      }) {
        Text("使用说明", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
      }
      if (expanded) {
        Text("通知读取仅处理包含金额与支付关键词的微信、支付宝通知。功能默认关闭，可随时撤销系统授权。", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
        Text("页面读取会识别支付结果页、账单详情页的金额、商户、时间与交易单号。支付完成后停留片刻，打开账单详情可补记。", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
        Text("页面识别目前支持已完成的人民币消费；转账、退款等仍通过通知记录。页面结构不受支持时可能无法识别。", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
        Text("商户信息可补全尚未填写的备注，已有手动备注不会被覆盖。", color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun AutomationEventRow(event: AutomationEventSummary, scope: CoroutineScope, onRetry: suspend () -> String) {
  val failed = event.status == AutomationEventStatus.Failed
  var retrying by remember { mutableStateOf(false) }
  var retryMessage by rememberSaveable(event.id, event.status) { mutableStateOf<String?>(null) }
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val captureSource = if (event.parser?.contains("-page-") == true) "页面" else "通知"
      Text("${event.sourcePackage.sourceName()} · $captureSource", style = MaterialTheme.typography.titleMedium)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (failed) Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = SextantColors.Danger, modifier = Modifier.size(18.dp))
        Text(event.status.label(), style = MaterialTheme.typography.labelLarge, color = if (failed) SextantColors.Danger else SextantColors.TextMuted)
      }
    }
    event.parsedSummary?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    Text(timestampLabel(event.postedAt), color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    if (failed) event.failureCode?.let {
      Text("原因：${it.failureLabel()}", color = SextantColors.Danger, style = MaterialTheme.typography.bodyMedium)
    }
    if (failed && event.failureCode !in setOf("payment_match_ambiguous", "automation_disabled")) {
      SextantTextButton(enabled = !retrying, onClick = {
        retrying = true
        retryMessage = null
        scope.launch {
          try {
            retryMessage = onRetry()
          } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            retryMessage = "无法重新加入处理队列，请重试。"
          } finally {
            retrying = false
          }
        }
      }) { Text(if (retrying) "正在重试" else "重试") }
    }
    retryMessage?.let { Text(it, color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium) }
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
    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) SextantColors.Text else SextantColors.TextSubtle)
      Text(description, color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
    SextantSwitch(checked = checked, enabled = enabled, onCheckedChange = null)
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
  "payment_match_ambiguous" -> "存在相近的同金额交易，请检查账本后手动补记"
  "automation_disabled" -> "自动记账或对应来源已关闭"
  else -> this
}
