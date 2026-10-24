package com.sextant.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.ExpenseTrendPoint
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.util.calendarAmount
import com.sextant.app.ui.util.expensePointLabel
import com.sextant.app.data.AnalysisMetric
import com.sextant.app.ui.util.analysisMetricLabel
import com.sextant.app.ui.util.formatCompactCurrency
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.spendingChartIndex
import java.time.LocalDate

private val weekdayShortLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/** 折线点落在真实日期上；直线连接避免曲线插值制造不存在的峰谷。 */
@Composable
fun SpendingTrendChart(allPoints: List<ExpenseTrendPoint>, period: AnalysisPeriod, metric: AnalysisMetric = AnalysisMetric.Expense) {
  if (allPoints.isEmpty()) return
  val today = LocalDate.now()
  // 当前周期只画截至今天的趋势，避免未来空白把真实变化挤在图表一角。
  val points = allPoints.take(allPoints.indexOfLast { it.date <= today || it.amountCents > 0 }.coerceAtLeast(0) + 1)
  val lastRecorded = points.lastIndex
  var selection by rememberSaveable(points.first().date.toString(), period, metric) { mutableStateOf<Int?>(null) }
  var focused by remember { mutableStateOf(false) }
  val selected = (selection ?: points.take(lastRecorded + 1).indices.maxBy { points[it].amountCents }).coerceIn(0, points.lastIndex)
  val point = points[selected]
  val dateLabel = expensePointLabel(point.date, period)
  val maxAmount = points.maxOf { it.amountCents }.coerceAtLeast(1)
  val accent = SextantColors.AccentStrong
  val fill = SextantColors.AccentSoft
  val grid = SextantColors.SurfaceContainerHighest
  val surface = SextantColors.SurfaceContainerLow
  val axisStyle = MaterialTheme.typography.labelSmall
  val labels = listOf(formatCompactCurrency(maxAmount), formatCompactCurrency(maxAmount / 2), "¥0")
  val measurer = rememberTextMeasurer()
  val axisWidth = with(LocalDensity.current) { labels.maxOf { measurer.measure(it, style = axisStyle).size.width }.toDp() }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
      verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
      Text(dateLabel, style = MaterialTheme.typography.labelLarge, color = SextantColors.TextMuted)
      Text(formatCurrency(point.amountCents), style = MaterialTheme.typography.titleLarge)
    }
    Row(Modifier.fillMaxWidth().height(160.dp)) {
      Column(Modifier.width(axisWidth).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End) {
        labels.forEach { Text(it, style = axisStyle, color = SextantColors.TextSubtle, maxLines = 1, softWrap = false) }
      }
      Spacer(Modifier.width(12.dp))
      Box(Modifier.weight(1f).fillMaxHeight()
        .pointerInput(points.size) {
          detectTapGestures { pos -> spendingChartIndex(pos.x, size.width.toFloat(), points.size)?.let { selection = it } }
        }
        .pointerInput(points.size) {
          detectHorizontalDragGestures(onDragStart = { pos ->
            spendingChartIndex(pos.x, size.width.toFloat(), points.size)?.let { selection = it }
          }, onHorizontalDrag = { change, _ ->
            change.consume()
            spendingChartIndex(change.position.x, size.width.toFloat(), points.size)?.let { selection = it }
          })
        }
        .onKeyEvent { event ->
          val next = when {
            event.type != KeyEventType.KeyDown -> null
            event.key == Key.DirectionLeft -> selected - 1
            event.key == Key.DirectionRight -> selected + 1
            else -> null
          }
          if (next == null) false else { selection = next.coerceIn(0, points.lastIndex); true }
        }
        .onFocusChanged { focused = it.isFocused }
        .then(
          if (focused) Modifier.border(2.dp, SextantColors.Accent, RoundedCornerShape(8.dp))
          else Modifier,
        )
        .focusable()
        .clearAndSetSemantics {
          contentDescription = analysisMetricLabel(metric) + "趋势折线图，单位元"
          stateDescription = dateLabel + "，" + formatCurrency(point.amountCents)
          progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..points.lastIndex.toFloat(), (points.size - 2).coerceAtLeast(0))
          setProgress { value ->
            if (!value.isFinite()) false else { selection = value.toInt().coerceIn(0, points.lastIndex); true }
          }
          customActions = buildList {
            if (selected > 0) add(CustomAccessibilityAction("上一个数据点") { selection = selected - 1; true })
            if (selected < points.lastIndex) add(CustomAccessibilityAction("下一个数据点") { selection = selected + 1; true })
          }
        }) {
        Canvas(Modifier.matchParentSize()) {
          val top = 6.dp.toPx()
          val bottom = size.height - 6.dp.toPx()
          fun position(index: Int) = Offset(size.width * index / (points.size - 1).coerceAtLeast(1),
            bottom - (points[index].amountCents.toDouble() / maxAmount * (bottom - top)).toFloat())
          listOf(top, (top + bottom) / 2, bottom).forEach { y ->
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
          }
          val line = Path().apply {
            val first = position(0); moveTo(first.x, first.y)
            for (index in 1..lastRecorded) { val p = position(index); lineTo(p.x, p.y) }
          }
          val area = Path().apply {
            addPath(line)
            lineTo(position(lastRecorded).x, bottom); lineTo(0f, bottom); close()
          }
          drawPath(area, Brush.verticalGradient(listOf(fill.copy(alpha = .75f), fill.copy(alpha = 0f)), top, bottom))
          drawPath(line, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
          val marker = position(selected)
          drawLine(grid, Offset(marker.x, top), Offset(marker.x, bottom), strokeWidth = 1.dp.toPx())
          if (selected <= lastRecorded) {
            drawCircle(surface, 5.dp.toPx(), marker)
            drawCircle(accent, 3.5.dp.toPx(), marker)
          }
        }
      }
    }
    Row(Modifier.fillMaxWidth().padding(start = axisWidth + 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      val ticks = when (period) {
        // 周视图通常 7 个点；跨周边界时可能多出一天，标签按真实日期取星期，
        // 并且超过 7 个点时只标首/中/尾，避免挤成一排。
        AnalysisPeriod.Week ->
          if (points.size <= 7) points.indices.toList()
          else listOf(0, points.size / 2, points.lastIndex).distinct()
        else -> listOf(0, points.size / 2, points.lastIndex).distinct()
      }
      ticks.forEach { index ->
        val text = when (period) {
          AnalysisPeriod.Week -> weekdayShortLabels[points[index].date.dayOfWeek.value - 1]
          AnalysisPeriod.Month -> points[index].date.dayOfMonth.toString() + "日"
          AnalysisPeriod.Year -> points[index].date.monthValue.toString() + "月"
        }
        Text(text, style = axisStyle, color = SextantColors.TextSubtle)
      }
    }
    Text(if (point.date > today && point.amountCents == 0L) "未到日期，尚无" + analysisMetricLabel(metric) + "记录" else "点按或横向滑动查看金额",
      style = MaterialTheme.typography.bodySmall, color = SextantColors.TextSubtle)
  }
}
