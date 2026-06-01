package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
      )
    }
    item { SectionTitle("数据管理") }
    item {
      Card(colors = CardDefaults.cardColors(containerColor = SextantColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("CSV 备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text("导出当前本地账本数据。", color = SextantColors.TextMuted)
          Button(onClick = { csv = repository.exportCsv() }) {
            Text("生成 CSV")
          }
        }
      }
    }
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
