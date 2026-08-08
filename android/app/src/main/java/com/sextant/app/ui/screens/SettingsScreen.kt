package com.sextant.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sextant.app.ui.components.CategoryManagement
import com.sextant.app.ui.components.SectionTitle
import com.sextant.app.ui.theme.SextantColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(state: AppState, repository: SextantRepository, onChanged: () -> Unit) {
  var destination by remember { mutableStateOf(SettingsDestination.Overview) }
  var operationMessage by remember { mutableStateOf<DataOperationMessage?>(null) }
  var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
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
  val csvExportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("text/csv"),
  ) { uri ->
    if (uri != null) runExport(uri, repository::exportCsv, "CSV 流水已保存。", addBom = true)
  }
  val backupImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> pendingRestoreUri = uri }

  if (destination == SettingsDestination.CategoryManagement) {
    BackHandler { destination = SettingsDestination.Overview }
  }

  when (destination) {
    SettingsDestination.Overview -> SettingsOverview(
      onCategoryManagementClick = { destination = SettingsDestination.CategoryManagement },
      isBusy = isBusy,
      onBackupExport = { backupExportLauncher.launch(backupFileName()) },
      onBackupImport = { backupImportLauncher.launch(arrayOf("application/json", "text/plain")) },
      onCsvExport = { csvExportLauncher.launch(csvFileName()) },
    )

    SettingsDestination.CategoryManagement -> CategoryManagementScreen(
      state = state,
      repository = repository,
      onChanged = onChanged,
      onNavigateBack = { destination = SettingsDestination.Overview },
    )
  }

  pendingRestoreUri?.let { uri ->
    AlertDialog(
      onDismissRequest = { pendingRestoreUri = null },
      confirmButton = {
        TextButton(onClick = {
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
      dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") } },
      title = { Text("恢复这份备份？") },
      text = { Text("当前账本中的分类、来源、流水、预算和设置将被完整替换。文件校验或写入失败时不会修改原数据。") },
    )
  }

  operationMessage?.let { message ->
    AlertDialog(
      onDismissRequest = { operationMessage = null },
      confirmButton = { TextButton(onClick = { operationMessage = null }) { Text("完成") } },
      title = { Text(message.title) },
      text = { Text(message.body) },
    )
  }
}

@Composable
private fun SettingsOverview(
  onCategoryManagementClick: () -> Unit,
  isBusy: Boolean,
  onBackupExport: () -> Unit,
  onBackupImport: () -> Unit,
  onCsvExport: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SectionTitle("设置")
    }
    item {
      SettingsEntry(
        title = "分类管理",
        description = "管理收入、支出分类与子分类",
        icon = Icons.Outlined.Category,
        onClick = onCategoryManagementClick,
      )
    }
    item { SectionTitle("数据管理") }
    item {
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("完整账本备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text("保存分类、来源、流水、预算和设置，可用于完整恢复。", color = SextantColors.TextMuted)
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onBackupExport, enabled = !isBusy) {
              Text(if (isBusy) "处理中" else "导出备份")
            }
            OutlinedButton(onClick = onBackupImport, enabled = !isBusy) {
              Text("导入恢复")
            }
          }
        }
      }
    }
    item {
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("CSV 流水", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text("导出便于表格查看的流水，不包含恢复所需的完整数据。", color = SextantColors.TextMuted)
          OutlinedButton(onClick = onCsvExport, enabled = !isBusy) {
            Text("导出 CSV")
          }
        }
      }
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
private fun SettingsEntry(
  title: String,
  description: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = SextantColors.Surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = SextantColors.AccentStrong,
        modifier = Modifier.size(24.dp),
      )
      Spacer(Modifier.width(14.dp))
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
      }
      Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = SextantColors.TextMuted,
      )
    }
  }
}

private enum class SettingsDestination {
  Overview,
  CategoryManagement,
}

private data class DataOperationMessage(val title: String, val body: String)

private fun BackupSummary.restoreMessage(): String =
  "已恢复 $transactionCount 笔流水、$categoryCount 个分类、$paymentMethodCount 个来源和 $budgetCount 项预算。"

private fun Throwable.toDataErrorMessage(): String = when (this) {
  is BackupException -> message ?: "备份文件无效。"
  else -> "无法读写所选文件，请重试。"
}

private fun backupFileName(): String = "sextant-backup-${fileDate()}.json"
private fun csvFileName(): String = "sextant-transactions-${fileDate()}.csv"
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
