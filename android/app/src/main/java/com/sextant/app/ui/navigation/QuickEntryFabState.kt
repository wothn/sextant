package com.sextant.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 记账入口 FAB 的显隐状态。
 *
 * 记账入口从底栏里搬出来、浮在内容之上以后，就成了唯一可能压住明细金额的东西。这里用滚动
 * 方向把它让开：内容向前滚动（翻看更早的明细）时收起，一旦往回滚就立刻回来。于是看账的
 * 时候它不在，想记账的时候它一定在。
 *
 * 状态挂在根布局的嵌套滚动上，而不是让每个页面各自上报：页面只要还在用 `LazyColumn` 或
 * `verticalScroll`，就自动接入，不用为 FAB 改一行页面代码，也不会漏掉设置里的子页面。
 * 连接本身不消费任何滚动，只读方向。
 */
@Stable
class QuickEntryFabState(private val hideThresholdPx: Float) {

  /** 当前是否应当显示。收起后不占位、不可点，也不进入无障碍焦点。 */
  var visible by mutableStateOf(true)
    private set

  /** 本次向前滚动的累计位移。往回滚动会清零，避免手指按下时的抖动就让 FAB 闪一下。 */
  private var travelledForward = 0f

  /** 挂到根布局上的滚动监听，见 [QuickEntryFabState] 的说明。 */
  val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
    // Compose 的嵌套滚动里 y 为负表示内容向前滚动（手指上滑），为正表示往回滚。
    // 符号约定只在这个适配层出现，下面的方法按「向前 / 往回」表达意图。
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      when {
        available.y < 0f -> onScrolledForward(-available.y)
        available.y > 0f -> onScrolledBackward()
      }
      return Offset.Zero
    }
  }

  /**
   * 内容向前滚动（翻看更早的明细）了 [distancePx]。不足 [hideThresholdPx] 的位移会被累计，
   * 所以慢速滚动同样能收起 FAB，而一次快速滑动立刻就能让它让开。
   */
  fun onScrolledForward(distancePx: Float) {
    travelledForward += distancePx
    if (travelledForward >= hideThresholdPx) visible = false
  }

  /** 内容往回滚动（往回翻）。任何一点回滚都让入口立刻回来。 */
  fun onScrolledBackward() {
    travelledForward = 0f
    visible = true
  }

  /** 复位。切换目的地时调用，否则新页面会继承上一个页面的收起状态。 */
  fun show() {
    travelledForward = 0f
    visible = true
  }
}

/** 需要累计这么多向前位移才收起 FAB：比这更小的抖动不该让入口闪烁。 */
private val HideThreshold = 16.dp

@Composable
fun rememberQuickEntryFabState(): QuickEntryFabState {
  val density = LocalDensity.current
  return remember(density) {
    QuickEntryFabState(hideThresholdPx = with(density) { HideThreshold.toPx() })
  }
}
