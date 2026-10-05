package com.sextant.app.ui.util

import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.AnalysisMetric
import com.sextant.app.data.AnalysisWindow
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

fun analysisPeriodLabel(period: AnalysisPeriod): String = when (period) {
  AnalysisPeriod.Week -> "周"
  AnalysisPeriod.Month -> "月"
  AnalysisPeriod.Year -> "年"
}

fun analysisWindowLabel(window: AnalysisWindow): String = when (window.period) {
  AnalysisPeriod.Week -> {
    val last = window.end.minusDays(1)
    val endYear = if (window.start.year == last.year) "" else "${last.year}年"
    "${window.start.year}年${window.start.monthValue}月${window.start.dayOfMonth}日 — $endYear${last.monthValue}月${last.dayOfMonth}日"
  }
  AnalysisPeriod.Month -> "${window.start.year}年${window.start.monthValue}月"
  AnalysisPeriod.Year -> "${window.start.year}年"
}

fun analysisMetricLabel(metric: AnalysisMetric): String = if (metric == AnalysisMetric.Expense) "支出" else "收入"

/** 日期入口的周范围省略年份与同月的重复月份；完整年份保留在明细及无障碍说明里。 */
fun analysisDateSelectorLabel(window: AnalysisWindow): String {
  if (window.period != AnalysisPeriod.Week) return analysisWindowLabel(window)
  val last = window.end.minusDays(1)
  val endMonth = if (window.start.month == last.month) "" else "${last.monthValue}月"
  return "${window.start.monthValue}月${window.start.dayOfMonth}–$endMonth${last.dayOfMonth}日"
}

/** 列出与浏览月份相交的完整周，包括月首、月尾的跨月周。 */
fun analysisWeeksInMonth(month: YearMonth): List<AnalysisWindow> =
  generateSequence(AnalysisWindow(AnalysisPeriod.Week, month.atDay(1))) { it.move(1) }
    .takeWhile { it.start <= month.atEndOfMonth() }.toList()

fun analysisWeekLabel(window: AnalysisWindow): String =
  "第${window.start.get(WeekFields.ISO.weekOfWeekBasedYear())}周"

fun analysisComparisonNote(window: AnalysisWindow, today: LocalDate = LocalDate.now(), metric: AnalysisMetric = AnalysisMetric.Expense): String? = when {
  window.start > today -> "仅统计已录入记录，未来日期不代表已发生${analysisMetricLabel(metric)}。"
  window.end > today -> "本${analysisPeriodLabel(window.period)}尚未结束，对比的是上${analysisPeriodLabel(window.period)}完整周期。"
  else -> null
}

fun expensePointLabel(date: LocalDate, period: AnalysisPeriod): String =
  if (period == AnalysisPeriod.Year) "${date.year}年${date.monthValue}月" else "${date.monthValue}月${date.dayOfMonth}日"
