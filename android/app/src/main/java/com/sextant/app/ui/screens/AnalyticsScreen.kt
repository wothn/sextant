package com.sextant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AppState
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.ProgressRow
import com.sextant.app.ui.components.SectionTitle
import com.sextant.app.ui.components.SummaryCard
import com.sextant.app.ui.theme.argbColor

@Composable
fun AnalyticsScreen(state: AppState) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { SummaryCard("本月收支", state.monthSummary.incomeCents, state.monthSummary.expenseCents) }
    item { SectionTitle("支出结构") }
    if (state.categoryBreakdown.isEmpty()) {
      item { EmptyCard("暂无分析", "录入支出后会展示分类占比。") }
    } else {
      items(state.categoryBreakdown) { item ->
        ProgressRow(item.categoryName, item.amountCents, state.monthSummary.expenseCents, argbColor(item.color))
      }
    }
    item { SectionTitle("预算进度") }
    if (state.budgets.isEmpty()) {
      item { EmptyCard("尚未设置预算", "在预算页为常用分类设置月预算。") }
    } else {
      items(state.budgets) { item ->
        ProgressRow(item.categoryName, item.spentCents, item.amountCents, argbColor(item.color))
      }
    }
  }
}
