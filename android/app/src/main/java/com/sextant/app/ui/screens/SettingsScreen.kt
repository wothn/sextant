package com.sextant.app.ui.screens

import com.sextant.app.ui.components.SextantAlertDialog
import com.sextant.app.ui.components.SextantFlexibleHeader
import com.sextant.app.ui.components.SextantLoadingIndicator
import com.sextant.app.ui.components.SextantWavyProgressIndicator
import com.sextant.app.ui.components.SextantRadioButton
import com.sextant.app.ui.components.SextantSurface
import com.sextant.app.ui.components.SextantTextButton

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.BackupException
import com.sextant.app.data.BackupSummary
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.ThemePreference
import com.sextant.app.ui.components.CategoryManagement
import com.sextant.app.ui.components.PaymentMethodManagement
import com.sextant.app.ui.theme.SextantColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
  state: AppState,
  repository: SextantRepository,
  themePreference: ThemePreference,
  onThemePreferenceChanged: (ThemePreference) -> Unit,
  onChanged: () -> Unit,
) {
  var destination by remember { mutableStateOf(SettingsDestination.Overview) }
  var operationMessage by remember { mutableStateOf<DataOperationMessage?>(null) }
  var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
  var showThemeDialog by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  fun runExport(uri: Uri, content: () -> String, successMessage: String, addBom: Boolean = false) {
    isBusy = true
    scope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          context.writeText(uri, content(), addBom)
        }
      }
      operationMessage = result.fold(
        onSuccess = { DataOperationMessage("导出完成", successMessage) },
        onFailure = { DataOperationMessage("导出失败", it.toDataErrorMessage()) },
      )
      isBusy = false
    }
  }

  val backupExportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    if (uri != null) runExport(uri, repository::exportBackup, "完整账本备份已保存。")
  }
  val backupImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> pendingRestoreUri = uri }

  if (destination != SettingsDestination.Overview) {
    BackHandler { destination = SettingsDestination.Overview }
  }

  when (destination) {
    SettingsDestination.Overview -> SettingsOverview(
      onAutomationClick = { destination = SettingsDestination.Automation },
      onCategoryManagementClick = { destination = SettingsDestination.CategoryManagement },
      onPaymentMethodManagementClick = { destination = SettingsDestination.PaymentMethodManagement },
      themePreference = themePreference,
      onThemeClick = { showThemeDialog = true },
      isBusy = isBusy,
      onBackupExport = { backupExportLauncher.launch(backupFileName()) },
      onBackupImport = { backupImportLauncher.launch(arrayOf("application/json", "text/plain")) },
    )

    SettingsDestination.CategoryManagement -> CategoryManagementScreen(
      state = state,
      repository = repository,
      onChanged = onChanged,
      onNavigateBack = { destination = SettingsDestination.Overview },
    )

    SettingsDestination.PaymentMethodManagement -> PaymentMethodManagementScreen(
      state = state,
      repository = repository,
      onChanged = onChanged,
      onNavigateBack = { destination = SettingsDestination.Overview },
    )

    SettingsDestination.Automation -> AutomationSettingsScreen(
      state = state,
      repository = repository,
      onChanged = onChanged,
      onNavigateBack = { destination = SettingsDestination.Overview },
    )
  }

  pendingRestoreUri?.let { uri ->
    SextantAlertDialog(
      onDismissRequest = { pendingRestoreUri = null },
      confirmButton = {
        SextantTextButton(onClick = {
          pendingRestoreUri = null
          isBusy = true
          scope.launch {
            val result = runCatching {
              withContext(Dispatchers.IO) {
                repository.restoreBackup(context.readBackupText(uri))
              }
            }
            operationMessage = result.fold(
              onSuccess = { summary -> DataOperationMessage("恢复完成", summary.restoreMessage()) },
              onFailure = { DataOperationMessage("恢复失败", it.toDataErrorMessage()) },
            )
            if (result.isSuccess) onChanged()
            isBusy = false
          }
        }) { Text("覆盖并恢复") }
      },
      dismissButton = { SextantTextButton(onClick = { pendingRestoreUri = null }) { Text("取消") } },
      title = { Text("恢复这份备份？") },
      text = { Text("当前账本中的分类、来源、流水、预算和设置将被完整替换。文件校验或写入失败时不会修改原数据。") },
    )
  }

  operationMessage?.let { message ->
    SextantAlertDialog(
      onDismissRequest = { operationMessage = null },
      confirmButton = { SextantTextButton(onClick = { operationMessage = null }) { Text("完成") } },
      title = { Text(message.title) },
      text = { Text(message.body) },
    )
  }

  if (showThemeDialog) {
    SextantAlertDialog(
      onDismissRequest = { showThemeDialog = false },
      confirmButton = {
        SextantTextButton(onClick = { showThemeDialog = false }) { Text("完成") }
      },
      title = { Text("主题") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("选择应用的显示方式", color = SextantColors.TextMuted)
          listOf(ThemePreference.Light, ThemePreference.Dark, ThemePreference.System).forEach { item ->
            ThemePreferenceOption(
              preference = item,
              selected = item == themePreference,
              onClick = { onThemePreferenceChanged(item) },
            )
          }
        }
      },
    )
  }
}

@Composable
private fun SettingsOverview(
  onAutomationClick: () -> Unit,
  onCategoryManagementClick: () -> Unit,
  onPaymentMethodManagementClick: () -> Unit,
  themePreference: ThemePreference,
  onThemeClick: () -> Unit,
  isBusy: Boolean,
  onBackupExport: () -> Unit,
  onBackupImport: () -> Unit,
) {
  val listState = rememberLazyListState()
  Column(Modifier.fillMaxSize()) {
    SextantFlexibleHeader(
      title = "设置",
      scrollState = listState,
      subtitle = "管理账本、外观与数据",
      modifier = Modifier.padding(horizontal = 20.dp),
    )
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item {
        SettingsSection("账本") {
          SettingsEntry(
            title = "自动记账",
            description = "从微信、支付宝支付通知生成流水",
            icon = Icons.Outlined.AutoAwesome,
            onClick = onAutomationClick,
          )
          SettingsEntry(
            title = "分类管理",
            description = "管理收入、支出分类与子分类",
            icon = Icons.Outlined.Category,
            onClick = onCategoryManagementClick,
          )
          SettingsEntry(
            title = "来源管理",
            description = "管理现金、银行卡和支付平台",
            icon = Icons.Outlined.Payments,
            onClick = onPaymentMethodManagementClick,
          )
        }
      }
      item {
        SettingsSection("外观") {
          ThemeSettingsEntry(preference = themePreference, onClick = onThemeClick)
        }
      }
      item {
        SettingsSection("数据") {
          // 备份读写没有可报的百分比，用波浪进度条说明「还在动」，而不是假装有进度。
          if (isBusy) {
            SextantWavyProgressIndicator(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
          }
          SettingsEntry(
            title = "导出完整备份",
            description = "将分类、流水、预算和设置保存为 JSON 文件",
            icon = Icons.Outlined.ArrowDownward,
            enabled = !isBusy,
            trailing = if (isBusy) { { SextantLoadingIndicator(size = 22.dp) } } else null,
            trailingLabel = if (isBusy) null else "导出",
            onClick = onBackupExport,
          )
          SettingsEntry(
            title = "从备份恢复",
            description = "用备份文件覆盖当前账本，恢复前会再次确认",
            icon = Icons.Outlined.ArrowUpward,
            enabled = !isBusy,
            trailingLabel = "导入",
            onClick = onBackupImport,
          )
        }
      }
    }
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      title,
      color = SextantColors.TextSubtle,
      style = MaterialTheme.typography.labelLarge,
    )
    SextantSurface(modifier = Modifier.fillMaxWidth()) {
      Column(content = content)
    }
  }
}

@Composable
private fun CategoryManagementScreen(
  state: AppState,
  repository: SextantRepository,
  onChanged: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      CategoryManagement(
        categories = state.categories,
        repository = repository,
        onChanged = onChanged,
        onNavigateBack = onNavigateBack,
      )
    }
  }
}

@Composable
private fun PaymentMethodManagementScreen(
  state: AppState,
  repository: SextantRepository,
  onChanged: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      PaymentMethodManagement(
        methods = state.allPaymentMethods,
        repository = repository,
        onChanged = onChanged,
        onNavigateBack = onNavigateBack,
      )
    }
  }
}

@Composable
private fun ThemeSettingsEntry(
  preference: ThemePreference,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 18.dp, vertical = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingsIcon(Icons.Outlined.Palette)
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text("主题", style = MaterialTheme.typography.titleMedium)
      Text(preference.label(), color = SextantColors.AccentStrong, style = MaterialTheme.typography.bodySmall)
    }
    Icon(Icons.Outlined.ChevronRight, contentDescription = "选择主题", tint = SextantColors.TextMuted)
  }
}

@Composable
private fun SettingsEntry(
  title: String,
  description: String,
  icon: ImageVector,
  enabled: Boolean = true,
  trailingLabel: String? = null,
  trailing: (@Composable () -> Unit)? = null,
  onClick: () -> Unit,
) {
  val titleColor = if (enabled) SextantColors.Text else SextantColors.TextSubtle
  val descriptionColor = if (enabled) SextantColors.TextMuted else SextantColors.TextSubtle
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 18.dp, vertical = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingsIcon(icon, enabled = enabled)
    Spacer(Modifier.width(14.dp))
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(title, color = titleColor, style = MaterialTheme.typography.titleMedium)
      Text(description, color = descriptionColor, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.width(12.dp))
    when {
      trailing != null -> trailing()
      trailingLabel != null -> Text(
        trailingLabel,
        color = if (enabled) SextantColors.AccentStrong else SextantColors.TextSubtle,
        style = MaterialTheme.typography.labelLarge,
      )
      else -> Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = descriptionColor)
    }
  }
}

@Composable
private fun SettingsIcon(icon: ImageVector, enabled: Boolean = true) {
  SextantSurface(
    modifier = Modifier.size(44.dp),
    color = if (enabled) SextantColors.AccentSoft else SextantColors.SurfaceContainer,
    shape = MaterialTheme.shapes.large,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (enabled) SextantColors.AccentStrong else SextantColors.TextSubtle,
      modifier = Modifier.padding(9.dp),
    )
  }
}

@Composable
private fun ThemePreferenceOption(
  preference: ThemePreference,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SextantRadioButton(
      selected = selected,
      onClick = onClick,
    )
    Spacer(Modifier.width(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(preference.label(), style = MaterialTheme.typography.bodyLarge)
      Text(preference.description(), color = SextantColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
  }
}

private enum class SettingsDestination {
  Overview,
  Automation,
  CategoryManagement,
  PaymentMethodManagement,
}

private fun ThemePreference.label(): String = when (this) {
  ThemePreference.Light -> "浅色模式"
  ThemePreference.Dark -> "深色模式"
  ThemePreference.System -> "跟随系统"
}

private fun ThemePreference.description(): String = when (this) {
  ThemePreference.Light -> "始终使用明亮界面"
  ThemePreference.Dark -> "始终使用深色界面"
  ThemePreference.System -> "跟随手机的系统设置"
}

private data class DataOperationMessage(val title: String, val body: String)

private fun BackupSummary.restoreMessage(): String =
  "已恢复 $transactionCount 笔流水、$categoryCount 个分类、$paymentMethodCount 个来源和 $budgetCount 项预算。"

private fun Throwable.toDataErrorMessage(): String = when (this) {
  is BackupException -> message ?: "备份文件无效。"
  else -> "无法读写所选文件，请重试。"
}

private fun backupFileName(): String = "sextant-backup-${fileDate()}.json"
private fun fileDate(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

private fun Context.writeText(uri: Uri, content: String, addBom: Boolean) {
  val stream = contentResolver.openOutputStream(uri, "wt")
    ?: throw IllegalStateException("无法打开目标文件")
  stream.bufferedWriter(Charsets.UTF_8).use { writer ->
    if (addBom) writer.write('\uFEFF'.code)
    writer.write(content)
  }
}

private fun Context.readBackupText(uri: Uri): String {
  val stream = contentResolver.openInputStream(uri)
    ?: throw IllegalStateException("无法打开备份文件")
  return stream.bufferedReader(Charsets.UTF_8).use { reader ->
    val output = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
      val count = reader.read(buffer)
      if (count < 0) break
      output.append(buffer, 0, count)
      if (output.length > MAX_BACKUP_CHARACTERS) throw BackupException("备份文件过大")
    }
    output.toString()
  }
}

private const val MAX_BACKUP_CHARACTERS = 25 * 1024 * 1024
