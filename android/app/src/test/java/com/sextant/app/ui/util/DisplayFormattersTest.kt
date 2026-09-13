package com.sextant.app.ui.util

import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.RecurringCycle
import com.sextant.app.data.Transaction
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
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

  @Test
  fun namesUpcomingDaysRelativelyButNeverCallsAPastDayTomorrow() {
    val today = LocalDate.of(2026, 2, 14) // 周六

    assertEquals("今天", futureDayLabel(LocalDate.of(2026, 2, 14).toStartOfDayMillis(), today))
    assertEquals("明天", futureDayLabel(LocalDate.of(2026, 2, 15).toStartOfDayMillis(), today))
    assertEquals("后天", futureDayLabel(LocalDate.of(2026, 2, 16).toStartOfDayMillis(), today))
    assertEquals("2月17日 周二", futureDayLabel(LocalDate.of(2026, 2, 17).toStartOfDayMillis(), today))
    // 跨年补年份，和 [dayGroupLabel] 往过去看时是同一套规则。
    assertEquals("2027年1月2日 周六", futureDayLabel(LocalDate.of(2027, 1, 2).toStartOfDayMillis(), today))
    // 已经过去的日子按日期展示，不能被说成「明天」。
    assertEquals("2月13日 周五", futureDayLabel(LocalDate.of(2026, 2, 13).toStartOfDayMillis(), today))
  }

  @Test
  fun spellsOutEveryRecurringCycleIncludingTheCustomInterval() {
    assertEquals("每周", RecurringCycle.Weekly.label(null))
    assertEquals("每月", RecurringCycle.Monthly.label(null))
    assertEquals("每季度", RecurringCycle.Quarterly.label(null))
    assertEquals("每半年", RecurringCycle.Semiannual.label(null))
    assertEquals("每年", RecurringCycle.Yearly.label(null))
    assertEquals("每 45 天", RecurringCycle.CustomDays.label(45))
    // 天数缺失或非法时回落到 30 天，和周期推算用的兜底保持一致。
    assertEquals("每 30 天", RecurringCycle.CustomDays.label(null))
    assertEquals("每 30 天", RecurringCycle.CustomDays.label(0))
  }

  @Test
  fun stampsDetailRowsWithTheirOwnDateAndTime() {
    val today = LocalDate.of(2026, 2, 14)
    val morning = LocalDate.of(2026, 2, 13).toStartOfDayMillis() + 8 * 60 * 60 * 1000 + 16 * 60 * 1000

    assertEquals("2月13日 08:16", timestampLabel(morning, today))
    assertEquals("2025年12月30日 09:05", timestampLabel(
      LocalDate.of(2025, 12, 30).toStartOfDayMillis() + 9 * 60 * 60 * 1000 + 5 * 60 * 1000,
      today,
    ))
  }

  @Test
  fun hidesGenericNotificationSummariesInLedgerRows() {
    val merchant = item(description = "微信支付", paymentMethodName = "微信")
    assertEquals("", displayDescription(merchant))
    // 通用摘要（「消费」）被丢掉，商户名留下；真实备注之间的分隔符归一成「 · 」。
    assertEquals("梧桐小馆", displayDescription(item(description = "梧桐小馆·消费", paymentMethodName = "微信")))
    assertEquals("午餐 · 咖啡", displayDescription(item(description = " 午餐 · 咖啡 ", paymentMethodName = "微信")))
  }

  private fun item(description: String, paymentMethodName: String) = TransactionListItem(
    transaction = Transaction("id", null, null, 100, TransactionType.Expense, description, 0, 0, 0),
    categoryName = "未分类",
    categoryColor = 0,
    paymentMethodName = paymentMethodName,
    includeInSpending = true,
  )
}
