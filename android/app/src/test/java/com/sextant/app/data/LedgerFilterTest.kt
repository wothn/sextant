package com.sextant.app.data

import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LedgerFilterTest {
  private lateinit var originalZone: TimeZone

  @Before
  fun setUp() {
    originalZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
  }

  @After
  fun tearDown() {
    TimeZone.setDefault(originalZone)
  }

  @Test
  fun countsOnlyConditionsOutsideTheDateRange() {
    val base = LedgerFilter.forMonth(LocalDate.of(2026, 10, 1).toStartOfDayMillis())
    assertEquals(0, base.conditionCount)
    assertFalse(base.hasConditions)
    assertEquals(1, base.copy(type = TransactionType.Income).conditionCount)
    assertEquals(1, base.copy(minAmountCents = 100).conditionCount)
    assertEquals(1, base.copy(maxAmountCents = 100).conditionCount)
    assertEquals(5, base.copy(
      query = "午饭",
      type = TransactionType.Expense,
      categoryId = "category",
      paymentMethodId = "source",
      minAmountCents = 100,
    ).conditionCount)
    assertEquals(0, base.copy(type = TransactionType.Expense).cleared().conditionCount)
    assertEquals(base, base.copy(type = TransactionType.Expense).cleared())
  }

  @Test
  fun anchorsTheAnalysisMonthOnTheLastDayOfTheRange() {
    val march = LedgerFilter.forMonth(LocalDate.of(2026, 3, 1).toStartOfDayMillis())
    assertEquals(march.rangeStart, march.anchorMonthStart)

    val spansTwoMonths = LedgerFilter(rangeStart = march.rangeStart, rangeEnd = march.rangeEnd + 86_400_000L)
    assertEquals(LocalDate.of(2026, 4, 1).toStartOfDayMillis(), spansTwoMonths.anchorMonthStart)
  }

}
