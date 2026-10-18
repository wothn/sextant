@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.sextant.app.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.sextant.app.ui.components.SextantAlertDialog
import com.sextant.app.ui.components.SextantCollapsibleFab
import com.sextant.app.ui.components.SextantTabContent
import com.sextant.app.ui.components.SextantPageContent
import com.sextant.app.ui.components.SextantPagePlaceholder
import com.sextant.app.ui.components.SextantReadError
import com.sextant.app.ui.components.SextantTextButton

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.sextant.app.data.StateProjection
import com.sextant.app.ui.components.rememberSaveAction
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
import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.AnalysisWindow
import com.sextant.app.data.toLocalDate
import com.sextant.app.data.toStartOfDayMillis
import com.sextant.app.data.BackupCoordinator
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.ThemePreference
import com.sextant.app.ui.components.QuickEntryDialog
import com.sextant.app.ui.components.LocalBottomBarInset
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

private val BottomBarHorizontalMargin = 24.dp

/** 底栏外侧的呼吸：上下一圈留白，也是底栏与 FAB 之间的间距。 */
private val BottomBarGap = 16.dp

@Composable
fun SextantApp(
  repository: SextantRepository,
  viewModel: LedgerViewModel,
  backupCoordinator: BackupCoordinator,
  themePreference: ThemePreference,
  onThemePreferenceChanged: (ThemePreference) -> Unit,
  requestedEditTransactionId: String? = null,
  onEditRequestConsumed: () -> Unit = {},
  widgetDestination: String? = null,
  widgetRecurringId: String? = null,
  onWidgetRequestConsumed: () -> Unit = {},
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
  var analysisAnchor by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
  var analysisPeriod by rememberSaveable { mutableStateOf(AnalysisPeriod.Month) }
  var analysisMetric by rememberSaveable { mutableStateOf(com.sextant.app.data.AnalysisMetric.Expense) }
  var analysisNavigationVersion by rememberSaveable { mutableStateOf(0) }
  var planSection by rememberSaveable { mutableStateOf(com.sextant.app.ui.screens.PlanSection.Budget) }
  var pendingRecurringId by rememberSaveable { mutableStateOf<String?>(null) }
  val analysisWindow = AnalysisWindow(analysisPeriod, analysisAnchor.toLocalDate())
  var lastPaymentMethodId by rememberSaveable { mutableStateOf<String?>(null) }
  val ui by viewModel.state.collectAsState()
  val projection = when (tab) {
    Tab.Discover -> StateProjection.Plans
    Tab.Settings -> StateProjection.Settings
    Tab.Analytics -> StateProjection.Analytics
    else -> StateProjection.Ledger
  }
  val request = LedgerRequest(when (tab) {
    Tab.Home -> filter
    Tab.Analytics -> analysisWindow.filter
    else -> LedgerFilter.allTime()
  }, projection, if (tab == Tab.Analytics) analysisPeriod else AnalysisPeriod.Month)
  val currentData = ui.dataFor(request)
  val state = currentData ?: AppState()
  val quickEntryFab = rememberQuickEntryFabState()
  val saveAction = rememberSaveAction("ledger", showFeedback = false) {
    showQuickEntry = false
    editingId = null
    pendingDelete = null
  }
  val themeSaveAction = rememberSaveAction("theme")
  val pageStates = rememberSaveableStateHolder()
  val ledgerPages = rememberSaveableStateHolder()
  LaunchedEffect(request) { viewModel.show(request) }
  LaunchedEffect(ui.theme) { if (ui.data != null) onThemePreferenceChanged(ui.theme) }
  LaunchedEffect(tab, calendarOpen) { if (tab == Tab.Home && !calendarOpen) quickEntryFab.show() }
  LaunchedEffect(editingId) {
    editingTransaction = editingId?.let { repository.findTransaction(it) }
  }
  LaunchedEffect(requestedEditTransactionId) {
    val id = requestedEditTransactionId ?: return@LaunchedEffect
    repository.findTransaction(id)?.let { item ->
      showQuickEntry = false
      pendingDelete = null
      saveAction.dismissError()
      filter = LedgerFilter.forMonth(item.transaction.transactionDate)
      tab = Tab.Home
      showCalendar = false
      editingId = id
    }
    onEditRequestConsumed()
  }
  LaunchedEffect(widgetDestination) {
    val destination = widgetDestination ?: return@LaunchedEffect
    showCalendar = false
    showQuickEntry = false
    editingId = null
    pendingDelete = null
    when (destination) {
      "home" -> { tab = Tab.Home; filter = LedgerFilter.forMonth(System.currentTimeMillis()) }
      "entry" -> { tab = Tab.Home; showQuickEntry = true }
      "today" -> { tab = Tab.Home; filter = LedgerFilter.forDay(System.currentTimeMillis()).copy(type = com.sextant.app.data.TransactionType.Expense) }
      "recent" -> { tab = Tab.Home; filter = LedgerFilter.allTime().copy(type = com.sextant.app.data.TransactionType.Expense) }
      "budget", "recurring" -> {
        tab = Tab.Discover
        planSection = if (destination == "budget") com.sextant.app.ui.screens.PlanSection.Budget else com.sextant.app.ui.screens.PlanSection.Recurring
        pendingRecurringId = widgetRecurringId
      }
      "expense", "income" -> {
        tab = Tab.Analytics
        analysisAnchor = System.currentTimeMillis()
        analysisPeriod = AnalysisPeriod.Month
        analysisMetric = if (destination == "income") com.sextant.app.data.AnalysisMetric.Income else com.sextant.app.data.AnalysisMetric.Expense
        analysisNavigationVersion++
      }
    }
    onWidgetRequestConsumed()
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
    // 底栏照旧交给 Scaffold 去量高度，但内容不再吃它的下边距：底栏是悬浮的，被它盖住的
    // 只有条本身，两侧和上方的空隙应该能看到明细在滚。于是这里只让开系统导航区，把量到的
    // 底栏高度通过 LocalBottomBarInset 交给页面，由页面在列表结尾预留。
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barInset = (padding.calculateBottomPadding() - navBarInset - BottomBarGap)
      .coerceAtLeast(0.dp)
    CompositionLocalProvider(LocalBottomBarInset provides barInset) {
      Box(
        Modifier
          .padding(top = padding.calculateTopPadding(), bottom = navBarInset)
          .fillMaxSize()
          // FAB 的滚动收起挂在内容区最外层：页面里的任何列表滚动都会经过这里，
          // 只读方向、不消费位移，所以页面的滚动行为不受影响。
          .nestedScroll(quickEntryFab.nestedScrollConnection),
      ) {
        SextantTabContent(selectedIndex = tab.ordinal) { index ->
          val pageTab = Tab.entries[index]
          val pageRequest = LedgerRequest(
            when (pageTab) {
              Tab.Home -> filter
              Tab.Analytics -> analysisWindow.filter
              else -> LedgerFilter.allTime()
            },
            when (pageTab) {
              Tab.Home -> StateProjection.Ledger
              Tab.Analytics -> StateProjection.Analytics
              Tab.Discover -> StateProjection.Plans
              Tab.Settings -> StateProjection.Settings
            },
            if (pageTab == Tab.Analytics) analysisPeriod else AnalysisPeriod.Month,
          )
          val pageData = ui.dataFor(pageRequest)
          val pageError = ui.error?.takeIf { ui.request == pageRequest }
          if (pageData == null) {
            SextantPagePlaceholder(pageTab.title, pageError, viewModel::refresh)
          } else {
            Column(Modifier.fillMaxSize()) {
              if (pageError != null) SextantReadError("刷新失败，当前显示上次读取的内容。", viewModel::refresh)
              pageStates.SaveableStateProvider(index) {
                when (pageTab) {
                  Tab.Home -> SextantPageContent(
                    targetState = showCalendar,
                    depth = { if (it) 1 else 0 },
                    modifier = Modifier.fillMaxSize(),
                  ) { calendar ->
                    ledgerPages.SaveableStateProvider(calendar) {
                      if (calendar) LedgerCalendarScreen(
                        state = pageData,
                        filter = filter,
                        onFilterChanged = { filter = it },
                        onBack = { showCalendar = false },
                        onTransactionClick = { saveAction.dismissError(); editingId = it.transaction.id },
                      ) else HomeScreen(
                        state = pageData,
                        filter = filter,
                        onFilterChanged = { filter = it },
                        onTransactionClick = { saveAction.dismissError(); editingId = it.transaction.id },
                        onOpenCalendar = { showCalendar = true },
                      )
                    }
                  }
                  Tab.Analytics -> androidx.compose.runtime.key(analysisNavigationVersion) { AnalyticsScreen(
                    state = pageData,
                    window = analysisWindow,
                    onWindowChanged = { analysisAnchor = it.anchor.toStartOfDayMillis(); analysisPeriod = it.period },
                    onTransactionClick = { saveAction.dismissError(); editingId = it.transaction.id },
                    metric = analysisMetric,
                    onMetricChanged = { analysisMetric = it },
                  ) }
                  Tab.Discover -> PlansScreen(pageData, repository,
                    section = planSection, onSectionChanged = { planSection = it },
                    requestedRecurringId = pendingRecurringId, onRecurringRequestConsumed = { pendingRecurringId = null })
                  Tab.Settings -> SettingsScreen(
                    state = pageData,
                    repository = repository,
                    backupCoordinator = backupCoordinator,
                    themePreference = themePreference,
                    onThemePreferenceChanged = { preference ->
                      themeSaveAction.launch { repository.setThemePreference(preference) }
                    },
                  )
                }
              }
            }
          }
        }
        SextantCollapsibleFab(
          // 记账入口只服务概览：这里才是「翻明细、看到想补记的一笔」的地方。
          // 分析、计划、设置都在做别的事，右下角常驻一颗铜橙按钮只会抢注意力；计划页的新增
          // 入口用区块标题右侧的按钮，和分类预算保持同一个模式。
          visible = tab == Tab.Home && !calendarOpen && quickEntryFab.visible && currentData != null,
          onClick = { saveAction.dismissError(); showQuickEntry = true },
          icon = Icons.Outlined.Add,
          contentDescription = "记一笔",
          modifier = Modifier.align(Alignment.BottomEnd)
            .padding(
              start = BottomBarHorizontalMargin,
              end = BottomBarHorizontalMargin,
              // 底栏上沿 + 一圈呼吸：按钮仍然贴底栏上方，且与底栏右边缘对齐。
              bottom = LocalBottomBarInset.current + BottomBarGap,
            ),
        )
      }
    }
  }

  if (showQuickEntry && currentData != null) {
    QuickEntryDialog(
      state = state,
      initialPaymentMethodId = lastPaymentMethodId,
      saving = saveAction.busy,
      saveError = saveAction.error,
      onDismiss = { showQuickEntry = false; saveAction.dismissError() },
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
      saving = saveAction.busy,
      saveError = saveAction.error,
      onDismiss = { editingId = null; saveAction.dismissError() },
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
          pendingDelete = null
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
    .padding(horizontal = BottomBarHorizontalMargin, vertical = BottomBarGap)) {
    LedgerNavigationBar(
      selectedTab = selectedTab,
      onTabSelected = onTabSelected,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
