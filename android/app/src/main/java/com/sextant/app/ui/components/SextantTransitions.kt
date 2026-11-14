package com.sextant.app.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sextant.app.ui.theme.SextantMotion
import kotlinx.coroutines.flow.collect

/**
 * 动效包装层。
 *
 * 只放「怎么动」，不放任何颜色、圆角或尺寸 token。Expressive 的动效原则是——位移用空间弹簧，
 * 颜色与透明度用临界阻尼的效果弹簧，反馈要短、要在手指上，不要循环装饰。
 */

/**
 * 主页面切换：按底栏标签顺序，新旧页面沿水平方向同步平移。
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
  AnimatedContent(
    targetState = selectedIndex,
    modifier = modifier.clipToBounds(),
    transitionSpec = {
      val direction = if (targetState > initialState) {
        AnimatedContentTransitionScope.SlideDirection.Left
      } else {
        AnimatedContentTransitionScope.SlideDirection.Right
      }
      slideIntoContainer(direction, animationSpec = spatialSpec) togetherWith
        slideOutOfContainer(direction, animationSpec = spatialSpec)
    },
    label = "tabContent",
  ) { index ->
    content(index)
  }
}

/** 子页面与面板共用层级方向；内容必须按传入的目标绘制，才能保留离场页面。 */
@Composable
fun <T> SextantPageContent(
  targetState: T,
  depth: (T) -> Int,
  modifier: Modifier = Modifier,
  content: @Composable (T) -> Unit,
) {
  val spatial = SextantMotion.defaultSpatial<IntOffset>()
  val size = SextantMotion.defaultSpatial<IntSize>()
  val effects = SextantMotion.fastEffects<Float>()
  AnimatedContent(
    targetState = targetState,
    modifier = modifier,
    transitionSpec = {
      val direction = if (depth(targetState) >= depth(initialState)) 1 else -1
      (slideInHorizontally(spatial) { it / 8 * direction } + fadeIn(effects) togetherWith
        slideOutHorizontally(spatial) { -it / 12 * direction } + fadeOut(effects))
        .using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> size }))
    },
    label = "pageContent",
    content = { content(it) },
  )
}

/**
 * 返回手势：把系统预测性返回的进度流交给平台，手势完成后再切页面。
 *
 * 比 `BackHandler` 多的是「手势还没松手时系统就能把上一页露出来」；取消手势不会触发跳转。
 * 页面状态本身是手写的（没有 Navigation），所以这里只做完成后的状态切换，不做跟手位移。
 */
@Composable
fun SextantPredictiveBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
  PredictiveBackHandler(enabled = enabled) { progress ->
    progress.collect { }
    onBack()
  }
}
