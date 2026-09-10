package com.sextant.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.sextant.app.automation.AutomationSecretStore
import com.sextant.app.automation.AUTOMATION_WORK_TAG
import com.sextant.app.automation.NotificationListenerConnection
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SextantRepository(context: Context) {
  private val appContext = context.applicationContext
  internal val database = SextantDatabase(context)
  private val backupStore = BackupStore(database)
  internal val automationStore = AutomationStore(database)
  internal val automationSecretStore = AutomationSecretStore(context.applicationContext)
  val notificationListenerConnection = NotificationListenerConnection.create(appContext)
  private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val changes: SharedFlow<Unit> = _changes.asSharedFlow()

  fun loadState(filter: LedgerFilter = LedgerFilter(Calendar.getInstance().startOfMonth())): AppState {
    val db = database.readableDatabase
    val categories = listCategories(db)
    val allPaymentMethods = listPaymentMethods(db)
    val paymentMethods = allPaymentMethods.filter { it.isActive }
    val monthEnd = filter.monthStart.endOfMonth()
    val transactions = listTransactions(db, categories, allPaymentMethods, filter, monthEnd)
    val now = Calendar.getInstance()
    val todayStart = now.startOfDay()
    val monthStart = filter.monthStart
    val groups = transactions.groupByDay()
    val analysisTransactions = listTransactions(
      db,
      categories,
      allPaymentMethods,
      LedgerFilter(monthStart),
      monthEnd,
    )
    val previousMonthStart = monthStart.shiftMonth(-1)
    val previousTransactions = listTransactions(
      db,
      categories,
      allPaymentMethods,
      LedgerFilter(previousMonthStart),
      monthStart,
    )
    val currentBreakdown = LedgerCalculations.breakdown(analysisTransactions.filter {
      it.transaction.type == TransactionType.Expense && it.includeInSpending
    })
    val previousBreakdown = LedgerCalculations.breakdown(previousTransactions.filter {
      it.transaction.type == TransactionType.Expense && it.includeInSpending
    }).associateBy { it.categoryName }
    val analysisSummary = LedgerCalculations.summarize(analysisTransactions)

    return AppState(
      categories = categories,
      paymentMethods = paymentMethods,
      allPaymentMethods = allPaymentMethods,
      transactions = transactions,
      groups = groups,
      todaySummary = LedgerCalculations.summarize(transactions.filter { it.transaction.transactionDate >= todayStart && it.transaction.transactionDate < todayStart + DAY_MILLIS }),
      monthSummary = analysisSummary,
      categoryBreakdown = currentBreakdown.map { it.copy(previousAmountCents = previousBreakdown[it.categoryName]?.amountCents ?: 0) },
      budgets = budgetProgress(db, categories, analysisTransactions, monthStart),
      monthlyBudgetCents = monthlyBudget(db),
      previousMonthSummary = LedgerCalculations.summarize(previousTransactions),
      dailySpending = LedgerCalculations.dailySpending(analysisTransactions, monthStart),
      projectedExpenseCents = LedgerCalculations.projectedExpense(analysisSummary.expenseCents, monthStart),
      automationSettings = automationStore.loadSettings(),
      automationEvents = automationStore.listRecentEvents(),
    )
  }

  fun setMonthlyBudget(amountCents: Long) {
    require(amountCents > 0) { "月预算金额必须大于 0" }
    database.writableDatabase.execSQL(
      "INSERT OR REPLACE INTO app_settings (key, value) VALUES ('monthly_budget_cents', ?)",
      arrayOf(amountCents.toString()),
    )
  }

  fun loadThemePreference(): ThemePreference {
    database.readableDatabase.rawQuery(
      "SELECT value FROM app_settings WHERE key = 'theme_preference'",
      emptyArray(),
    ).use { cursor ->
      return ThemePreference.fromStorage(if (cursor.moveToFirst()) cursor.getString(0) else null)
    }
  }

  fun setThemePreference(preference: ThemePreference) {
    if (loadThemePreference() == preference) return
    database.writableDatabase.execSQL(
      "INSERT OR REPLACE INTO app_settings (key, value) VALUES ('theme_preference', ?)",
      arrayOf(preference.storageValue),
    )
    notifyChanged()
  }

  fun addTransaction(
    type: TransactionType,
    amountCents: Long,
    categoryId: String?,
    paymentMethodId: String?,
    description: String,
    transactionDate: Long,
  ) {
    require(amountCents > 0) { "流水金额必须大于 0" }
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
          description.trim(),
          transactionDate,
          now,
          now,
        ),
      )
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    notifyChanged()
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
    require(amountCents > 0) { "流水金额必须大于 0" }
    database.writableDatabase.execSQL(
      """
      UPDATE transactions SET type = ?, amountCents = ?, categoryId = ?, paymentMethodId = ?,
        description = ?, transactionDate = ?, updatedAt = ?,
        isUserEdited = CASE WHEN entryOrigin = 'notification' THEN 1 ELSE isUserEdited END
      WHERE id = ?
      """.trimIndent(),
      arrayOf<Any?>(type.dbValue, amountCents, categoryId, paymentMethodId, description.trim(), transactionDate, System.currentTimeMillis(), id),
    )
    notifyChanged()
  }

  fun deleteTransaction(id: String) {
    database.writableDatabase.delete("transactions", "id = ?", arrayOf(id))
    notifyChanged()
  }

  fun loadAutomationSettings(): AutomationSettings = automationStore.loadSettings()

  fun updateAutomationSettings(settings: AutomationSettings) {
    automationStore.updateSettings(settings)
    notifyChanged()
  }

  fun listAutomationEvents(): List<AutomationEventSummary> = automationStore.listRecentEvents()

  fun findTransaction(id: String): TransactionListItem? {
    val transactionDate = database.readableDatabase.rawQuery(
      "SELECT transactionDate FROM transactions WHERE id = ?",
      arrayOf(id),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null } ?: return null
    val monthStart = Calendar.getInstance().apply {
      timeInMillis = transactionDate
      set(Calendar.DAY_OF_MONTH, 1)
    }.startOfDay()
    val categories = listCategories(database.readableDatabase)
    val paymentMethods = listPaymentMethods(database.readableDatabase)
    return listTransactions(
      database.readableDatabase,
      categories,
      paymentMethods,
      LedgerFilter(monthStart),
      monthStart.endOfMonth(),
    ).firstOrNull { it.transaction.id == id }
  }

  fun saveClassificationRule(
    sourcePackage: String,
    direction: TransactionType,
    kind: AutomationKind,
    pattern: String,
    categoryId: String,
  ) {
    automationStore.saveRule(sourcePackage, direction, kind, pattern, categoryId)
    notifyChanged()
  }

  internal fun notifyChanged() {
    _changes.tryEmit(Unit)
  }

  fun upsertBudget(categoryId: String, amountCents: Long) {
    require(categoryId.isNotBlank()) { "预算分类不能为空" }
    require(amountCents > 0) { "预算金额必须大于 0" }
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
    require(parentCategoryId != id) { "分类不能设为自己的子分类" }
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

  fun addPaymentMethod(name: String, color: Long) {
    val trimmedName = name.trim()
    require(trimmedName.isNotBlank()) { "来源名称不能为空" }
    require(trimmedName.length <= MAX_PAYMENT_METHOD_NAME_LENGTH) { "来源名称不能超过 40 个字符" }
    requirePaymentMethodNameAvailable(trimmedName)
    database.writableDatabase.execSQL(
      "INSERT INTO payment_methods (id, name, color, isBuiltIn, isActive, createdAt) VALUES (?, ?, ?, 0, 1, ?)",
      arrayOf<Any>(UUID.randomUUID().toString(), trimmedName, color, System.currentTimeMillis()),
    )
  }

  fun updatePaymentMethod(id: String, name: String, color: Long) {
    val trimmedName = name.trim()
    require(trimmedName.isNotBlank()) { "来源名称不能为空" }
    require(trimmedName.length <= MAX_PAYMENT_METHOD_NAME_LENGTH) { "来源名称不能超过 40 个字符" }
    requirePaymentMethodNameAvailable(trimmedName, excludingId = id)
    database.writableDatabase.execSQL(
      "UPDATE payment_methods SET name = ?, color = ? WHERE id = ?",
      arrayOf<Any>(trimmedName, color, id),
    )
  }

  fun setPaymentMethodActive(id: String, isActive: Boolean) {
    database.writableDatabase.execSQL(
      "UPDATE payment_methods SET isActive = ? WHERE id = ?",
      arrayOf<Any>(if (isActive) 1 else 0, id),
    )
  }

  fun deletePaymentMethod(id: String) {
    val db = database.writableDatabase
    db.rawQuery(
      """
      SELECT p.isBuiltIn, COUNT(t.id)
      FROM payment_methods p
      LEFT JOIN transactions t ON t.paymentMethodId = p.id
      WHERE p.id = ?
      GROUP BY p.id
      """.trimIndent(),
      arrayOf(id),
    ).use { cursor ->
      require(cursor.moveToFirst()) { "来源不存在" }
      require(cursor.getInt(0) == 0) { "内置来源不能删除" }
      require(cursor.getLong(1) == 0L) { "已被流水使用的来源不能删除" }
    }
    db.delete("payment_methods", "id = ?", arrayOf(id))
  }

  fun exportBackup(): String = backupStore.export()

  fun restoreBackup(content: String): BackupSummary {
    runCatching { WorkManager.getInstance(appContext).cancelAllWorkByTag(AUTOMATION_WORK_TAG) }
    val summary = backupStore.restore(content)
    notifyChanged()
    return summary
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
      """
      SELECT p.id, p.name, p.color, p.isBuiltIn, p.isActive, COUNT(t.id)
      FROM payment_methods p
      LEFT JOIN transactions t ON t.paymentMethodId = p.id
      GROUP BY p.id
      ORDER BY p.isActive DESC, p.createdAt ASC
      """.trimIndent(),
      emptyArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        rows += PaymentMethod(
          id = cursor.getString(0),
          name = cursor.getString(1),
          color = cursor.getLong(2),
          isBuiltIn = cursor.getInt(3) == 1,
          isActive = cursor.getInt(4) == 1,
          transactionCount = cursor.getLong(5),
        )
      }
    }
    return rows
  }

  private fun requirePaymentMethodNameAvailable(name: String, excludingId: String? = null) {
    val sql = if (excludingId == null) {
      "SELECT 1 FROM payment_methods WHERE name = ? COLLATE NOCASE LIMIT 1"
    } else {
      "SELECT 1 FROM payment_methods WHERE name = ? COLLATE NOCASE AND id != ? LIMIT 1"
    }
    val args = if (excludingId == null) arrayOf(name) else arrayOf(name, excludingId)
    database.readableDatabase.rawQuery(
      sql,
      args,
    ).use { cursor ->
      require(!cursor.moveToFirst()) { "来源名称已存在" }
    }
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
      SELECT id, categoryId, paymentMethodId, amountCents, type, description, transactionDate, createdAt, updatedAt,
        entryOrigin, automationKind, isUserEdited
      FROM transactions
      WHERE transactionDate >= ? AND transactionDate < ?
        AND (? = '' OR description LIKE '%' || ? || '%')
        AND (? = '' OR categoryId = ? OR categoryId IN (
          SELECT id FROM categories WHERE parentCategoryId = ?
        ))
        AND (? = '' OR paymentMethodId = ?)
        AND (? = '' OR type = ?)
      ORDER BY transactionDate DESC
      """.trimIndent(),
      arrayOf(
        filter.monthStart.toString(), monthEnd.toString(), filter.query.trim(), filter.query.trim(),
        filter.categoryId.orEmpty(), filter.categoryId.orEmpty(), filter.categoryId.orEmpty(),
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
          entryOrigin = cursor.getString(9).toEntryOrigin(),
          automationKind = cursor.getStringOrNull(10)?.toAutomationKind(),
          isUserEdited = cursor.getInt(11) == 1,
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

  private fun monthlyBudget(db: SQLiteDatabase): Long? {
    db.rawQuery("SELECT value FROM app_settings WHERE key = 'monthly_budget_cents'", emptyArray()).use { cursor ->
      return if (cursor.moveToFirst()) cursor.getString(0).toLongOrNull() else null
    }
  }
}

private fun Category.displayName(categoryById: Map<String, Category>): String {
  val parentName = parentCategoryId?.let(categoryById::get)?.name
  return if (parentName == null) name else "$parentName / $name"
}

private fun Category.childInclusiveIds(categories: List<Category>): Set<String> {
  return categories.filter { it.parentCategoryId == id }.mapTo(mutableSetOf(id)) { it.id }
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

private fun Long.shiftMonth(amount: Int): Long = Calendar.getInstance().run {
  timeInMillis = this@shiftMonth
  add(Calendar.MONTH, amount)
  timeInMillis
}

private const val DAY_MILLIS = 86_400_000L
private const val MAX_PAYMENT_METHOD_NAME_LENGTH = 40

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

private fun String.toEntryOrigin() = if (this == "notification") EntryOrigin.Notification else EntryOrigin.Manual

private fun String.toAutomationKind() = AutomationKind.entries.firstOrNull {
  it.name.equals(this, ignoreCase = true)
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? {
  return if (isNull(index)) null else getString(index)
}
