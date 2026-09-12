package com.sextant.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import com.sextant.app.ui.components.SextantTextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sextant.app.data.AppState
import com.sextant.app.data.CategoryBreakdown
import com.sextant.app.data.DailySpending
import com.sextant.app.ui.components.EmptyCard
import com.sextant.app.ui.components.SextantFlexibleHeader
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.components.SextantSurface
import com.sextant.app.ui.components.SextantHeading
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantBarShape
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.isCurrentMonth
import com.sextant.app.ui.util.monthLabel
import kotlin.math.abs

@Composable
fun AnalyticsScreen(state: AppState, monthStart: Long) {
  val listState = rememberLazyListState()
  Column(Modifier.fillMaxSize()) {
    SextantFlexibleHeader(
      title = if (isCurrentMonth(monthStart)) "本月分析" else "${monthLabel(monthStart)}分析",
      scrollState = listState,
      subtitle = monthInsight(state),
      modifier = Modifier.padding(horizontal = 20.dp),
    )
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
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
            CategoryStructure(state.categoryBreakdown, state.monthSummary.expenseCents)
          }
        }
      }
      item { BudgetForecast(state) }
    }
  }
}

@Composable
private fun MonthComparison(state: AppState) {
  val current = state.monthSummary.expenseCents
  val previous = state.previousMonthSummary.expenseCents
  val change = if (previous <= 0) null else (current - previous).toDouble() / previous
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("本月支出", color = SextantColors.TextMuted, fontWeight = FontWeight.SemiBold)
    Text(formatCurrency(current), style = MaterialTheme.typography.headlineLarge)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
  var selectedPoint by remember(points) { mutableStateOf(peak) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${selectedPoint?.dayOfMonth ?: peak?.dayOfMonth}日支出", color = SextantColors.TextMuted, style = MaterialTheme.typography.labelMedium)
        Text(formatCurrency(selectedPoint?.amountCents ?: 0), fontSize = 22.sp, fontWeight = FontWeight.Bold)
      }
      peak?.let {
        Text("峰值 ${it.dayOfMonth}日", color = SextantColors.AccentStrong, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
      }
    }
    Row(Modifier.fillMaxWidth().height(138.dp)) {
      Column(
        modifier = Modifier.width(58.dp).height(132.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
      ) {
        Text(compactAmount(maxAmount), color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelSmall)
        Text(compactAmount(maxAmount / 2), color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelSmall)
        Text("¥0", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelSmall)
      }
      Spacer(Modifier.width(8.dp))
      Box(Modifier.weight(1f).height(132.dp)) {
        Canvas(Modifier.matchParentSize()) {
          listOf(0f, size.height / 2f, size.height - 1f).forEach { y ->
            drawLine(SextantColors.SurfaceContainerHighest, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
          }
        }
        Row(
          modifier = Modifier.matchParentSize(),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          points.forEach { point ->
            val ratio = point.amountCents.toFloat() / maxAmount
            val selected = point.dayOfMonth == selectedPoint?.dayOfMonth
            Box(
              Modifier
                .weight(1f)
                .height((3 + 126 * ratio).dp)
                .clip(SextantBarShape)
                .background(if (selected) SextantColors.AccentStrong else SextantColors.AccentSoft)
                .semantics {
                  contentDescription = "${point.dayOfMonth}日，支出${formatCurrency(point.amountCents)}"
                  this.selected = selected
                }
                .clickable { selectedPoint = point },
            )
          }
        }
      }
    }
    val selectedIndex = points.indexOf(selectedPoint)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SextantTextButton(enabled = selectedIndex > 0, onClick = { selectedPoint = points[selectedIndex - 1] }) { Text("前一天") }
      SextantTextButton(enabled = selectedIndex >= 0 && selectedIndex < points.lastIndex, onClick = { selectedPoint = points[selectedIndex + 1] }) { Text("后一天") }
    }
    Row(Modifier.fillMaxWidth().padding(start = 66.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("1日", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
      Text("${(points.size + 1) / 2}日", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
      Text("${points.size}日", color = SextantColors.TextSubtle, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun CategoryStructure(items: List<CategoryBreakdown>, total: Long) {
  val leading = items.take(4)
  val otherAmount = items.drop(4).sumOf { it.amountCents }
  val slices = buildList {
    leading.forEach { add(CategorySlice(it.categoryName, it.amountCents, argbColor(it.color))) }
    if (otherAmount > 0) add(CategorySlice("其他", otherAmount, SextantColors.TextSubtle))
  }
  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    CategoryDonut(slices, total)
    items.take(5).forEach { item ->
      val ratio = if (total <= 0) 0f else item.amountCents.toFloat() / total
      val change = if (item.previousAmountCents <= 0) null else (item.amountCents - item.previousAmountCents).toDouble() / item.previousAmountCents
      val color = argbColor(item.color)
      Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).height(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(9.dp))
            Text(item.categoryName, fontWeight = FontWeight.SemiBold)
          }
          Text("${formatCurrency(item.amountCents)}  ·  ${(ratio * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(SextantColors.SurfaceContainerHighest)) {
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
private fun CategoryDonut(slices: List<CategorySlice>, total: Long) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
      Canvas(Modifier.matchParentSize().padding(7.dp)) {
        val stroke = 22.dp.toPx()
        drawArc(
          color = SextantColors.SurfaceContainerHighest,
          startAngle = -90f,
          sweepAngle = 360f,
          useCenter = false,
          style = Stroke(stroke),
        )
        var startAngle = -90f
        slices.forEach { slice ->
          val sweep = if (total <= 0) 0f else slice.amountCents.toFloat() / total * 360f
          val gap = if (sweep > 4f) 2f else 0f
          drawArc(
            color = slice.color,
            startAngle = startAngle + gap / 2f,
            sweepAngle = (sweep - gap).coerceAtLeast(0f),
            useCenter = false,
            style = Stroke(stroke),
          )
          startAngle += sweep
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("总支出", color = SextantColors.TextSubtle, style = MaterialTheme.typography.labelSmall)
        Text(compactAmount(total), fontWeight = FontWeight.Bold, fontSize = 18.sp)
      }
    }
    Spacer(Modifier.width(18.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      slices.forEach { slice ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(8.dp).clip(CircleShape).background(slice.color))
          Spacer(Modifier.width(8.dp))
          Text(slice.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
          Text(
            "${if (total <= 0) 0 else (slice.amountCents * 100 / total)}%",
            color = SextantColors.TextMuted,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}

private data class CategorySlice(val label: String, val amountCents: Long, val color: Color)

private fun compactAmount(amountCents: Long): String {
  val yuan = amountCents / 100.0
  return when {
    yuan >= 10_000 -> "¥${"%.1f".format(yuan / 10_000)}万"
    yuan >= 1_000 -> "¥${"%.1f".format(yuan / 1_000)}k"
    else -> "¥${yuan.toInt()}"
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
      SextantSurface(modifier = Modifier.fillMaxWidth(), color = SextantColors.SurfaceContainer) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("预计月底支出", color = SextantColors.TextMuted)
          Text(formatCurrency(projected), style = MaterialTheme.typography.headlineSmall)
          Text(
            if (difference < 0) "预计超出预算 ${formatCurrency(-difference)}" else "预计低于预算 ${formatCurrency(difference)}",
            color = riskColor,
            style = MaterialTheme.typography.titleSmall,
          )
          val riskBudgets = state.budgets.sortedByDescending { if (it.amountCents <= 0) 0f else it.spentCents.toFloat() / it.amountCents }.take(3)
          if (riskBudgets.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            SextantHeading("需要关注")
            riskBudgets.forEach { item ->
              FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.categoryName, color = SextantColors.TextMuted)
                Text(
                  "${((item.spentCents.toFloat() / item.amountCents.coerceAtLeast(1)) * 100).toInt()}%",
                  style = MaterialTheme.typography.titleSmall,
                )
              }
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
      SextantHeading(title)
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
