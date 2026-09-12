package com.sextant.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.sextant.app.ui.theme.SextantMotion

/**
 * 动效包装层。
 *
 * 只放「怎么动」，不放任何颜色、圆角或尺寸 token，因此这个文件不参与样式冻结守卫：
 * 它不会让调用方绕过主题。Expressive 的动效原则是——位移用带过冲的空间弹簧，
 * 颜色与透明度用临界阻尼的效果弹簧，反馈要短、要在手指上，不要循环装饰。
 */

/**
 * 主页面切换：新页面从右侧滑入、旧页面淡出。
 *
 * 用 `AnimatedContent` + 弹簧而不是默认的淡入淡出，因为底部导航的切换是有方向的
 * ——用户按的是「更右」还是「更左」的标签，位移把这个方向说清楚。
 */
@Composable
fun SextantTabContent(
  selectedIndex: Int,
  modifier: Modifier = Modifier,
  content: @Composable (Int) -> Unit,
) {
  // 弹簧来自 CompositionLocal，而 transitionSpec 不是 composable lambda，
  // 所以先把值取出来再传进去。
  val spatialSpec = SextantMotion.defaultSpatial<IntOffset>()
  val effectsSpec = SextantMotion.defaultEffects<Float>()
  AnimatedContent(
    targetState = selectedIndex,
    modifier = modifier,
    transitionSpec = {
      val forward = targetState > initialState
      val enter = slideInHorizontally(
        animationSpec = spatialSpec,
        initialOffsetX = { width -> if (forward) width / 6 else -width / 6 },
      ) + fadeIn(animationSpec = effectsSpec)
      val exit = slideOutHorizontally(
        animationSpec = spatialSpec,
        targetOffsetX = { width -> if (forward) -width / 8 else width / 8 },
      ) + fadeOut(animationSpec = effectsSpec)
      enter togetherWith exit
    },
    label = "tabContent",
  ) { index ->
    content(index)
  }
}