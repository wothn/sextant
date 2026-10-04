package com.sextant.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sextant.app.data.CategoryBreakdown
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.data.AnalysisMetric
import com.sextant.app.ui.util.analysisMetricLabel
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.spendingShareLabel
import kotlin.math.atan2
import kotlin.math.hypot

/** 扇区保留真实占比，不为小分类强制扩大角度；列表提供完整名称和替代操作。 */
@Composable
fun ExpensePieChart(items: List<CategoryBreakdown>, total: Long, selectedId: String?, metric: AnalysisMetric = AnalysisMetric.Expense, onSelect: (String) -> Unit) {
  if (total <= 0 || items.isEmpty()) return
  fun identity(item: CategoryBreakdown) = item.memberCategoryIds.sorted().joinToString("|")
  val selected = items.firstOrNull { identity(it) == selectedId } ?: items.first()
  val colors = items.map { argbColor(it.color) }
  val selectedIndex = items.indexOf(selected)
  val surface = SextantColors.SurfaceContainerLow
  val fontScale = LocalDensity.current.fontScale
  SextantSurface(Modifier.fillMaxWidth(), color = surface) {
    BoxWithConstraints(Modifier.padding(20.dp)) {
      val stacked = maxWidth < 340.dp || fontScale > 1.3f
      val chart: @Composable () -> Unit = {
        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
          Canvas(Modifier.fillMaxSize()
            .pointerInput(items, total) {
              detectTapGestures { point ->
                val x = point.x - size.width / 2f; val y = point.y - size.height / 2f
                val radius = minOf(size.width, size.height) / 2f
                if (hypot(x, y) in (radius - 33.dp.toPx())..(radius - 3.dp.toPx())) {
                  val angle = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450) % 360).toFloat()
                  var end = 0f
                  val hit = items.indexOfFirst { end += (it.amountCents.toDouble() / total * 360).toFloat(); angle < end }
                  if (hit >= 0) onSelect(identity(items[hit]))
                }
              }
            }
            .clearAndSetSemantics {
              contentDescription = analysisMetricLabel(metric) + "分类占比饼图"
              stateDescription = selected.categoryName + "，" + spendingShareLabel(selected.amountCents, total)
              customActions = items.map { item ->
                CustomAccessibilityAction(item.categoryName + " " + spendingShareLabel(item.amountCents, total)) {
                  onSelect(identity(item)); true
                }
              }
            }) {
            val stroke = 30.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            var start = -90f
            items.forEachIndexed { index, item ->
              val sweep = (item.amountCents.toDouble() / total * 360).toFloat()
              drawArc(colors[index].copy(alpha = if (index == selectedIndex) 1f else .65f), start, sweep,
                false, Offset(inset, inset), arcSize, style = Stroke(stroke))
              // 分隔线不会改变扇区的统计角度。
              if (items.size > 1 && sweep > 2f) {
                drawArc(surface, start, .7f, false, Offset(inset, inset), arcSize, style = Stroke(stroke + 1.dp.toPx()))
              }
              start += sweep
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("占总" + analysisMetricLabel(metric), style = MaterialTheme.typography.labelSmall, color = SextantColors.TextSubtle)
            Text(spendingShareLabel(selected.amountCents, total), style = MaterialTheme.typography.titleLarge)
          }
        }
      }
      val detail: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorDot(argbColor(selected.color))
            Text(selected.categoryName, style = MaterialTheme.typography.titleMedium)
          }
          Text(formatCurrency(selected.amountCents), style = MaterialTheme.typography.titleLarge)
          Text(if (selectedIndex == 0) analysisMetricLabel(metric) + "最多的分类" else "已选分类", style = MaterialTheme.typography.bodySmall,
            color = SextantColors.TextMuted)
          Text("点击饼图查看占比", style = MaterialTheme.typography.bodySmall, color = SextantColors.TextSubtle)
        }
      }
      if (stacked) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)) { chart(); detail() }
      } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(24.dp)) {
          chart(); Box(Modifier.weight(1f)) { detail() }
        }
      }
    }
  }
}
