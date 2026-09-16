package com.sextant.app.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.sextant.app.automation.AUTOMATION_WORK_TAG
import com.sextant.app.automation.AutomationSecretStore
import com.sextant.app.automation.NotificationListenerConnection
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 账本的读写入口。
 *
 * 所有方法都是 `suspend`：Room 不允许在主线程上碰数据库，而账本查询本身是重活
 * （一次 [loadState] 要跑四五条聚合）。这里因此不存在「同步版本」——调用方要么在协程里
 * 调用，要么自己说明为什么必须阻塞。
 *
 * 写入成功后通过 [changes] 通知界面重载。发一条信号而不是把 [AppState] 做成 `Flow`，
 * 是因为这份状态是「筛选条件 + 当日 + 本月 + 上月」的联合投影：做成 Flow 就要在每条
 * DAO 查询上重复一遍同样的拼装逻辑，收益却只是省掉一次显式重载。
 */
class SextantRepository(context: Context) {
  private val appContext = context.applicationContext
  internal val database = SextantDatabase.get(context)
  private val backupStore = BackupStore(database)
  internal val automationStore = AutomationStore(database)
  internal val automationSecretStore = AutomationSecretStore(context.applicationContext)
  val notificationListenerConnection = NotificationListenerConnection.create(appContext)
  private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val changes: SharedFlow<Unit> = _changes.asSharedFlow()

  suspend fun loadState(filter: LedgerFilter = LedgerFilter.allTime()): AppState {
    val categories = listCategories()
    val allPaymentMethods = listPaymentMethods()
    val paymentMethods = allPaymentMethods.filter { it.isActive }
    val transactions = listTransactions(categories, allPaymentMethods, filter)
    val (todayStart, todayEnd) = todayRange()
    val todayTransactions = listTransactions(
      categories,
      allPaymentMethods,
      LedgerFilter(rangeStart = todayStart, rangeEnd = todayEnd),
    )
    val monthStart = filter.anchorMonthStart
    val monthEnd = monthRangeOf(monthStart).second
    val groups = transactions.groupByDay()
    val analysisTransactions = listTransactions(
      categories,
      allPaymentMethods,
      LedgerFilter(rangeStart = monthStart, rangeEnd = monthEnd),
    )
    val previousMonthStart = monthRangeOf(monthStart - 1).first
    val previousTransactions = listTransactions(
      categories,
      allPaymentMethods,
      LedgerFilter(rangeStart = previousMonthStart, rangeEnd = monthStart),
    )
    val currentBreakdown = LedgerCalculations.breakdown(monthSpending(analysisTransactions), categories)
    val previousBreakdown = LedgerCalculations.breakdown(monthSpending(previousTransactions), categories)
      .associateBy { it.categoryName }
    val analysisSummary = LedgerCalculations.summarize(analysisTransactions)

    return AppState(
      categories = categories,
      paymentMethods = paymentMethods,
      allPaymentMethods = allPaymentMethods,
      transactions = transactions,
      groups = groups,
      todaySummary = LedgerCalculations.summarize(todayTransactions),
      rangeSummary = LedgerCalculations.summarize(transactions),
      monthSummary = analysisSummary,
      categoryBreakdown = currentBreakdown.map { it.copy(previousAmountCents = previousBreakdown[it.categoryName]?.amountCents ?: 0) },
      analysisTransactions = analysisTransactions,
      budgets = budgetProgress(categories, analysisTransactions, monthStart),
      monthlyBudgetCents = monthlyBudget(),
      recurringExpenses = listRecurringExpenses(),
      previousMonthSummary = LedgerCalculations.summarize(previousTransactions),
      dailySpending = LedgerCalculations.dailySpending(analysisTransactions, monthStart),
      projectedExpenseCents = LedgerCalculations.projectedExpense(analysisSummary.expenseCents, monthStart),
      automationSettings = automationStore.loadSettings(),
      automationEvents = automationStore.listRecentEvents(),
    )
  }

  suspend fun setMonthlyBudget(amountCents: Long) {
    require(amountCents > 0) { "月预算金额必须大于 0" }
    database.appSettingDao().upsert(AppSettingEntity(SextantDatabase.MONTHLY_BUDGET_KEY, amountCents.toString()))
  }

  suspend fun loadThemePreference(): ThemePreference =
    ThemePreference.fromStorage(database.appSettingDao().findValue(SextantDatabase.THEME_PREFERENCE_KEY))

  suspend fun setThemePreference(preference: ThemePreference) {
    if (loadThemePreference() == preference) return
    database.appSettingDao().upsert(AppSettingEntity(SextantDatabase.THEME_PREFERENCE_KEY, preference.storageValue))
    notifyChanged()
  }

  suspend fun addTransaction(
    type: TransactionType,
    amountCents: Long,
    categoryId: String?,
    paymentMethodId: String?,
    description: String,
    transactionDate: Long,
  ) {
    require(amountCents > 0) { "流水金额必须大于 0" }
    val now = System.currentTimeMillis()
    database.transactionDao().insert(
      TransactionEntity(
        id = UUID.randomUUID().toString(),
        categoryId = categoryId,
        paymentMethodId = paymentMethodId,
        amountCents = amountCents,
        type = type,
        description = description.trim(),
        transactionDate = transactionDate,
        createdAt = now,
        updatedAt = now,
        entryOrigin = EntryOrigin.Manual,
        automationKind = null,
        sourceEventId = null,
        isUserEdited = false,
      ),
    )
    notifyChanged()
  }

  suspend fun updateTransaction(
    id: String,
    type: TransactionType,
    amountCents: Long,
    categoryId: String?,
    paymentMethodId: String?,
    description: String,
    transactionDate: Long,
  ) {
    require(amountCents > 0) { "流水金额必须大于 0" }
    // 改过的自动入账流水会被打上 isUserEdited 标记，之后通知里的「撤销」就不能再删掉它——
    // 用户的修改优先于通知的过期意图。
    database.transactionDao().update(
      id = id,
      type = type,
      amountCents = amountCents,
      categoryId = categoryId,
      paymentMethodId = paymentMethodId,
      description = description.trim(),
      transactionDate = transactionDate,
      updatedAt = System.currentTimeMillis(),
    )
    notifyChanged()
  }

  suspend fun deleteTransaction(id: String) {
    database.transactionDao().deleteById(id)
    notifyChanged()
  }

  suspend fun loadAutomationSettings(): AutomationSettings = automationStore.loadSettings()

  suspend fun updateAutomationSettings(settings: AutomationSettings) {
    automationStore.updateSettings(settings)
    notifyChanged()
  }

  suspend fun listAutomationEvents(): List<AutomationEventSummary> = automationStore.listRecentEvents()

  /**
   * 按 id 找一笔流水，连它的分类与来源展示信息一起返回。
   *
   * 先查这笔流水落在哪个月，再按那个月取一次明细，是为了复用手写的筛选投影：调用方要的是
   * 和列表里一模一样的一行（分类显示名、来源名、计入支出与否），而不是一条裸记录。
   */
  suspend fun findTransaction(id: String): TransactionListItem? {
    val transactionDate = database.transactionDao().findById(id)?.transactionDate ?: return null
    val categories = listCategories()
    val paymentMethods = listPaymentMethods()
    return listTransactions(categories, paymentMethods, LedgerFilter.forMonth(transactionDate))
      .firstOrNull { it.transaction.id == id }
  }

  suspend fun saveClassificationRule(
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

  suspend fun upsertBudget(categoryId: String, amountCents: Long) {
    require(categoryId.isNotBlank()) { "预算分类不能为空" }
    require(amountCents > 0) { "预算金额必须大于 0" }
    val budgets = database.budgetDao()
    val startDate = LedgerFilter.currentMonth().rangeStart
    val existingId = budgets.findIdForCategoryMonth(categoryId, startDate)
    if (existingId != null) {
      budgets.updateAmount(existingId, amountCents)
    } else {
      budgets.insert(
        BudgetEntity(
          id = UUID.randomUUID().toString(),
          categoryId = categoryId,
          amountCents = amountCents,
          period = "month",
          startDate = startDate,
          alertThreshold = 0.8,
          isActive = true,
        ),
      )
    }
  }

  /**
   * 新增（[id] 为 null）或更新一笔周期支出，返回它的 id。
   *
   * 和预算不同，这里没有「同一分类只能有一条」的约束：住房分类下同时有房租、物业费、宽带
   * 是常态，条目之间靠 id 区分，不靠 `(categoryId, period, startDate)`。
   *
   * [RecurringExpenseDraft.customDays] 只在 [RecurringCycle.CustomDays] 下有意义：其它周期
   * 传进来的值会被归一化成 null 而不是报错，保证库里不会出现「每月 + 30 天」这种自相矛盾的行。
   */
  suspend fun saveRecurringExpense(id: String?, draft: RecurringExpenseDraft): String {
    val trimmedName = draft.name.trim()
    require(trimmedName.isNotBlank()) { "周期支出名称不能为空" }
    require(trimmedName.length <= MAX_RECURRING_NAME_LENGTH) { "周期支出名称不能超过 $MAX_RECURRING_NAME_LENGTH 个字符" }
    require(draft.amountCents > 0) { "周期支出金额必须大于 0" }
    require(draft.anchorDate > 0) { "周期支出需要首次扣费日" }

    val resolvedCustomDays = if (draft.cycle == RecurringCycle.CustomDays) {
      val days = requireNotNull(draft.customDays) { "自定义周期需要填写天数" }
      require(days > 0) { "自定义周期天数必须大于 0" }
      days
    } else {
      null
    }

    val now = System.currentTimeMillis()
    val dao = database.recurringExpenseDao()
    val resolvedId = id ?: UUID.randomUUID().toString()
    val trimmedNote = draft.note.trim()

    if (id == null) {
      dao.insert(
        RecurringExpenseEntity(
          id = resolvedId,
          name = trimmedName,
          amountCents = draft.amountCents,
          cycle = draft.cycle,
          customDays = resolvedCustomDays,
          anchorDate = draft.anchorDate,
          categoryId = draft.categoryId,
          paymentMethodId = draft.paymentMethodId,
          endedAt = draft.endedAt,
          isActive = draft.isActive,
          note = trimmedNote,
          createdAt = now,
          updatedAt = now,
        ),
      )
    } else {
      require(dao.exists(id)) { "周期支出不存在" }
      dao.update(
        id = id,
        name = trimmedName,
        amountCents = draft.amountCents,
        cycle = draft.cycle,
        customDays = resolvedCustomDays,
        anchorDate = draft.anchorDate,
        categoryId = draft.categoryId,
        paymentMethodId = draft.paymentMethodId,
        endedAt = draft.endedAt,
        isActive = draft.isActive,
        note = trimmedNote,
        updatedAt = now,
      )
    }

    notifyChanged()
    return resolvedId
  }

  suspend fun deleteRecurringExpense(id: String) {
    database.recurringExpenseDao().deleteById(id)
    notifyChanged()
  }

  suspend fun setRecurringExpenseActive(id: String, isActive: Boolean) {
    database.recurringExpenseDao().setActive(id, isActive, System.currentTimeMillis())
    notifyChanged()
  }

  suspend fun addCategory(
    name: String,
    type: TransactionType,
    color: Long,
    includeInSpending: Boolean,
    parentCategoryId: String?,
  ) {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return
    database.categoryDao().insert(
      CategoryEntity(
        id = UUID.randomUUID().toString(),
        name = trimmedName,
        type = type,
        color = color,
        includeInSpending = includeInSpending,
        parentCategoryId = parentCategoryId,
        isBuiltIn = false,
        isActive = true,
        createdAt = System.currentTimeMillis(),
      ),
    )
    notifyChanged()
  }

  suspend fun updateCategory(
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
    database.categoryDao().update(
      id = id,
      name = trimmedName,
      type = type,
      color = color,
      includeInSpending = includeInSpending,
      parentCategoryId = parentCategoryId,
    )
    notifyChanged()
  }

  suspend fun archiveCategory(id: String) {
    database.categoryDao().archive(id)
    notifyChanged()
  }

  suspend fun addPaymentMethod(name: String, color: Long) {
    val trimmedName = name.trim()
    require(trimmedName.isNotBlank()) { "来源名称不能为空" }
    require(trimmedName.length <= MAX_PAYMENT_METHOD_NAME_LENGTH) { "来源名称不能超过 40 个字符" }
    requirePaymentMethodNameAvailable(trimmedName)
    database.paymentMethodDao().insert(
      PaymentMethodEntity(
        id = UUID.randomUUID().toString(),
        name = trimmedName,
        color = color,
        isBuiltIn = false,
        isActive = true,
        createdAt = System.currentTimeMillis(),
      ),
    )
    notifyChanged()
  }

  suspend fun updatePaymentMethod(id: String, name: String, color: Long) {
    val trimmedName = name.trim()
    require(trimmedName.isNotBlank()) { "来源名称不能为空" }
    require(trimmedName.length <= MAX_PAYMENT_METHOD_NAME_LENGTH) { "来源名称不能超过 40 个字符" }
    requirePaymentMethodNameAvailable(trimmedName, excludingId = id)
    database.paymentMethodDao().update(id, trimmedName, color)
    notifyChanged()
  }

  suspend fun setPaymentMethodActive(id: String, isActive: Boolean) {
    database.paymentMethodDao().setActive(id, isActive)
    notifyChanged()
  }

  /**
   * 删除来源。内置来源和已经被流水用过的来源都不允许删除。
   *
   * 检查与删除放在同一个事务里：否则「查完发现计数为 0，删除前刚好又记了一笔」会留下
   * 指向不存在来源的流水。
   */
  suspend fun deletePaymentMethod(id: String) {
    database.withTransaction {
      val methods = database.paymentMethodDao()
      val method = requireNotNull(methods.findById(id)) { "来源不存在" }
      require(!method.isBuiltIn) { "内置来源不能删除" }
      val used = methods.listWithUsage().firstOrNull { it.method.id == id }?.transactionCount ?: 0L
      require(used == 0L) { "已被流水使用的来源不能删除" }
      methods.deleteById(id)
    }
    notifyChanged()
  }

  suspend fun exportBackup(): String = backupStore.export()

  suspend fun restoreBackup(content: String): BackupSummary {
    runCatching { WorkManager.getInstance(appContext).cancelAllWorkByTag(AUTOMATION_WORK_TAG) }
    val summary = backupStore.restore(content)
    notifyChanged()
    return summary
  }

  private suspend fun listCategories(): List<Category> =
    database.categoryDao().listActive().map { entity ->
      Category(
        id = entity.id,
        name = entity.name,
        type = entity.type,
        color = entity.color,
        includeInSpending = entity.includeInSpending,
        parentCategoryId = entity.parentCategoryId,
      )
    }

  private suspend fun listPaymentMethods(): List<PaymentMethod> =
    database.paymentMethodDao().listWithUsage().map { row ->
      PaymentMethod(
        id = row.method.id,
        name = row.method.name,
        color = row.method.color,
        isBuiltIn = row.method.isBuiltIn,
        isActive = row.method.isActive,
        transactionCount = row.transactionCount,
      )
    }

  private suspend fun requirePaymentMethodNameAvailable(name: String, excludingId: String? = null) {
    val methods = database.paymentMethodDao()
    val conflict = if (excludingId == null) {
      methods.findIdByName(name)
    } else {
      methods.findIdByNameExcluding(name, excludingId)
    }
    require(conflict == null) { "来源名称已存在" }
  }

  private suspend fun listTransactions(
    categories: List<Category>,
    paymentMethods: List<PaymentMethod>,
    filter: LedgerFilter,
  ): List<TransactionListItem> {
    val categoryById = categories.associateBy { it.id }
    val paymentById = paymentMethods.associateBy { it.id }
    return database.transactionDao().listFiltered(
      rangeStart = filter.rangeStart,
      rangeEnd = filter.rangeEnd,
      query = filter.query.trim(),
      categoryId = filter.categoryId.orEmpty(),
      paymentMethodId = filter.paymentMethodId.orEmpty(),
      // 方向是「传空即不过滤」的筛选项，所以要绑库里的字面量而不是枚举：枚举会被
      // TypeConverter 绑成 "expense"，`:type = ''` 永远不成立，「全部」就退化成「只看支出」，
      // 连按方向拆分的月汇总也跟着只算支出。字面量直接复用实体那一份映射，
      // `BackupStore` / `AutomationStore` 里已经各有一份私有副本，不再抄第三份。
      type = filter.type?.let { transactionTypeStorageValue(it) }.orEmpty(),
      minAmountCents = filter.minAmountCents ?: -1L,
      maxAmountCents = filter.maxAmountCents ?: -1L,
    ).map { row ->
      val category = row.categoryId?.let(categoryById::get)
      val paymentMethod = row.paymentMethodId?.let(paymentById::get)
      val transaction = Transaction(
        id = row.id,
        categoryId = row.categoryId,
        paymentMethodId = row.paymentMethodId,
        amountCents = row.amountCents,
        type = row.type,
        description = row.description,
        transactionDate = row.transactionDate,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
        entryOrigin = row.entryOrigin,
        automationKind = row.automationKind,
        isUserEdited = row.isUserEdited,
      )
      TransactionListItem(
        transaction = transaction,
        categoryName = category?.displayName(categoryById) ?: if (transaction.type == TransactionType.Transfer) "转移记录" else "未分类",
        categoryColor = category?.color ?: 0xFF3B82F6,
        paymentMethodName = paymentMethod?.name ?: "未设置",
        includeInSpending = category?.includeInSpending ?: true,
      )
    }
  }

  private suspend fun budgetProgress(
    categories: List<Category>,
    transactions: List<TransactionListItem>,
    monthStart: Long,
  ): List<BudgetProgress> {
    val categoryById = categories.associateBy { it.id }
    return database.budgetDao().listActiveForMonth(monthStart).mapNotNull { budget ->
      // 已归档分类的预算不再出现在页面上：分类列表里没有它，说明它已经停用。
      val category = categoryById[budget.categoryId] ?: return@mapNotNull null
      val budgetCategoryIds = category.childInclusiveIds(categories)
      val spent = transactions
        .filter { it.transaction.categoryId in budgetCategoryIds && it.transaction.type == TransactionType.Expense }
        .sumOf { it.transaction.amountCents }
      BudgetProgress(category.displayName(categoryById), category.color, budget.amountCents, spent)
    }
  }

  private suspend fun monthlyBudget(): Long? =
    database.appSettingDao().findValue(SextantDatabase.MONTHLY_BUDGET_KEY)?.toLongOrNull()

  /**
   * 全部周期支出，启用的排在前面，同组内按首次扣费日。
   *
   * 「下一次扣费」是按当前时间推算出来的（见 `RecurringSchedule.kt`），不落库，
   * 所以这里不排序也排不了；页面按推算结果再排一次。
   */
  private suspend fun listRecurringExpenses(): List<RecurringExpense> =
    database.recurringExpenseDao().listAll().map { row ->
      RecurringExpense(
        id = row.id,
        name = row.name,
        amountCents = row.amountCents,
        cycle = row.cycle,
        customDays = row.customDays,
        anchorDate = row.anchorDate,
        categoryId = row.categoryId,
        paymentMethodId = row.paymentMethodId,
        endedAt = row.endedAt,
        isActive = row.isActive,
        note = row.note,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
      )
    }
}

/** 计入支出结构的流水：支出，且分类计入支出。 */
private fun monthSpending(items: List<TransactionListItem>): List<TransactionListItem> =
  items.filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }

private fun Category.displayName(categoryById: Map<String, Category>): String {
  val parentName = parentCategoryId?.let(categoryById::get)?.name
  return if (parentName == null) name else "$parentName / $name"
}

private fun Category.childInclusiveIds(categories: List<Category>): Set<String> {
  return categories.filter { it.parentCategoryId == id }.mapTo(mutableSetOf(id)) { it.id }
}

private fun List<TransactionListItem>.groupByDay(): List<DailyTransactionGroup> =
  // 按「本地时区当天零点」分组：跨年但同月同日的流水不会被并到一起，
  // 分组结果也不再依赖展示文案，日期头怎么写由 UI 层决定。
  groupBy { it.transaction.transactionDate.toLocalDate().toStartOfDayMillis() }
    .map { (dayStartMillis, items) -> DailyTransactionGroup(dayStartMillis, items) }

private const val MAX_PAYMENT_METHOD_NAME_LENGTH = 40
private const val MAX_RECURRING_NAME_LENGTH = 20
