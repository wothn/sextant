package com.sextant.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.AppState
import com.sextant.app.data.DailyTransactionGroup
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.formatAmountPlain
import com.sextant.app.ui.util.signedAmountPlain
import com.sextant.app.ui.util.transactionSubtitle

@Composable
fun HomeScreen(state: AppState, onQuickEntry: () -> Unit) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      LedgerTopBar(onQuickEntry = onQuickEntry)
    }
    item {
      LedgerOverviewCard(state)
    }
    if (state.groups.isEmpty()) {
      item { EmptyCard("还没有记录", "记一笔后，这里会按日期排好最近的明细。") }
    }
    state.groups.forEach { group ->
      item {
        DailyLedgerCard(group)
      }
    }
  }
}

@Composable
private fun LedgerTopBar(onQuickEntry: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = SextantColors.Text, modifier = Modifier.size(30.dp))
      Text("记账", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = {}) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历", tint = SextantColors.Text)
      }
      IconButton(onClick = {}) {
        Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = SextantColors.Text)
      }
      IconButton(onClick = onQuickEntry) {
        Icon(Icons.Outlined.CheckBox, contentDescription = "快速记账", tint = SextantColors.Text)
      }
    }
  }
}

@Composable
private fun LedgerOverviewCard(state: AppState) {
  Card(
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6D6)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("今日支出(元)", color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
          Text(formatAmountPlain(state.todaySummary.expense), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OverviewMetric("本月支出(元)", state.monthSummary.expense, Modifier.weight(1f))
        OverviewMetric("本月收入(元)", state.monthSummary.income, Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun OverviewMetric(label: String, value: Double, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
    Text(formatAmountPlain(value), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.Black)
  }
}

@Composable
private fun DailyLedgerCard(group: DailyTransactionGroup) {
  val income = group.items.filter { it.transaction.type == TransactionType.Income }.sumOf { it.transaction.amount }
  val expense = group.items
    .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
    .sumOf { it.transaction.amount }

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
        LedgerTransactionRow(item)
      }
    }
  }
}

@Composable
private fun LedgerTransactionRow(item: TransactionListItem) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
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
