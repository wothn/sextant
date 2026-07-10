package com.sextant.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sextant.app.data.AppState
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.SextantRepository
import com.sextant.app.ui.components.QuickEntryDialog
import com.sextant.app.ui.navigation.LedgerNavigationBar
import com.sextant.app.ui.navigation.Tab
import com.sextant.app.ui.screens.AnalyticsScreen
import com.sextant.app.ui.screens.BudgetScreen
import com.sextant.app.ui.screens.HomeScreen
import com.sextant.app.ui.screens.SettingsScreen
import com.sextant.app.ui.theme.SextantColors
import java.util.Calendar

@Composable
fun SextantApp(repository: SextantRepository) {
  var state by remember { mutableStateOf(AppState()) }
  var tab by remember { mutableStateOf(Tab.Home) }
  var showQuickEntry by remember { mutableStateOf(false) }
  var editingTransaction by remember { mutableStateOf<TransactionListItem?>(null) }
  var pendingDelete by remember { mutableStateOf<TransactionListItem?>(null) }
  var filter by remember { mutableStateOf(LedgerFilter(Calendar.getInstance().run { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); timeInMillis })) }
  var lastPaymentMethodId by remember { mutableStateOf<String?>(null) }
  var reloadKey by remember { mutableIntStateOf(0) }

  LaunchedEffect(reloadKey, filter) {
    state = repository.loadState(filter)
  }

  Scaffold(
    bottomBar = {
      LedgerNavigationBar(
        selectedTab = tab,
        onTabSelected = { tab = it },
        onQuickEntry = { showQuickEntry = true },
      )
    },
    containerColor = SextantColors.Background,
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      when (tab) {
        Tab.Home -> HomeScreen(
          state = state,
          filter = filter,
          onFilterChanged = { filter = it },
          onQuickEntry = { showQuickEntry = true },
          onTransactionClick = { editingTransaction = it },
        )
        Tab.Analytics -> AnalyticsScreen(state)
        Tab.Discover -> BudgetScreen(state, repository) { reloadKey++ }
        Tab.Settings -> SettingsScreen(state, repository) { reloadKey++ }
      }
    }
  }

  if (showQuickEntry) {
    QuickEntryDialog(
      state = state,
      initialPaymentMethodId = lastPaymentMethodId,
      onDismiss = { showQuickEntry = false },
      onSubmit = { type, amountCents, categoryId, paymentMethodId, description, transactionDate ->
        repository.addTransaction(type, amountCents, categoryId, paymentMethodId, description, transactionDate)
        lastPaymentMethodId = paymentMethodId
        showQuickEntry = false
        reloadKey++
      },
    )
  }

  editingTransaction?.let { item ->
    QuickEntryDialog(
      state = state,
      editing = item,
      onDismiss = { editingTransaction = null },
      onDelete = {
        pendingDelete = item
      },
      onSubmit = { type, amountCents, categoryId, paymentMethodId, description, transactionDate ->
        repository.updateTransaction(item.transaction.id, type, amountCents, categoryId, paymentMethodId, description, transactionDate)
        editingTransaction = null
        reloadKey++
      },
    )
  }

  pendingDelete?.let { item ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("删除这笔记录？") },
      text = { Text("删除后将从流水和统计中移除。") },
      confirmButton = {
        TextButton(onClick = {
          repository.deleteTransaction(item.transaction.id)
          pendingDelete = null
          editingTransaction = null
          reloadKey++
        }) { Text("删除") }
      },
      dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
    )
  }
}
