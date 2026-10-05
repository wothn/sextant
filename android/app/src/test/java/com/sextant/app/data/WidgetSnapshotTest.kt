package com.sextant.app.data

import android.app.Application
import android.content.Context
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetSnapshotTest {
  private val context: Context get() = RuntimeEnvironment.getApplication()
  private lateinit var zone: TimeZone
  private val today = LocalDate.of(2026, 10, 1)
  private val now get() = today.toStartOfDayMillis() + 12 * 60 * 60 * 1000L
  @Before fun before() {
    zone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }
  @After fun after() {
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    TimeZone.setDefault(zone)
  }

  @Test fun recentSpendingCrossesMonthsButSummaryDoesNotAndNonSpendingIsExcluded() = runBlocking {
    val repository = SextantRepository(context)
    val excluded = repository.loadState().categories.first { it.type == TransactionType.Expense && !it.includeInSpending }
    suspend fun add(amount: Long, at: Long, type: TransactionType = TransactionType.Expense, category: String? = null) {
      repository.addTransaction(type, amount, category, null, "$amount", at)
    }
    add(100, today.minusDays(2).toStartOfDayMillis())
    add(200, today.minusDays(1).toStartOfDayMillis())
    add(300, today.toStartOfDayMillis())
    add(400, now - 1)
    add(5000, now - 1, TransactionType.Income)
    add(6000, now - 1, TransactionType.Transfer)
    add(7000, now - 1, category = excluded.id)
    // 未来记录不应出现在「最近」列表。
    add(8000, today.plusDays(1).toStartOfDayMillis())
    repository.setMonthlyBudget(10000)
    repository.setThemePreference(ThemePreference.Dark)
    val snapshot = repository.loadWidgetSnapshot(now)
    assertEquals(listOf(400L, 300L, 200L), snapshot.recent.map { it.transaction.amountCents })
    assertEquals(700L, snapshot.todaySummary.expenseCents)
    assertEquals(2, snapshot.todayCount)
    assertEquals(8700L, snapshot.monthSummary.expenseCents)
    assertEquals(5000L, snapshot.monthSummary.incomeCents)
    assertEquals(10000L, snapshot.monthlyBudgetCents)
    assertEquals(ThemePreference.Dark, snapshot.theme)
    repository.deleteTransaction(snapshot.recent.first().transaction.id)
    val refreshed = repository.loadWidgetSnapshot(now)
    assertEquals(listOf(300L, 200L, 100L), refreshed.recent.map { it.transaction.amountCents })
    assertEquals(300L, refreshed.todaySummary.expenseCents)
  }

  @Test fun upcomingUsesHalfOpenSevenDaysAndExcludesPausedAndEndedPlans() = runBlocking {
    val repository = SextantRepository(context)
    suspend fun plan(name: String, anchor: LocalDate, active: Boolean = true, end: LocalDate? = null) {
      repository.saveRecurringExpense(null, RecurringExpenseDraft(name, 1800, RecurringCycle.Monthly,
        null, anchor.toStartOfDayMillis(), null, null, end?.toStartOfDayMillis(), active, ""))
    }
    plan("今天", today)
    plan("第七天", today.plusDays(6))
    plan("第八天", today.plusDays(7))
    plan("暂停", today, false)
    plan("已结束", today.minusMonths(1), end = today)
    val snapshot = repository.loadWidgetSnapshot(now)
    assertEquals(listOf("今天", "第七天"), snapshot.upcoming.map { it.expense.name })
    assertEquals(0L, snapshot.monthSummary.expenseCents)
    assertTrue(snapshot.recent.isEmpty())
    assertNull(snapshot.monthlyBudgetCents)
  }

  @Test fun archivedCategoryMatchesExistingLedgerFallbackAndSameDateOrderIsStable() = runBlocking {
    val repository = SextantRepository(context)
    val excluded = repository.loadState().categories.first { it.type == TransactionType.Expense && !it.includeInSpending }
    repository.addTransaction(TransactionType.Expense, 500, excluded.id, null, "归档", now)
    repository.archiveCategory(excluded.id)
    val snapshot = repository.loadWidgetSnapshot(now)
    assertEquals(500L, snapshot.recent.single().transaction.amountCents)
    assertEquals(500L, snapshot.todaySummary.expenseCents)
    assertEquals(repository.loadWidgetSnapshot(now).recent, snapshot.recent)
  }
}
