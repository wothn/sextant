package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sextant.app.data.*
import com.sextant.app.ui.util.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun AgentDetailLinks(details: List<AgentLedgerDetail>, repository: SextantRepository) {
  var selected by rememberSaveable { mutableIntStateOf(-1) }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    details.forEachIndexed { index, detail ->
      AssistChip(onClick = { selected = index }, label = { Text("查看明细 · ${detail.title}") })
    }
  }
  details.getOrNull(selected)?.let { detail ->
    Dialog(onDismissRequest = { selected = -1 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selected = -1 }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回对话") }
            Text(detail.title, style = MaterialTheme.typography.titleLarge)
          }
          Text(listOfNotNull(detail.filter().rangeLabel(), detail.type.label, detail.categoryName).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium)
          var rows by remember(detail) { mutableStateOf<List<TransactionListItem>?>(null) }
          var failed by remember(detail) { mutableStateOf(false) }
          var attempt by remember { mutableIntStateOf(0) }
          LaunchedEffect(detail, attempt) {
            failed = false
            try { rows = repository.detailTransactions(detail) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
          }
          val loaded = rows
          when {
            failed -> TextButton(onClick = { attempt++ }) { Text("读取失败，重试") }
            loaded == null -> CircularProgressIndicator(Modifier.padding(16.dp))
            else -> {
              val total = loaded.sumOf { it.transaction.amountCents }
              if (total != detail.totalCents || loaded.size != detail.count) {
                Text("回答时共 ${detail.count} 笔 · ${formatCurrency(detail.totalCents)}", style = MaterialTheme.typography.bodySmall)
              }
              Text("当前账本 · ${loaded.size} 笔 · ${formatCurrency(total)}",
                Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
              if (loaded.isEmpty()) Text("这个范围内没有流水")
              val days = remember(loaded) { loaded.groupBy { it.transaction.transactionDate.toLocalDate() } }
              LazyColumn {
                days.forEach { (day, records) ->
                  item { Text(calendarDateLabel(day), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
                  items(records, key = { it.transaction.id }) { item ->
                    LedgerTransactionRow(item, item.categoryName, null)
                    HorizontalDivider()
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
