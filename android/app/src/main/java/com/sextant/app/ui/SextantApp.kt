@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.sextant.app.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.sextant.app.ui.components.SextantAlertDialog
import com.sextant.app.ui.components.SextantCollapsibleFab
import com.sextant.app.ui.components.SextantTabContent
import com.sextant.app.ui.components.SextantTextButton

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.sextant.app.data.StateProjection
import com.sextant.app.ui.components.rememberSaveAction
import com.sextant.app.ui.components.SextantLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.BackupCoordinator
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.ThemePreference
import com.sextant.app.ui.components.QuickEntryDialog
import com.sextant.app.ui.navigation.LedgerNavigationBar
import com.sextant.app.ui.navigation.Tab
import com.sextant.app.ui.navigation.rememberQuickEntryFabState
import com.sextant.app.ui.screens.AnalyticsScreen
import com.sextant.app.ui.screens.HomeScreen
import com.sextant.app.ui.screens.LedgerCalendarScreen
import androidx.activity.compose.BackHandler
import com.sextant.app.ui.screens.PlansScreen
import com.sextant.app.ui.screens.SettingsScreen
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.LedgerFilterSaver

private val BottomBarHorizontalMargin = 48.dp

@Composable
fun SextantApp(
  repository: SextantRepository,
  viewModel: LedgerViewModel,
  backupCoordinator: BackupCoordinator,
  themePreference: ThemePreference,
  onThemePreferenceChanged: (ThemePreference) -> Unit,
  requestedEditTransactionId: String? = null,
  onEditRequestConsumed: () -> Unit = {},
) {
  var tab by rememberSaveable { mutableStateOf(Tab.Home) }
  var showCalendar by rememberSaveable { mutableStateOf(false) }
  val calendarOpen = tab == Tab.Home && showCalendar
  BackHandler(enabled = calendarOpen) { showCalendar = false }
  var showQuickEntry by rememberSaveable { mutableStateOf(false) }
  var editingId by rememberSaveable { mutableStateOf<String?>(null) }
  var editingTransaction by remember { mutableStateOf<TransactionListItem?>(null) }
  var pendingDelete by remember { mutableStateOf<TransactionListItem?>(null) }
  var filter by rememberSaveable(stateSaver = LedgerFilterSaver) { mutableStateOf(LedgerFilter.allTime()) }
  var lastPaymentMethodId by rememberSaveable { mutableStateOf<String?>(null) }
  val ui by viewModel.state.collectAsState()
  val projection = when (tab) {
    Tab.Discover -> StateProjection.Plans
    Tab.Settings -> StateProjection.Settings
    Tab.Analytics -> StateProjection.Analytics
    else -> StateProjection.Ledger
  }
  val request = LedgerRequest(if (tab == Tab.Home || tab == Tab.Analytics) filter else LedgerFilter.allTime(), projection)
  val state = ui.data ?: AppState()
  val quickEntryFab = rememberQuickEntryFabState()
  val saveAction = rememberSaveAction("ledger") {
    showQuickEntry = false
    editingId = null
    pendingDelete = null
  }
  val pageStates = rememberSaveableStateHolder()
  LaunchedEffect(request) { viewModel.show(request) }
  LaunchedEffect(ui.theme) { if (ui.data != null) onThemePreferenceChanged(ui.theme) }
  LaunchedEffect(tab, calendarOpen) { if (tab == Tab.Home && !calendarOpen) quickEntryFab.show() }
  LaunchedEffect(editingId) {
    editingTransaction = editingId?.let { repository.findTransaction(it) }
  }
  LaunchedEffect(requestedEditTransactionId) {
    val id = requestedEditTransactionId ?: return@LaunchedEffect
    repository.findTransaction(id)?.let { item ->
      filter = LedgerFilter.forMonth(item.transaction.transactionDate)
      tab = Tab.Home
      showCalendar = false
      editingId = id
    }
    onEditRequestConsumed()
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Scaffold(
    bottomBar = {
      if (!calendarOpen) LedgerBottomBar(
        selectedTab = tab,
        onTabSelected = { tab = it },
      )
    },
    containerColor = SextantColors.Background,
  ) { padding ->
    Box(
      Modifier
        .padding(padding)
        .fillMaxSize()
        // FAB 的滚动收起挂在内容区最外层：页面里的任何列表滚动都会经过这里，
        // 只读方向、不消费位移，所以页面的滚动行为不受影响。
        .nestedScroll(quickEntryFab.nestedScrollConnection),
    ) {
      SextantTabContent(selectedIndex = tab.ordinal) { index ->
        if (ui.request != request || ui.data == null || index != tab.ordinal) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (ui.error == null || ui.request != request) SextantLoadingIndicator()
            else SextantTextButton(onClick = viewModel::refresh) { Text("读取失败，点击重试") }
          }
        } else {
          pageStates.SaveableStateProvider(if (calendarOpen) "calendar" else index) {
            when (Tab.entries[index]) {
              Tab.Home -> if (calendarOpen) LedgerCalendarScreen(
                state = state,
                filter = filter,
                onFilterChanged = { filter = it },
                onBack = { showCalendar = false },
                onTransactionClick = { editingId = it.transaction.id },
              ) else HomeScreen(
                state = state,
                filter = filter,
                onFilterChanged = { filter = it },
                onTransactionClick = { editingId = it.transaction.id },
                onOpenCalendar = { showCalendar = true },
              )
              Tab.Analytics -> AnalyticsScreen(
                state = state,
                monthStart = filter.anchorMonthStart,
                onTransactionClick = { editingId = it.transaction.id },
              )
              Tab.Discover -> PlansScreen(state, repository)
              Tab.Settings -> SettingsScreen(
                state = state,
                repository = repository,
                backupCoordinator = backupCoordinator,
                themePreference = themePreference,
                onThemePreferenceChanged = { preference ->
                  saveAction.launch { repository.setThemePreference(preference) }
                },
              )
            }
          }
        }
      }
      SextantCollapsibleFab(
        // 记账入口只服务概览：这里才是「翻明细、看到想补记的一笔」的地方。
        // 分析、计划、设置都在做别的事，右下角常驻一颗铜橙按钮只会抢注意力；计划页的新增
        // 入口用区块标题右侧的按钮，和分类预算保持同一个模式。
        visible = tab == Tab.Home && !calendarOpen && quickEntryFab.visible && ui.data != null && ui.request == request,
        onClick = { showQuickEntry = true },
        icon = Icons.Outlined.Add,
        contentDescription = "记一笔",
        modifier = Modifier.align(Alignment.BottomEnd)
          .padding(horizontal = BottomBarHorizontalMargin, vertical = 16.dp),
      )
    }
  }

  if (showQuickEntry && ui.data != null) {
    QuickEntryDialog(
      state = state,
      initialPaymentMethodId = lastPaymentMethodId,
      onDismiss = { showQuickEntry = false },
      onSubmit = { type, amountCents, categoryId, paymentMethodId, description, transactionDate, rememberMerchant ->
        lastPaymentMethodId = paymentMethodId
        saveAction.launch {
          repository.addTransaction(type, amountCents, categoryId, paymentMethodId, description, transactionDate)
        }
      },
    )
  }

  editingTransaction?.takeIf { it.transaction.id == editingId }?.let { item ->
    QuickEntryDialog(
      state = state,
      editing = item,
      onDismiss = { editingId = null },
      onDelete = {
        pendingDelete = item
      },
      onSubmit = { type, amountCents, categoryId, paymentMethodId, description, transactionDate, rememberMerchant ->
        saveAction.launch {
          repository.updateTransaction(item.transaction.id, type, amountCents, categoryId, paymentMethodId, description, transactionDate, rememberMerchant)
        }
      },
    )
  }

  pendingDelete?.let { item ->
    SextantAlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("删除这笔记录？") },
      text = { Text("删除后将从流水和统计中移除。") },
      confirmButton = {
        SextantTextButton(onClick = {
          saveAction.launch {
            repository.deleteTransaction(item.transaction.id)
          }
        }) { Text("删除") }
      },
      dismissButton = { SextantTextButton(onClick = { pendingDelete = null }) { Text("取消") } },
    )
  }
}

/**
 * 悬浮底栏：四个等宽目的地，外部留白与系统安全区一起计入 Scaffold 内容边距。
 *
 */
@Composable
private fun LedgerBottomBar(
  selectedTab: Tab,
  onTabSelected: (Tab) -> Unit,
) {
  Box(Modifier.fillMaxWidth().navigationBarsPadding()
    .padding(horizontal = BottomBarHorizontalMargin, vertical = 16.dp)) {
    LedgerNavigationBar(
      selectedTab = selectedTab,
      onTabSelected = onTabSelected,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
