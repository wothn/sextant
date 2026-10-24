package com.sextant.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.theme.SextantColors
import com.sextant.app.ui.theme.SextantMotion

/** 小尺寸的品牌签名，按当前密度用矢量绘制。 */
@Composable
fun SextantMark(modifier: Modifier = Modifier) {
  val color = SextantColors.Accent
  Canvas(modifier.size(24.dp)) {
    val stroke = 1.5.dp.toPx()
    drawArc(color, 25f, 130f, false, Offset(size.width * .1f, 0f),
      Size(size.width * .8f, size.height * .85f), style = Stroke(stroke))
    val pivot = Offset(size.width * .5f, size.height * .15f)
    drawLine(color, pivot, Offset(size.width * .2f, size.height * .86f), stroke)
    drawLine(color, pivot, Offset(size.width * .8f, size.height * .86f), stroke)
    drawCircle(color, stroke, pivot)
  }
}

/**
 * 预算刻度：四分之一刻度对应 25%、50%、75%，填充限制在 0–100%。
 *
 * 改造后仍然是「有含义的刻度 + 真实比例」，但换成了 expressive 的形体：轨道加粗到 8dp、
 * 两端圆头、填充用空间弹簧生长（数值变化时看得出方向），刻度改成轨道上的缺口而不是压在
 * 填充上的实线——原来那条线在填充色和轨道色接近时几乎看不见。
 *
 * 轨道与填充的端点都内缩一个圆头半径，可见范围正好落在画布的 `[0, width]` 内：与上方正文
 * 共用同一条 20dp 边距，也保证填充和刻度都按可见长度计算，而不是按画布长度。
 *
 * 超预算的真实数值仍然由周边文字表达，不会因为图形截满 100% 就隐藏超额信息。
 */
@Composable
fun BudgetScale(
  progress: Float,
  color: Color,
  modifier: Modifier = Modifier,
  notchColor: Color = SextantColors.Background,
) {
  val track = SextantColors.SurfaceContainerHighest
  val fraction = progress.coerceIn(0f, 1f)
  val animated by animateFloatAsState(
    targetValue = fraction,
    // 进度是「数值」不是「位置」：用临界阻尼的效果弹簧，避免 0.8 阻尼的空间弹簧
    // 在 100% 附近冲过头再弹回来，看起来像超额。
    animationSpec = SextantMotion.defaultEffects(),
    label = "budgetScale",
  )
  Canvas(modifier.fillMaxWidth().height(14.dp).semantics {
    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
    contentDescription = "预算使用进度"
  }) {
    val thickness = 8.dp.toPx()
    // 圆头会从线段端点再向外画出半个线宽。轨道两端各内缩一个半径，可见范围才正好是画布的
    // [0, width]——否则进度条会比同一列 20dp 边距的正文左右各宽出 4dp，成为页面上唯一
    // 撑破边距的元素（实测左端比「本月预算进度」的左边还多出 4dp）。
    val cap = thickness / 2f
    val centerY = cap
    val trackEnd = (size.width - cap).coerceAtLeast(cap)
    drawLine(
      color = track,
      start = Offset(cap, centerY),
      end = Offset(trackEnd, centerY),
      strokeWidth = thickness,
      cap = StrokeCap.Round,
    )
    if (animated > 0f) {
      // 填充同样内缩：可见长度按 [0, width] 映射，右端圆头不再多长出一截，
      // 100% 时与轨道完全重合，刻度缺口也就落在真实的 25/50/75% 上。
      val fillEnd = (size.width * animated - cap).coerceAtLeast(cap)
      drawLine(
        color = color,
        start = Offset(cap, centerY),
        end = Offset(fillEnd, centerY),
        strokeWidth = thickness,
        cap = StrokeCap.Round,
      )
    }
    // 刻度缺口直接切穿轨道，指向真实的 25/50/75%。放在面板内时，
    // 调用方传入承载表面的 notchColor，保证缺口与背景一致。
    for (index in 1..3) {
      val x = size.width * index / 4
      drawLine(
        color = notchColor,
        start = Offset(x, centerY - thickness / 2f),
        end = Offset(x, centerY + thickness / 2f),
        strokeWidth = 2.dp.toPx(),
      )
    }
  }
}
