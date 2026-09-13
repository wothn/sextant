@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.sextant.app.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.components.SextantSurface
import com.sextant.app.data.AppState
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.AutomationKind
import com.sextant.app.data.EntryOrigin
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.ThemePreference
import com.sextant.app.data.TransactionType
import com.sextant.app.automation.ALIPAY_PACKAGE
import com.sextant.app.automation.WECHAT_PACKAGE
import com.sextant.app.ui.components.QuickEntryDialog
import com.sextant.app.ui.navigation.LedgerNavigationBar
import com.sextant.app.ui.navigation.Tab
import com.sextant.app.ui.navigation.rememberQuickEntryFabState
import com.sextant.app.ui.screens.AnalyticsScreen
import com.sextant.app.ui.screens.BudgetScreen
import com.sextant.app.ui.screens.HomeScreen
import com.sextant.app.ui.screens.SettingsScreen
import com.sextant.app.ui.theme.SextantColors
import kotlinx.coroutines.flow.collect

@Composable
fun SextantApp(
  repository: SextantRepository,
  themePreference: ThemePreference,
  onThemePreferenceChanged: (ThemePreference) -> Unit,
  requestedEditTransactionId: String? = null,
  onEditRequestConsumed: () -> Unit = {},
) {
  var state by remember { mutableStateOf(AppState()) }
  var tab by remember { mutableStateOf(Tab.Home) }
  var showQuickEntry by remember { mutableStateOf(false) }
  var editingTransaction by remember { mutableStateOf<TransactionListItem?>(null) }
  var pendingDelete by remember { mutableStateOf<TransactionListItem?>(null) }
  var pendingRule by remember { mutableStateOf<ClassificationRulePrompt?>(null) }
  var filter by remember { mutableStateOf(LedgerFilter.allTime()) }
  var lastPaymentMethodId by remember { mutableStateOf<String?>(null) }
  var reloadKey by remember { mutableIntStateOf(0) }
  val quickEntryFab = rememberQuickEntryFabState()

  LaunchedEffect(reloadKey, filter) {
    state = repository.loadState(filter)
  }

  // 回到概览时复位：否则会继承上次在这个页面上滚出来的收起状态，入口凭空消失。
  LaunchedEffect(tab) { if (tab == Tab.Home) quickEntryFab.show() }

  LaunchedEffect(repository) {
    repository.changes.collect {
      reloadKey++
      onThemePreferenceChanged(repository.loadThemePreference())
    }
  }

  LaunchedEffect(requestedEditTransactionId) {
    val id = requestedEditTransactionId ?: return@LaunchedEffect
    repository.findTransaction(id)?.let { item ->
      editingTransaction = item
      filter = LedgerFilter.forMonth(item.transaction.transactionDate)
    }
    onEditRequestConsumed()
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
        when (Tab.entries[index]) {
          Tab.Home -> HomeScreen(
            state = state,
            filter = filter,
            onFilterChanged = { filter = it },
            onTransactionClick = { editingTransaction = it },
          )
          Tab.Analytics -> AnalyticsScreen(
            state = state,
            monthStart = filter.anchorMonthStart,
            // 分类明细里的流水和首页明细一样可以点开编辑。
            onTransactionClick = { editingTransaction = it },
          )
          Tab.Discover -> BudgetScreen(state, repository) { reloadKey++ }
          Tab.Settings -> SettingsScreen(
            state = state,
            repository = repository,
            themePreference = themePreference,
            onThemePreferenceChanged = { preference ->
              repository.setThemePreference(preference)
              onThemePreferenceChanged(preference)
            },
            onChanged = { reloadKey++ },
          )
        }
      }
      SextantCollapsibleFab(
        // 记账入口只服务概览：这里才是「翻明细、看到想补记的一笔」的地方。
        // 分析、预算、设置都在做别的事，右下角常驻一颗铜橙按钮只会抢注意力。
        visible = tab == Tab.Home && quickEntryFab.visible,
        onClick = { showQuickEntry = true },
        icon = Icons.Outlined.Add,
        contentDescription = "记一笔",
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      )
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
    SextantAlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("删除这笔记录？") },
      text = { Text("删除后将从流水和统计中移除。") },
      confirmButton = {
        SextantTextButton(onClick = {
          repository.deleteTransaction(item.transaction.id)
          pendingDelete = null
          editingTransaction = null
          reloadKey++
        }) { Text("删除") }
      },
      dismissButton = { SextantTextButton(onClick = { pendingDelete = null }) { Text("取消") } },
    )
  }

  pendingRule?.let { rule ->
    SextantAlertDialog(
      onDismissRequest = { pendingRule = null },
      title = { Text("记住这次分类？") },
      text = { Text("以后遇到相似商户或摘要时，自动使用这个分类。规则只作用于相同支付应用和资金方向。") },
      confirmButton = {
        SextantTextButton(onClick = {
          runCatching {
            repository.saveClassificationRule(rule.sourcePackage, rule.direction, rule.kind, rule.pattern, rule.categoryId)
          }
          pendingRule = null
        }) { Text("记住") }
      },
      dismissButton = { SextantTextButton(onClick = { pendingRule = null }) { Text("仅本次") } },
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

private data class ClassificationRulePrompt(
  val sourcePackage: String,
  val direction: TransactionType,
  val kind: AutomationKind,
  val pattern: String,
  val categoryId: String,
)