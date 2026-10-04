package com.sextant.app.data

import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AnalysisPeriodTest {
  private lateinit var originalZone: TimeZone
  @Before fun setup() {
    originalZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
  }
  @After fun cleanup() { TimeZone.setDefault(originalZone) }

  @Test fun weekStartsMondayAndCrossesYearWithoutLosingDays() {
    val window = AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2027, 1, 1))
    assertEquals(LocalDate.of(2026, 12, 28), window.start)
    assertEquals(LocalDate.of(2027, 1, 4), window.end)
    assertEquals(LocalDate.of(2026, 12, 21), window.previous.start)
    assertEquals(window.start, window.previous.end)
    assertEquals(7, expenseTrend(emptyList(), window).size)
  }

  @Test fun leapMonthAndYearIncludeAllCalendarBuckets() {
    val month = AnalysisWindow(AnalysisPeriod.Month, LocalDate.of(2024, 2, 29))
    assertEquals(29, expenseTrend(emptyList(), month).size)
    assertEquals(LocalDate.of(2024, 3, 1), month.end)
    val year = AnalysisWindow(AnalysisPeriod.Year, month.anchor)
    assertEquals(LocalDate.of(2024, 1, 1), year.start)
    assertEquals(LocalDate.of(2025, 1, 1), year.end)
    assertEquals(12, expenseTrend(emptyList(), year).size)
  }

  @Test fun changingGranularityKeepsTheDateContext() {
    val date = LocalDate.of(2026, 10, 4)
    val year = AnalysisWindow(AnalysisPeriod.Year, date)
    val week = AnalysisWindow(AnalysisPeriod.Week, year.anchor)
    assertEquals(LocalDate.of(2026, 9, 28), week.start)
    assertEquals(LocalDate.of(2025, 10, 4), year.move(-1).anchor)
    assertEquals(LocalDate.of(2026, 11, 4), AnalysisWindow(AnalysisPeriod.Month, date).move(1).anchor)
  }

  @Test fun incomeTrendCountsIncomeRegardlessOfExpenseFlagAndExcludesTransfers() {
    val window = AnalysisWindow(AnalysisPeriod.Month, LocalDate.of(2026, 10, 4))
    val rows = listOf(row(1000, "2026-10-01", TransactionType.Income),
      row(2000, "2026-10-01", TransactionType.Income, false),
      row(9000, "2026-10-01", TransactionType.Transfer), row(8000, "2026-10-01", TransactionType.Expense))
    val points = expenseTrend(rows, window, AnalysisMetric.Income)
    assertEquals(3000L, points.first().amountCents)
    assertEquals(AnalysisMetric.Income.amount(LedgerCalculations.summarize(rows)), points.sumOf { it.amountCents })
    assertEquals(8000L, expenseTrend(rows, window).sumOf { it.amountCents })
  }

  @Test fun trendUsesLocalDatesAndOnlyCountedExpenses() {
    val window = AnalysisWindow(AnalysisPeriod.Year, LocalDate.of(2026, 10, 4))
    val rows = listOf(row(100, "2026-01-01", TransactionType.Expense),
      row(200, "2026-01-31", TransactionType.Expense), row(500, "2026-12-31", TransactionType.Expense),
      row(700, "2026-02-01", TransactionType.Income), row(800, "2026-02-01", TransactionType.Transfer),
      row(900, "2026-02-01", TransactionType.Expense, false))
    val points = expenseTrend(rows, window)
    assertEquals(300L, points.first().amountCents)
    assertEquals(500L, points.last().amountCents)
    assertEquals(0L, points[1].amountCents)
    assertEquals(LedgerCalculations.summarize(rows).expenseCents, points.sumOf { it.amountCents })
  }

  private fun row(amount: Long, date: String, type: TransactionType, counted: Boolean = true): TransactionListItem {
    val stamp = LocalDate.parse(date).toStartOfDayMillis()
    return TransactionListItem(Transaction("$date-$type-$amount", null, null, amount, type, "", stamp, 0, 0),
      "未分类", 1, "现金", counted)
  }
}
