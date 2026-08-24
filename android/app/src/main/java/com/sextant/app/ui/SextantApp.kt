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
import com.sextant.app.data.AutomationKind
import com.sextant.app.data.EntryOrigin
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.TransactionType
import com.sextant.app.automation.ALIPAY_PACKAGE
import com.sextant.app.automation.WECHAT_PACKAGE
import com.sextant.app.ui.components.QuickEntryDialog
import com.sextant.app.ui.navigation.LedgerNavigationBar
import com.sextant.app.ui.navigation.Tab
import com.sextant.app.ui.screens.AnalyticsScreen
import com.sextant.app.ui.screens.BudgetScreen
import com.sextant.app.ui.screens.HomeScreen
import com.sextant.app.ui.screens.SettingsScreen
import com.sextant.app.ui.theme.SextantColors
import java.util.Calendar
import kotlinx.coroutines.flow.collect

@Composable
fun SextantApp(
  repository: SextantRepository,
  requestedEditTransactionId: String? = null,
  onEditRequestConsumed: () -> Unit = {},
) {
  var state by remember { mutableStateOf(AppState()) }
  var tab by remember { mutableStateOf(Tab.Home) }
  var showQuickEntry by remember { mutableStateOf(false) }
  var editingTransaction by remember { mutableStateOf<TransactionListItem?>(null) }
  var pendingDelete by remember { mutableStateOf<TransactionListItem?>(null) }
  var pendingRule by remember { mutableStateOf<ClassificationRulePrompt?>(null) }
  var filter by remember { mutableStateOf(LedgerFilter(Calendar.getInstance().run { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); timeInMillis })) }
  var lastPaymentMethodId by remember { mutableStateOf<String?>(null) }
  var reloadKey by remember { mutableIntStateOf(0) }

  LaunchedEffect(reloadKey, filter) {
    state = repository.loadState(filter)
  }

  LaunchedEffect(repository) {
    repository.changes.collect { reloadKey++ }
  }

  LaunchedEffect(requestedEditTransactionId) {
    val id = requestedEditTransactionId ?: return@LaunchedEffect
    repository.findTransaction(id)?.let { item ->
      editingTransaction = item
      filter = filter.copy(monthStart = Calendar.getInstance().apply {
        timeInMillis = item.transaction.transactionDate
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }.timeInMillis)
    }
    onEditRequestConsumed()
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
        if (
          item.transaction.entryOrigin == EntryOrigin.Notification &&
          categoryId != null && categoryId != item.transaction.categoryId &&
          item.transaction.automationKind != null
        ) {
          val sourcePackage = when (item.paymentMethodName) {
            "微信" -> WECHAT_PACKAGE
            "支付宝" -> ALIPAY_PACKAGE
            else -> null
          }
          if (sourcePackage != null) {
            pendingRule = ClassificationRulePrompt(
              sourcePackage = sourcePackage,
              direction = type,
              kind = item.transaction.automationKind,
              pattern = description.ifBlank { item.transaction.description },
              categoryId = categoryId,
            )
          }
        }
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

  pendingRule?.let { rule ->
    AlertDialog(
      onDismissRequest = { pendingRule = null },
      title = { Text("记住这次分类？") },
      text = { Text("以后遇到相似商户或摘要时，自动使用这个分类。规则只作用于相同支付应用和资金方向。") },
      confirmButton = {
        TextButton(onClick = {
          runCatching {
            repository.saveClassificationRule(rule.sourcePackage, rule.direction, rule.kind, rule.pattern, rule.categoryId)
          }
          pendingRule = null
        }) { Text("记住") }
      },
      dismissButton = { TextButton(onClick = { pendingRule = null }) { Text("仅本次") } },
    )
  }
}

private data class ClassificationRulePrompt(
  val sourcePackage: String,
  val direction: TransactionType,
  val kind: AutomationKind,
  val pattern: String,
  val categoryId: String,
)
