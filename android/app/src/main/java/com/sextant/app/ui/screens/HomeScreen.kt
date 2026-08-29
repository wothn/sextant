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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sextant.app.ui.components.CategoryPicker
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.signedAmountPlain
import com.sextant.app.ui.util.label
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
  state: AppState,
  filter: LedgerFilter,
  onFilterChanged: (LedgerFilter) -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit,
) {
  var showFilters by remember { mutableStateOf(false) }
  var draftFilter by remember(filter) { mutableStateOf(filter) }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
  ) {
    item {
      LedgerHeader(
        filter = filter,
        onFilterClick = {
          draftFilter = filter
          showFilters = true
        },
        onFilterChanged = onFilterChanged,
      )
    }
    item {
      LedgerOverview(state)
    }
    item {
      HorizontalDivider(color = SextantColors.Border)
    }
    item {
      RecentLedgerHeader(state.transactions.size)
    }
    if (state.groups.isEmpty()) {
      item { EmptyCard("还没有记录", "记一笔后，这里会按日期排好最近的明细。") }
    }
    state.groups.forEach { group ->
      item {
        DailyLedgerCard(group, onTransactionClick)
      }
    }
  }

  if (showFilters) {
    ModalBottomSheet(onDismissRequest = { showFilters = false }) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("筛选流水", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LedgerFilters(state, draftFilter) { draftFilter = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          TextButton(
            onClick = { draftFilter = filter.copy(query = "", type = null, categoryId = null, paymentMethodId = null) },
            modifier = Modifier.weight(1f),
          ) { Text("清除") }
          Button(
            onClick = {
              onFilterChanged(draftFilter)
              showFilters = false
            },
            modifier = Modifier.weight(2f),
          ) { Text("查看结果") }
        }
      }
    }
  }
}

@Composable
private fun LedgerHeader(
  filter: LedgerFilter,
  onFilterClick: () -> Unit,
  onFilterChanged: (LedgerFilter) -> Unit,
) {
  val activeFilters = listOf(filter.query.isNotBlank(), filter.type != null, filter.categoryId != null, filter.paymentMethodId != null).count { it }
  Row(
    modifier = Modifier.fillMaxWidth().height(48.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("概览", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SextantColors.Text)
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = { onFilterChanged(filter.copy(monthStart = filter.monthStart.shiftMonth(-1))) },
        modifier = Modifier.size(34.dp),
      ) {
        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月", modifier = Modifier.size(20.dp))
      }
      Text(
        SimpleDateFormat("yyyy年M月", Locale.CHINA).format(filter.monthStart),
        color = SextantColors.Text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
      )
      IconButton(
        onClick = { onFilterChanged(filter.copy(monthStart = filter.monthStart.shiftMonth(1))) },
        modifier = Modifier.size(34.dp),
      ) {
        Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月", modifier = Modifier.size(20.dp))
      }
      IconButton(onClick = onFilterClick, modifier = Modifier.size(40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Outlined.Tune,
            contentDescription = if (activeFilters > 0) "筛选，已启用 $activeFilters 项" else "筛选",
            modifier = Modifier.size(20.dp),
            tint = if (activeFilters > 0) SextantColors.AccentStrong else SextantColors.TextMuted,
          )
          if (activeFilters > 0) {
            Text(
              activeFilters.toString(),
              color = SextantColors.AccentStrong,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LedgerFilters(state: AppState, filter: LedgerFilter, onChanged: (LedgerFilter) -> Unit) {
  val typeCategories = state.categories.filter { filter.type == null || it.type == filter.type }
  val parentCategories = typeCategories.filter { it.parentCategoryId == null }
  val selectedCategory = typeCategories.firstOrNull { it.id == filter.categoryId }
  val selectedParentId = selectedCategory?.parentCategoryId ?: selectedCategory?.id
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    OutlinedTextField(
      value = filter.query,
      onValueChange = { onChanged(filter.copy(query = it)) },
      label = { Text("搜索备注") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Text("收支类型", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(selected = filter.type == null, onClick = { onChanged(filter.copy(type = null)) }, label = { Text("全部") })
      TransactionType.entries.forEach { type ->
        FilterChip(
          selected = filter.type == type,
          onClick = {
            val currentCategoryMatches = state.categories.any { it.id == filter.categoryId && it.type == type }
            onChanged(filter.copy(type = type, categoryId = filter.categoryId.takeIf { currentCategoryMatches }))
          },
          label = { Text(type.label) },
        )
      }
    }
    Text("分类", style = MaterialTheme.typography.labelLarge)
    FilterChip(
      selected = filter.categoryId == null,
      onClick = { onChanged(filter.copy(categoryId = null)) },
      label = { Text("全部分类") },
    )
    CategoryPicker(
      parentCategories = parentCategories,
      typeCategories = typeCategories,
      selectedParentId = selectedParentId,
      selectedCategoryId = filter.categoryId,
      onSelect = { onChanged(filter.copy(categoryId = it.id)) },
    )
    Text("来源", style = MaterialTheme.typography.labelLarge)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(selected = filter.paymentMethodId == null, onClick = { onChanged(filter.copy(paymentMethodId = null)) }, label = { Text("全部来源") })
      state.paymentMethods.forEach { method ->
        FilterChip(selected = filter.paymentMethodId == method.id, onClick = { onChanged(filter.copy(paymentMethodId = method.id)) }, label = { Text(method.name) })
      }
    }
  }
}

private fun Long.shiftMonth(amount: Int): Long = Calendar.getInstance().run {
  timeInMillis = this@shiftMonth
  add(Calendar.MONTH, amount)
  timeInMillis
}

@Composable
private fun LedgerOverview(state: AppState) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("本月结余", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelLarge)
      Text(
        formatBalance(state.monthSummary.netCents),
        color = SextantColors.Text,
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
      )
    }
    Row(Modifier.fillMaxWidth()) {
      OverviewMetric("支出", state.monthSummary.expenseCents, Modifier.weight(1f))
      OverviewMetric("收入", state.monthSummary.incomeCents, Modifier.weight(1f))
      OverviewMetric("今日支出", state.todaySummary.expenseCents, Modifier.weight(1f))
    }
    state.monthlyBudgetCents?.let { total ->
      MonthlyBudgetProgress(spent = state.monthSummary.expenseCents, total = total)
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

private fun formatBalance(valueCents: Long): String {
  val sign = when {
    valueCents < 0 -> "−"
    valueCents > 0 -> "+"
    else -> ""
  }
  return "$sign¥${formatAmountPlain(abs(valueCents))}"
}

@Composable
private fun MonthlyBudgetProgress(spent: Long, total: Long) {
  val ratio = if (total <= 0) 0f else spent.toFloat() / total
  val progress = ratio.coerceIn(0f, 1f)
  val color = when {
    ratio >= 1f -> SextantColors.Danger
    ratio >= 0.8f -> SextantColors.Warning
    else -> SextantColors.Accent
  }
  Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("预算进度", color = SextantColors.TextMuted, style = MaterialTheme.typography.labelMedium)
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
private fun DailyLedgerCard(group: DailyTransactionGroup, onTransactionClick: (TransactionListItem) -> Unit) {
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
    group.items.forEachIndexed { index, item ->
      if (index > 0) {
        Box(Modifier.fillMaxWidth().padding(start = 48.dp).height(1.dp).background(SextantColors.Border))
      }
      LedgerTransactionRow(item, onTransactionClick)
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
