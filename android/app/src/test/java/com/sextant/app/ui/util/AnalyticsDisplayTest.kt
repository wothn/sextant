package com.sextant.app.ui.util

import org.junit.Test
import org.junit.Assert.*

class AnalyticsDisplayTest {
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

}
