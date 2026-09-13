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

  @Test
  fun namesTheLastThreeDaysRelativelyAndKeepsTheWeekdayForOlderOnes() {
    val today = LocalDate.of(2026, 2, 14) // 周六

    assertEquals("今天", dayGroupLabel(LocalDate.of(2026, 2, 14).toStartOfDayMillis(), today))
    assertEquals("昨天", dayGroupLabel(LocalDate.of(2026, 2, 13).toStartOfDayMillis(), today))
    assertEquals("前天", dayGroupLabel(LocalDate.of(2026, 2, 12).toStartOfDayMillis(), today))
    assertEquals("2月11日 周三", dayGroupLabel(LocalDate.of(2026, 2, 11).toStartOfDayMillis(), today))
    assertEquals("1月4日 周日", dayGroupLabel(LocalDate.of(2026, 1, 4).toStartOfDayMillis(), today))
    // 未来日期不编造「明天」，仍然按日期展示。
    assertEquals("2月20日 周五", dayGroupLabel(LocalDate.of(2026, 2, 20).toStartOfDayMillis(), today))
  }

  @Test
  fun addsTheYearOnlyWhenTheDayIsOutsideTheCurrentYear() {
    val today = LocalDate.of(2026, 2, 14)
    assertEquals("2025年12月30日 周二", dayGroupLabel(LocalDate.of(2025, 12, 30).toStartOfDayMillis(), today))
    assertEquals("2027年1月2日 周六", dayGroupLabel(LocalDate.of(2027, 1, 2).toStartOfDayMillis(), today))
  }

  @Test
  fun treatsAnyMomentWithinADayAsThatSameDay() {
    val today = LocalDate.of(2026, 2, 14)
    val earlyToday = LocalDate.of(2026, 2, 14).toStartOfDayMillis()
    val lateYesterday = LocalDate.of(2026, 2, 13).toStartOfDayMillis() + 23 * 60 * 60 * 1000 + 59 * 60 * 1000

    assertEquals("今天", dayGroupLabel(earlyToday, today))
    assertEquals("昨天", dayGroupLabel(lateYesterday, today))
  }
}
