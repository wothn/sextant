package com.sextant.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.AppState
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.CategoryBreakdown
import com.sextant.app.data.DailyTransactionGroup
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.signedAmountPlain
import com.sextant.app.ui.util.transactionSubtitle
import com.sextant.app.ui.util.label
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
  state: AppState,
  filter: LedgerFilter,
  onFilterChanged: (LedgerFilter) -> Unit,
  onQuickEntry: () -> Unit,
  onTransactionClick: (TransactionListItem) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      LedgerTopBar(onQuickEntry = onQuickEntry)
    }
    item {
      LedgerOverview(state)
    }
    item { LedgerFilters(state, filter, onFilterChanged) }
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
}

@Composable
private fun LedgerFilters(state: AppState, filter: LedgerFilter, onChanged: (LedgerFilter) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = { onChanged(filter.copy(monthStart = filter.monthStart.shiftMonth(-1))) }) { Text("上个月") }
      Text(SimpleDateFormat("yyyy年M月", Locale.CHINA).format(filter.monthStart), fontWeight = FontWeight.Bold)
      TextButton(onClick = { onChanged(filter.copy(monthStart = filter.monthStart.shiftMonth(1))) }) { Text("下个月") }
    }
    OutlinedTextField(
      value = filter.query,
      onValueChange = { onChanged(filter.copy(query = it)) },
      label = { Text("搜索备注") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(selected = filter.type == null, onClick = { onChanged(filter.copy(type = null)) }, label = { Text("全部") })
      TransactionType.entries.forEach { type ->
        FilterChip(selected = filter.type == type, onClick = { onChanged(filter.copy(type = type)) }, label = { Text(type.label) })
      }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(selected = filter.categoryId == null, onClick = { onChanged(filter.copy(categoryId = null)) }, label = { Text("全部分类") })
      state.categories.filter { filter.type == null || it.type == filter.type }.forEach { category ->
        FilterChip(selected = filter.categoryId == category.id, onClick = { onChanged(filter.copy(categoryId = category.id)) }, label = { Text(category.name) })
      }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(selected = filter.paymentMethodId == null, onClick = { onChanged(filter.copy(paymentMethodId = null)) }, label = { Text("全部方式") })
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
private fun LedgerTopBar(onQuickEntry: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("记账", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("今日与本月收支", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodyMedium)
      }
    }
    Button(
      onClick = onQuickEntry,
      shape = RoundedCornerShape(14.dp),
      colors = ButtonDefaults.buttonColors(containerColor = SextantColors.Accent, contentColor = SextantColors.OnAccent),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(6.dp))
      Text("记一笔", fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun LedgerOverview(state: AppState) {
  Card(
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = SextantColors.Surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("今日支出", color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
        Text(formatAmountPlain(state.todaySummary.expenseCents), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = SextantColors.Text)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OverviewMetric("本月支出", state.monthSummary.expenseCents, SextantColors.Danger, Modifier.weight(1f))
        OverviewMetric("本月收入", state.monthSummary.incomeCents, SextantColors.Success, Modifier.weight(1f))
        OverviewMetric("本月结余", state.monthSummary.netCents, SextantColors.AccentStrong, Modifier.weight(1f))
      }
      if (state.categoryBreakdown.isNotEmpty()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SextantColors.Border))
        TopSpendingBreakdown(state.categoryBreakdown.take(3), state.categoryBreakdown.sumOf { it.amountCents })
      }
    }
  }
}

@Composable
private fun OverviewMetric(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(label, color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    Text(formatAmountPlain(value), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = color)
  }
}

@Composable
private fun TopSpendingBreakdown(items: List<CategoryBreakdown>, total: Long) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("本月主要支出", fontWeight = FontWeight.SemiBold, color = SextantColors.Text)
    items.forEach { item ->
      val color = argbColor(item.color)
      val percent = if (total <= 0) 0f else (item.amountCents.toDouble() / total).toFloat().coerceIn(0f, 1f)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Text(item.categoryName, fontWeight = FontWeight.SemiBold)
          }
          Text(formatAmountPlain(item.amountCents), color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(SextantColors.SurfaceStrong)) {
          Box(Modifier.fillMaxWidth(percent).height(7.dp).clip(CircleShape).background(color))
        }
      }
    }
  }
}

@Composable
private fun RecentLedgerHeader(count: Int) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Text("最近明细", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("$count 笔", color = SextantColors.TextSubtle, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun DailyLedgerCard(group: DailyTransactionGroup, onTransactionClick: (TransactionListItem) -> Unit) {
  val income = group.items.filter { it.transaction.type == TransactionType.Income }.sumOf { it.transaction.amountCents }
  val expense = group.items
    .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
    .sumOf { it.transaction.amountCents }

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = SextantColors.Surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(Modifier.fillMaxWidth()) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(group.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("支 ${formatAmountPlain(expense)}    收 ${formatAmountPlain(income)}", color = SextantColors.TextSubtle, fontSize = 18.sp)
      }
      group.items.forEachIndexed { index, item ->
        if (index > 0) {
          Box(Modifier.fillMaxWidth().padding(start = 86.dp).height(1.dp).background(SextantColors.Border))
        }
        LedgerTransactionRow(item, onTransactionClick)
      }
    }
  }
}

@Composable
private fun LedgerTransactionRow(item: TransactionListItem, onClick: (TransactionListItem) -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable { onClick(item) }.padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      LedgerCategoryIcon(item)
      Spacer(Modifier.width(14.dp))
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(item.categoryName, fontSize = 19.sp, fontWeight = FontWeight.Bold)
          if (item.transaction.description.isNotBlank()) {
            Text(
              item.transaction.description,
              color = SextantColors.TextSubtle,
              modifier = Modifier
                .border(1.dp, SextantColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        Text(
          transactionSubtitle(item),
          color = SextantColors.TextSubtle,
          fontSize = 16.sp,
        )
      }
    }
    Text(
      text = signedAmountPlain(item),
      color = Color.Black,
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun LedgerCategoryIcon(item: TransactionListItem) {
  val color = argbColor(item.categoryColor)
  Box(
    modifier = Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = if (item.categoryName.contains("餐") || item.categoryName.contains("食")) {
        Icons.Outlined.Restaurant
      } else {
        Icons.Outlined.Home
      },
      contentDescription = null,
      tint = color,
      modifier = Modifier.size(26.dp),
    )
  }
}
