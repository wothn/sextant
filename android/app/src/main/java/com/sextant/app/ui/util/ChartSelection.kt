package com.sextant.app.ui.util

/** 按折线节点的等距坐标选择最近日期；图外拖动落到端点。 */
internal fun spendingChartIndex(x: Float, width: Float, count: Int): Int? {
  if (!x.isFinite() || !width.isFinite() || width <= 0f || count <= 0) return null
  return kotlin.math.round((x.coerceIn(0f, width) / width) * (count - 1)).toInt().coerceIn(0, count - 1)
}
