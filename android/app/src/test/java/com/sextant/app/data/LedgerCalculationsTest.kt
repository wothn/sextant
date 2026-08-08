package com.sextant.app.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LedgerCalculationsTest {
  private lateinit var originalTimeZone: TimeZone

  @Before
  fun useUtc() {
    originalTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  }

  @After
  fun restoreTimeZone() {
    TimeZone.setDefault(originalTimeZone)
  }

  @Test
  fun summaryExcludesTransfersAndNonSpendingExpenses() {
    val items = listOf(
      item(TransactionType.Income, 50_000),
      item(TransactionType.Expense, 12_500),
      item(TransactionType.Expense, 8_000, includeInSpending = false),
      item(TransactionType.Transfer, 99_000),
    )

    assertEquals(Summary(incomeCents = 50_000, expenseCents = 12_500), LedgerCalculations.summarize(items))
  }

  @Test
  fun breakdownGroupsAndSortsByAmount() {
    val items = listOf(
      item(TransactionType.Expense, 1_200, categoryName = "餐饮", color = 1),
      item(TransactionType.Expense, 800, categoryName = "餐饮", color = 1),
      item(TransactionType.Expense, 3_000, categoryName = "交通", color = 2),
    )

    assertEquals(
      listOf(
        CategoryBreakdown("交通", 2, 3_000),
        CategoryBreakdown("餐饮", 1, 2_000),
      ),
      LedgerCalculations.breakdown(items),
    )
  }

  @Test
  fun dailySpendingCoversLeapMonthAndIgnoresExcludedRows() {
    val monthStart = utcMillis(2024, Calendar.FEBRUARY, 1)
    val items = listOf(
      item(TransactionType.Expense, 1_000, date = utcMillis(2024, Calendar.FEBRUARY, 2)),
      item(TransactionType.Expense, 500, date = utcMillis(2024, Calendar.FEBRUARY, 2)),
      item(TransactionType.Expense, 9_000, includeInSpending = false, date = utcMillis(2024, Calendar.FEBRUARY, 3)),
      item(TransactionType.Income, 8_000, date = utcMillis(2024, Calendar.FEBRUARY, 4)),
    )

    val result = LedgerCalculations.dailySpending(items, monthStart)

    assertEquals(29, result.size)
    assertEquals(DailySpending(2, 1_500), result[1])
    assertEquals(DailySpending(3, 0), result[2])
  }

  @Test
  fun projectionUsesElapsedDaysOnlyForCurrentMonth() {
    val february = utcMillis(2024, Calendar.FEBRUARY, 1)
    val februaryTenth = utcMillis(2024, Calendar.FEBRUARY, 10)
    val january = utcMillis(2024, Calendar.JANUARY, 1)

    assertEquals(29_000, LedgerCalculations.projectedExpense(10_000, february, februaryTenth))
    assertEquals(10_000, LedgerCalculations.projectedExpense(10_000, january, februaryTenth))
  }

  private fun item(
    type: TransactionType,
    amountCents: Long,
    includeInSpending: Boolean = true,
    categoryName: String = "分类",
    color: Long = 1,
    date: Long = 0,
  ) = TransactionListItem(
    transaction = Transaction("id-$type-$amountCents-$date", null, null, amountCents, type, "", date, 0, 0),
    categoryName = categoryName,
    categoryColor = color,
    paymentMethodName = "现金",
    includeInSpending = includeInSpending,
  )

  private fun utcMillis(year: Int, month: Int, day: Int): Long = Calendar.getInstance().run {
    clear()
    set(year, month, day)
    timeInMillis
  }
}
