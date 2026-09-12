package com.sextant.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sextant.app.ui.theme.SextantMotion
import kotlinx.coroutines.delay

/**
 * 动效包装层。
 *
 * 只放「怎么动」，不放任何颜色、圆角或尺寸 token，因此这个文件不参与样式冻结守卫：
 * 它不会让调用方绕过主题。Expressive 的动效原则是——位移用带过冲的空间弹簧，
 * 颜色与透明度用临界阻尼的效果弹簧，反馈要短、要在手指上，不要循环装饰。
 */

/**
 * 列表入场：每一项按顺序延迟出现，位移用空间弹簧，透明度用效果弹簧。
 *
 * `revealed` 用 `rememberSaveable` 记住：`LazyColumn` 里的项滚出屏幕会被回收，
 * 不记住的话往回滚一次就重播一次入场，列表会一直闪。
 */
@Composable
fun SextantRevealItem(
  index: Int,
  modifier: Modifier = Modifier,
  staggerMillis: Long = 36L,
  content: @Composable () -> Unit,
) {
  var revealed by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(index.coerceAtMost(10) * staggerMillis)
    revealed = true
  }
  val alpha by animateFloatAsState(
    targetValue = if (revealed) 1f else 0f,
    animationSpec = SextantMotion.defaultEffects(),
    label = "revealAlpha",
  )
  val rise by animateDpAsState(
    targetValue = if (revealed) 0.dp else 14.dp,
    animationSpec = SextantMotion.defaultSpatial(),
    label = "revealRise",
  )
  Box(modifier.alpha(alpha).offset(y = rise)) { content() }
}

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
  AnimatedContent(
    targetState = selectedIndex,
    modifier = modifier,
    transitionSpec = {
      val forward = targetState > initialState
      val enter = slideInHorizontally(
        animationSpec = spatialSpec,
        initialOffsetX = { width -> if (forward) width / 6 else -width / 6 },
      ) + fadeIn(animationSpec = tween(durationMillis = 180))
      val exit = slideOutHorizontally(
        animationSpec = spatialSpec,
        targetOffsetX = { width -> if (forward) -width / 8 else width / 8 },
      ) + fadeOut(animationSpec = tween(durationMillis = 140))
      enter togetherWith exit
    },
    label = "tabContent",
  ) { index ->
    content(index)
  }
}