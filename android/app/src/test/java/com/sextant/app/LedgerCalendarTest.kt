package com.sextant.app

import com.sextant.app.data.*
import com.sextant.app.ui.util.calendarAmount
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Before
import org.junit.After

class LedgerCalendarTest {
  private lateinit var originalZone: TimeZone
  @Before fun setup() { originalZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai")) }
  @After fun cleanup() { TimeZone.setDefault(originalZone) }

  @Test fun leapMonthAndWeekAlignment() {
    val days = calendarDays(YearMonth.of(2024, 2))
    assertEquals(35, days.size)
    assertEquals(listOf(null, null, null), days.take(3))
    assertEquals(LocalDate.of(2024, 2, 29), days.filterNotNull().last())
    assertEquals(29, days.filterNotNull().size)
    assertEquals(42, calendarDays(YearMonth.of(2026, 3)).size)
  }

  @Test fun localMidnightAndYearBoundaryKeepAccountingRules() {
    val midnight = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun item(time: Long, type: TransactionType, amount: Long, counted: Boolean = true) = TransactionListItem(
      Transaction("$time-$type-$amount", null, null, amount, type, "", time, time, time), "", 0, "", counted,
    )
    val calendar = LedgerCalendar(listOf(
      item(midnight - 1, TransactionType.Expense, 100),
      item(midnight, TransactionType.Expense, 200),
      item(midnight, TransactionType.Expense, 300, false),
      item(midnight, TransactionType.Income, 500),
      item(midnight, TransactionType.Transfer, 900),
    ))
    assertEquals(Summary(0, 100), calendar.years[2025])
    assertEquals(Summary(500, 200), calendar.years[2026])
    assertEquals(calendar.years[2026], calendar.months[YearMonth.of(2026, 1)])
    assertEquals(calendar.years[2026], calendar.summaries[LocalDate.of(2026, 1, 1)])
    assertEquals(4, calendar.days[LocalDate.of(2026, 1, 1)]!!.size)
    assertTrue(LedgerCalendar(emptyList()).summaries.isEmpty())
  }

  @Test fun compactAmountsRetainSmallValuesAndLabelLargeUnits() {
    assertEquals("0.01", calendarAmount(1))
    assertEquals("12.34", calendarAmount(1234))
    assertEquals("1.0千", calendarAmount(100000))
    assertEquals("1.0万", calendarAmount(1000000))
    assertEquals("1.0亿", calendarAmount(10000000000))
  }
}
