package com.sextant.app.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.AnalysisWindow
import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.AnalysisMetric
import com.sextant.app.data.LedgerCalculations
import com.sextant.app.data.AnalysisReport
import com.sextant.app.data.expenseTrend
import com.sextant.app.data.toLocalDate
import com.sextant.app.data.BudgetProgress
import com.sextant.app.data.Category
import com.sextant.app.data.CategoryBreakdown
import com.sextant.app.data.DailySpending
import com.sextant.app.data.DailyTransactionGroup
import com.sextant.app.data.EntryOrigin
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.PaymentMethod
import com.sextant.app.data.Summary
import com.sextant.app.data.ThemePreference
import com.sextant.app.data.Transaction
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.SextantCollapsibleFab
import com.sextant.app.ui.navigation.LedgerNavigationBar
import com.sextant.app.ui.navigation.Tab
import com.sextant.app.ui.screens.AnalyticsScreen
import com.sextant.app.ui.screens.CategoryDetailScreen
import com.sextant.app.ui.screens.HomeScreen
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantTheme
import java.util.Calendar

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(
  name = "浅色",
  group = "主题",
  device = Devices.PIXEL_7,
  showSystemUi = true,
  uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
  name = "深色",
  group = "主题",
  device = Devices.PIXEL_7,
  showSystemUi = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
private annotation class SextantThemePreviews

@SextantThemePreviews
@Composable
private fun HomeScreenPreview() {
  SextantTheme(themePreference = ThemePreference.System) {
    PreviewAppShell(selectedTab = Tab.Home) {
      HomeScreen(
        state = populatedPreviewState,
        filter = previewFilter,
        onFilterChanged = {},
        onTransactionClick = {},
        onOpenCalendar = {},
      )
    }
  }
}

@SextantThemePreviews
@Composable
private fun AnalyticsScreenPreview() {
  SextantTheme(themePreference = ThemePreference.System) {
    PreviewAppShell(selectedTab = Tab.Analytics) {
      AnalyticsScreen(
        state = populatedPreviewState.copy(analysisReport = AnalysisReport(populatedPreviewState.monthSummary,
          populatedPreviewState.previousMonthSummary,
          expenseTrend(populatedPreviewState.analysisTransactions, AnalysisWindow(AnalysisPeriod.Month, previewFilter.rangeStart.toLocalDate())),
          incomeTrend = expenseTrend(populatedPreviewState.analysisTransactions, AnalysisWindow(AnalysisPeriod.Month, previewFilter.rangeStart.toLocalDate()), AnalysisMetric.Income),
          incomeBreakdown = LedgerCalculations.breakdown(populatedPreviewState.analysisTransactions.filter(AnalysisMetric.Income::includes), populatedPreviewState.categories))),
        window = AnalysisWindow(AnalysisPeriod.Month, previewFilter.rangeStart.toLocalDate()),
        onWindowChanged = {},
        onTransactionClick = {},
      )
    }
  }
}

@SextantThemePreviews
@Composable
private fun CategoryDetailScreenPreview() {
  SextantTheme(themePreference = ThemePreference.System) {
    PreviewAppShell(selectedTab = Tab.Analytics) {
      CategoryDetailScreen(
        category = populatedPreviewState.categoryBreakdown.first(),
        scopeLabel = "预览月份",
        transactions = populatedPreviewState.analysisTransactions,
        categories = populatedPreviewState.categories,
        periodTotalCents = populatedPreviewState.monthSummary.expenseCents,
        onBack = {},
        onTransactionClick = {},
      )
    }
  }
}

@Preview(
  name = "首页空状态",
  group = "状态",
  device = Devices.PIXEL_7,
  showSystemUi = true,
)
@Composable
private fun EmptyHomeScreenPreview() {
  SextantTheme(themePreference = ThemePreference.Light) {
    PreviewAppShell(selectedTab = Tab.Home) {
      HomeScreen(
        state = AppState(),
        filter = previewFilter,
        onFilterChanged = {},
        onTransactionClick = {},
        onOpenCalendar = {},
      )
    }
  }
}

@Composable
private fun PreviewAppShell(selectedTab: Tab, content: @Composable () -> Unit) {
  Scaffold(
    bottomBar = {
      LedgerNavigationBar(
        selectedTab = selectedTab,
        onTabSelected = {},
      )
    },
    containerColor = SextantColors.Background,
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      content()
      // 记账入口只出现在概览，浮在内容右下角；收起状态由滚动决定，预览里固定在展开态。
      if (selectedTab == Tab.Home) {
        SextantCollapsibleFab(
          visible = true,
          onClick = {},
          icon = Icons.Outlined.Add,
          contentDescription = "记一笔",
          modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
      }
    }
  }
}

private val previewFilter = LedgerFilter.forMonth(previewTime(day = 1))

private val previewCategories = listOf(
  Category("dining", "餐饮", TransactionType.Expense, 0xFFFF7043L, true, null),
  Category("transport", "交通", TransactionType.Expense, 0xFF42A5F5L, true, null),
  Category("shopping", "购物", TransactionType.Expense, 0xFFF97316L, true, null),
  Category("salary", "工资", TransactionType.Income, 0xFF66BB6AL, true, null),
)

private val previewPaymentMethods = listOf(
  PaymentMethod("wechat", "微信", 0xFF1F9D66L, isBuiltIn = true, transactionCount = 12),
  PaymentMethod("alipay", "支付宝", 0xFF0F9F9AL, isBuiltIn = true, transactionCount = 8),
  PaymentMethod("bank", "银行卡", 0xFF2563EBL, isBuiltIn = true, transactionCount = 3),
)

private val previewTransactions = listOf(
  previewTransaction(
    id = "lunch",
    categoryId = "dining",
    categoryName = "餐饮",
    categoryColor = 0xFFFF7043L,
    paymentMethodId = "wechat",
    paymentMethodName = "微信",
    amountCents = 3_280,
    description = "梧桐小馆",
    day = 30,
    hour = 12,
    minute = 34,
    entryOrigin = EntryOrigin.Notification,
  ),
  previewTransaction(
    id = "metro",
    categoryId = "transport",
    categoryName = "交通",
    categoryColor = 0xFF42A5F5L,
    paymentMethodId = "alipay",
    paymentMethodName = "支付宝",
    amountCents = 600,
    description = "地铁出行",
    day = 30,
    hour = 8,
    minute = 16,
  ),
  previewTransaction(
    id = "groceries",
    categoryId = "shopping",
    categoryName = "购物",
    categoryColor = 0xFFF97316L,
    paymentMethodId = "bank",
    paymentMethodName = "银行卡",
    amountCents = 12_890,
    description = "周末采购",
    day = 29,
    hour = 19,
    minute = 42,
  ),
  previewTransaction(
    id = "salary",
    categoryId = "salary",
    categoryName = "工资",
    categoryColor = 0xFF66BB6AL,
    paymentMethodId = "bank",
    paymentMethodName = "银行卡",
    amountCents = 1_680_000,
    description = "八月工资",
    day = 28,
    hour = 10,
    minute = 0,
    type = TransactionType.Income,
  ),
)

private val populatedPreviewState = AppState(
  categories = previewCategories,
  paymentMethods = previewPaymentMethods,
  allPaymentMethods = previewPaymentMethods,
  transactions = previewTransactions,
  groups = listOf(
    DailyTransactionGroup(previewTime(day = 30), previewTransactions.take(2)),
    DailyTransactionGroup(previewTime(day = 29), listOf(previewTransactions[2])),
    DailyTransactionGroup(previewTime(day = 28), listOf(previewTransactions[3])),
  ),
  todaySummary = Summary(incomeCents = 0, expenseCents = 3_880),
  rangeSummary = Summary(incomeCents = 1_680_000, expenseCents = 638_220),
  monthSummary = Summary(incomeCents = 1_680_000, expenseCents = 638_220),
  categoryBreakdown = listOf(
    CategoryBreakdown("餐饮", 0xFFFF7043L, 214_800, 187_200, memberCategoryIds = setOf("dining")),
    CategoryBreakdown("购物", 0xFFF97316L, 162_500, 194_300, memberCategoryIds = setOf("shopping")),
    CategoryBreakdown("交通", 0xFF42A5F5L, 96_320, 82_600, memberCategoryIds = setOf("transport")),
    CategoryBreakdown("娱乐休闲", 0xFFAB47BCL, 89_600, 67_400, memberCategoryIds = setOf("entertainment")),
    CategoryBreakdown("生活服务", 0xFF26A69AL, 75_000, 71_200, memberCategoryIds = setOf("services")),
  ),
  analysisTransactions = previewTransactions,
  budgets = listOf(
    BudgetProgress("餐饮", 0xFFFF7043L, 300_000, 214_800),
    BudgetProgress("购物", 0xFFF97316L, 200_000, 162_500),
    BudgetProgress("交通", 0xFF42A5F5L, 150_000, 96_320),
  ),
  monthlyBudgetCents = 1_200_000,
  previousMonthSummary = Summary(incomeCents = 1_620_000, expenseCents = 702_400),
  dailySpending = previewDailySpending(),
  projectedExpenseCents = 731_800,
)

private fun previewDailySpending() = listOf(
  12_500L,
  8_900L,
  26_800L,
  17_200L,
  34_600L,
  11_400L,
  48_300L,
  16_700L,
  22_100L,
  9_800L,
  38_600L,
  28_400L,
  15_200L,
  52_800L,
  19_600L,
  24_300L,
  13_700L,
  31_900L,
  18_500L,
  44_200L,
  10_600L,
  27_800L,
  21_400L,
  36_500L,
  14_900L,
  23_700L,
  41_300L,
  17_600L,
  29_800L,
  3_880L,
).mapIndexed { index, amount -> DailySpending(dayOfMonth = index + 1, amountCents = amount) }

private fun previewTransaction(
  id: String,
  categoryId: String,
  categoryName: String,
  categoryColor: Long,
  paymentMethodId: String,
  paymentMethodName: String,
  amountCents: Long,
  description: String,
  day: Int,
  hour: Int,
  minute: Int,
  type: TransactionType = TransactionType.Expense,
  entryOrigin: EntryOrigin = EntryOrigin.Manual,
): TransactionListItem {
  val timestamp = previewTime(day, hour, minute)
  return TransactionListItem(
    transaction = Transaction(
      id = id,
      categoryId = categoryId,
      paymentMethodId = paymentMethodId,
      amountCents = amountCents,
      type = type,
      description = description,
      transactionDate = timestamp,
      createdAt = timestamp,
      updatedAt = timestamp,
      entryOrigin = entryOrigin,
    ),
    categoryName = categoryName,
    categoryColor = categoryColor,
    paymentMethodName = paymentMethodName,
    includeInSpending = true,
  )
}

private fun previewTime(day: Int, hour: Int = 0, minute: Int = 0): Long =
  Calendar.getInstance().apply {
    set(2026, Calendar.AUGUST, day, hour, minute, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis
