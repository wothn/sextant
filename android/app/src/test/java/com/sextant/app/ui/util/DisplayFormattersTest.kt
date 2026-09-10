package com.sextant.app.ui.util

import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.toStartOfDayMillis
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayFormattersTest {
  @Test
  fun parsesPositiveAmountsExactlyToCents() {
    assertEquals(1L, parseAmountCents("0.01"))
    assertEquals(1_234L, parseAmountCents(" 12.34 "))
    assertEquals(100L, parseAmountCents("1"))
  }

  @Test
  fun rejectsZeroNegativeOverPrecisionAndOverflow() {
    assertNull(parseAmountCents("0"))
    assertNull(parseAmountCents("-1"))
    assertNull(parseAmountCents("1.001"))
    assertNull(parseAmountCents("999999999999999999999999"))
    assertNull(parseAmountCents("not-a-number"))
  }

  @Test
  fun labelsRangeEndpointsAndDetectsTheCurrentMonth() {
    val today = LocalDate.now()
    val thisMonth = LedgerFilter.currentMonth()
    assertTrue(thisMonth.isCurrentMonth())

    val lastMonth = LedgerFilter.forMonth(LocalDate.of(today.year - 1, 6, 15).toStartOfDayMillis())
    assertEquals("${today.year - 1}年6月1日 - ${today.year - 1}年6月30日", lastMonth.rangeLabel())
    assertFalse(lastMonth.isCurrentMonth())

    val wholeYear = LedgerFilter(
      rangeStart = LocalDate.of(today.year - 1, 1, 1).toStartOfDayMillis(),
      rangeEnd = LocalDate.of(today.year, 1, 1).toStartOfDayMillis(),
    )
    assertEquals("${today.year - 1}年1月1日 - ${today.year - 1}年12月31日", wholeYear.rangeLabel())
    assertFalse(wholeYear.isCurrentMonth())

    val crossYear = LedgerFilter(
      rangeStart = LocalDate.of(today.year - 1, 12, 20).toStartOfDayMillis(),
      rangeEnd = LocalDate.of(today.year, 1, 6).toStartOfDayMillis(),
    )
    assertEquals("${today.year - 1}年12月20日 - ${today.year}年1月5日", crossYear.rangeLabel())
  }

  @Test
  fun labelsTheMonthOfATimestamp() {
    val date = LocalDate.of(2024, 2, 29)
    assertEquals("2024年2月", monthLabel(date.toStartOfDayMillis()))
    assertFalse(isCurrentMonth(date.toStartOfDayMillis()))
    assertTrue(isCurrentMonth(System.currentTimeMillis()))
  }
}
