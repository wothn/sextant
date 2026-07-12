package com.sextant.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sextant.app.data.CategoryBreakdown
import com.sextant.app.data.DailySpending
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.SectionTitle
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.formatCurrency
import kotlin.math.abs

@Composable
fun AnalyticsScreen(state: AppState) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(22.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("本月分析")
        Text(monthInsight(state), color = SextantColors.TextSubtle)
      }
    }
    item { MonthComparison(state) }
    item {
      AnalyticsSection("每日支出", "查看这个月的消费节奏") {
        if (state.dailySpending.none { it.amountCents > 0 }) {
          EmptyCard("暂无趋势", "录入支出后，这里会显示每日消费变化。")
        } else {
          SpendingChart(state.dailySpending)
        }
      }
    }
    item {
      AnalyticsSection("支出结构", "本月金额最高的消费分类") {
        if (state.categoryBreakdown.isEmpty()) {
          EmptyCard("暂无分析", "录入支出后会展示分类占比。")
        } else {
          CategoryStructure(state.categoryBreakdown.take(5), state.monthSummary.expenseCents)
        }
      }
    }
    item { BudgetForecast(state) }
  }
}

@Composable
private fun MonthComparison(state: AppState) {
  val current = state.monthSummary.expenseCents
  val previous = state.previousMonthSummary.expenseCents
  val change = if (previous <= 0) null else (current - previous).toDouble() / previous
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("本月支出", color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
    Text(formatCurrency(current), fontSize = 38.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
      ComparisonMetric("上月支出", formatCurrency(previous))
      ComparisonMetric(
        "环比变化",
        change?.let { "${if (it >= 0) "+" else ""}${(it * 100).toInt()}%" } ?: "—",
        when {
          change == null -> SextantColors.Text
          change > 0 -> SextantColors.Danger
          else -> SextantColors.Success
        },
      )
      val savingRate = if (state.monthSummary.incomeCents <= 0) null else state.monthSummary.netCents.toDouble() / state.monthSummary.incomeCents
      ComparisonMetric("结余率", savingRate?.let { "${(it * 100).toInt()}%" } ?: "—")
    }
  }
}

@Composable
private fun ComparisonMetric(label: String, value: String, color: Color = SextantColors.Text) {
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(label, color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelMedium)
    Text(value, color = color, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun SpendingChart(points: List<DailySpending>) {
  val maxAmount = points.maxOfOrNull { it.amountCents }?.coerceAtLeast(1) ?: 1
  val peak = points.maxByOrNull { it.amountCents }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().height(132.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      points.forEach { point ->
        val ratio = point.amountCents.toFloat() / maxAmount
        Box(
          Modifier.weight(1f).height((6 + 126 * ratio).dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(if (point == peak) SextantColors.AccentStrong else SextantColors.AccentSoft),
        )
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("1日", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
      peak?.let { Text("最高 ${it.dayOfMonth}日 · ${formatCurrency(it.amountCents)}", color = SextantColors.AccentStrong, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall) }
      Text("${points.size}日", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun CategoryStructure(items: List<CategoryBreakdown>, total: Long) {
  Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
    items.forEach { item ->
      val ratio = if (total <= 0) 0f else item.amountCents.toFloat() / total
      val change = if (item.previousAmountCents <= 0) null else (item.amountCents - item.previousAmountCents).toDouble() / item.previousAmountCents
      val color = argbColor(item.color)
      Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).height(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(9.dp))
            Text(item.categoryName, fontWeight = FontWeight.SemiBold)
          }
          Text("${formatCurrency(item.amountCents)}  ·  ${(ratio * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(SextantColors.SurfaceStrong)) {
          Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(7.dp).clip(CircleShape).background(color))
        }
        Text(
          change?.let { "较上月${if (it >= 0) "增加" else "减少"} ${(abs(it) * 100).toInt()}%" } ?: "上月无同类支出",
          color = when {
            change == null -> SextantColors.TextSubtle
            change > 0 -> SextantColors.Danger
            else -> SextantColors.Success
          },
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun BudgetForecast(state: AppState) {
  val budget = state.monthlyBudgetCents
  AnalyticsSection("预算预测", "按当前消费速度估算月底结果") {
    if (budget == null) {
      EmptyCard("尚未设置总预算", "设置月度总预算后可查看月底支出预测。")
    } else {
      val projected = state.projectedExpenseCents
      val difference = budget - projected
      val riskColor = if (difference < 0) SextantColors.Danger else SextantColors.Success
      Column(
        modifier = Modifier.fillMaxWidth().background(SextantColors.SurfaceAlt, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("预计月底支出", color = SextantColors.TextMuted)
        Text(formatCurrency(projected), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
          if (difference < 0) "预计超出预算 ${formatCurrency(-difference)}" else "预计低于预算 ${formatCurrency(difference)}",
          color = riskColor,
          fontWeight = FontWeight.SemiBold,
        )
        val riskBudgets = state.budgets.sortedByDescending { if (it.amountCents <= 0) 0f else it.spentCents.toFloat() / it.amountCents }.take(3)
        if (riskBudgets.isNotEmpty()) {
          Spacer(Modifier.height(2.dp))
          Text("需要关注", fontWeight = FontWeight.SemiBold)
          riskBudgets.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(item.categoryName, color = SextantColors.TextMuted)
              Text("${((item.spentCents.toFloat() / item.amountCents.coerceAtLeast(1)) * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AnalyticsSection(title: String, subtitle: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(subtitle, color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
    }
    content()
  }
}

private fun monthInsight(state: AppState): String {
  val current = state.monthSummary.expenseCents
  val previous = state.previousMonthSummary.expenseCents
  if (current == 0L) return "记录支出后，这里会解释你的消费变化。"
  if (previous == 0L) return "这是第一个可分析的月份，继续记录以建立趋势。"
  val change = (current - previous).toDouble() / previous
  val direction = if (change >= 0) "增加" else "减少"
  val topCategory = state.categoryBreakdown.firstOrNull()?.categoryName
  return "本月支出较上月$direction ${abs(change * 100).toInt()}%${topCategory?.let { "，主要支出是$it" }.orEmpty()}。"
}
