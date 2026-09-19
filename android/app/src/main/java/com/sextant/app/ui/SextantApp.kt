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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.components.SextantSurface
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
import com.sextant.app.ui.screens.PlansScreen
import com.sextant.app.ui.screens.SettingsScreen
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.LedgerFilterSaver

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
  LaunchedEffect(tab) { if (tab == Tab.Home) quickEntryFab.show() }
  LaunchedEffect(editingId) {
    editingTransaction = editingId?.let { repository.findTransaction(it) }
  }
  LaunchedEffect(requestedEditTransactionId) {
    val id = requestedEditTransactionId ?: return@LaunchedEffect
    repository.findTransaction(id)?.let { item ->
      filter = LedgerFilter.forMonth(item.transaction.transactionDate)
      tab = Tab.Home
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
      LedgerBottomBar(
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
          pageStates.SaveableStateProvider(index) {
            when (Tab.entries[index]) {
              Tab.Home -> HomeScreen(
                state = state,
                filter = filter,
                onFilterChanged = { filter = it },
                onTransactionClick = { editingId = it.transaction.id },
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
        visible = tab == Tab.Home && quickEntryFab.visible && ui.data != null && ui.request == request,
        onClick = { showQuickEntry = true },
        icon = Icons.Outlined.Add,
        contentDescription = "记一笔",
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      )
    }
  }

  if (showQuickEntry && ui.data != null) {
    QuickEntryDialog(
      state = state,
      initialPaymentMethodId = lastPaymentMethodId,
      onDismiss = { showQuickEntry = false },
      onSubmit = { type, amountCents, categoryId, paymentMethodId, description, transactionDate ->
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
      onSubmit = { type, amountCents, categoryId, paymentMethodId, description, transactionDate ->
        saveAction.launch {
          repository.updateTransaction(item.transaction.id, type, amountCents, categoryId, paymentMethodId, description, transactionDate)
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
 * 底栏：只有四个目的地，占满整行。
 *
 * 记账 FAB 曾经内嵌在这一行里：导航栏为了给它让位缩进 72dp，四个标签被挤到左边，而 FAB
 * 仍然会压住明细最右列——也就是金额。现在两者分开：导航栏独占底栏，记账入口只出现在概览，
 * 浮在内容区右下角并随滚动方向收起（见 [SextantCollapsibleFab]）。
 *
 * FAB 也曾经展开一个「记支出 / 记收入」菜单，后来去掉了：支出和收入不是两个动作，是同一个
 * 动作的一个参数，而记账面板顶部本来就有收支方向的三段选择（还多一个转账）。把参数提到
 * 菜单层，只会让高频的记支出每次多一次点击。现在单击直接进表单，方向在表单里选。
 */
@Composable
private fun LedgerBottomBar(
  selectedTab: Tab,
  onTabSelected: (Tab) -> Unit,
) {
  SextantSurface(color = SextantColors.SurfaceContainerLow, shape = RectangleShape) {
    LedgerNavigationBar(
      selectedTab = selectedTab,
      onTabSelected = onTabSelected,
      modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
    )
  }
}
