package com.sextant.app.ui.util

import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.AnalysisWindow
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class AnalysisFormattersTest {
  @Test fun weekNumberUsesMondayBasedWeekYearAtYearBoundaries() {
    assertEquals("第41周", analysisWeekLabel(
      AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2026, 10, 5))))
    assertEquals("第53周", analysisWeekLabel(
      AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2027, 1, 1))))
    assertEquals("第1周", analysisWeekLabel(
      AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2027, 1, 4))))
  }

  @Test fun crossYearWeekLabelsBothYears() {
    assertEquals("2026年12月28日 — 2027年1月3日",
      analysisWindowLabel(AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2027, 1, 1))))
  }

  @Test fun weekDateSelectorOmitsYearAndRepeatedMonth() {
    assertEquals("10月5–11日", analysisDateSelectorLabel(
      AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2026, 10, 5))))
    assertEquals("9月28–10月4日", analysisDateSelectorLabel(
      AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2026, 10, 1))))
    assertEquals("12月28–1月3日", analysisDateSelectorLabel(
      AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2027, 1, 1))))
    assertEquals("2026年10月", analysisDateSelectorLabel(
      AnalysisWindow(AnalysisPeriod.Month, LocalDate.of(2026, 10, 5))))
    assertEquals("2026年", analysisDateSelectorLabel(
      AnalysisWindow(AnalysisPeriod.Year, LocalDate.of(2026, 10, 5))))
  }

  @Test fun weekPickerIncludesCompleteWeeksAtMonthBoundaries() {
    val weeks = analysisWeeksInMonth(YearMonth.of(2026, 3))
    assertEquals(6, weeks.size)
    assertEquals(LocalDate.of(2026, 2, 23), weeks.first().start)
    assertEquals(LocalDate.of(2026, 4, 6), weeks.last().end)
    weeks.zipWithNext().forEach { (previous, next) -> assertEquals(previous.end, next.start) }
    val leapFebruary = analysisWeeksInMonth(YearMonth.of(2024, 2))
    assertEquals(LocalDate.of(2024, 1, 29), leapFebruary.first().start)
    assertEquals(LocalDate.of(2024, 3, 4), leapFebruary.last().end)
  }

  @Test fun currentPeriodStillExplainsComparisonOnItsLastDay() {
    val today = LocalDate.of(2026, 10, 4)
    assertEquals("本周尚未结束，对比的是上周完整周期。",
      analysisComparisonNote(AnalysisWindow(AnalysisPeriod.Week, today), today))
    assertNull(analysisComparisonNote(AnalysisWindow(AnalysisPeriod.Week, today.minusWeeks(1)), today))
  }

  @Test fun previousPeriodTextTracksSelectedGranularity() {
    assertEquals("较上年减少 50%", spendingChangeLabel(1000, 2000, "上年"))
    assertEquals("上周未记录支出", spendingChangeLabel(1000, 0, "上周"))
    assertEquals("上年未记录收入", spendingChangeLabel(1000, 0, "上年", "收入"))
    assertEquals("仅统计已录入记录，未来日期不代表已发生收入。",
      analysisComparisonNote(AnalysisWindow(AnalysisPeriod.Month, LocalDate.of(2026, 11, 1)),
        LocalDate.of(2026, 10, 4), com.sextant.app.data.AnalysisMetric.Income))
  }
}
