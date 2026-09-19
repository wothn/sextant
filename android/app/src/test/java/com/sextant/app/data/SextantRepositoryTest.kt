package com.sextant.app.data

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 仓储层的行为。
 *
 * 仓储的读写都是挂起的（Room 不允许在主线程上访问数据库），所以每个用例体都包在
 * [runBlocking] 里。刻意不把 `runBlocking` 下沉到「每个调用一次」：那样每个用例里都会
 * 散落七八个桥接点，读起来全是噪音，而 `assertThrows` 又要跨着桥接边界工作。
 *
 * 测试用的库由 [SextantDatabase.createForTests] 建，允许主线程查询——被阻塞的正是
 * Robolectric 的主线程，而 Room 的线程检查是为真机的多线程场景准备的护栏。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SextantRepositoryTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase(DATABASE_NAME)
  }

  @After
  fun tearDown() {
    SextantDatabase.closeForTests()
    context.deleteDatabase(DATABASE_NAME)
  }

  @Test
  fun budgetWritesPublishCommittedValuesAndFailuresDoNotPublish() = runBlocking {
    val repository = SextantRepository(context)
    val initialRevision = repository.changes.value
    repository.setMonthlyBudget(120000)
    assertTrue(repository.changes.value > initialRevision)
    assertEquals(120000L, repository.loadState(projection = StateProjection.Plans).monthlyBudgetCents)
    val category = repository.loadState().categories.first { it.type == TransactionType.Expense && it.parentCategoryId == null }
    val beforeCategoryWrite = repository.changes.value
    repository.upsertBudget(category.id, 30000)
    assertTrue(repository.changes.value > beforeCategoryWrite)
    assertTrue(repository.loadState(projection = StateProjection.Plans).budgets.any { it.amountCents == 30000L })
    val committed = repository.changes.value
    assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setMonthlyBudget(0) } }
    assertEquals(committed, repository.changes.value)
  }

  @Test
  fun plansIgnoreHistoricalLedgerFilterAndSettingsDoNotLoadLedgerRows() = runBlocking {
    val repository = SextantRepository(context)
    val originalZone = java.util.TimeZone.getDefault()
    try {
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
      val today = java.time.LocalDate.now()
      val current = today.toStartOfDayMillis()
      val historical = today.minusMonths(2).withDayOfMonth(10).toStartOfDayMillis()
      repository.addTransaction(TransactionType.Expense, 1234, null, null, "current", current)
      repository.addTransaction(TransactionType.Expense, 9999, null, null, "history", historical)
      val historicalFilter = LedgerFilter.forMonth(historical)
      assertEquals(9999L, repository.loadState(historicalFilter, StateProjection.Ledger).monthSummary.expenseCents)
      val plans = repository.loadState(historicalFilter, StateProjection.Plans)
      assertEquals(1234L, plans.monthSummary.expenseCents)
      assertTrue(plans.transactions.isEmpty())
      val settings = repository.loadState(historicalFilter, StateProjection.Settings)
      assertTrue(settings.transactions.isEmpty())
      assertTrue(settings.categories.isNotEmpty())
      val analytics = repository.loadState(historicalFilter, StateProjection.Analytics)
      assertTrue(analytics.transactions.isEmpty())
      assertEquals(9999L, analytics.monthSummary.expenseCents)
      assertEquals(1, analytics.analysisTransactions.size)
    } finally {
      java.util.TimeZone.setDefault(originalZone)
    }
  }

  @Test
  fun defaultLedgerIncludesPastYearsWithoutMergingDaysAndKeepsMonthlySummary() = runBlocking {
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

  /**
   * 不筛方向就是「全部」。
   *
   * 这里守的是一个已经发生过的回归：DAO 的 `(:type = '' OR type = :type)` 一旦被绑成枚举，
   * 「不筛选」会静默变成「只看支出」——收入流水既不在明细里，也进不了收入侧汇总，
   * 而首页的按天小计本来就按方向拆开显示收入。
   */
  @Test
  fun unfilteredLedgerKeepsEveryDirectionAndCountsIncomeInTheSummary() = runBlocking {
    val repository = SextantRepository(context)
    val seeded = repository.loadState(filter())
    val expenseCategory = seeded.categories.first { it.type == TransactionType.Expense }
    val incomeCategory = seeded.categories.first { it.type == TransactionType.Income }
    repository.addTransaction(TransactionType.Expense, 1_000, expenseCategory.id, null, "午饭", now())
    repository.addTransaction(TransactionType.Income, 5_000, incomeCategory.id, null, "退款到账", now())
    repository.addTransaction(TransactionType.Transfer, 300, null, null, "转出到银行卡", now())

    val state = repository.loadState()

    assertEquals(
      setOf("午饭", "退款到账", "转出到银行卡"),
      state.transactions.map { it.transaction.description }.toSet(),
    )
    assertEquals(
      setOf(TransactionType.Expense, TransactionType.Income, TransactionType.Transfer),
      state.transactions.map { it.transaction.type }.toSet(),
    )
    // 没有分类的流水按方向给文案，转账不会退化成「未分类」。
    assertEquals(
      "转移记录",
      state.transactions.single { it.transaction.type == TransactionType.Transfer }.categoryName,
    )

    // 汇总口径：收入必须真的进收入侧，转账两个方向都不算进收支。
    assertEquals(Summary(incomeCents = 5_000, expenseCents = 1_000), state.monthSummary)
    assertEquals(Summary(incomeCents = 5_000, expenseCents = 1_000), state.rangeSummary)
    assertEquals(5_000L, state.todaySummary.incomeCents)
    assertEquals(3, state.groups.single().items.size)
  }

  /** 选了具体方向才收窄，三个方向各自可用。 */
  @Test
  fun directionFilterNarrowsTheLedgerToThatDirection() = runBlocking {
    val repository = SextantRepository(context)
    val seeded = repository.loadState(filter())
    val expenseCategory = seeded.categories.first { it.type == TransactionType.Expense }
    val incomeCategory = seeded.categories.first { it.type == TransactionType.Income }
    repository.addTransaction(TransactionType.Expense, 1_000, expenseCategory.id, null, "午饭", now())
    repository.addTransaction(TransactionType.Income, 5_000, incomeCategory.id, null, "退款到账", now())
    repository.addTransaction(TransactionType.Transfer, 300, null, null, "转出到银行卡", now())

    suspend fun descriptions(type: TransactionType?): Set<String> =
      repository.loadState(filter(type = type)).transactions.map { it.transaction.description }.toSet()

    assertEquals(setOf("午饭", "退款到账", "转出到银行卡"), descriptions(null))
    assertEquals(setOf("午饭"), descriptions(TransactionType.Expense))
    assertEquals(setOf("退款到账"), descriptions(TransactionType.Income))
    assertEquals(setOf("转出到银行卡"), descriptions(TransactionType.Transfer))
  }

  @Test
  fun mergesSubCategoriesIntoTheirRootCategoryAndKeepsTheMonthRowsForDetail() = runBlocking {
    val repository = SextantRepository(context)
    val seeded = repository.loadState(filter())
    val dining = seeded.categories.single { it.name == "餐饮" && it.parentCategoryId == null }
    val child = seeded.categories.first { it.parentCategoryId == dining.id }
    val transport = seeded.categories.single { it.name == "交通" && it.parentCategoryId == null }
    repository.addTransaction(TransactionType.Expense, 1_000, dining.id, null, "正餐", now())
    repository.addTransaction(TransactionType.Expense, 2_000, child.id, null, "奶茶", now())
    repository.addTransaction(TransactionType.Expense, 500, transport.id, null, "地铁", now())

    val state = repository.loadState(filter())

    assertEquals(
      listOf(
        CategoryBreakdown("餐饮", dining.color, 3_000, memberCategoryIds = setOf(dining.id, child.id)),
        CategoryBreakdown("交通", transport.color, 500, memberCategoryIds = setOf(transport.id)),
      ),
      state.categoryBreakdown,
    )
    // 分类明细要能拿到分析月份的全部流水，不受明细筛选条件影响。
    assertEquals(
      setOf("正餐", "奶茶", "地铁"),
      state.analysisTransactions.map { it.transaction.description }.toSet(),
    )
  }

  @Test
  fun addUpdateFilterAndDeleteTransaction() = runBlocking {
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
  fun rejectsNonPositiveAmountsAtRepositoryBoundary() = runBlocking {
    val repository = SextantRepository(context)

    assertEquals("流水金额必须大于 0", assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.addTransaction(TransactionType.Expense, 0, null, null, "", now()) }
    }.message)
    assertEquals("月预算金额必须大于 0", assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.setMonthlyBudget(-1) }
    }.message)
    assertEquals("预算金额必须大于 0", assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.upsertBudget("category", 0) }
    }.message)
  }

  @Test
  fun addEditDeactivateReactivateAndDeleteUnusedSource() = runBlocking {
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
  fun protectsBuiltInAndReferencedSourcesWhileKeepingHistoricalNames() = runBlocking {
    val repository = SextantRepository(context)
    val initial = repository.loadState(filter())
    val builtIn = initial.paymentMethods.first()
    val category = initial.categories.first { it.type == TransactionType.Expense }

    assertEquals("内置来源不能删除", assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.deletePaymentMethod(builtIn.id) }
    }.message)

    repository.addPaymentMethod("公司卡", 30)
    val custom = repository.loadState(filter()).paymentMethods.single { it.name == "公司卡" }
    repository.addTransaction(TransactionType.Expense, 500, category.id, custom.id, "工作餐", now())

    assertEquals("已被流水使用的来源不能删除", assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.deletePaymentMethod(custom.id) }
    }.message)

    repository.setPaymentMethodActive(custom.id, false)
    val state = repository.loadState(filter(query = "工作餐"))
    assertTrue(state.paymentMethods.none { it.id == custom.id })
    assertEquals("公司卡", state.transactions.single().paymentMethodName)
    assertEquals(1L, state.allPaymentMethods.single { it.id == custom.id }.transactionCount)
  }

  @Test
  fun rejectsDuplicateSourceNames() = runBlocking {
    val repository = SextantRepository(context)
    repository.addPaymentMethod("信用卡", 10)

    assertEquals("来源名称已存在", assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.addPaymentMethod(" 信用卡 ", 20) }
    }.message)
  }

  @Test
  fun completeBackupRestoreRemovesLaterChanges() = runBlocking {
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
  fun completeBackupRestoreCarriesRecurringExpensesAndDropsLaterOnes() = runBlocking {
    val repository = SextantRepository(context)
    val anchor = monthStart()
    repository.saveRecurringExpense(null, recurringDraft(name = "房租", amountCents = 280_000, anchorDate = anchor))
    val backup = repository.exportBackup()
    repository.saveRecurringExpense(
      null,
      recurringDraft(name = "宽带", amountCents = 120_000, cycle = RecurringCycle.Yearly, anchorDate = anchor),
    )

    val summary = repository.restoreBackup(backup)

    assertEquals(1, summary.recurringExpenseCount)
    val restored = repository.loadState(filter()).recurringExpenses
    assertEquals(listOf("房租"), restored.map { it.name })
    assertEquals(280_000L, restored.single().amountCents)
    assertEquals(RecurringCycle.Monthly, restored.single().cycle)
  }

  @Test
  fun failedRestoreRollsBackEveryDeletion() = runBlocking {
    val database = SextantDatabase.createForTests(context)
    val db = database.openHelper.writableDatabase
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
    // 恢复的第一批插入就是分类，让它在触发器上失败，就能验证「整批回滚」而不是「部分成功」。
    db.execSQL(
      "CREATE TRIGGER reject_restore BEFORE INSERT ON categories BEGIN SELECT RAISE(ABORT, 'blocked'); END",
    )

    val error = assertThrows(BackupException::class.java) { runBlocking { store.restore(backup) } }

    assertEquals("恢复失败，原有数据未被修改", error.message)
    assertEquals("恢复前原值", db.query(SimpleSQLiteQuery("SELECT description FROM transactions")).use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    })
    database.close()
  }

  @Test
  fun filtersByDateRangeWhileTodaySummaryStaysCurrent() = runBlocking {
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
  fun filtersByAmountRange() = runBlocking {
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

  @Test
  fun recurringExpensesNeverChangeLedgerTotalsBecauseTheyAreAPlanNotATransaction() = runBlocking {
    val repository = SextantRepository(context)
    val housing = repository.loadState(filter()).categories.single { it.name == "住房" && it.parentCategoryId == null }
    repository.addTransaction(TransactionType.Expense, 1_500, housing.id, null, "物业维修", now())
    val before = repository.loadState(filter())

    repository.saveRecurringExpense(
      id = null,
      draft = recurringDraft(amountCents = 280_000, anchorDate = monthStart(), categoryId = housing.id),
    )
    val after = repository.loadState(filter())

    assertEquals(1, after.recurringExpenses.size)
    assertEquals(280_000L, after.recurringExpenses.single().amountCents)
    // 关键口径：周期支出不生成流水，收支、明细和支出结构都不受影响。
    assertEquals(before.monthSummary, after.monthSummary)
    assertEquals(before.rangeSummary, after.rangeSummary)
    assertEquals(before.transactions.size, after.transactions.size)
    assertEquals(before.categoryBreakdown, after.categoryBreakdown)
  }

  @Test
  fun severalRecurringExpensesShareOneCategoryBecauseIdentityIsTheNamedEntry() = runBlocking {
    val repository = SextantRepository(context)
    val housing = repository.loadState(filter()).categories.single { it.name == "住房" && it.parentCategoryId == null }
    val anchor = monthStart()

    repository.saveRecurringExpense(null, recurringDraft(name = "房租", amountCents = 280_000, anchorDate = anchor, categoryId = housing.id))
    repository.saveRecurringExpense(null, recurringDraft(name = "物业费", amountCents = 15_000, cycle = RecurringCycle.Quarterly, anchorDate = anchor, categoryId = housing.id))
    repository.saveRecurringExpense(null, recurringDraft(name = "宽带", amountCents = 120_000, cycle = RecurringCycle.Yearly, anchorDate = anchor, categoryId = housing.id))

    val state = repository.loadState(filter())
    assertEquals(setOf("房租", "物业费", "宽带"), state.recurringExpenses.map { it.name }.toSet())
    // 每月折算 2800 + 50 + 100 = 2950 元：跨周期可比是这一列数字存在的意义。
    assertEquals(295_000L, state.recurringExpenses.sumOf { it.monthlyEquivalentCents() })
  }

  @Test
  fun recurringExpenseRejectsInvalidInputAndStaleIds() = runBlocking {
    val repository = SextantRepository(context)
    val anchor = monthStart()

    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.saveRecurringExpense(null, recurringDraft(name = "   ", amountCents = 1_000, anchorDate = anchor)) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.saveRecurringExpense(null, recurringDraft(amountCents = 0, anchorDate = anchor)) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking {
        repository.saveRecurringExpense(
          null,
          recurringDraft(amountCents = 1_000, cycle = RecurringCycle.CustomDays, customDays = null, anchorDate = anchor),
        )
      }
    }
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking {
        repository.saveRecurringExpense(
          null,
          recurringDraft(amountCents = 1_000, cycle = RecurringCycle.CustomDays, customDays = 0, anchorDate = anchor),
        )
      }
    }
    // 过期的 id 不能悄悄降级成插入。
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.saveRecurringExpense("missing", recurringDraft(amountCents = 1_000, anchorDate = anchor)) }
    }

    assertEquals(0, repository.loadState(filter()).recurringExpenses.size)
  }

  @Test
  fun customDaysIsDroppedForFixedCyclesAndUpdateReplacesInPlace() = runBlocking {
    val repository = SextantRepository(context)
    val anchor = monthStart()
    val id = repository.saveRecurringExpense(
      null,
      recurringDraft(amountCents = 280_000, customDays = 45, anchorDate = anchor, note = "每月 1 号"),
    )

    // 「每月」还带 45 天是自相矛盾的组合，归一化掉而不是报错。
    val created = repository.loadState(filter()).recurringExpenses.single()
    assertEquals(id, created.id)
    assertNull(created.customDays)
    assertEquals("每月 1 号", created.note)

    repository.saveRecurringExpense(
      id,
      recurringDraft(amountCents = 300_000, cycle = RecurringCycle.CustomDays, customDays = 45, anchorDate = anchor),
    )
    val updated = repository.loadState(filter()).recurringExpenses.single()
    assertEquals(1, repository.loadState(filter()).recurringExpenses.size)
    assertEquals(id, updated.id)
    assertEquals(300_000L, updated.amountCents)
    assertEquals(RecurringCycle.CustomDays, updated.cycle)
    assertEquals(45, updated.customDays)

    repository.setRecurringExpenseActive(id, false)
    assertFalse(repository.loadState(filter()).recurringExpenses.single().isActive)

    repository.deleteRecurringExpense(id)
    assertEquals(0, repository.loadState(filter()).recurringExpenses.size)
  }

  @Test
  fun endedAtRoundTripsAndClearsSoACanceledPlanCanStillBeKept() = runBlocking {
    val repository = SextantRepository(context)
    val anchor = monthStart()
    val stop = anchor + 90L * 86_400_000L

    val id = repository.saveRecurringExpense(null, recurringDraft(anchorDate = anchor, endedAt = stop))
    assertEquals(stop, repository.loadState(filter()).recurringExpenses.single().endedAt)

    repository.saveRecurringExpense(id, recurringDraft(amountCents = 300_000, anchorDate = anchor, endedAt = null))
    val cleared = repository.loadState(filter()).recurringExpenses.single()
    assertEquals(id, cleared.id)
    assertEquals(300_000L, cleared.amountCents)
    assertNull(cleared.endedAt)
  }

  private fun recurringDraft(
    name: String = "房租",
    amountCents: Long = 280_000,
    cycle: RecurringCycle = RecurringCycle.Monthly,
    customDays: Int? = null,
    anchorDate: Long = monthStart(),
    categoryId: String? = null,
    paymentMethodId: String? = null,
    endedAt: Long? = null,
    isActive: Boolean = true,
    note: String = "",
  ) = RecurringExpenseDraft(
    name = name,
    amountCents = amountCents,
    cycle = cycle,
    customDays = customDays,
    anchorDate = anchorDate,
    categoryId = categoryId,
    paymentMethodId = paymentMethodId,
    endedAt = endedAt,
    isActive = isActive,
    note = note,
  )

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
