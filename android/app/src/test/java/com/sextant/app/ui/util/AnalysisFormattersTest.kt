package com.sextant.app.ui.util

import com.sextant.app.data.AnalysisPeriod
import com.sextant.app.data.AnalysisWindow
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AnalysisFormattersTest {
  @Test fun crossYearWeekLabelsBothYears() {
    assertEquals("2026年12月28日 — 2027年1月3日",
      analysisWindowLabel(AnalysisWindow(AnalysisPeriod.Week, LocalDate.of(2027, 1, 1))))
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
