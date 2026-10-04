package com.sextant.app.ui.util

import org.junit.Assert.*
import org.junit.Test

class ChartSelectionTest {
  @Test
  fun selectsNearestLineNodeIncludingZeroAmountDays() {
    assertEquals(0, spendingChartIndex(0f, 310f, 31))
    assertEquals(0, spendingChartIndex(4f, 310f, 31))
    assertEquals(1, spendingChartIndex(9.9f, 310f, 31))
    assertEquals(1, spendingChartIndex(10f, 310f, 31))
    assertEquals(15, spendingChartIndex(155f, 310f, 31))
    assertEquals(30, spendingChartIndex(310f, 310f, 31))
  }

  @Test
  fun draggingOutsideThePlotStaysAtNearestBoundary() {
    assertEquals(0, spendingChartIndex(-80f, 280f, 28))
    assertEquals(27, spendingChartIndex(400f, 280f, 28))
    assertEquals(0, spendingChartIndex(1f, 300f, 1))
  }

  @Test
  fun missingOrInvalidGeometryDoesNotInventASelection() {
    assertNull(spendingChartIndex(0f, 0f, 31))
    assertNull(spendingChartIndex(5f, 100f, 0))
    assertNull(spendingChartIndex(Float.NaN, 100f, 31))
    assertNull(spendingChartIndex(5f, Float.POSITIVE_INFINITY, 31))
  }
}
