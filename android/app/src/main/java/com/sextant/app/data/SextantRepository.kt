package com.sextant.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class SextantRepository(context: Context) {
  private val database = SextantDatabase(context)

  fun loadState(filter: LedgerFilter = LedgerFilter(Calendar.getInstance().startOfMonth())): AppState {
    val db = database.readableDatabase
    val categories = listCategories(db)
    val paymentMethods = listPaymentMethods(db)
    val monthEnd = filter.monthStart.endOfMonth()
    val transactions = listTransactions(db, categories, paymentMethods, filter, monthEnd)
    val now = Calendar.getInstance()
    val todayStart = now.startOfDay()
    val monthStart = filter.monthStart
    val groups = transactions.groupByDay()

    return AppState(
      categories = categories,
      paymentMethods = paymentMethods,
      transactions = transactions,
      groups = groups,
      todaySummary = summarize(transactions.filter { it.transaction.transactionDate >= todayStart && it.transaction.transactionDate < todayStart + DAY_MILLIS }),
      monthSummary = summarize(transactions),
      categoryBreakdown = breakdown(transactions.filter {
        it.transaction.type == TransactionType.Expense && it.includeInSpending
      }),
      budgets = budgetProgress(db, categories, transactions, monthStart),
    )
  }

  fun addTransaction(
    type: TransactionType,
    amountCents: Long,
    categoryId: String?,
    paymentMethodId: String?,
    description: String,
    transactionDate: Long,
  ) {
    val now = System.currentTimeMillis()
    val db = database.writableDatabase
    db.beginTransaction()
    try {
      db.execSQL(
        """
        INSERT INTO transactions
          (id, categoryId, paymentMethodId, amountCents, type, description, transactionDate, createdAt, updatedAt)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        arrayOf<Any?>(
          UUID.randomUUID().toString(),
          categoryId,
          paymentMethodId,
          amountCents,
          type.dbValue,
          description,
          transactionDate,
          now,
          now,
        ),
      )
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun updateTransaction(
    id: String,
    type: TransactionType,
    amountCents: Long,
    categoryId: String?,
    paymentMethodId: String?,
    description: String,
    transactionDate: Long,
  ) {
    database.writableDatabase.execSQL(
      """
      UPDATE transactions SET type = ?, amountCents = ?, categoryId = ?, paymentMethodId = ?,
        description = ?, transactionDate = ?, updatedAt = ? WHERE id = ?
      """.trimIndent(),
      arrayOf<Any?>(type.dbValue, amountCents, categoryId, paymentMethodId, description.trim(), transactionDate, System.currentTimeMillis(), id),
    )
  }

  fun deleteTransaction(id: String) {
    database.writableDatabase.delete("transactions", "id = ?", arrayOf(id))
  }

  fun upsertBudget(categoryId: String, amountCents: Long) {
    val db = database.writableDatabase
    val startDate = Calendar.getInstance().startOfMonth()
    val existing = db.rawQuery(
      "SELECT id FROM budgets WHERE categoryId = ? AND period = 'month' AND startDate = ?",
      arrayOf(categoryId, startDate.toString()),
    )
    existing.use { cursor ->
      if (cursor.moveToFirst()) {
        db.execSQL(
          "UPDATE budgets SET amountCents = ?, isActive = 1 WHERE id = ?",
          arrayOf(amountCents, cursor.getString(0)),
        )
      } else {
        db.execSQL(
          "INSERT INTO budgets (id, categoryId, amountCents, period, startDate, alertThreshold, isActive) VALUES (?, ?, ?, 'month', ?, 0.8, 1)",
          arrayOf<Any>(UUID.randomUUID().toString(), categoryId, amountCents, startDate),
        )
      }
    }
  }

  fun addCategory(
    name: String,
    type: TransactionType,
    color: Long,
    includeInSpending: Boolean,
    parentCategoryId: String?,
  ) {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return
    val now = System.currentTimeMillis()
    database.writableDatabase.execSQL(
      """
      INSERT INTO categories
        (id, name, type, color, includeInSpending, parentCategoryId, isBuiltIn, isActive, createdAt)
      VALUES (?, ?, ?, ?, ?, ?, 0, 1, ?)
      """.trimIndent(),
      arrayOf<Any?>(
        UUID.randomUUID().toString(),
        trimmedName,
        type.dbValue,
        color,
        if (includeInSpending) 1 else 0,
        parentCategoryId,
        now,
      ),
    )
  }

  fun updateCategory(
    id: String,
    name: String,
    type: TransactionType,
    color: Long,
    includeInSpending: Boolean,
    parentCategoryId: String?,
  ) {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return
    database.writableDatabase.execSQL(
      """
      UPDATE categories
      SET name = ?, type = ?, color = ?, includeInSpending = ?, parentCategoryId = ?
      WHERE id = ?
      """.trimIndent(),
      arrayOf<Any?>(
        trimmedName,
        type.dbValue,
        color,
        if (includeInSpending) 1 else 0,
        parentCategoryId,
        id,
      ),
    )
  }

  fun archiveCategory(id: String) {
    database.writableDatabase.execSQL(
      "UPDATE categories SET isActive = 0 WHERE id = ? OR parentCategoryId = ?",
      arrayOf(id, id),
    )
  }

  fun exportCsv(): String {
    val rows = mutableListOf("date,type,category,paymentMethod,amount,description")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    database.readableDatabase.rawQuery(
      """
      SELECT t.transactionDate, t.type,
        CASE WHEN parent.name IS NULL THEN COALESCE(c.name, '未分类') ELSE parent.name || ' / ' || c.name END,
        COALESCE(p.name, '未设置'), t.amountCents, t.description
      FROM transactions t
      LEFT JOIN categories c ON c.id = t.categoryId
      LEFT JOIN categories parent ON parent.id = c.parentCategoryId
      LEFT JOIN payment_methods p ON p.id = t.paymentMethodId
      ORDER BY t.transactionDate DESC
      """.trimIndent(),
      emptyArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        rows += listOf(
          dateFormat.format(cursor.getLong(0)),
          cursor.getString(1),
          cursor.getString(2),
          cursor.getString(3),
          "%.2f".format(Locale.US, cursor.getLong(4) / 100.0),
          cursor.getString(5).replace("\"", "\"\""),
        ).joinToString(",") { "\"$it\"" }
      }
    }
    return rows.joinToString("\n")
  }

  private fun listCategories(db: SQLiteDatabase): List<Category> {
    val rows = mutableListOf<Category>()
    db.rawQuery(
      """
      SELECT id, name, type, color, includeInSpending, parentCategoryId
      FROM categories
      WHERE isActive = 1
      ORDER BY type ASC, parentCategoryId IS NOT NULL ASC, createdAt ASC
      """.trimIndent(),
      emptyArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        rows += Category(
          id = cursor.getString(0),
          name = cursor.getString(1),
          type = cursor.getString(2).toTransactionType(),
          color = cursor.getLong(3),
          includeInSpending = cursor.getInt(4) == 1,
          parentCategoryId = cursor.getStringOrNull(5),
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
    filter: LedgerFilter,
    monthEnd: Long,
  ): List<TransactionListItem> {
    val categoryById = categories.associateBy { it.id }
    val paymentById = paymentMethods.associateBy { it.id }
    val rows = mutableListOf<TransactionListItem>()
    db.rawQuery(
      """
      SELECT id, categoryId, paymentMethodId, amountCents, type, description, transactionDate, createdAt, updatedAt
      FROM transactions
      WHERE transactionDate >= ? AND transactionDate < ?
        AND (? = '' OR description LIKE '%' || ? || '%')
        AND (? = '' OR categoryId = ?)
        AND (? = '' OR paymentMethodId = ?)
        AND (? = '' OR type = ?)
      ORDER BY transactionDate DESC
      """.trimIndent(),
      arrayOf(
        filter.monthStart.toString(), monthEnd.toString(), filter.query.trim(), filter.query.trim(),
        filter.categoryId.orEmpty(), filter.categoryId.orEmpty(),
        filter.paymentMethodId.orEmpty(), filter.paymentMethodId.orEmpty(),
        filter.type?.dbValue.orEmpty(), filter.type?.dbValue.orEmpty(),
      ),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val category = cursor.getStringOrNull(1)?.let(categoryById::get)
        val paymentMethod = cursor.getStringOrNull(2)?.let(paymentById::get)
        val transaction = Transaction(
          id = cursor.getString(0),
          categoryId = cursor.getStringOrNull(1),
          paymentMethodId = cursor.getStringOrNull(2),
          amountCents = cursor.getLong(3),
          type = cursor.getString(4).toTransactionType(),
          description = cursor.getString(5),
          transactionDate = cursor.getLong(6),
          createdAt = cursor.getLong(7),
          updatedAt = cursor.getLong(8),
        )
        rows += TransactionListItem(
          transaction = transaction,
          categoryName = category?.displayName(categoryById) ?: if (transaction.type == TransactionType.Transfer) "转移记录" else "未分类",
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
      "SELECT categoryId, amountCents FROM budgets WHERE isActive = 1 AND period = 'month' AND startDate = ?",
      arrayOf(monthStart.toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val categoryId = cursor.getString(0)
        val category = categoryById[categoryId] ?: continue
        val budgetCategoryIds = category.childInclusiveIds(categories)
        val spent = transactions
          .filter { it.transaction.categoryId in budgetCategoryIds && it.transaction.type == TransactionType.Expense }
          .sumOf { it.transaction.amountCents }
        rows += BudgetProgress(category.displayName(categoryById), category.color, cursor.getLong(1), spent)
      }
    }
    return rows
  }
}

private fun Category.displayName(categoryById: Map<String, Category>): String {
  val parentName = parentCategoryId?.let(categoryById::get)?.name
  return if (parentName == null) name else "$parentName / $name"
}

private fun Category.childInclusiveIds(categories: List<Category>): Set<String> {
  return categories.filter { it.parentCategoryId == id }.mapTo(mutableSetOf(id)) { it.id }
}

private fun summarize(items: List<TransactionListItem>): Summary {
  val income = items.filter { it.transaction.type == TransactionType.Income }.sumOf { it.transaction.amountCents }
  val expense = items
    .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
    .sumOf { it.transaction.amountCents }
  return Summary(income, expense)
}

private fun breakdown(items: List<TransactionListItem>): List<CategoryBreakdown> {
  return items.groupBy { it.categoryName }
    .map { (categoryName, rows) ->
      CategoryBreakdown(categoryName, rows.first().categoryColor, rows.sumOf { it.transaction.amountCents })
    }
    .sortedByDescending { it.amountCents }
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

private fun Long.endOfMonth(): Long = Calendar.getInstance().run {
  timeInMillis = this@endOfMonth
  add(Calendar.MONTH, 1)
  timeInMillis
}

private const val DAY_MILLIS = 86_400_000L

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
