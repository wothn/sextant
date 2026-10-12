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
  fun countsEveryActiveConditionIncludingTheDateRange() {
    val base = LedgerFilter.allTime()
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
    val month = LedgerFilter.forMonth(LocalDate.of(2026, 10, 1).toStartOfDayMillis())
    assertEquals(1, month.conditionCount)
    assertTrue(month.hasConditions)
    assertEquals(2, month.copy(type = TransactionType.Income).conditionCount)
    assertEquals(1, LedgerFilter.forDay(month.rangeStart).conditionCount)
    assertEquals(0, base.copy(query = "  ").conditionCount)
    assertEquals(1, base.copy(minAmountCents = 100, maxAmountCents = 200).conditionCount)
  }

  @Test
  fun clearingRemovesEveryConditionAndRestoresAllTime() {
    val month = LedgerFilter.forMonth(LocalDate.of(2026, 10, 1).toStartOfDayMillis())
    val filters = listOf(
      LedgerFilter.allTime(),
      month,
      LedgerFilter.forDay(month.rangeStart),
      month.copy(query = "午饭"),
      month.copy(type = TransactionType.Expense),
      month.copy(categoryId = "category"),
      month.copy(paymentMethodId = "source"),
      month.copy(minAmountCents = 100),
      month.copy(maxAmountCents = 200),
      month.copy(
        query = "午饭",
        type = TransactionType.Expense,
        categoryId = "category",
        paymentMethodId = "source",
        minAmountCents = 100,
        maxAmountCents = 200,
      ),
    )

    for (filter in filters) {
      val cleared = filter.cleared()
      assertEquals(LedgerFilter.allTime(), cleared)
      assertTrue(cleared.isAllTime)
      assertFalse(cleared.hasConditions)
      assertEquals(0, cleared.conditionCount)
      assertEquals(cleared, cleared.cleared())
    }
  }

  @Test
  fun anchorsTheAnalysisMonthOnTheLastDayOfTheRange() {
    val march = LedgerFilter.forMonth(LocalDate.of(2026, 3, 1).toStartOfDayMillis())
    assertEquals(march.rangeStart, march.anchorMonthStart)

    val spansTwoMonths = LedgerFilter(rangeStart = march.rangeStart, rangeEnd = march.rangeEnd + 86_400_000L)
    assertEquals(LocalDate.of(2026, 4, 1).toStartOfDayMillis(), spansTwoMonths.anchorMonthStart)
  }

}
