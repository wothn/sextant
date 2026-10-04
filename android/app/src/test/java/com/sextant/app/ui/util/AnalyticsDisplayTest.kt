package com.sextant.app.ui.util

import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class AnalyticsDisplayTest {
  private lateinit var originalZone: TimeZone
  private val today = LocalDate.of(2026, 10, 4)

  @Before fun setZone() {
    originalZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
  }

  @After fun restoreZone() { TimeZone.setDefault(originalZone) }

  @Test fun currentMonthExplainsThatComparisonUsesPreviousWholeMonth() {
    val month = millis(today.withDayOfMonth(1))
    assertEquals("本月已记录支出", analyticsExpenseLabel(month, today))
    assertEquals("本月尚未结束，对比的是上月整月已记录支出。", analyticsComparisonNote(month, today))
  }

  @Test fun historicalAndFutureMonthsNeverClaimToBeThisMonth() {
    val previous = millis(today.minusMonths(1).withDayOfMonth(1))
    val future = millis(today.plusMonths(1).withDayOfMonth(1))
    assertEquals("2026年9月已记录支出", analyticsExpenseLabel(previous, today))
    assertEquals("与上月整月已记录支出比较。", analyticsComparisonNote(previous, today))
    assertEquals("仅统计已录入记录，未来日期不代表已发生支出。", analyticsComparisonNote(future, today))
  }

  @Test fun tinySharesAndLargeAmountsStayMeaningful() {
    assertEquals("<0.1%", spendingShareLabel(1, Long.MAX_VALUE))
    assertEquals("50%", spendingShareLabel(Long.MAX_VALUE / 2, Long.MAX_VALUE))
    assertEquals("—", spendingShareLabel(1, 0))
    assertEquals("0%", spendingShareLabel(0, 100))
    assertEquals("33.3%", spendingShareLabel(1, 3))
  }

  @Test fun comparisonsDoNotInventTrendsFromAMissingBaselineOrRounding() {
    assertEquals("上月未记录支出", spendingChangeLabel(100, 0))
    assertEquals("与上月相同", spendingChangeLabel(100, 100))
    assertEquals("较上月减少 100%", spendingChangeLabel(0, 100))
    assertEquals("较上月增加 <0.1%", spendingChangeLabel(Long.MAX_VALUE, Long.MAX_VALUE - 1))
    assertEquals("较上月增加 25%", spendingChangeLabel(125, 100))
  }

  private fun millis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
