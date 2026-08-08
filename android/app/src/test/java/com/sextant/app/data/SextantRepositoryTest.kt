package com.sextant.app.data

import android.content.Context
import java.util.Calendar
import org.junit.After
import org.junit.Assert.assertEquals
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
  fun csvUsesSourceHeaderAndEscapesDescription() {
    val repository = SextantRepository(context)
    val initial = repository.loadState(filter())
    val category = initial.categories.first { it.type == TransactionType.Expense }
    val source = initial.paymentMethods.first()
    repository.addTransaction(
      TransactionType.Expense,
      1_299,
      category.id,
      source.id,
      "咖啡, \"大杯\"",
      now(),
    )

    val csv = repository.exportCsv()

    assertTrue(csv.startsWith("date,type,category,source,amount,description\n"))
    assertTrue(csv.contains("\"12.99\""))
    assertTrue(csv.contains("\"咖啡, \"\"大杯\"\"\""))
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
    db.execSQL("INSERT INTO transactions VALUES ('transaction', 'category', 'source', 100, 'expense', '备份值', 1, 1, 1)")
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

  private fun filter(
    query: String = "",
    type: TransactionType? = null,
  ) = LedgerFilter(monthStart = monthStart(), query = query, type = type)

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
