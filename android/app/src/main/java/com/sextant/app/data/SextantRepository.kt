package com.sextant.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class SextantRepository(context: Context) {
  private val database = SextantDatabase(context)

  fun loadState(): AppState {
    val db = database.readableDatabase
    val categories = listCategories(db)
    val paymentMethods = listPaymentMethods(db)
    val transactions = listTransactions(db, categories, paymentMethods)
    val now = Calendar.getInstance()
    val todayStart = now.startOfDay()
    val monthStart = now.startOfMonth()
    val groups = transactions.groupByDay()

    return AppState(
      categories = categories,
      paymentMethods = paymentMethods,
      transactions = transactions,
      groups = groups,
      todaySummary = summarize(transactions.filter { it.transaction.transactionDate >= todayStart }),
      monthSummary = summarize(transactions.filter { it.transaction.transactionDate >= monthStart }),
      categoryBreakdown = breakdown(transactions.filter {
        it.transaction.transactionDate >= monthStart && it.transaction.type == TransactionType.Expense && it.includeInSpending
      }),
      budgets = budgetProgress(db, categories, transactions, monthStart),
    )
  }

  fun addTransaction(
    type: TransactionType,
    amount: Double,
    categoryId: String?,
    paymentMethodId: String?,
    description: String,
  ) {
    val now = System.currentTimeMillis()
    database.writableDatabase.execSQL(
      """
      INSERT INTO transactions
        (id, categoryId, paymentMethodId, amount, type, description, transactionDate, createdAt, updatedAt)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """.trimIndent(),
      arrayOf(
        UUID.randomUUID().toString(),
        categoryId,
        paymentMethodId,
        amount,
        type.dbValue,
        description,
        now,
        now,
        now,
      ),
    )
  }

  fun upsertBudget(categoryId: String, amount: Double) {
    val db = database.writableDatabase
    val startDate = Calendar.getInstance().startOfMonth()
    val existing = db.rawQuery(
      "SELECT id FROM budgets WHERE categoryId = ? AND period = 'month' AND startDate = ?",
      arrayOf(categoryId, startDate.toString()),
    )
    existing.use { cursor ->
      if (cursor.moveToFirst()) {
        db.execSQL(
          "UPDATE budgets SET amount = ?, isActive = 1 WHERE id = ?",
          arrayOf(amount, cursor.getString(0)),
        )
      } else {
        db.execSQL(
          "INSERT INTO budgets (id, categoryId, amount, period, startDate, alertThreshold, isActive) VALUES (?, ?, ?, 'month', ?, 0.8, 1)",
          arrayOf(UUID.randomUUID().toString(), categoryId, amount, startDate),
        )
      }
    }
  }

  fun exportCsv(): String {
    val state = loadState()
    val rows = mutableListOf("date,type,category,paymentMethod,amount,description")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    state.transactions.forEach { item ->
      rows += listOf(
        dateFormat.format(item.transaction.transactionDate),
        item.transaction.type.dbValue,
        item.categoryName,
        item.paymentMethodName,
        "%.2f".format(Locale.US, item.transaction.amount),
        item.transaction.description.replace("\"", "\"\""),
      ).joinToString(",") { "\"$it\"" }
    }
    return rows.joinToString("\n")
  }

  private fun listCategories(db: SQLiteDatabase): List<Category> {
    val rows = mutableListOf<Category>()
    db.rawQuery(
      "SELECT id, name, type, color, includeInSpending FROM categories WHERE isActive = 1 ORDER BY createdAt ASC",
      emptyArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        rows += Category(
          id = cursor.getString(0),
          name = cursor.getString(1),
          type = cursor.getString(2).toTransactionType(),
          color = cursor.getLong(3),
          includeInSpending = cursor.getInt(4) == 1,
        )
      }
    }
    return rows
  }

  private fun listPaymentMethods(db: SQLiteDatabase): List<PaymentMethod> {
    val rows = mutableListOf<PaymentMethod>()
    db.rawQuery(
      "SELECT id, name, color FROM payment_methods WHERE isActive = 1 ORDER BY createdAt ASC",
      emptyArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        rows += PaymentMethod(cursor.getString(0), cursor.getString(1), cursor.getLong(2))
      }
    }
    return rows
  }

  private fun listTransactions(
    db: SQLiteDatabase,
    categories: List<Category>,
    paymentMethods: List<PaymentMethod>,
  ): List<TransactionListItem> {
    val categoryById = categories.associateBy { it.id }
    val paymentById = paymentMethods.associateBy { it.id }
    val rows = mutableListOf<TransactionListItem>()
    db.rawQuery(
      """
      SELECT id, categoryId, paymentMethodId, amount, type, description, transactionDate, createdAt, updatedAt
      FROM transactions
      ORDER BY transactionDate DESC
      LIMIT 150
      """.trimIndent(),
      emptyArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val category = cursor.getStringOrNull(1)?.let(categoryById::get)
        val paymentMethod = cursor.getStringOrNull(2)?.let(paymentById::get)
        val transaction = Transaction(
          id = cursor.getString(0),
          categoryId = cursor.getStringOrNull(1),
          paymentMethodId = cursor.getStringOrNull(2),
          amount = cursor.getDouble(3),
          type = cursor.getString(4).toTransactionType(),
          description = cursor.getString(5),
          transactionDate = cursor.getLong(6),
          createdAt = cursor.getLong(7),
          updatedAt = cursor.getLong(8),
        )
        rows += TransactionListItem(
          transaction = transaction,
          categoryName = category?.name ?: if (transaction.type == TransactionType.Transfer) "转移记录" else "未分类",
          categoryColor = category?.color ?: 0xFF3B82F6,
          paymentMethodName = paymentMethod?.name ?: "未设置",
          includeInSpending = category?.includeInSpending ?: true,
        )
      }
    }
    return rows
  }

  private fun budgetProgress(
    db: SQLiteDatabase,
    categories: List<Category>,
    transactions: List<TransactionListItem>,
    monthStart: Long,
  ): List<BudgetProgress> {
    val categoryById = categories.associateBy { it.id }
    val rows = mutableListOf<BudgetProgress>()
    db.rawQuery(
      "SELECT categoryId, amount FROM budgets WHERE isActive = 1 AND period = 'month' AND startDate = ?",
      arrayOf(monthStart.toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val categoryId = cursor.getString(0)
        val category = categoryById[categoryId] ?: continue
        val spent = transactions
          .filter { it.transaction.categoryId == categoryId && it.transaction.type == TransactionType.Expense }
          .sumOf { it.transaction.amount }
        rows += BudgetProgress(category.name, category.color, cursor.getDouble(1), spent)
      }
    }
    return rows
  }
}

private fun summarize(items: List<TransactionListItem>): Summary {
  val income = items.filter { it.transaction.type == TransactionType.Income }.sumOf { it.transaction.amount }
  val expense = items
    .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
    .sumOf { it.transaction.amount }
  return Summary(income, expense)
}

private fun breakdown(items: List<TransactionListItem>): List<CategoryBreakdown> {
  return items.groupBy { it.categoryName }
    .map { (categoryName, rows) ->
      CategoryBreakdown(categoryName, rows.first().categoryColor, rows.sumOf { it.transaction.amount })
    }
    .sortedByDescending { it.amount }
}

private fun List<TransactionListItem>.groupByDay(): List<DailyTransactionGroup> {
  val formatter = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)
  return groupBy { formatter.format(it.transaction.transactionDate) }
    .map { (label, items) -> DailyTransactionGroup(label, items) }
}

private fun Calendar.startOfDay(): Long {
  set(Calendar.HOUR_OF_DAY, 0)
  set(Calendar.MINUTE, 0)
  set(Calendar.SECOND, 0)
  set(Calendar.MILLISECOND, 0)
  return timeInMillis
}

private fun Calendar.startOfMonth(): Long {
  set(Calendar.DAY_OF_MONTH, 1)
  return startOfDay()
}

private val TransactionType.dbValue: String
  get() = when (this) {
    TransactionType.Expense -> "expense"
    TransactionType.Income -> "income"
    TransactionType.Transfer -> "transfer"
  }

private fun String.toTransactionType() = when (this) {
  "income" -> TransactionType.Income
  "transfer" -> TransactionType.Transfer
  else -> TransactionType.Expense
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? {
  return if (isNull(index)) null else getString(index)
}
