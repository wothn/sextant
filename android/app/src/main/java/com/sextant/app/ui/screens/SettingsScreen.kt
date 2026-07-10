package com.sextant.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.SextantRepository
import com.sextant.app.ui.components.CategoryManagement
import com.sextant.app.ui.components.SectionTitle
import com.sextant.app.ui.theme.SextantColors

@Composable
fun SettingsScreen(state: AppState, repository: SextantRepository, onChanged: () -> Unit) {
  var csv by remember { mutableStateOf<String?>(null) }
  var destination by remember { mutableStateOf(SettingsDestination.Overview) }

  if (destination == SettingsDestination.CategoryManagement) {
    BackHandler { destination = SettingsDestination.Overview }
  }

  when (destination) {
    SettingsDestination.Overview -> SettingsOverview(
      repository = repository,
      onCategoryManagementClick = { destination = SettingsDestination.CategoryManagement },
      onCsvGenerated = { csv = it },
    )

    SettingsDestination.CategoryManagement -> CategoryManagementScreen(
      state = state,
      repository = repository,
      onChanged = onChanged,
      onNavigateBack = { destination = SettingsDestination.Overview },
    )
  }

  if (csv != null) {
    AlertDialog(
      onDismissRequest = { csv = null },
      confirmButton = { TextButton(onClick = { csv = null }) { Text("完成") } },
      title = { Text("CSV 已生成") },
      text = { Text(csv.orEmpty().lineSequence().take(8).joinToString("\n")) },
    )
  }
}

@Composable
private fun SettingsOverview(
  repository: SextantRepository,
  onCategoryManagementClick: () -> Unit,
  onCsvGenerated: (String) -> Unit,
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
          Text("CSV 备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text("导出当前本地账本数据。", color = SextantColors.TextMuted)
          Button(onClick = { onCsvGenerated(repository.exportCsv()) }) {
            Text("生成 CSV")
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
