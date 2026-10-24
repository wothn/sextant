package com.sextant.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class AnalysisPeriod { Week, Month, Year }

enum class AnalysisMetric {
  Expense, Income;

  fun includes(item: TransactionListItem): Boolean = when (this) {
    Expense -> item.transaction.type == TransactionType.Expense && item.includeInSpending
    Income -> item.transaction.type == TransactionType.Income
  }

  fun amount(summary: Summary): Long = when (this) {
    Expense -> summary.expenseCents
    Income -> summary.incomeCents
  }
}

/** 本地日历区间，结束时间不包含在统计内；周一为每周起点。 */
data class AnalysisWindow(val period: AnalysisPeriod, val anchor: LocalDate) {
  val start: LocalDate = when (period) {
    AnalysisPeriod.Week -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    AnalysisPeriod.Month -> anchor.withDayOfMonth(1)
    AnalysisPeriod.Year -> anchor.withDayOfYear(1)
  }
  val end: LocalDate = shift(start, 1)
  val filter: LedgerFilter get() = LedgerFilter(start.toStartOfDayMillis(), end.toStartOfDayMillis())
  val previous: AnalysisWindow get() = AnalysisWindow(period, shift(start, -1))
  fun move(offset: Long): AnalysisWindow = AnalysisWindow(period, shift(anchor, offset))
  private fun shift(date: LocalDate, offset: Long): LocalDate = when (period) {
    AnalysisPeriod.Week -> date.plusWeeks(offset)
    AnalysisPeriod.Month -> date.plusMonths(offset)
    AnalysisPeriod.Year -> date.plusYears(offset)
  }
}

data class ExpenseTrendPoint(val date: LocalDate, val amountCents: Long)

data class AnalysisReport(
  val summary: Summary,
  val previousSummary: Summary,
  val trend: List<ExpenseTrendPoint>,
  val incomeTrend: List<ExpenseTrendPoint> = emptyList(),
  val incomeBreakdown: List<CategoryBreakdown> = emptyList(),
  /** 这份报告是按哪个周期算出来的；界面在切周期的过渡帧里靠它解释 trend，而不是当前选择。 */
  val period: AnalysisPeriod = AnalysisPeriod.Month,
)

internal fun expenseTrend(items: List<TransactionListItem>, window: AnalysisWindow,
  metric: AnalysisMetric = AnalysisMetric.Expense): List<ExpenseTrendPoint> {
  val amounts = items.filter(metric::includes).groupBy {
    val date = it.transaction.transactionDate.toLocalDate()
    if (window.period == AnalysisPeriod.Year) date.withDayOfMonth(1) else date
  }.mapValues { (_, rows) -> rows.sumOf { it.transaction.amountCents } }
  return generateSequence(window.start) {
    if (window.period == AnalysisPeriod.Year) it.plusMonths(1) else it.plusDays(1)
  }.takeWhile { it < window.end }.map { ExpenseTrendPoint(it, amounts[it] ?: 0L) }.toList()
}
