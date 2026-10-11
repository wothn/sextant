package com.sextant.app.ui.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class PieSlice(val index: Int, val start: Float, val sweep: Float) {
  private val midpoint get() = Math.toRadians((start + sweep / 2).toDouble())
  val x get() = cos(midpoint).toFloat()
  val y get() = sin(midpoint).toFloat()
  val isRight get() = x >= 0f
}

/** 绘制、引导线和命中检测共用真实扇区角度，不放大小占比。 */
internal fun pieSlices(amounts: List<Long>, total: Long): List<PieSlice> {
  if (total <= 0) return emptyList()
  var start = -90f
  return amounts.mapIndexed { index, amount ->
    val sweep = (amount.coerceAtLeast(0).toDouble() / total * 360).toFloat()
    PieSlice(index, start, sweep).also { start += sweep }
  }.filter { it.sweep > 0f }
}

/** 坐标相对圆心；圆环之外及空心区域不选择分类。 */
internal fun pieSliceAt(x: Float, y: Float, outerRadius: Float, stroke: Float, slices: List<PieSlice>): Int? {
  if (!x.isFinite() || !y.isFinite() || outerRadius <= 0f || stroke <= 0f) return null
  if (hypot(x, y) !in (outerRadius - stroke)..outerRadius) return null
  val angle = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450) % 360).toFloat() - 90f
  return slices.firstOrNull { angle >= it.start && angle < it.start + it.sweep }?.index
}

/** 每侧按锚点从上到下排列，碰撞时向下推，再从底部回推，保持顺序及完整点击范围。 */
internal fun pieLabelCenters(anchors: List<Float>, top: Float, bottom: Float, spacing: Float): List<Float> {
  if (anchors.isEmpty()) return emptyList()
  require(spacing > 0f && bottom - top + .01f >= (anchors.size - 1) * spacing)
  val order = anchors.indices.sortedBy { anchors[it] }
  val positions = FloatArray(anchors.size)
  order.forEachIndexed { rank, index ->
    positions[rank] = maxOf(anchors[index].coerceIn(top, bottom), if (rank == 0) top else positions[rank - 1] + spacing)
  }
  positions[positions.lastIndex] = positions.last().coerceAtMost(bottom)
  for (rank in positions.lastIndex - 1 downTo 0) {
    positions[rank] = minOf(positions[rank], positions[rank + 1] - spacing)
  }
  val result = FloatArray(anchors.size)
  order.forEachIndexed { rank, index -> result[index] = positions[rank] }
  return result.toList()
}
