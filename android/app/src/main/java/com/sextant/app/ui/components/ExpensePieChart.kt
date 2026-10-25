package com.sextant.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.sextant.app.data.AnalysisMetric
import com.sextant.app.data.CategoryBreakdown
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.argbColor
import com.sextant.app.ui.util.analysisMetricLabel
import com.sextant.app.ui.util.calendarAmount
import com.sextant.app.ui.util.formatCompactCurrency
import com.sextant.app.ui.util.formatCurrency
import com.sextant.app.ui.util.pieLabelCenters
import com.sextant.app.ui.util.pieSliceAt
import com.sextant.app.ui.util.pieSlices
import com.sextant.app.ui.util.spendingShareLabel

/** 分类与占比沿引导线标注；中心默认显示总额，选中分类后显示该分类金额。 */
@Composable
fun ExpensePieChart(
  items: List<CategoryBreakdown>,
  total: Long,
  selectedId: String?,
  metric: AnalysisMetric = AnalysisMetric.Expense,
  onSelect: (String?) -> Unit,
) {
  if (total <= 0 || items.isEmpty()) return
  fun identity(item: CategoryBreakdown) = item.memberCategoryIds.sorted().joinToString("|")
  val selected = items.firstOrNull { identity(it) == selectedId }
  fun select(index: Int) {
    val id = identity(items[index])
    onSelect(if (id == selectedId) null else id)
  }
  val selectedIndex = selected?.let(items::indexOf) ?: -1
  val slices = pieSlices(items.map { it.amountCents }, total)
  val colors = sliceColors(items)
  val shares = items.map { spendingShareLabel(it.amountCents, total) }
  val surface = SextantColors.SurfaceContainerLow
  val textColor = SextantColors.Text
  val subtle = SextantColors.TextSubtle
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val nameStyle = MaterialTheme.typography.labelMedium
  val shareStyle = MaterialTheme.typography.labelSmall
  val amountStyle = MaterialTheme.typography.titleLarge
  val metricLabel = analysisMetricLabel(metric)
  val centerLabel = selected?.categoryName ?: "总$metricLabel"
  val amount = selected?.amountCents ?: total
  val exactAmount = formatCurrency(amount)

  SextantSurface(Modifier.fillMaxWidth(), color = surface) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
      val availableWidth = maxWidth
      // 标签占两侧，宽屏限制圆环大小；行高随文字测量，分类密集时增加图表高度。
      val shareWidth = with(density) { shares.maxOf { measurer.measure(it, style = shareStyle).size.width }.toDp() }
      val labelWidth = maxOf((availableWidth * .2f).coerceAtMost(112.dp), shareWidth + 4.dp)
      val diameter = (availableWidth - (labelWidth + 12.dp) * 2).coerceAtMost(240.dp)
      val radius = with(density) { diameter.toPx() / 2 }
      val stroke = radius * .28f
      val widthPx = with(density) { availableWidth.toPx() }
      val labelWidthPx = with(density) { labelWidth.roundToPx() }
      val nameHeight = items.maxOf {
        measurer.measure(it.categoryName, style = nameStyle.copy(fontWeight = FontWeight.Bold),
          constraints = Constraints(maxWidth = labelWidthPx), maxLines = 2, overflow = TextOverflow.Ellipsis).size.height
      }
      val shareHeight = shares.maxOf { measurer.measure(it, style = shareStyle).size.height }
      val rowHeight = maxOf(with(density) { 48.dp.toPx() }, nameHeight + shareHeight + with(density) { 8.dp.toPx() })
      val sideCount = maxOf(slices.count { it.isRight }, slices.count { !it.isRight })
      val heightPx = maxOf(radius * 2 + with(density) { 24.dp.toPx() }, sideCount * rowHeight)
      val center = Offset(widthPx / 2, heightPx / 2)
      val labelCenters = mutableMapOf<Int, Float>()
      listOf(false, true).forEach { right ->
        val side = slices.filter { it.isRight == right }
        val centers = pieLabelCenters(side.map { center.y + it.y * (radius + with(density) { 6.dp.toPx() }) },
          rowHeight / 2, heightPx - rowHeight / 2, rowHeight)
        side.forEachIndexed { index, slice -> labelCenters[slice.index] = centers[index] }
      }
      val centerWidth = diameter * .66f
      val compact = measurer.measure(exactAmount, style = amountStyle).size.width > with(density) { centerWidth.toPx() }
      val centerAmount = if (compact) formatCompactCurrency(amount) else exactAmount
      val centerAmountStyle = if (measurer.measure(centerAmount, style = amountStyle).size.width > with(density) { centerWidth.toPx() })
        MaterialTheme.typography.titleMedium else amountStyle
      val centerLabelStyle = MaterialTheme.typography.labelSmall
      val centerTextHeight = measurer.measure(centerLabel, style = centerLabelStyle, maxLines = 1).size.height +
        measurer.measure(centerAmount, style = centerAmountStyle).size.height
      val showCenterLabel = centerTextHeight <= with(density) { (diameter * .52f).toPx() }

      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(with(density) { heightPx.toDp() }), contentAlignment = AbsoluteAlignment.TopLeft) {
          Canvas(Modifier.fillMaxSize()
            .pointerInput(items, total, selectedId, diameter, heightPx) {
              detectTapGestures { point ->
                pieSliceAt(point.x - center.x, point.y - center.y, radius, stroke, slices)?.let(::select)
              }
            }
            .clearAndSetSemantics {
              contentDescription = "$metricLabel 分类占比饼图"
              stateDescription = "$centerLabel，$exactAmount" + (selected?.let { "，" + spendingShareLabel(it.amountCents, total) } ?: "")
              customActions = slices.map { slice ->
                CustomAccessibilityAction(items[slice.index].categoryName + " " + shares[slice.index]) {
                  select(slice.index); true
                }
              } + if (selected != null) listOf(CustomAccessibilityAction("显示总$metricLabel") { onSelect(null); true }) else emptyList()
            }) {
            val arcRadius = radius - stroke / 2
            val arcOffset = center - Offset(arcRadius, arcRadius)
            val arcSize = Size(arcRadius * 2, arcRadius * 2)
            slices.forEach { slice ->
              val active = slice.index == selectedIndex
              val color = colors[slice.index].copy(alpha = if (selected == null || active) 1f else .45f)
              drawArc(color, slice.start, slice.sweep, false, arcOffset, arcSize,
                style = Stroke(stroke + if (active) 3.dp.toPx() else 0f))
              // 分隔线覆盖绘制，不改变扇区占比和点击角度。
              if (slices.size > 1 && slice.sweep > 2f) {
                drawArc(surface, slice.start, .7f, false, arcOffset, arcSize, style = Stroke(stroke + 4.dp.toPx()))
              }
              val direction = Offset(slice.x, slice.y)
              val anchor = center + direction * (radius + if (active) 2.dp.toPx() else 0f)
              val radialEnd = center + direction * (radius + 6.dp.toPx())
              val labelY = labelCenters.getValue(slice.index)
              val sign = if (slice.isRight) 1 else -1
              // 先横向离开圆环，再连接错开的标签，避免密集标签的引导线穿过扇区。
              val sideExit = Offset(center.x + sign * (radius + 4.dp.toPx()), radialEnd.y)
              val elbow = Offset(center.x + sign * (radius + 8.dp.toPx()), labelY)
              val end = Offset(if (slice.isRight) size.width - labelWidth.toPx() else labelWidth.toPx(), labelY)
              val lineColor = colors[slice.index].copy(alpha = if (active) 1f else .65f)
              val lineWidth = if (active) 1.5.dp.toPx() else 1.dp.toPx()
              drawLine(lineColor, anchor, radialEnd, lineWidth, StrokeCap.Round)
              drawLine(lineColor, radialEnd, sideExit, lineWidth, StrokeCap.Round)
              drawLine(lineColor, sideExit, elbow, lineWidth, StrokeCap.Round)
              drawLine(lineColor, elbow, end, lineWidth, StrokeCap.Round)
              if (active) drawCircle(colors[slice.index], 2.5.dp.toPx(), anchor)
            }
          }
          slices.forEach { slice ->
            val item = items[slice.index]
            val active = slice.index == selectedIndex
            Column(
              Modifier.absoluteOffset(
                x = if (slice.isRight) availableWidth - labelWidth else 0.dp,
                y = with(density) { (labelCenters.getValue(slice.index) - rowHeight / 2).toDp() },
              ).width(labelWidth).height(with(density) { rowHeight.toDp() })
                .selectable(
                  selected = active,
                  interactionSource = null,
                  indication = null,
                  role = Role.Button,
                  onClick = { select(slice.index) },
                )
                .semantics { contentDescription = "${item.categoryName}，${shares[slice.index]}，${formatCurrency(item.amountCents)}" },
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = if (slice.isRight) Alignment.Start else Alignment.End,
            ) {
              val textAlign = if (slice.isRight) TextAlign.Start else TextAlign.End
              Text(item.categoryName, Modifier.fillMaxWidth(), style = nameStyle,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = textColor,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
              Text(shares[slice.index], Modifier.fillMaxWidth(), style = shareStyle, color = subtle,
                maxLines = 1, textAlign = textAlign)
            }
          }
          Column(
            Modifier.absoluteOffset(x = (availableWidth - centerWidth) / 2,
              y = with(density) { center.y.toDp() } - diameter * .26f)
              .width(centerWidth).height(diameter * .52f)
              .clickable(
                interactionSource = null,
                indication = null,
                enabled = selected != null,
                onClickLabel = "显示总$metricLabel",
              ) { onSelect(null) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            if (showCenterLabel) {
              Text(centerLabel, style = centerLabelStyle, color = subtle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(centerAmount, style = centerAmountStyle, color = textColor, textAlign = TextAlign.Center)
          }
        }
        // 极长金额或大字体下圆心用单位简写，精确金额仍完整展示，不截断金额。
        if (compact || !showCenterLabel) {
          Text("$centerLabel · $exactAmount", style = MaterialTheme.typography.titleMedium,
            color = textColor, textAlign = TextAlign.Center)
        }
        if (items.size > 1) {
          Text(if (selected == null) "点击扇区或标签查看金额" else "再次点击已选分类或圆心查看总额",
            style = MaterialTheme.typography.bodySmall, color = subtle, textAlign = TextAlign.Center)
        }
      }
    }
  }
}

/**
 * 分类色在种子数据里可能重复（生活服务 / 兼职 / 储蓄都是同一个青色）。同色扇区挨在一起
 * 会读成一块，所以遇到已用颜色时把色相推开 29°，既保留分类原色的识别度，又保证相邻扇区可分。
 */
private fun sliceColors(items: List<com.sextant.app.data.CategoryBreakdown>): List<Color> {
  val used = HashSet<Int>()
  val hsv = FloatArray(3)
  return items.map { item ->
    val base = argbColor(item.color)
    var color = base
    var shift = 0
    while (!used.add(color.toArgb()) && shift < 12) {
      shift++
      android.graphics.Color.colorToHSV(base.toArgb(), hsv)
      hsv[0] = (hsv[0] + 29f * shift) % 360f
      color = Color(android.graphics.Color.HSVToColor(hsv))
    }
    color
  }
}
