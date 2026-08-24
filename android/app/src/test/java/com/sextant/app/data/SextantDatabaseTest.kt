package com.sextant.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SextantDatabaseTest {
  private lateinit var context: Context
  private var helper: SextantDatabase? = null

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    context.deleteDatabase(DATABASE_NAME)
  }

  @After
  fun tearDown() {
    helper?.close()
    context.deleteDatabase(DATABASE_NAME)
  }

  @Test
  fun freshDatabaseHasCompleteSchemaIndexesAndSeedData() {
    val db = openCurrentDatabase()

    assertEquals(SextantDatabase.DATABASE_VERSION, db.version)
    assertTrue(db.hasTable("categories"))
    assertTrue(db.hasTable("payment_methods"))
    assertTrue(db.hasTable("transactions"))
    assertTrue(db.hasTable("budgets"))
    assertTrue(db.hasTable("app_settings"))
    assertTrue(db.hasTable("automation_events"))
    assertTrue(db.hasTable("classification_rules"))
    assertTrue(db.hasColumn("transactions", "amountCents"))
    assertTrue(db.hasColumn("transactions", "entryOrigin"))
    assertTrue(db.hasColumn("transactions", "automationKind"))
    assertTrue(db.hasColumn("transactions", "sourceEventId"))
    assertTrue(db.hasColumn("transactions", "isUserEdited"))
    assertTrue(db.hasColumn("categories", "parentCategoryId"))
    assertTrue(db.hasIndex("idx_transactions_type_date"))
    assertTrue(db.hasIndex("idx_transactions_category_date"))
    assertTrue(db.hasIndex("idx_transactions_payment_date"))
    assertTrue(db.hasIndex("idx_transactions_source_event"))
    assertEquals(setOf("银行卡", "微信", "支付宝", "现金"), db.stringSet("SELECT name FROM payment_methods"))
    assertEquals(4L, db.longValue("SELECT COUNT(*) FROM payment_methods WHERE isBuiltIn = 1 AND isActive = 1"))
    assertTrue(db.longValue("SELECT COUNT(*) FROM categories") > 7)
  }

  @Test
  fun versionOneUpgradePreservesLedgerDataAndConvertsAmountsToCents() {
    createVersionOneDatabase()

    val db = openCurrentDatabase()

    assertEquals(SextantDatabase.DATABASE_VERSION, db.version)
    assertFalse(db.hasTable("accounts"))
    assertTrue(db.hasColumn("categories", "parentCategoryId"))
    assertEquals("我的分类", db.stringValue("SELECT name FROM categories WHERE id = 'category-old'"))
    assertEquals("旧来源", db.stringValue("SELECT name FROM payment_methods WHERE id = 'source-old'"))
    assertEquals(1_234L, db.longValue("SELECT amountCents FROM transactions WHERE id = 'transaction-old'"))
    assertEquals("旧流水", db.stringValue("SELECT description FROM transactions WHERE id = 'transaction-old'"))
    assertEquals(56_789L, db.longValue("SELECT amountCents FROM budgets WHERE id = 'budget-old'"))
    assertTrue(db.hasTable("app_settings"))
    assertTrue(db.hasTable("automation_events"))
    assertTrue(db.hasTable("classification_rules"))
    assertEquals("manual", db.stringValue("SELECT entryOrigin FROM transactions WHERE id = 'transaction-old'"))
    assertTrue(db.hasIndex("idx_transactions_payment_date"))
  }

  private fun openCurrentDatabase(): SQLiteDatabase {
    helper = SextantDatabase(context)
    return helper!!.writableDatabase
  }

  private fun createVersionOneDatabase() {
    context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
      db.execSQL(
        "CREATE TABLE accounts (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, balance REAL NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT 'CNY', isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE categories (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, color INTEGER NOT NULL, includeInSpending INTEGER NOT NULL DEFAULT 1, isBuiltIn INTEGER NOT NULL DEFAULT 0, isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE payment_methods (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL DEFAULT 0, isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE transactions (id TEXT PRIMARY KEY NOT NULL, categoryId TEXT, paymentMethodId TEXT, amount REAL NOT NULL, type TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', transactionDate INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE budgets (id TEXT PRIMARY KEY NOT NULL, categoryId TEXT NOT NULL, amount REAL NOT NULL, period TEXT NOT NULL, startDate INTEGER NOT NULL, alertThreshold REAL NOT NULL DEFAULT 0.8, isActive INTEGER NOT NULL DEFAULT 1)",
      )
      db.execSQL("CREATE INDEX idx_transactions_date ON transactions(transactionDate)")
      db.execSQL("INSERT INTO accounts VALUES ('account-old', '旧账户', 'cash', 0, 'CNY', 1, 1, 1)")
      db.execSQL("INSERT INTO categories VALUES ('category-old', '我的分类', 'expense', 1, 1, 0, 1, 1)")
      db.execSQL("INSERT INTO payment_methods VALUES ('source-old', '旧来源', 2, 0, 1, 1)")
      db.execSQL("INSERT INTO transactions VALUES ('transaction-old', 'category-old', 'source-old', 12.34, 'expense', '旧流水', 10, 11, 12)")
      db.execSQL("INSERT INTO budgets VALUES ('budget-old', 'category-old', 567.89, 'month', 1, 0.8, 1)")
      db.version = 1
    }
  }

  private fun SQLiteDatabase.hasTable(name: String): Boolean = rawQuery(
    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
    arrayOf(name),
  ).use { it.moveToFirst() }

  private fun SQLiteDatabase.hasIndex(name: String): Boolean = rawQuery(
    "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
    arrayOf(name),
  ).use { it.moveToFirst() }

  private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean = rawQuery(
    "PRAGMA table_info($table)",
    emptyArray(),
  ).use { cursor ->
    while (cursor.moveToNext()) {
      if (cursor.getString(1) == column) return@use true
    }
    false
  }

  private fun SQLiteDatabase.stringSet(sql: String): Set<String> = rawQuery(sql, emptyArray()).use { cursor ->
    buildSet {
      while (cursor.moveToNext()) add(cursor.getString(0))
    }
  }

  private fun SQLiteDatabase.stringValue(sql: String): String = rawQuery(sql, emptyArray()).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getString(0)
  }

  private fun SQLiteDatabase.longValue(sql: String): Long = rawQuery(sql, emptyArray()).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getLong(0)
  }

  private companion object {
    const val DATABASE_NAME = "sextant.db"
  }
}
