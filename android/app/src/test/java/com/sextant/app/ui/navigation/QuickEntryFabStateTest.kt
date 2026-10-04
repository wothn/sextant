package com.sextant.app.ui.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryFabStateTest {
  @Test
  fun upwardGestureOnShortListKeepsEntryVisible() {
    val state = QuickEntryFabState(hideThresholdPx = 16f)

    state.nestedScrollConnection.onPostScroll(
      consumed = Offset.Zero,
      available = Offset(0f, -100f),
      source = NestedScrollSource.UserInput,
    )

    assertTrue(state.visible)
  }

  @Test
  fun actualForwardScrollHidesEntryAfterThresholdAndBackwardGestureRestoresIt() {
    val state = QuickEntryFabState(hideThresholdPx = 16f)
    val connection = state.nestedScrollConnection

    connection.onPostScroll(Offset(0f, -8f), Offset.Zero, NestedScrollSource.UserInput)
    assertTrue(state.visible)
    connection.onPostScroll(Offset(0f, -8f), Offset.Zero, NestedScrollSource.UserInput)
    assertFalse(state.visible)
    // At the top of the list a backward gesture can remain unconsumed.
    connection.onPostScroll(Offset.Zero, Offset(0f, 1f), NestedScrollSource.UserInput)
    assertTrue(state.visible)
    connection.onPostScroll(Offset(0f, -8f), Offset.Zero, NestedScrollSource.UserInput)
    assertTrue(state.visible)
  }
}
