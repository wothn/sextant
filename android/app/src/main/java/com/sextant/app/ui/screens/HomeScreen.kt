package com.sextant.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.AppState
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.DailyTransactionGroup
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.EntryOrigin
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.LedgerFilterSheet
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.isCurrentMonth
import com.sextant.app.ui.util.monthLabel
import com.sextant.app.ui.util.signedAmountPlain
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HomeScreen(
  state: AppState,
  filter: LedgerFilter,
  onFilterChanged: (LedgerFilter) -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit,
) {
  var showFilters by remember { mutableStateOf(false) }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
  ) {
    item {
      LedgerHeader(filter = filter, onFilterClick = { showFilters = true })
    }
    item {
      LedgerOverview(state, filter)
    }
    item {
      HorizontalDivider(color = SextantColors.Border)
    }
    item {
      RecentLedgerHeader(state.transactions.size)
    }
    if (state.groups.isEmpty()) {
      item { EmptyLedgerCard(filter) }
    }
    state.groups.forEach { group ->
      item(key = "day:${group.label}", contentType = "dayHeader") {
        DailyLedgerHeader(group)
      }
      itemsIndexed(group.items, key = { _, item -> item.transaction.id }, contentType = { _, _ -> "transaction" }) { index, item ->
        Column {
          if (index > 0) {
            Box(Modifier.fillMaxWidth().padding(start = 48.dp).height(1.dp).background(SextantColors.Border))
          }
          LedgerTransactionRow(item, onTransactionClick)
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
private fun EmptyLedgerCard(filter: LedgerFilter) {
  if (filter.hasConditions || !filter.isAllTime) {
    EmptyCard("这个条件下还没有记录", "换个时间范围，或清除筛选条件后再看看。")
  } else {
    EmptyCard("还没有记录", "记一笔后，这里会按日期排好最近的明细。")
  }
}

@Composable
private fun LedgerHeader(filter: LedgerFilter, onFilterClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().height(48.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("概览", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SextantColors.Text)
    FilterTrigger(filter, onFilterClick)
  }
}

@Composable
private fun FilterTrigger(filter: LedgerFilter, onClick: () -> Unit) {
  val highlighted = filter.hasConditions || !filter.isAllTime
  Box(
    modifier = Modifier
      .size(40.dp)
      .clip(CircleShape)
      .background(if (highlighted) SextantColors.AccentSoft else SextantColors.SurfaceAlt)
      .clickable(onClickLabel = "打开筛选", onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Outlined.Tune,
      contentDescription = if (filter.hasConditions) "筛选，已启用 ${filter.conditionCount} 项条件" else "筛选",
      tint = if (highlighted) SextantColors.AccentStrong else SextantColors.TextMuted,
      modifier = Modifier.size(20.dp),
    )
    if (filter.conditionCount > 0) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 3.dp, end = 3.dp)
          .size(16.dp)
          .clip(CircleShape)
          .background(SextantColors.AccentStrong),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = filter.conditionCount.toString(),
          color = SextantColors.OnAccent,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun LedgerOverview(state: AppState, filter: LedgerFilter) {
  val currentMonth = filter.isCurrentMonth()
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("今日支出", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelLarge)
      Text(
        "¥${formatAmountPlain(state.todaySummary.expenseCents)}",
        color = SextantColors.Text,
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
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
    Text(formatAmountPlain(value), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SextantColors.Text)
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
  Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, color = SextantColors.TextMuted, style = MaterialTheme.typography.labelMedium)
      Text(
        "${(ratio * 100).toInt()}%  ·  ${formatAmountPlain(spent)} / ${formatAmountPlain(total)}",
        color = color,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium,
      )
    }
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(SextantColors.SurfaceStrong)) {
      Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(CircleShape).background(color))
    }
  }
}

@Composable
private fun RecentLedgerHeader(count: Int) {
  Row(
    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("明细", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SextantColors.Text)
    Text("$count 笔", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
private fun DailyLedgerHeader(group: DailyTransactionGroup) {
  val income = group.items.filter { it.transaction.type == TransactionType.Income }.sumOf { it.transaction.amountCents }
  val expense = group.items
    .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
    .sumOf { it.transaction.amountCents }

  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = group.label,
        modifier = Modifier.weight(1f),
        color = SextantColors.Text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.width(12.dp))
      Text(
        text = dailySummary(expense, income),
        color = SextantColors.TextSubtle,
        fontSize = 12.sp,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

private fun dailySummary(expense: Long, income: Long): String = buildList {
  if (expense > 0) add("支 ${formatAmountPlain(expense)}")
  if (income > 0) add("收 ${formatAmountPlain(income)}")
}.joinToString("  ")

@Composable
private fun LedgerTransactionRow(item: TransactionListItem, onClick: (TransactionListItem) -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable { onClick(item) }.padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LedgerCategoryIcon(item)
    Spacer(Modifier.width(12.dp))
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = item.categoryName,
          modifier = Modifier.weight(1f),
          color = SextantColors.Text,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = signedAmountPlain(item),
          color = SextantColors.Text,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          softWrap = false,
        )
      }
      Text(
        text = ledgerMetadata(item),
        color = SextantColors.TextSubtle,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun ledgerMetadata(item: TransactionListItem): String {
  val genericParts = setOf("消费", "交易提醒", "微信支付", "支付宝")
  val description = item.transaction.description
    .split("·")
    .map(String::trim)
    .filter { it.isNotBlank() && it !in genericParts && it != item.paymentMethodName }
    .joinToString(" · ")
  return buildList {
    add(SimpleDateFormat("HH:mm", Locale.CHINA).format(item.transaction.transactionDate))
    if (item.paymentMethodName.isNotBlank()) add(item.paymentMethodName)
    if (description.isNotBlank()) add(description)
    if (item.transaction.entryOrigin == EntryOrigin.Notification) {
      add(if (item.transaction.isUserEdited) "已修正" else "自动录入")
    }
  }.joinToString(" · ")
}

@Composable
private fun LedgerCategoryIcon(item: TransactionListItem) {
  Box(
    modifier = Modifier.size(36.dp).clip(CircleShape).background(SextantColors.AccentSoft),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = if (item.categoryName.contains("餐") || item.categoryName.contains("食")) {
        Icons.Outlined.Restaurant
      } else {
        Icons.Outlined.Home
      },
      contentDescription = null,
      tint = SextantColors.AccentStrong,
      modifier = Modifier.size(19.dp),
    )
  }
}

