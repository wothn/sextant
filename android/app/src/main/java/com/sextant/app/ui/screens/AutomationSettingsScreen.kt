package com.sextant.app.ui.screens

import android.Manifest
import android.content.ComponentName
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sextant.app.SextantApplication
import com.sextant.app.automation.ALIPAY_PACKAGE
import com.sextant.app.automation.SextantNotificationListenerService
import com.sextant.app.automation.WECHAT_PACKAGE
import com.sextant.app.data.AppState
import com.sextant.app.data.AutomationEventStatus
import com.sextant.app.data.SextantRepository
import com.sextant.app.ui.components.sextantSwitchColors
import com.sextant.app.ui.theme.SextantColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AutomationSettingsScreen(
  state: AppState,
  repository: SextantRepository,
  onChanged: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var draft by remember(state.automationSettings) { mutableStateOf(state.automationSettings) }
  var apiKey by remember { mutableStateOf("") }
  var message by remember { mutableStateOf<String?>(null) }
  var listenerRefresh by remember { mutableIntStateOf(0) }
  val listenerEnabled = remember(listenerRefresh) {
    val expected = ComponentName(context, SextantNotificationListenerService::class.java)
    Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
      .orEmpty()
      .split(':')
      .mapNotNull(ComponentName::unflattenFromString)
      .any { it == expected }
  }
  val notificationAccessLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) {
    listenerRefresh++
    onChanged()
  }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted -> message = if (granted) "已允许显示自动记账结果通知。" else "未允许结果通知，自动入账仍会继续。" }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
        Text("自动记账", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      }
    }
    item {
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("通知读取授权", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text(
            "Sextant 只处理微信和支付宝中包含金额与支付关键词的通知。普通聊天不会保存或上传。功能默认关闭，可随时撤销系统授权。",
            color = SextantColors.TextMuted,
          )
          Text(if (listenerEnabled) "状态：已授权" else "状态：未授权", color = if (listenerEnabled) SextantColors.Success else SextantColors.Danger)
          OutlinedButton(onClick = {
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
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("支付应用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          InlineSwitch("微信", WECHAT_PACKAGE, draft.wechatEnabled) { draft = draft.copy(wechatEnabled = it) }
          InlineSwitch("支付宝", ALIPAY_PACKAGE, draft.alipayEnabled) { draft = draft.copy(alipayEnabled = it) }
        }
      }
    }
    item {
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          InlineSwitch("入账结果通知", "可撤销或打开编辑", draft.resultNotificationsEnabled) {
            draft = draft.copy(resultNotificationsEnabled = it)
          }
          if (draft.resultNotificationsEnabled && Build.VERSION.SDK_INT >= 33) {
            OutlinedButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
              Text("允许结果通知")
            }
          }
        }
      }
    }
    item {
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          InlineSwitch("AI 增强", "金额明确后，增强收支方向、商户、分类和描述", draft.cloudEnabled) {
            draft = draft.copy(cloudEnabled = it)
          }
          if (draft.cloudEnabled) {
            Text(
              "启用后，仅将已确认含有明确金额的通知发送到你配置的第三方接口。AI 不会猜测缺失金额；请求失败时使用可信的本地结果。",
              color = SextantColors.Danger,
            )
            OutlinedTextField(
              value = draft.cloudBaseUrl,
              onValueChange = { draft = draft.copy(cloudBaseUrl = it) },
              label = { Text("HTTPS Base URL") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = draft.cloudModel,
              onValueChange = { draft = draft.copy(cloudModel = it) },
              label = { Text("模型名称") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = apiKey,
              onValueChange = { apiKey = it },
              label = { Text(if (draft.hasCloudApiKey) "API Key（已保存，留空则不修改）" else "API Key") },
              visualTransformation = PasswordVisualTransformation(),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = {
                scope.launch {
                  val result = runCatching {
                    if (apiKey.isNotBlank()) repository.setAutomationApiKey(apiKey)
                    repository.updateAutomationSettings(draft)
                    withContext(Dispatchers.IO) {
                      (context.applicationContext as SextantApplication).automationCoordinator.testCloudConnection()
                    }
                  }
                  message = if (result.getOrDefault(false)) "连接测试成功。" else "连接测试失败，请检查接口、模型和密钥。"
                  onChanged()
                }
              }) { Text("测试连接") }
              OutlinedButton(onClick = {
                repository.clearAutomationApiKey()
                apiKey = ""
                draft = draft.copy(cloudEnabled = false, hasCloudApiKey = false)
                message = "已清除 API Key。"
              }) { Text("清除密钥") }
            }
          }
        }
      }
    }
    item {
      Button(
        onClick = {
          val result = runCatching {
            if (apiKey.isNotBlank()) repository.setAutomationApiKey(apiKey)
            repository.updateAutomationSettings(draft)
          }
          message = result.fold({ "自动记账设置已保存。" }, { it.message ?: "保存失败。" })
          if (result.isSuccess) {
            apiKey = ""
            onChanged()
          }
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
        Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${event.sourcePackage.sourceName()} · ${event.status.label()}", fontWeight = FontWeight.SemiBold)
            Text(SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(event.postedAt)), color = SextantColors.TextMuted)
            event.parsedSummary?.let { Text(it, color = SextantColors.TextMuted) }
            if (event.status == AutomationEventStatus.Failed) {
              event.failureCode?.let { Text("原因：${it.failureLabel()}", color = SextantColors.Danger) }
            }
            if (event.status == AutomationEventStatus.Failed) {
              OutlinedButton(onClick = {
                val queued = (context.applicationContext as SextantApplication).automationCoordinator.retry(event.id)
                message = if (queued) "已重新加入处理队列。" else "原通知已过期，无法重试。"
                onChanged()
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
  Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
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
    Switch(
      checked = checked,
      enabled = enabled,
      onCheckedChange = onCheckedChange,
      colors = sextantSwitchColors(),
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
  AutomationEventStatus.WaitingCloud -> "等待 AI 增强"
  AutomationEventStatus.Posted -> "已入账"
  AutomationEventStatus.Duplicate -> "已去重"
  AutomationEventStatus.Failed -> "处理失败"
  AutomationEventStatus.Undone -> "已撤销"
}

private fun String.failureLabel() = when (this) {
  "amount_missing" -> "通知未包含明确金额"
  "amount_ambiguous" -> "通知包含多个可能的金额"
  "direction_missing" -> "无法确定收支方向，且 AI 增强未启用"
  "ai_not_configured", "cloud_not_configured" -> "AI 增强配置不可用"
  "ai_validation_failed", "cloud_validation_failed" -> "AI 返回内容未通过本地校验"
  "cloud_request_failed" -> "AI 请求失败"
  else -> this
}
