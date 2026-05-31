package com.sextant.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class SextantDatabase(context: Context) : SQLiteOpenHelper(context, "sextant.db", null, 2) {
  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL("PRAGMA journal_mode = WAL")
    db.execSQL(
      """
      CREATE TABLE accounts (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        type TEXT NOT NULL,
        balance REAL NOT NULL DEFAULT 0,
        currency TEXT NOT NULL DEFAULT 'CNY',
        isActive INTEGER NOT NULL DEFAULT 1,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE categories (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        type TEXT NOT NULL,
        color INTEGER NOT NULL,
        includeInSpending INTEGER NOT NULL DEFAULT 1,
        isBuiltIn INTEGER NOT NULL DEFAULT 0,
        isActive INTEGER NOT NULL DEFAULT 1,
        createdAt INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE payment_methods (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        color INTEGER NOT NULL,
        isBuiltIn INTEGER NOT NULL DEFAULT 0,
        isActive INTEGER NOT NULL DEFAULT 1,
        createdAt INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE transactions (
        id TEXT PRIMARY KEY NOT NULL,
        categoryId TEXT,
        paymentMethodId TEXT,
        accountId TEXT,
        amount REAL NOT NULL,
        type TEXT NOT NULL,
        description TEXT NOT NULL DEFAULT '',
        transactionDate INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE budgets (
        id TEXT PRIMARY KEY NOT NULL,
        categoryId TEXT NOT NULL,
        amount REAL NOT NULL,
        period TEXT NOT NULL,
        startDate INTEGER NOT NULL,
        alertThreshold REAL NOT NULL DEFAULT 0.8,
        isActive INTEGER NOT NULL DEFAULT 1
      )
      """.trimIndent(),
    )
    db.execSQL("CREATE INDEX idx_transactions_date ON transactions(transactionDate)")
    seedDefaults(db)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE transactions ADD COLUMN accountId TEXT")
    }
  }

  private fun seedDefaults(db: SQLiteDatabase) {
    val now = System.currentTimeMillis()
    defaultAccounts.forEach { item ->
      db.execSQL(
        "INSERT INTO accounts (id, name, type, balance, currency, createdAt, updatedAt) VALUES (?, ?, ?, ?, 'CNY', ?, ?)",
        arrayOf<Any>(id(), item.name, item.type, item.balance, now, now),
      )
    }

    defaultCategories.forEach { item ->
      db.execSQL(
        "INSERT INTO categories (id, name, type, color, includeInSpending, isBuiltIn, createdAt) VALUES (?, ?, ?, ?, ?, 1, ?)",
        arrayOf<Any>(id(), item.name, item.type, item.color.toLong(), item.includeInSpending, now),
      )
    }
    defaultPaymentMethods.forEach { item ->
      db.execSQL(
        "INSERT INTO payment_methods (id, name, color, isBuiltIn, isActive, createdAt) VALUES (?, ?, ?, 1, 1, ?)",
        arrayOf<Any>(id(), item.name, item.color.toLong(), now),
      )
    }
  }

  private fun id() = UUID.randomUUID().toString()
}

private data class SeedCategory(
  val name: String,
  val type: String,
  val color: Int,
  val includeInSpending: Int,
)

private data class SeedPaymentMethod(
  val name: String,
  val color: Int,
)

private data class SeedAccount(
  val name: String,
  val type: String,
  val balance: Double,
)

private val defaultAccounts = listOf(
  SeedAccount("现金", "cash", 0.0),
  SeedAccount("银行卡", "bank", 0.0),
  SeedAccount("微信钱包", "wallet", 0.0),
  SeedAccount("支付宝", "wallet", 0.0),
)

private val defaultCategories = listOf(
  SeedCategory("餐饮", "expense", 0xFFFF7043.toInt(), 1),
  SeedCategory("交通", "expense", 0xFF42A5F5.toInt(), 1),
  SeedCategory("购物", "expense", 0xFFAB47BC.toInt(), 1),
  SeedCategory("工资", "income", 0xFF66BB6A.toInt(), 1),
  SeedCategory("兼职", "income", 0xFF26A69A.toInt(), 1),
  SeedCategory("基金定投", "expense", 0xFF5C6BC0.toInt(), 0),
  SeedCategory("储蓄", "expense", 0xFF26A69A.toInt(), 0),
)

private val defaultPaymentMethods = listOf(
  SeedPaymentMethod("银行卡", 0xFF2563EB.toInt()),
  SeedPaymentMethod("微信", 0xFF1F9D66.toInt()),
  SeedPaymentMethod("支付宝", 0xFF0F9F9A.toInt()),
  SeedPaymentMethod("现金", 0xFFD97706.toInt()),
)
