package com.sextant.app.ui.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记账 FAB 的收起规则。
 *
 * 这里断言的是行为本身：哪些滚动会让入口让开、哪些会让它回来。阈值和复位都要覆盖，
 * 因为「翻明细时 FAB 压住金额」和「FAB 一直不回来」是同一个状态机的两种失败方向。
 *
 * 方向按意图表达：向前 = 翻看更早的明细，往回 = 往回翻。
 */
class QuickEntryFabStateTest {

  private fun state(thresholdPx: Float = 16f) = QuickEntryFabState(thresholdPx)

  @Test
  fun `默认可见`() {
    assertTrue(state().visible)
  }

  @Test
  fun `向前滚动超过阈值后收起`() {
    val state = state()
    state.onScrolledForward(16f)
    assertFalse(state.visible)
  }

  @Test
  fun `阈值内的滚动不会让入口闪烁`() {
    val state = state()
    state.onScrolledForward(15f)
    assertTrue(state.visible)
  }

  @Test
  fun `慢速滚动按累计位移收起`() {
    val state = state()
    state.onScrolledForward(6f)
    state.onScrolledForward(6f)
    assertTrue(state.visible)
    state.onScrolledForward(6f)
    assertFalse(state.visible)
  }

  @Test
  fun `任意一次回滚都立即恢复`() {
    val state = state()
    state.onScrolledForward(120f)
    assertFalse(state.visible)
    state.onScrolledBackward()
    assertTrue(state.visible)
  }

  @Test
  fun `回滚会清零累计位移`() {
    val state = state()
    state.onScrolledForward(40f)
    assertFalse(state.visible)
    // 顶部回弹：往回滚一段再往前，重新开始计算，不该沿用旧的累计量。
    state.onScrolledBackward()
    state.onScrolledForward(15f)
    assertTrue(state.visible)
    state.onScrolledForward(1f)
    assertFalse(state.visible)
  }

  @Test
  fun `复位后回到可见且累计位移清零`() {
    val state = state()
    state.onScrolledForward(100f)
    assertFalse(state.visible)

    state.show()
    assertTrue(state.visible)
    state.onScrolledForward(1f)
    assertTrue(state.visible)
  }

  /**
   * 方向映射本身也要断言：Compose 的嵌套滚动里，手指上滑（内容向前翻）时 `available.y` 是负的。
   * 这里写反，FAB 就会和手指反着来——而这正是外面看不出、只有真机上滑一下才会暴露的错误。
   */
  @Test
  fun `嵌套滚动的 y 为负表示向前滚动`() {
    val state = state()
    state.nestedScrollConnection.onPreScroll(Offset(0f, -16f), NestedScrollSource.UserInput)
    assertFalse(state.visible)
  }

  @Test
  fun `嵌套滚动的 y 为正表示往回滚动`() {
    val state = state()
    state.nestedScrollConnection.onPreScroll(Offset(0f, -16f), NestedScrollSource.SideEffect)
    assertFalse(state.visible)

    state.nestedScrollConnection.onPreScroll(Offset(0f, 1f), NestedScrollSource.UserInput)
    assertTrue(state.visible)
  }

  @Test
  fun `滚动监听不消费位移`() {
    val state = state()
    val consumed = state.nestedScrollConnection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
    assertEquals(Offset.Zero, consumed)
  }
}
