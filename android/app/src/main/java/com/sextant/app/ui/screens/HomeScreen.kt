package com.sextant.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import com.sextant.app.ui.components.CategoryIcon
import com.sextant.app.ui.components.categoryIcon
import com.sextant.app.ui.components.BudgetScale
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.LedgerFilterSheet
import com.sextant.app.ui.components.LocalBottomBarInset
import com.sextant.app.ui.components.SextantBadge
import com.sextant.app.ui.components.SextantFlexibleHeader
import com.sextant.app.ui.components.SextantFabClearance
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.components.SextantMetric
import com.sextant.app.ui.components.SextantSurface
import com.sextant.app.ui.components.SextantTextButton
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.DailyTransactionGroup
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.EntryOrigin
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.dayGroupLabel
import com.sextant.app.ui.util.displayDescription
import com.sextant.app.ui.util.isCurrentMonth
import com.sextant.app.ui.util.monthLabel
import com.sextant.app.ui.util.signedAmountGrouped
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material.icons.outlined.CalendarMonth
import com.sextant.app.ui.components.SextantTonalIconButton
import com.sextant.app.ui.theme.SextantFullShape
import com.sextant.app.ui.theme.sextantEmphasized

private val ScreenPadding = PaddingValues(start = 20.dp, end = 20.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
  state: AppState,
  filter: LedgerFilter,
  onFilterChanged: (LedgerFilter) -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit,
  onOpenCalendar: () -> Unit,
  onCreateEntry: () -> Unit = {},
) {
  var showFilters by rememberSaveable { mutableStateOf(false) }
  val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }
  val listState = rememberLazyListState()
  var lastAppliedFilter by remember { mutableStateOf(filter) }
  LaunchedEffect(filter) {
    // 只有筛选条件真的变了才回到顶部；从别的 Tab 切回来时保留原来的滚动位置。
    if (filter != lastAppliedFilter) {
      lastAppliedFilter = filter
      listState.scrollToItem(0)
    }
  }

  Column(Modifier.fillMaxSize()) {
    // 标题固定在列表之外：它随内容滚动收起，但不会跟着滚出屏幕，
    // 因此滚动到深处时仍然知道自己在哪一页。Expressive 的 large flexible app bar 行为。
    SextantFlexibleHeader(
      title = "概览",
      scrollState = listState,
      modifier = Modifier.padding(horizontal = 20.dp),
      actions = {
        SextantTonalIconButton(onClick = onOpenCalendar) {
          Icon(Icons.Outlined.CalendarMonth, contentDescription = "收支日历", modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(8.dp))
        FilterTrigger(filter) { showFilters = true }
      },
    )
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      // 底部留出记账 FAB 的位置，最后一笔明细的金额不会停在它底下；FAB 上面还有悬浮底栏，
      // 所以还要加上 LocalBottomBarInset。
      contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + SextantFabClearance),
    ) {
      item {
        Column(Modifier.padding(ScreenPadding).padding(top = 8.dp, bottom = 16.dp)) {
          LedgerOverview(state, filter)
        }
      }
      item {
        Column(Modifier.fillMaxWidth().padding(ScreenPadding)) {
          RecentLedgerHeader(state.transactions.size, filter)
        }
      }
      if (filter.hasConditions) {
        item(key = "filterSummary") {
          Row(
            Modifier.fillMaxWidth().padding(ScreenPadding).padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              "已启用 ${filter.conditionCount} 项筛选",
              color = SextantColors.TextMuted,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )
            SextantTextButton(onClick = { onFilterChanged(LedgerFilter.allTime()) }) { Text("清除") }
          }
        }
      }
      if (state.groups.isEmpty()) {
        item {
          Column(Modifier.padding(ScreenPadding)) {
            EmptyLedgerCard(filter, onCreateEntry) { onFilterChanged(LedgerFilter.allTime()) }
          }
        }
      }
      state.groups.forEach { group ->
        item(key = "day:${group.dayStartMillis}", contentType = "dayHeader") {
          Column(Modifier.background(SextantColors.Background).padding(ScreenPadding)) { DailyLedgerHeader(group) }
        }
        items(group.items, key = { it.transaction.id }, contentType = { "transaction" }) { item ->
          val category = categoriesById[item.transaction.categoryId]
          Column(Modifier.padding(ScreenPadding)) {
            LedgerTransactionRow(
              item = item,
              categoryName = category?.name ?: item.categoryName,
              parentName = categoriesById[category?.parentCategoryId]?.name,
              onClick = onTransactionClick,
            )
          }
        }
      }
    }
  }

  if (showFilters) {
    LedgerFilterSheet(
      state = state,
      filter = filter,
      onApply = {
        onFilterChanged(it)
        showFilters = false
      },
      onDismiss = { showFilters = false },
    )
  }
}

@Composable
private fun EmptyLedgerCard(filter: LedgerFilter, onCreateEntry: () -> Unit, onClearFilter: () -> Unit) {
  if (filter.hasConditions) {
    EmptyCard(
      title = "这个条件下还没有记录",
      description = "换个时间范围，或清除筛选条件后再看看。",
      actionLabel = "清除筛选",
      onAction = onClearFilter,
    )
  } else {
    EmptyCard(
      title = "还没有记录",
      description = "记一笔后，这里会按日期排好最近的明细。",
      actionLabel = "记一笔",
      onAction = onCreateEntry,
    )
  }
}

/**
 * 筛选入口。有生效条件时整颗按钮换成铜橙淡底并挂上数量徽标——
 * 旧版靠一个 16dp 的小圆点，数字挤在 10sp 里，看不清也不好点。
 */
@Composable
private fun FilterTrigger(filter: LedgerFilter, onClick: () -> Unit) {
  Box(contentAlignment = Alignment.TopEnd) {
    SextantTonalIconButton(
      onClick = onClick,
      highlighted = filter.hasConditions,
    ) {
      Icon(
        Icons.Outlined.Tune,
        contentDescription = if (filter.hasConditions) "筛选，已启用 ${filter.conditionCount} 项条件" else "筛选",
        modifier = Modifier.size(24.dp),
      )
    }
    if (filter.conditionCount > 0) {
      Box(Modifier.padding(top = 1.dp, end = 1.dp)) { SextantBadge(filter.conditionCount) }
    }
  }
}

@Composable
private fun LedgerOverview(state: AppState, filter: LedgerFilter) {
  val currentMonth = filter.isCurrentMonth()
  SextantSurface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SextantMetric(
        label = "今日支出",
        value = formatCurrency(state.todaySummary.expenseCents),
        primary = true,
        valueStyle = MaterialTheme.typography.headlineLarge.sextantEmphasized(),
      )
      // 汇总口径固定为「当前查看月份」，不跟随明细的筛选范围放大成区间或累计总额：
      // 概览只回答「这个月花了多少、收了多少」，全量数据留给分析页。
      val monthScope = monthScopeLabel(filter, currentMonth)
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val metricModifier = Modifier.widthIn(min = (maxWidth - 16.dp) / 2, max = maxWidth)
        FlowRow(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OverviewMetric("${monthScope}支出", state.monthSummary.expenseCents, metricModifier)
          OverviewMetric("${monthScope}收入", state.monthSummary.incomeCents, metricModifier)
        }
      }
      state.monthlyBudgetCents?.let { total ->
        MonthlyBudgetProgress(
          label = "${monthScope}预算",
          spent = state.monthSummary.expenseCents,
          total = total,
        )
      }
    }
  }
}

/**
 * 概览汇总口径的名称：锚定月份就是本月时说「本月」，否则点名具体的年月，
 * 避免浏览历史月份时把那个月的数字误读成当月。汇总与预算进度共用同一条规则。
 */
private fun monthScopeLabel(filter: LedgerFilter, isCurrentMonthView: Boolean): String =
  if (filter.isAllTime || isCurrentMonthView) "本月" else monthLabel(filter.anchorMonthStart)

@Composable
private fun OverviewMetric(label: String, value: Long, modifier: Modifier = Modifier) {
  SextantMetric(label = label, value = formatCurrency(value), modifier = modifier)
}

@Composable
private fun MonthlyBudgetProgress(label: String, spent: Long, total: Long) {
  val ratio = if (total <= 0) 0f else spent.toFloat() / total
  val progress = ratio.coerceIn(0f, 1f)
  val color = when {
    ratio >= 1f -> SextantColors.Danger
    ratio >= 0.8f -> SextantColors.Warning
    else -> SextantColors.Accent
  }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(label, color = SextantColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
      Text(
        "已用 ${(ratio * 100).toInt()}%",
        color = color,
        style = MaterialTheme.typography.labelLarge,
      )
    }
    BudgetScale(progress, color, notchColor = SextantColors.SurfaceContainerLow)
    Text(
      "${formatCurrency(spent)} / ${formatCurrency(total)}",
      color = SextantColors.TextMuted,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun RecentLedgerHeader(count: Int, filter: LedgerFilter) {
  Row(
    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SextantHeading(if (filter.hasConditions) "筛选明细" else "明细")
    Text("$count 笔", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun DailyLedgerHeader(group: DailyTransactionGroup) {
  val income = group.items.filter { it.transaction.type == TransactionType.Income }.sumOf { it.transaction.amountCents }
  val expense = group.items
    .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
    .sumOf { it.transaction.amountCents }

  FlowRow(
    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = dayGroupLabel(group.dayStartMillis),
      color = SextantColors.Text,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = dailySummary(expense, income),
      color = SextantColors.TextSubtle,
      style = MaterialTheme.typography.bodyMedium,
      softWrap = true,
    )
  }
}

private fun dailySummary(expense: Long, income: Long): String = buildList {
  if (expense > 0) add("支出 ${formatCurrency(expense)}")
  if (income > 0) add("收入 ${formatCurrency(income)}")
}.joinToString("  ")

/**
 * 单笔明细。
 *
 * 行与行之间不再画 1dp 分隔线：明细本来就是一整块列表，靠留白和分类图标分组更安静，
 * 而那条从 48dp 处起始的线在深色主题下几乎看不见，在浅色主题下又把列表切得很碎。
 */
@Composable
internal fun LedgerTransactionRow(
  item: TransactionListItem,
  categoryName: String,
  parentName: String?,
  onClick: (TransactionListItem) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().clickable(onClickLabel = "编辑这笔记录") { onClick(item) }.padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CategoryIcon(categoryIcon(categoryName), argbColor(item.categoryColor), size = 40.dp, iconSize = 22.dp)
    Spacer(Modifier.width(12.dp))
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      // 金额固定贴在行右缘：一列数字左端对齐才能逐行扫读。
      // 分类名让出空间并在过长时省略，避免金额跟着名字长短左右浮动。
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // 分类名和来源标签是一组：标签紧贴名字右边，剩下的空白留给金额。
        // 名字过长时先省略名字（fill = false 让它只占实际需要的宽度），标签和金额都保持完整。
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = categoryName,
            modifier = Modifier.weight(1f, fill = false),
            color = SextantColors.Text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (item.transaction.entryOrigin != EntryOrigin.Manual) {
            LedgerOriginTag()
          }
        }
        Text(
          // 明细行只留正负号：汇总和日小计已经承担了货币符号，每一行再重复一个 ¥ 只会把
          // 数字往左推、离右缘更远。方向仍由符号表达，格式仍走共享函数。
          text = signedAmountGrouped(item),
          color = SextantColors.Text,
          style = MaterialTheme.typography.titleMedium.sextantEmphasized(),
          textAlign = TextAlign.End,
          softWrap = true,
        )
      }
      Text(
        text = ledgerMetadata(item, parentName),
        color = SextantColors.TextSubtle,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun ledgerMetadata(item: TransactionListItem, parentName: String?): String {
  return buildList {
    if (!parentName.isNullOrBlank()) add(parentName)
    add(SimpleDateFormat("HH:mm", Locale.CHINA).format(item.transaction.transactionDate))
    if (item.paymentMethodName.isNotBlank()) add(item.paymentMethodName)
    displayDescription(item).takeIf { it.isNotBlank() }?.let(::add)
  }.joinToString(" · ")
}

/**
 * 明细行的自动记账来源标签。
 *
 * 它挂在分类名右边，而不是排在说明行末尾：说明行已经有上级分类、时间、来源和备注，
 * 状态挤在里面要读完一整行才看得到。淡底胶囊紧挨分类名，一眼就能分出「这笔是自动记的」，
 * 又不会像铜橙强调色那样跟右侧金额抢注意力。
 */
@Composable
private fun LedgerOriginTag() {
  Text(
    text = "自动录入",
    modifier = Modifier
      .background(SextantColors.SurfaceContainer, SextantFullShape)
      .padding(horizontal = 6.dp, vertical = 2.dp),
    color = SextantColors.TextMuted,
    style = MaterialTheme.typography.labelSmall,
    maxLines = 1,
    softWrap = false,
  )
}
