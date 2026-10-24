package com.sextant.app.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.sextant.app.ui.components.SextantPageContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.Category
import com.sextant.app.data.CategoryBreakdown
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionSortOrder
import com.sextant.app.data.TransactionType
import com.sextant.app.data.sortedForDetail
import com.sextant.app.ui.components.CategoryIcon
import com.sextant.app.ui.components.ColorDot
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.LocalBottomBarInset
import com.sextant.app.ui.components.SectionTitle
import com.sextant.app.ui.components.SextantFlexibleHeader
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.components.SextantMetric
import com.sextant.app.ui.components.SextantIconButton
import com.sextant.app.ui.components.SextantSegmentedButtonGroup
import com.sextant.app.ui.components.SextantSurface
import com.sextant.app.ui.components.SextantTextButton
import com.sextant.app.ui.components.SpendingTrendChart
import com.sextant.app.ui.components.ExpensePieChart
import com.sextant.app.data.AnalysisMetric
import com.sextant.app.ui.util.analysisMetricLabel
import com.sextant.app.ui.components.SextantTabRow
import com.sextant.app.ui.components.AnalysisDateSelector
import com.sextant.app.ui.util.analysisDateSelectorLabel
import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.AnalysisWindow
import com.sextant.app.ui.util.analysisWindowLabel
import com.sextant.app.ui.util.analysisPeriodLabel
import com.sextant.app.ui.util.analysisComparisonNote
import com.sextant.app.ui.components.categoryIcon
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.sextantEmphasized
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.displayDescription
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.signedAmountGrouped
import com.sextant.app.ui.util.timestampLabel
import com.sextant.app.ui.util.spendingShareLabel
import com.sextant.app.ui.util.spendingChangeLabel
import java.time.LocalDate

@Composable
fun AnalyticsScreen(
  state: AppState,
  window: AnalysisWindow,
  onWindowChanged: (AnalysisWindow) -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit = {},
  metric: AnalysisMetric,
  onMetricChanged: (AnalysisMetric) -> Unit,
) {
  val listState = rememberLazyListState()
  val breakdown = if (metric == AnalysisMetric.Expense) state.categoryBreakdown else state.analysisReport?.incomeBreakdown.orEmpty()
  var detailMembers by rememberSaveable(window, metric) { mutableStateOf<ArrayList<String>?>(null) }
  val pages = rememberSaveableStateHolder()
  val detail = detailMembers?.let { members -> breakdown.firstOrNull { it.memberCategoryIds == members.toSet() } }
  SextantPageContent(targetState = detail?.memberCategoryIds?.sorted(),
    depth = { if (it == null) 0 else 1 }, modifier = Modifier.fillMaxSize()) { target ->
    pages.SaveableStateProvider(target?.joinToString("|") ?: "overview") {
      val visibleDetail = breakdown.firstOrNull { it.memberCategoryIds == target?.toSet() }
      if (visibleDetail != null) {
        CategoryDetailScreen(visibleDetail, analysisWindowLabel(window), state.analysisTransactions,
          state.categories, state.analysisReport?.summary?.let(metric::amount) ?: 0L,
          metric = metric,
          onBack = { detailMembers = null }, onTransactionClick = onTransactionClick)
      } else {
        AnalyticsOverview(state, window, metric, listState, onWindowChanged, onMetricChanged = onMetricChanged) { detailMembers = ArrayList(it.memberCategoryIds) }
      }
    }
  }
}

@Composable
private fun AnalyticsOverview(
  state: AppState,
  window: AnalysisWindow,
  metric: AnalysisMetric,
  listState: LazyListState,
  onWindowChanged: (AnalysisWindow) -> Unit,
  onMetricChanged: (AnalysisMetric) -> Unit,
  onCategoryClick: (CategoryBreakdown) -> Unit,
) {
  val report = state.analysisReport
  if (report == null) {
    Column(Modifier.fillMaxSize()) {
      SextantFlexibleHeader(title = "分析", scrollState = listState,
        subtitle = "按日期查看收支趋势与分类", modifier = Modifier.padding(horizontal = 20.dp))
      Box(Modifier.fillMaxWidth().weight(1f).padding(20.dp), contentAlignment = Alignment.Center) {
        EmptyCard("暂无分析数据", "这个时间范围还没有可统计的流水，换个周期或先记一笔。")
      }
    }
    return
  }
  val label = analysisMetricLabel(metric)
  val total = metric.amount(report.summary)
  val reportPeriod = report.period
  val breakdown = if (metric == AnalysisMetric.Expense) state.categoryBreakdown else report.incomeBreakdown
  val trend = if (metric == AnalysisMetric.Expense) report.trend else report.incomeTrend
  var selectedCategory by rememberSaveable(window, metric) { mutableStateOf<String?>(null) }
  Column(Modifier.fillMaxSize()) {
    SextantFlexibleHeader(title = "分析", scrollState = listState,
      subtitle = "按日期查看收支趋势与分类", modifier = Modifier.padding(horizontal = 20.dp))
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp + LocalBottomBarInset.current),
      verticalArrangement = Arrangement.spacedBy(24.dp)) {
      item(key = "period") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          SextantSegmentedButtonGroup(options = AnalysisPeriod.entries, selected = window.period,
            onSelect = { onWindowChanged(AnalysisWindow(it, window.anchor)) }, label = { analysisPeriodLabel(it) },
            modifier = Modifier.fillMaxWidth())
          AnalysisDateAndMetric(window, metric, onWindowChanged, onMetricChanged)
          if (window.start != AnalysisWindow(window.period, LocalDate.now()).start) {
            SextantTextButton(onClick = { onWindowChanged(AnalysisWindow(window.period, LocalDate.now())) }) {
              Text("回到本" + analysisPeriodLabel(window.period))
            }
          }
        }
      }
      item(key = "summary") { AnalysisSummary(state, window, metric, reportPeriod) }
      item(key = "trend") {
        AnalyticsSection(label + "趋势", if (reportPeriod == AnalysisPeriod.Year) "按月汇总 · 单位：元" else "按日汇总 · 单位：元") {
          if (total == 0L) {
            EmptyCard("暂无" + label + "趋势", "这个时间范围还没有计入统计的" + label + "，可以切换时间查看。")
          } else {
            SextantSurface(Modifier.fillMaxWidth(), color = SextantColors.SurfaceContainerLow) {
              Box(Modifier.padding(16.dp)) { SpendingTrendChart(trend, reportPeriod, metric) }
            }
          }
        }
      }
      item(key = "structure") {
        AnalyticsSection(label + "结构", "一级分类占比") {
          if (breakdown.isEmpty()) {
            EmptyCard("暂无分类占比", "计入统计的" + label + "会在这里按分类汇总。")
          } else {
            ExpensePieChart(breakdown, total, selectedCategory, metric = metric,
              onSelect = { selectedCategory = it })
          }
        }
      }
      items(breakdown, key = { "category:" + it.memberCategoryIds.sorted() }) { category ->
        CategorySpendingRow(category, total, reportPeriod, metric, onCategoryClick)
      }
    }
  }
}

/** 先确定日期，再选择该周期内的收支；长周区间或大字体下按此顺序分行。 */
@Composable
private fun AnalysisDateAndMetric(
  window: AnalysisWindow,
  metric: AnalysisMetric,
  onWindowChanged: (AnalysisWindow) -> Unit,
  onMetricChanged: (AnalysisMetric) -> Unit,
) {
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val tabStyle = MaterialTheme.typography.titleSmall
  val dateStyle = MaterialTheme.typography.titleMedium
  val dateLabel = analysisDateSelectorLabel(window)
  val tabTextWidth = AnalysisMetric.entries.maxOf {
    textMeasurer.measure(analysisMetricLabel(it), style = tabStyle).size.width
  }
  val tabsWidth = ((with(density) { tabTextWidth.toDp() } + 28.dp).coerceAtLeast(52.dp) * 2)
  val dateWidth = with(density) { textMeasurer.measure(dateLabel, style = dateStyle).size.width.toDp() }
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    if (maxWidth >= dateWidth + 124.dp + tabsWidth + 4.dp) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AnalysisDateSelector(window, onWindowChanged, Modifier.weight(1f))
        // 收支是「看哪一面」，不是筛选条件；用文字页签，避免和上面的周期分段按钮
        // 抢同一档视觉重量。
        SextantTabRow(
          options = AnalysisMetric.entries,
          selected = metric,
          onSelect = onMetricChanged,
          label = { analysisMetricLabel(it) },
          modifier = Modifier.width(tabsWidth),
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AnalysisDateSelector(window, onWindowChanged, Modifier.fillMaxWidth())
        SextantTabRow(
          options = AnalysisMetric.entries,
          selected = metric,
          onSelect = onMetricChanged,
          label = { analysisMetricLabel(it) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

/**
 * 单个分类的所选区间明细。
 *
 * 金额是所选收支类型的分类合计，列表按同一类型、计入统计及所属分类的口径过滤，
 * 所以合计与明细永远对得上。默认按金额从大到小，可以切成按时间从新到旧。
 */
@Composable
internal fun CategoryDetailScreen(
  category: CategoryBreakdown,
  scopeLabel: String,
  transactions: List<TransactionListItem>,
  categories: List<Category>,
  periodTotalCents: Long,
  metric: AnalysisMetric = AnalysisMetric.Expense,
  onBack: () -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit,
) {
  val rows = remember(category.memberCategoryIds, transactions, metric) {
    transactions.filter {
      metric.includes(it) && category.includes(it.transaction.categoryId)
    }
  }
  AnalyticsTransactionDetail(category.categoryName, rows, categories, onBack, onTransactionClick) {
    CategoryDetailSummary(category, scopeLabel, rows.size, periodTotalCents, metric)
  }
}

/** 分类下钻保留核对与编辑路径，保持原始金额和可恢复排序。 */
@Composable
private fun AnalyticsTransactionDetail(
  title: String,
  transactions: List<TransactionListItem>,
  categories: List<Category>,
  onBack: () -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit,
  summary: @Composable () -> Unit,
) {
  BackHandler(onBack = onBack)
  var sort by rememberSaveable { mutableStateOf(TransactionSortOrder.Amount) }
  val categoriesById = remember(categories) { categories.associateBy { it.id } }
  val rows = remember(transactions, sort) { transactions.sortedForDetail(sort) }

  Column(Modifier.fillMaxSize()) {
    // 返回入口固定在列表之外：明细可能很长，回到分析页不该依赖滚回顶部。
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(SextantColors.SurfaceContainerLowest)
        .padding(horizontal = 20.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SextantIconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回分析")
      }
      SectionTitle(title)
    }
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp + LocalBottomBarInset.current),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item { summary() }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("排序", color = SextantColors.TextMuted, style = MaterialTheme.typography.labelLarge)
          SextantSegmentedButtonGroup(
            options = TransactionSortOrder.entries,
            selected = sort,
            onSelect = { sort = it },
            label = { if (it == TransactionSortOrder.Amount) "金额" else "时间" },
            modifier = Modifier.weight(1f),
          )
        }
      }
      if (rows.isEmpty()) {
        item { EmptyCard("没有明细", "当前范围没有符合统计口径的流水。") }
      } else {
        items(rows, key = { it.transaction.id }) { item ->
          CategoryDetailRow(item, categoriesById) { onTransactionClick(it) }
        }
      }
    }
  }
}

@Composable
private fun CategoryDetailSummary(
  category: CategoryBreakdown,
  scope: String,
  count: Int,
  periodTotalCents: Long,
  metric: AnalysisMetric,
) {
  val label = analysisMetricLabel(metric)
  val share = if (periodTotalCents <= 0) null else spendingShareLabel(category.amountCents, periodTotalCents)
  SextantSurface(modifier = Modifier.fillMaxWidth(), color = SextantColors.SurfaceContainer) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(scope + label, color = SextantColors.TextMuted, style = MaterialTheme.typography.labelLarge)
      Text(formatCurrency(category.amountCents), style = MaterialTheme.typography.headlineLarge.sextantEmphasized())
      Text(
        buildString {
          append("共 ").append(count).append(" 笔")
          share?.let { append("  ·  占").append(scope).append(label).append("的 ").append(it) }
        },
        color = SextantColors.TextSubtle,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun CategoryDetailRow(
  item: TransactionListItem,
  categoriesById: Map<String, Category>,
  onClick: (TransactionListItem) -> Unit,
) {
  // 明细里已经知道顶层分类，所以主标题写具体的那一级分类。
  val categoryName = categoriesById[item.transaction.categoryId]?.name ?: item.categoryName
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClickLabel = "编辑这笔流水") { onClick(item) }
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CategoryIcon(categoryIcon(categoryName), argbColor(item.categoryColor))
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = categoryName,
          modifier = Modifier.weight(1f),
          color = SextantColors.Text,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = signedAmountGrouped(item),
          color = SextantColors.Text,
          style = MaterialTheme.typography.titleMedium,
          softWrap = true,
        )
      }
      Text(
        text = detailMetadata(item),
        color = SextantColors.TextSubtle,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun detailMetadata(item: TransactionListItem): String = listOf(
  timestampLabel(item.transaction.transactionDate),
  item.paymentMethodName,
  displayDescription(item),
).filter { it.isNotBlank() }.joinToString("  ·  ")

@Composable
private fun AnalysisSummary(
  state: AppState,
  window: AnalysisWindow,
  metric: AnalysisMetric,
  period: AnalysisPeriod,
) {
  val report = state.analysisReport ?: return
  val label = analysisMetricLabel(metric)
  val previousLabel = "上" + analysisPeriodLabel(period)
  SextantSurface(Modifier.fillMaxWidth(), color = SextantColors.SurfaceContainerLow) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SextantMetric(label = "总" + label, value = formatCurrency(metric.amount(report.summary)), primary = true,
        valueStyle = MaterialTheme.typography.headlineLarge.sextantEmphasized())
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (metric == AnalysisMetric.Expense) SextantMetric(label = "收入", value = formatCurrency(report.summary.incomeCents))
        else SextantMetric(label = "支出", value = formatCurrency(report.summary.expenseCents))
        SextantMetric(label = "结余", value = formatCurrency(report.summary.netCents))
      }
      Text(spendingChangeLabel(metric.amount(report.summary), metric.amount(report.previousSummary), previousLabel, label),
        color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
      analysisComparisonNote(AnalysisWindow(period, window.anchor), metric = metric)?.let {
        Text(it, color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun CategorySpendingRow(
  item: CategoryBreakdown,
  total: Long,
  period: AnalysisPeriod,
  metric: AnalysisMetric,
  onCategoryClick: (CategoryBreakdown) -> Unit,
) {
  val ratio = if (total <= 0) 0f else item.amountCents.toFloat() / total
  val color = argbColor(item.color)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClickLabel = "查看${item.categoryName}的区间明细") { onCategoryClick(item) }
      .padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    CategoryAmountHeading(item.categoryName, formatCurrency(item.amountCents), color)
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Text(
        spendingChangeLabel(item.amountCents, item.previousAmountCents, "上" + analysisPeriodLabel(period), analysisMetricLabel(metric)),
        modifier = Modifier.weight(1f),
        color = SextantColors.TextSubtle,
        style = MaterialTheme.typography.bodySmall,
      )
      Text(
        "占比 ${spendingShareLabel(item.amountCents, total)}",
        color = SextantColors.TextSubtle,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.End,
      )
    }
    // 占比条是静态读数，不用波浪进度：波浪会持续重绘，屏幕上有五六个时滚动会掉帧，
    // 而且波形会让「占比」本身更难读。
    LinearProgressIndicator(
      progress = { ratio.coerceIn(0f, 1f) },
      modifier = Modifier.fillMaxWidth(),
      color = color,
      trackColor = SextantColors.SurfaceContainerHighest,
      strokeCap = StrokeCap.Round,
    )
  }
}

/** 常规宽度下名称与金额分列；长金额单独占一行，避免把名称挤没。 */
@Composable
private fun CategoryAmountHeading(name: String, amount: String, color: Color) {
  val amountStyle = MaterialTheme.typography.titleMedium
  val nameStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
  val textMeasurer = rememberTextMeasurer()
  val amountWidth = textMeasurer.measure(amount, style = amountStyle).size.width
  val nameWidth = textMeasurer.measure(name.take(4), style = nameStyle).size.width
  val density = LocalDensity.current
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    // 四个分类字加色点、箭头和三处间距；文字预留随字体缩放，短名称按实际宽度保留。
    val stacked = amountWidth + nameWidth + with(density) { 60.dp.toPx() } > with(density) { maxWidth.toPx() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ColorDot(color)
        Text(
          name,
          modifier = Modifier.weight(1f),
          style = nameStyle,
          maxLines = if (stacked) 2 else 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!stacked) {
          Text(amount, style = amountStyle, textAlign = TextAlign.End)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SextantColors.TextSubtle, modifier = Modifier.size(16.dp))
      }
      if (stacked) {
        Text(amount, modifier = Modifier.fillMaxWidth(), style = amountStyle, textAlign = TextAlign.End)
      }
    }
  }
}

@Composable
private fun AnalyticsSection(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      SextantHeading(title)
      if (subtitle != null) {
        Text(subtitle, color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
      }
    }
    content()
  }
}
