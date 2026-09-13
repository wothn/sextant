package com.sextant.app.data

import android.content.Context
import java.util.Calendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SextantRepositoryTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    context.deleteDatabase(DATABASE_NAME)
  }

  @After
  fun tearDown() {
    context.deleteDatabase(DATABASE_NAME)
  }

  @Test
  fun defaultLedgerIncludesPastYearsWithoutMergingDaysAndKeepsMonthlySummary() {
    val repository = SextantRepository(context)
    val originalZone = java.util.TimeZone.getDefault()
    try {
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
      val today = java.time.LocalDate.now()
      val current = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
      val historical = today.minusYears(28).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
      repository.addTransaction(TransactionType.Expense, 100, null, null, "current", current)
      repository.addTransaction(TransactionType.Expense, 200, null, null, "historical", historical)

      val state = repository.loadState()
      assertEquals(2, state.transactions.size)
      assertEquals(2, state.groups.size)
      // 分组键是本地时区当天零点，并保持「最新在前」的顺序，展示文案由 UI 层决定。
      assertEquals(state.groups.map { it.dayStartMillis }.sortedDescending(), state.groups.map { it.dayStartMillis })
      assertTrue(state.groups.first().dayStartMillis > state.groups.last().dayStartMillis)
      state.groups.forEach { group ->
        assertEquals(group.dayStartMillis, group.dayStartMillis.toLocalDate().toStartOfDayMillis())
      }
      assertEquals(300L, state.rangeSummary.expenseCents)
      assertEquals(100L, state.monthSummary.expenseCents)
      assertEquals(1, repository.loadState(LedgerFilter.currentMonth()).transactions.size)
    } finally {
      java.util.TimeZone.setDefault(originalZone)
    }
  }

  @Test
  fun addUpdateFilterAndDeleteTransaction() {
    val repository = SextantRepository(context)
    val initial = repository.loadState(filter())
    val category = initial.categories.first { it.type == TransactionType.Expense }
    val source = initial.paymentMethods.first()

    repository.addTransaction(TransactionType.Expense, 1_250, category.id, source.id, "  午饭  ", now())

    val added = repository.loadState(filter(query = "午饭")).transactions.single()
    assertEquals("午饭", added.transaction.description)
    assertEquals(1_250, added.transaction.amountCents)

    repository.updateTransaction(added.transaction.id, TransactionType.Income, 2_500, category.id, source.id, "退款", now())

    val updated = repository.loadState(filter(query = "退款", type = TransactionType.Income)).transactions.single()
    assertEquals(2_500, updated.transaction.amountCents)
    assertEquals(TransactionType.Income, updated.transaction.type)

    repository.deleteTransaction(updated.transaction.id)
    assertTrue(repository.loadState(filter(query = "退款")).transactions.isEmpty())
  }

  @Test
  fun rejectsNonPositiveAmountsAtRepositoryBoundary() {
    val repository = SextantRepository(context)

    assertEquals("流水金额必须大于 0", assertThrows(IllegalArgumentException::class.java) {
      repository.addTransaction(TransactionType.Expense, 0, null, null, "", now())
    }.message)
    assertEquals("月预算金额必须大于 0", assertThrows(IllegalArgumentException::class.java) {
      repository.setMonthlyBudget(-1)
    }.message)
    assertEquals("预算金额必须大于 0", assertThrows(IllegalArgumentException::class.java) {
      repository.upsertBudget("category", 0)
    }.message)
  }

  @Test
  fun addEditDeactivateReactivateAndDeleteUnusedSource() {
    val repository = SextantRepository(context)

    repository.addPaymentMethod("  信用卡  ", 10)
    var state = repository.loadState(filter())
    val source = state.allPaymentMethods.single { it.name == "信用卡" }
    assertTrue(source.isActive)
    assertFalse(source.isBuiltIn)

    repository.updatePaymentMethod(source.id, "储蓄卡", 20)
    repository.setPaymentMethodActive(source.id, false)
    state = repository.loadState(filter())
    assertTrue(state.paymentMethods.none { it.id == source.id })
    assertEquals(
      PaymentMethod(source.id, "储蓄卡", 20, isBuiltIn = false, isActive = false),
      state.allPaymentMethods.single { it.id == source.id },
    )

    repository.setPaymentMethodActive(source.id, true)
    assertTrue(repository.loadState(filter()).paymentMethods.any { it.id == source.id })

    repository.deletePaymentMethod(source.id)
    assertTrue(repository.loadState(filter()).allPaymentMethods.none { it.id == source.id })
  }

  @Test
  fun protectsBuiltInAndReferencedSourcesWhileKeepingHistoricalNames() {
    val repository = SextantRepository(context)
    val initial = repository.loadState(filter())
    val builtIn = initial.paymentMethods.first()
    val category = initial.categories.first { it.type == TransactionType.Expense }

    assertEquals("内置来源不能删除", assertThrows(IllegalArgumentException::class.java) {
      repository.deletePaymentMethod(builtIn.id)
    }.message)

    repository.addPaymentMethod("公司卡", 30)
    val custom = repository.loadState(filter()).paymentMethods.single { it.name == "公司卡" }
    repository.addTransaction(TransactionType.Expense, 500, category.id, custom.id, "工作餐", now())

    assertEquals("已被流水使用的来源不能删除", assertThrows(IllegalArgumentException::class.java) {
      repository.deletePaymentMethod(custom.id)
    }.message)

    repository.setPaymentMethodActive(custom.id, false)
    val state = repository.loadState(filter(query = "工作餐"))
    assertTrue(state.paymentMethods.none { it.id == custom.id })
    assertEquals("公司卡", state.transactions.single().paymentMethodName)
    assertEquals(1L, state.allPaymentMethods.single { it.id == custom.id }.transactionCount)
  }

  @Test
  fun rejectsDuplicateSourceNames() {
    val repository = SextantRepository(context)
    repository.addPaymentMethod("信用卡", 10)

    assertEquals("来源名称已存在", assertThrows(IllegalArgumentException::class.java) {
      repository.addPaymentMethod(" 信用卡 ", 20)
    }.message)
  }

  @Test
  fun completeBackupRestoreRemovesLaterChanges() {
    val repository = SextantRepository(context)
    val initial = repository.loadState(filter())
    val category = initial.categories.first { it.type == TransactionType.Expense }
    val source = initial.paymentMethods.first()
    repository.addTransaction(TransactionType.Expense, 1_000, category.id, source.id, "保留", now())
    val backup = repository.exportBackup()
    repository.addTransaction(TransactionType.Expense, 2_000, category.id, source.id, "稍后添加", now())

    val summary = repository.restoreBackup(backup)

    assertEquals(1, summary.transactionCount)
    assertEquals(listOf("保留"), repository.loadState(filter()).transactions.map { it.transaction.description })
  }

  @Test
  fun failedRestoreRollsBackEveryDeletion() {
    val database = SextantDatabase(context)
    val db = database.writableDatabase
    db.execSQL("DELETE FROM budgets")
    db.execSQL("DELETE FROM transactions")
    db.execSQL("DELETE FROM payment_methods")
    db.execSQL("DELETE FROM categories")
    db.execSQL("INSERT INTO categories VALUES ('category', '分类', 'expense', 1, 1, NULL, 0, 1, 1)")
    db.execSQL("INSERT INTO payment_methods VALUES ('source', '现金', 1, 0, 1, 1)")
    db.execSQL("INSERT INTO transactions (id, categoryId, paymentMethodId, amountCents, type, description, transactionDate, createdAt, updatedAt) VALUES ('transaction', 'category', 'source', 100, 'expense', '备份值', 1, 1, 1)")
    val store = BackupStore(database)
    val backup = store.export()
    db.execSQL("UPDATE transactions SET description = '恢复前原值'")
    db.execSQL(
      "CREATE TRIGGER reject_restore BEFORE INSERT ON categories BEGIN SELECT RAISE(ABORT, 'blocked'); END",
    )

    val error = assertThrows(BackupException::class.java) { store.restore(backup) }

    assertEquals("恢复失败，原有数据未被修改", error.message)
    assertEquals("恢复前原值", db.rawQuery("SELECT description FROM transactions", emptyArray()).use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    })
    database.close()
  }

  @Test
  fun filtersByDateRangeWhileTodaySummaryStaysCurrent() {
    val repository = SextantRepository(context)
    val initial = repository.loadState(filter())
    val category = initial.categories.first { it.type == TransactionType.Expense }
    val source = initial.paymentMethods.first()
    val earlierDay = now() - 40 * 86_400_000L
    repository.addTransaction(TransactionType.Expense, 1_000, category.id, source.id, "今天", now())
    repository.addTransaction(TransactionType.Expense, 2_000, category.id, source.id, "早前", earlierDay)

    val thisMonth = repository.loadState(LedgerFilter.currentMonth())
    assertEquals(listOf("今天"), thisMonth.transactions.map { it.transaction.description })
    assertEquals(1_000, thisMonth.rangeSummary.expenseCents)
    assertEquals(1_000, thisMonth.todaySummary.expenseCents)
    assertEquals(1_000, thisMonth.monthSummary.expenseCents)

    val earlierMonth = repository.loadState(LedgerFilter.forMonth(earlierDay))
    assertEquals(listOf("早前"), earlierMonth.transactions.map { it.transaction.description })
    assertEquals(2_000, earlierMonth.rangeSummary.expenseCents)
    // 今日支出与所选区间无关，始终是今天的真实流水。
    assertEquals(1_000, earlierMonth.todaySummary.expenseCents)
    assertEquals(2_000, earlierMonth.monthSummary.expenseCents)

    // 跨月区间同时包含两笔，区间合计取整个区间。
    val spanning = LedgerFilter(
      rangeStart = LedgerFilter.forMonth(earlierDay).rangeStart,
      rangeEnd = LedgerFilter.currentMonth().rangeEnd,
    )
    val spanningState = repository.loadState(spanning)
    assertEquals(setOf("今天", "早前"), spanningState.transactions.map { it.transaction.description }.toSet())
    assertEquals(3_000, spanningState.rangeSummary.expenseCents)
  }

  @Test
  fun filtersByAmountRange() {
    val repository = SextantRepository(context)
    val initial = repository.loadState(filter())
    val category = initial.categories.first { it.type == TransactionType.Expense }
    val source = initial.paymentMethods.first()
    repository.addTransaction(TransactionType.Expense, 500, category.id, source.id, "小额", now())
    repository.addTransaction(TransactionType.Expense, 5_000, category.id, source.id, "中额", now())
    repository.addTransaction(TransactionType.Expense, 50_000, category.id, source.id, "大额", now())
    val base = LedgerFilter.currentMonth()

    assertEquals(
      listOf("中额", "大额", "小额"),
      repository.loadState(base).transactions.map { it.transaction.description }.sorted(),
    )
    assertEquals(
      listOf("中额"),
      repository.loadState(base.copy(minAmountCents = 1_000, maxAmountCents = 10_000)).transactions.map { it.transaction.description },
    )
    assertEquals(
      listOf("中额", "大额"),
      repository.loadState(base.copy(minAmountCents = 1_000)).transactions.map { it.transaction.description }.sorted(),
    )
    assertEquals(
      listOf("小额"),
      repository.loadState(base.copy(maxAmountCents = 1_000)).transactions.map { it.transaction.description },
    )
  }

  @Test
  fun countsOnlyConditionsOutsideTheDateRange() {
    val base = LedgerFilter.currentMonth()
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
    val march = LedgerFilter.forMonth(now())
    assertEquals(march.rangeStart, march.anchorMonthStart)

    val spansTwoMonths = LedgerFilter(rangeStart = march.rangeStart, rangeEnd = march.rangeEnd + 86_400_000L)
    assertTrue(spansTwoMonths.anchorMonthStart > march.anchorMonthStart)
  }

  private fun filter(
    query: String = "",
    type: TransactionType? = null,
  ) = LedgerFilter.forMonth(monthStart()).copy(query = query, type = type)

  private fun monthStart(): Long = Calendar.getInstance().run {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
  }

  private fun now(): Long = System.currentTimeMillis()

  private companion object {
    const val DATABASE_NAME = "sextant.db"
  }
}
