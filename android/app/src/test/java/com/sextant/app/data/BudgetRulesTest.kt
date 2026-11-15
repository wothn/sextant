package com.sextant.app.data

import android.content.Context
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BudgetRulesTest {
  private lateinit var context: Context
  private lateinit var originalZone: TimeZone

  @Before fun setUp() {
    originalZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }

  @After fun tearDown() {
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    TimeZone.setDefault(originalZone)
  }

  private fun month(value: String) = LocalDate.parse("$value-01").toStartOfDayMillis()

  @Test fun versionsCarryAcrossUnopenedMonthsAndKeepHistoricalSpendingAndLimits() = runBlocking {
    val repository = SextantRepository(context)
    val category = repository.loadState().categories.first { it.type == TransactionType.Expense && it.includeInSpending }
    for (key in listOf("", category.id)) {
      repository.writeBudgetRule(key, 20000, BudgetScope.FromThisMonth, month("2026-11"))
      repository.writeBudgetRule(key, 23000, BudgetScope.FromThisMonth, month("2026-11"))
      repository.writeBudgetRule(key, 30000, BudgetScope.ThisMonth, month("2026-12"))
      repository.writeBudgetRule(key, 25000, BudgetScope.FromThisMonth, month("2027-02"))
    }
    repository.addTransaction(TransactionType.Expense, 1000, category.id, null, "十一月消费", month("2026-11"))
    repository.addTransaction(TransactionType.Expense, 5000, category.id, null, "十二月消费", month("2026-12"))
    for ((date, limit, spent) in listOf(
      Triple("2026-11", 23000L, 1000L), Triple("2026-12", 30000L, 5000L),
      Triple("2027-01", 23000L, 0L), Triple("2027-02", 25000L, 0L), Triple("2027-09", 25000L, 0L),
    )) {
      val state = repository.loadState(LedgerFilter.forMonth(month(date)))
      assertEquals(date, limit, state.monthlyBudgetCents)
      assertEquals(date, limit, state.budgets.single().amountCents)
      assertEquals(date, spent, state.budgets.single().spentCents)
      assertEquals(limit, repository.loadWidgetSnapshot(month(date)).monthlyBudgetCents)
    }
    assertNull(repository.loadState(LedgerFilter.forMonth(month("2026-10"))).monthlyBudgetCents)
  }

  @Test fun clearingCanPauseOneMonthOrStopFutureMonthsWithoutErasingHistory() = runBlocking {
    val repository = SextantRepository(context)
    val category = repository.loadState().categories.first { it.type == TransactionType.Expense && it.includeInSpending }
    val current = LedgerFilter.currentMonth().rangeStart
    val previous = monthRangeOf(current - 1).first
    val next = current.toLocalDate().plusMonths(1).toStartOfDayMillis()
    repository.writeBudgetRule(category.id, 20000, BudgetScope.FromThisMonth, previous)
    repository.clearBudget(category.id, BudgetScope.ThisMonth)
    assertTrue(repository.loadState(projection = StateProjection.Plans).budgets.isEmpty())
    assertEquals(20000L, repository.loadState(LedgerFilter.forMonth(next)).budgets.single().amountCents)
    repository.upsertBudget(category.id, 25000)
    assertEquals(25000L, repository.loadState(projection = StateProjection.Plans).budgets.single().amountCents)
    repository.clearBudget(category.id)
    assertTrue(repository.loadState(LedgerFilter.forMonth(next)).budgets.isEmpty())
    assertEquals(20000L, repository.loadState(LedgerFilter.forMonth(previous)).budgets.single().amountCents)
    repository.writeBudgetRule("", 90000, BudgetScope.FromThisMonth, previous)
    repository.clearMonthlyBudget(BudgetScope.ThisMonth)
    assertNull(repository.loadState(projection = StateProjection.Plans).monthlyBudgetCents)
    assertEquals(90000L, repository.loadState(LedgerFilter.forMonth(next)).monthlyBudgetCents)
    repository.clearMonthlyBudget()
    assertNull(repository.loadState(LedgerFilter.forMonth(next)).monthlyBudgetCents)
    assertEquals(90000L, repository.loadState(LedgerFilter.forMonth(previous)).monthlyBudgetCents)
  }

  @Test fun temporaryBudgetWithoutBaselineExpiresAndBackupPreservesVersionsAndStops() = runBlocking {
    val repository = SextantRepository(context)
    val category = repository.loadState().categories.first { it.type == TransactionType.Expense && it.includeInSpending }
    repository.setMonthlyBudget(90000, BudgetScope.ThisMonth)
    val current = LedgerFilter.currentMonth().rangeStart
    val next = current.toLocalDate().plusMonths(1).toStartOfDayMillis()
    assertNull(repository.loadState(LedgerFilter.forMonth(next)).monthlyBudgetCents)
    repository.writeBudgetRule(category.id, 10000, BudgetScope.FromThisMonth, month("2025-11"))
    repository.writeBudgetRule(category.id, 0, BudgetScope.ThisMonth, month("2025-12"))
    val store = BackupStore(repository.database)
    val backup = store.export()
    repository.upsertBudget(category.id, 99999)
    store.restore(backup)
    assertEquals(90000L, repository.loadState(projection = StateProjection.Plans).monthlyBudgetCents)
    assertNull(repository.loadState(LedgerFilter.forMonth(next)).monthlyBudgetCents)
    assertTrue(repository.loadState(LedgerFilter.forMonth(month("2025-12"))).budgets.isEmpty())
    assertEquals(10000L, repository.loadState(LedgerFilter.forMonth(month("2026-01"))).budgets.single().amountCents)
  }

  @Test fun restoringOldBackupConvertsOnlyCurrentCategoriesToRecurringAndDoesNotInventTotalHistory() = runBlocking {
    val repository = SextantRepository(context)
    val store = BackupStore(repository.database)
    val document = BackupCodec.decodeAndValidate(store.export())
    val category = document.categories.first { it.type == "expense" }
    val current = LedgerFilter.currentMonth().rangeStart
    val previous = monthRangeOf(current - 1).first
    val next = current.toLocalDate().plusMonths(1).toStartOfDayMillis()
    store.restore(BackupCodec.encode(document.copy(databaseVersion = 14, version = 4,
      budgets = listOf(BackupBudget("old", category.id, 12000, "month", previous, 0.8, true),
        BackupBudget("current", category.id, 15000, "month", current, 0.8, true)),
      settings = listOf(BackupSetting(SextantDatabase.MONTHLY_BUDGET_KEY, "90000")),
    )))
    assertEquals(12000L, repository.loadState(LedgerFilter.forMonth(previous)).budgets.single().amountCents)
    assertNull(repository.loadState(LedgerFilter.forMonth(previous)).monthlyBudgetCents)
    assertEquals(15000L, repository.loadState(LedgerFilter.forMonth(next)).budgets.single().amountCents)
    assertEquals(90000L, repository.loadState(LedgerFilter.forMonth(next)).monthlyBudgetCents)
  }
}
