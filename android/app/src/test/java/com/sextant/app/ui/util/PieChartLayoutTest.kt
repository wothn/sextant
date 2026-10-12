package com.sextant.app.ui.util

import org.junit.Assert.*
import org.junit.Test

class PieChartLayoutTest {
  @Test
  fun labelsClusteredAtBothEdgesStayInBoundsAndKeepTouchTargetsSeparate() {
    val anchors = listOf(199f, 0f, 198f, 1f, 2f)
    val centers = pieLabelCenters(anchors, 24f, 216f, 48f)
    assertTrue(centers.all { it in 24f..216f })
    val ordered = anchors.indices.sortedBy { anchors[it] }.map { centers[it] }
    assertTrue(ordered.zipWithNext().all { (a, b) -> b - a >= 48f })
  }

  @Test
  fun separatedLabelsStayAttachedToTheirNaturalPositions() {
    val anchors = listOf(180f, 30f, 100f)
    assertEquals(anchors, pieLabelCenters(anchors, 24f, 216f, 48f))
    assertEquals(listOf(100f), pieLabelCenters(listOf(100f), 24f, 216f, 48f))
    assertTrue(pieLabelCenters(emptyList(), 24f, 216f, 48f).isEmpty())
  }

  @Test
  fun dominantCategoryDoesNotEnlargeSmallSlicesOrLoseTheirLabels() {
    val amounts = listOf(999_000L) + List(10) { 100L }
    val slices = pieSlices(amounts, amounts.sum())
    assertEquals(11, slices.size)
    assertEquals(359.64f, slices.first().sweep, .001f)
    assertEquals(.036f, slices.last().sweep, .001f)
    val side = slices.filter { !it.isRight }
    val centers = pieLabelCenters(side.map { 240f + it.y * 80f }, 24f, 456f, 48f)
    assertEquals(10, centers.size)
    assertTrue(centers.all { it in 24f..456f })
    assertTrue(centers.sorted().zipWithNext().all { (a, b) -> b - a >= 48f })
  }

  @Test
  fun ringHitTestingMatchesSectorsAndIgnoresHoleAndLabels() {
    val slices = pieSlices(listOf(75L, 25L), 100L)
    assertEquals(0, pieSliceAt(90f, 0f, 100f, 25f, slices))
    assertEquals(1, pieSliceAt(-64f, -64f, 100f, 25f, slices))
    assertNull(pieSliceAt(0f, 0f, 100f, 25f, slices))
    assertNull(pieSliceAt(110f, 0f, 100f, 25f, slices))
    assertNull(pieSliceAt(Float.NaN, 0f, 100f, 25f, slices))
    val single = pieSlices(listOf(100L), 100L)
    assertEquals(0, pieSliceAt(0f, -90f, 100f, 25f, single))
    assertTrue(pieSlices(listOf(100L), 0).isEmpty())
  }
}
