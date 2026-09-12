package com.sextant.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import com.sextant.app.ui.components.CategoryIcon
import com.sextant.app.ui.components.categoryIcon
import com.sextant.app.ui.components.BudgetScale
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.LedgerFilterSheet
import com.sextant.app.ui.components.SextantBadge
import com.sextant.app.ui.components.SextantFlexibleHeader
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.components.SextantSurface
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.DailyTransactionGroup
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.EntryOrigin
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantFullShape
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.isCurrentMonth
import com.sextant.app.ui.util.monthLabel
import com.sextant.app.ui.util.signedAmountPlain
import java.text.SimpleDateFormat
import java.util.Locale

private val ScreenPadding = PaddingValues(start = 20.dp, end = 20.dp)

@Composable
fun HomeScreen(
  state: AppState,
  filter: LedgerFilter,
  onFilterChanged: (LedgerFilter) -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit,
) {
  var showFilters by remember { mutableStateOf(false) }
  val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }
  val listState = rememberLazyListState()

  Column(Modifier.fillMaxSize()) {
    // 标题固定在列表之外：它随内容滚动收起，但不会跟着滚出屏幕，
    // 因此滚动到深处时仍然知道自己在哪一页。Expressive 的 large flexible app bar 行为。
    SextantFlexibleHeader(
      title = "概览",
      scrollState = listState,
      subtitle = overviewSubtitle(state, filter),
      modifier = Modifier.padding(horizontal = 20.dp),
      actions = { FilterTrigger(filter) { showFilters = true } },
    )
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 28.dp),
    ) {
      item {
        Box {
          Column(Modifier.padding(ScreenPadding)) { LedgerOverview(state, filter) }
        }
      }
      item {
        Column(Modifier.fillMaxWidth().padding(ScreenPadding)) {
          RecentLedgerHeader(state.transactions.size)
        }
      }
      if (state.groups.isEmpty()) {
        item {
          Column(Modifier.padding(ScreenPadding)) { EmptyLedgerCard(filter) }
        }
      }
      state.groups.forEach { group ->
        item(key = "day:${group.label}", contentType = "dayHeader") {
          Column(Modifier.padding(ScreenPadding)) { DailyLedgerHeader(group) }
        }
        itemsIndexed(group.items, key = { _, item -> item.transaction.id }, contentType = { _, _ -> "transaction" }) { index, item ->
          val category = categoriesById[item.transaction.categoryId]
          Box {
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

private fun overviewSubtitle(state: AppState, filter: LedgerFilter): String {
  val scope = when {
    filter.isAllTime -> "全部时间"
    filter.isCurrentMonth() -> monthLabel(filter.anchorMonthStart)
    else -> "${monthLabel(filter.anchorMonthStart)}起"
  }
  return "$scope · 共 ${state.transactions.size} 笔"
}

@Composable
private fun EmptyLedgerCard(filter: LedgerFilter) {
  if (filter.hasConditions || !filter.isAllTime) {
    EmptyCard("这个条件下还没有记录", "换个时间范围，或清除筛选条件后再看看。")
  } else {
    EmptyCard("还没有记录", "记一笔后，这里会按日期排好最近的明细。")
  }
}

/**
 * 筛选入口。有生效条件时整颗按钮换成铜橙淡底并挂上数量徽标——
 * 旧版靠一个 16dp 的小圆点，数字挤在 10sp 里，看不清也不好点。
 */
@Composable
private fun FilterTrigger(filter: LedgerFilter, onClick: () -> Unit) {
  val highlighted = filter.hasConditions || !filter.isAllTime
  Box(contentAlignment = Alignment.TopEnd) {
    SextantSurface(
      modifier = Modifier
        .size(48.dp)
        .clickable(onClickLabel = "打开筛选", onClick = onClick),
      color = if (highlighted) SextantColors.AccentSoft else SextantColors.SurfaceContainer,
      shape = SextantFullShape,
    ) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
          Icons.Outlined.Tune,
          contentDescription = if (filter.hasConditions) "筛选，已启用 ${filter.conditionCount} 项条件" else "筛选",
          tint = if (highlighted) SextantColors.AccentStrong else SextantColors.TextMuted,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    if (filter.conditionCount > 0) {
      Box(Modifier.padding(top = 1.dp, end = 1.dp)) { SextantBadge(filter.conditionCount) }
    }
  }
}

@Composable
private fun LedgerOverview(state: AppState, filter: LedgerFilter) {
  val currentMonth = filter.isCurrentMonth()
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("今日支出", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelLarge)
      Text(
        "¥${formatAmountPlain(state.todaySummary.expenseCents)}",
        color = SextantColors.Text,
        style = MaterialTheme.typography.headlineLarge,
      )
    }
    Row(Modifier.fillMaxWidth()) {
      OverviewMetric(if (filter.isAllTime) "累计支出" else "区间支出", state.rangeSummary.expenseCents, Modifier.weight(1f))
      OverviewMetric(if (filter.isAllTime) "累计收入" else "区间收入", state.rangeSummary.incomeCents, Modifier.weight(1f))
    }
    state.monthlyBudgetCents?.let { total ->
      MonthlyBudgetProgress(
        label = if (filter.isAllTime || currentMonth) "本月预算进度" else "${monthLabel(filter.anchorMonthStart)}预算进度",
        spent = state.monthSummary.expenseCents,
        total = total,
      )
    }
  }
}

@Composable
private fun OverviewMetric(label: String, value: Long, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelMedium)
    Text(
      formatAmountPlain(value),
      style = MaterialTheme.typography.titleLarge,
      color = SextantColors.Text,
    )
  }
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
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(label, color = SextantColors.TextMuted, style = MaterialTheme.typography.labelMedium)
      Text(
        "${(ratio * 100).toInt()}%  ·  ${formatAmountPlain(spent)} / ${formatAmountPlain(total)}",
        color = color,
        style = MaterialTheme.typography.labelLarge,
      )
    }
    BudgetScale(progress, color)
  }
}

@Composable
private fun RecentLedgerHeader(count: Int) {
  Row(
    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SextantHeading("明细")
    Text("$count 笔", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
private fun DailyLedgerHeader(group: DailyTransactionGroup) {
  val income = group.items.filter { it.transaction.type == TransactionType.Income }.sumOf { it.transaction.amountCents }
  val expense = group.items
    .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
    .sumOf { it.transaction.amountCents }

  FlowRow(
    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = group.label,
      color = SextantColors.Text,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = dailySummary(expense, income),
      color = SextantColors.TextSubtle,
      style = MaterialTheme.typography.labelMedium,
      softWrap = true,
    )
  }
}

private fun dailySummary(expense: Long, income: Long): String = buildList {
  if (expense > 0) add("支 ${formatAmountPlain(expense)}")
  if (income > 0) add("收 ${formatAmountPlain(income)}")
}.joinToString("  ")

/**
 * 单笔明细。
 *
 * 行与行之间不再画 1dp 分隔线：明细本来就是一整块列表，靠留白和分类图标分组更安静，
 * 而那条从 48dp 处起始的线在深色主题下几乎看不见，在浅色主题下又把列表切得很碎。
 */
@Composable
private fun LedgerTransactionRow(
  item: TransactionListItem,
  categoryName: String,
  parentName: String?,
  onClick: (TransactionListItem) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().clickable { onClick(item) }.padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CategoryIcon(categoryIcon(categoryName), argbColor(item.categoryColor))
    Spacer(Modifier.width(14.dp))
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      FlowRow(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = categoryName,
          color = SextantColors.Text,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = signedAmountPlain(item),
          color = SextantColors.Text,
          style = MaterialTheme.typography.titleMedium,
          softWrap = true,
        )
      }
      Text(
        text = ledgerMetadata(item, parentName),
        color = SextantColors.TextSubtle,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun ledgerMetadata(item: TransactionListItem, parentName: String?): String {
  val genericParts = setOf("消费", "交易提醒", "微信支付", "支付宝")
  val description = item.transaction.description
    .split("·")
    .map(String::trim)
    .filter { it.isNotBlank() && it !in genericParts && it != item.paymentMethodName }
    .joinToString(" · ")
  return buildList {
    if (!parentName.isNullOrBlank()) add(parentName)
    add(SimpleDateFormat("HH:mm", Locale.CHINA).format(item.transaction.transactionDate))
    if (item.paymentMethodName.isNotBlank()) add(item.paymentMethodName)
    if (description.isNotBlank()) add(description)
    if (item.transaction.entryOrigin == EntryOrigin.Notification) {
      add(if (item.transaction.isUserEdited) "已修正" else "自动录入")
    }
  }.joinToString(" · ")
}
