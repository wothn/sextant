@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.sextant.app.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.sextant.app.ui.components.SextantAlertDialog
import com.sextant.app.ui.components.SextantCollapsibleFab
import com.sextant.app.ui.components.SextantTabContent
import com.sextant.app.ui.components.SextantPageContent
import com.sextant.app.ui.components.SextantPagePlaceholder
import com.sextant.app.ui.components.SextantPredictiveBackHandler
import com.sextant.app.ui.components.SextantReadError
import com.sextant.app.ui.components.SextantTextButton

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.sextant.app.data.StateProjection
import com.sextant.app.ui.components.rememberSaveAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
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
import com.sextant.app.ui.components.LocalSnackbarHostState
import com.sextant.app.ui.navigation.LedgerNavigationBar
import com.sextant.app.ui.navigation.LedgerNavigationRail
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
import kotlinx.coroutines.launch

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
  openAgent: Boolean = false,
  onAgentRequestConsumed: () -> Unit = {},
) {
  var tab by rememberSaveable { mutableStateOf(Tab.Home) }
  LaunchedEffect(openAgent) {
    if (openAgent) { tab = Tab.Chat; onAgentRequestConsumed() }
  }
  var showCalendar by rememberSaveable { mutableStateOf(false) }
  val calendarOpen = tab == Tab.Home && showCalendar
  SextantPredictiveBackHandler(enabled = calendarOpen) { showCalendar = false }
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
  LaunchedEffect(Unit) {
    lastPaymentMethodId = repository.loadLastPaymentMethodId() ?: lastPaymentMethodId
  }
  val ui by viewModel.state.collectAsState()
  val projection = when (tab) {
    Tab.Discover -> StateProjection.Plans
    Tab.Settings -> StateProjection.Settings
    Tab.Chat -> StateProjection.Settings
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
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  var successMessage by remember { mutableStateOf<String?>(null) }
  var pendingUndoItem by remember { mutableStateOf<TransactionListItem?>(null) }
  val saveAction = rememberSaveAction("ledger", showFeedback = false) {
    showQuickEntry = false
    editingId = null
    pendingDelete = null
    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    val undoItem = pendingUndoItem
    if (undoItem != null) {
      scope.launch {
        val result = snackbarHostState.showSnackbar(
          message = "已删除这笔记录",
          actionLabel = "撤销",
          duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
          repository.restoreTransaction(undoItem.transaction)
        }
      }
    } else {
      successMessage?.let { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
      }
    }
    pendingUndoItem = null
    successMessage = null
  }
  val themeSaveAction = rememberSaveAction("theme")
  val pageStates = rememberSaveableStateHolder()
  val ledgerPages = rememberSaveableStateHolder()
  // 切换周/月/年或筛选时，新请求的数据要过一帧才到。保留上一份成功加载的数据，
  // 避免中间闪一下整页骨架；只有从未加载过的页面才显示占位。
  val pageDataCache = remember { mutableStateMapOf<Int, AppState>() }
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

  val useRail = LocalConfiguration.current.screenWidthDp >= 600 && !calendarOpen
  val chatKeyboardOpen = tab == Tab.Chat && WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (!calendarOpen && !useRail && !chatKeyboardOpen) LedgerBottomBar(
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
    CompositionLocalProvider(
      LocalBottomBarInset provides barInset,
      LocalSnackbarHostState provides snackbarHostState,
    ) {
      Row(
        Modifier
          .padding(top = padding.calculateTopPadding(), bottom = navBarInset)
          .consumeWindowInsets(PaddingValues(top = padding.calculateTopPadding(), bottom = navBarInset))
          .then(if (tab == Tab.Chat) Modifier.imePadding() else Modifier)
          .fillMaxSize()
          // FAB 的滚动收起挂在内容区最外层：页面里的任何列表滚动都会经过这里，
          // 只读方向、不消费位移，所以页面的滚动行为不受影响。
          .nestedScroll(quickEntryFab.nestedScrollConnection),
      ) {
        if (useRail) {
          LedgerNavigationRail(selectedTab = tab, onTabSelected = { tab = it })
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
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
              Tab.Chat -> StateProjection.Settings
            },
            if (pageTab == Tab.Analytics) analysisPeriod else AnalysisPeriod.Month,
          )
          val loadedData = ui.dataFor(pageRequest)
          val pageData = loadedData ?: pageDataCache[index]
          LaunchedEffect(index, loadedData) {
            if (loadedData != null) pageDataCache[index] = loadedData
          }
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
                        onCreateEntry = { saveAction.dismissError(); showQuickEntry = true },
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
                  Tab.Chat -> com.sextant.app.ui.screens.AgentChatScreen(
                    (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.sextant.app.SextantApplication).agentCoordinator,
                    repository,
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
        successMessage = "已记录一笔"
        saveAction.launch {
          repository.setLastPaymentMethodId(paymentMethodId)
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
        lastPaymentMethodId = paymentMethodId
        successMessage = "已保存修改"
        saveAction.launch {
          repository.setLastPaymentMethodId(paymentMethodId)
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
        SextantTextButton(
          contentColor = SextantColors.Danger,
          onClick = {
            pendingDelete = null
            pendingUndoItem = item
            successMessage = null
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            saveAction.launch {
              repository.deleteTransaction(item.transaction.id)
            }
          },
        ) { Text("删除") }
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
