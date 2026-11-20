package com.sextant.app.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.sextant.app.automation.AUTOMATION_WORK_TAG
import com.sextant.app.automation.AutomationSecretStore
import com.sextant.app.automation.NotificationListenerConnection
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 账本读写入口。成功写入递增版本；界面按当前页面重新读取一致的数据库快照。 */
enum class StateProjection { All, Ledger, Analytics, Plans, Settings }

class SextantRepository(context: Context) {
  private val appContext = context.applicationContext
  internal val database = SextantDatabase.get(context)
  private val backupStore = BackupStore(database)
  internal val automationStore = AutomationStore(database)
  internal val automationSecretStore = AutomationSecretStore(context.applicationContext)
  internal val automationDiagnostics = AutomationDiagnostics(database,
    automationSecretStore::encrypt, automationSecretStore::decrypt)
  val diagnosticEntryCount get() = automationDiagnostics.count
  val recentDiagnostics get() = automationDiagnostics.recent
  val diagnosticWriteError get() = automationDiagnostics.lastError
  val notificationListenerConnection = NotificationListenerConnection.create(appContext)
  private val _changes = MutableStateFlow(0L)
  val changes: StateFlow<Long> = _changes.asStateFlow()

  /** 同一次事务内读取桌面数据，月份固定为真实当前月。 */
  suspend fun loadWidgetSnapshot(now: Long = System.currentTimeMillis()): WidgetSnapshot = withContext(Dispatchers.Default) {
    database.withTransaction {
      val categories = listCategories()
      val methods = listPaymentMethods()
      val month = listTransactions(categories, methods, LedgerFilter.forMonth(now))
      val (start, end) = dayRangeOf(now)
      val today = month.filter { it.transaction.transactionDate in start until end }
      val recent = transactionItems(database.transactionDao().recentSpending(now + 1, 3), categories, methods)
      WidgetSnapshot(
        updatedAt = now,
        todaySummary = LedgerCalculations.summarize(today),
        todayCount = monthSpending(today).size,
        monthSummary = LedgerCalculations.summarize(month),
        monthlyBudgetCents = monthlyBudget(LedgerFilter.forMonth(now).rangeStart),
        recent = recent,
        upcoming = upcomingWidgetCharges(listRecurringExpenses(), now.toLocalDate()),
        theme = loadThemePreference(),
      )
    }
  }

  suspend fun loadState(
    filter: LedgerFilter = LedgerFilter.allTime(),
    projection: StateProjection = StateProjection.All,
    analysisPeriod: AnalysisPeriod = AnalysisPeriod.Month,
  ): AppState = withContext(Dispatchers.Default) {
    database.withTransaction { loadSnapshot(filter, projection, analysisPeriod) }
  }

  private suspend fun loadSnapshot(filter: LedgerFilter, projection: StateProjection, analysisPeriod: AnalysisPeriod): AppState {
    val categories = listCategories()
    val allPaymentMethods = listPaymentMethods()
    val paymentMethods = allPaymentMethods.filter { it.isActive }
    if (projection == StateProjection.Settings) return AppState(
      categories = categories,
      paymentMethods = paymentMethods,
      allPaymentMethods = allPaymentMethods,
      automationSettings = automationStore.loadSettings(),
      automationEvents = automationStore.listRecentEvents(),
      merchantCategoryRules = listMerchantCategoryRules(),
    )
    if (projection == StateProjection.Plans) {
      val month = LedgerFilter.currentMonth()
      val rows = listTransactions(categories, allPaymentMethods, month)
      return AppState(
        categories = categories,
        paymentMethods = paymentMethods,
        allPaymentMethods = allPaymentMethods,
        monthSummary = LedgerCalculations.summarize(rows),
        budgets = budgetProgress(categories, rows, month.rangeStart),
        monthlyBudgetCents = monthlyBudget(month.rangeStart),
        recurringExpenses = listRecurringExpenses(),
      )
    }
    if (projection == StateProjection.Analytics) {
      val window = AnalysisWindow(analysisPeriod, if (filter.isAllTime) java.time.LocalDate.now() else filter.rangeStart.toLocalDate())
      val rows = listTransactions(categories, allPaymentMethods, window.filter)
      val previousRows = listTransactions(categories, allPaymentMethods, window.previous.filter)
      val previous = LedgerCalculations.breakdown(monthSpending(previousRows), categories).associateBy { it.categoryName }
      val previousIncome = LedgerCalculations.breakdown(previousRows.filter(AnalysisMetric.Income::includes), categories)
        .associateBy { it.categoryName }
      val summary = LedgerCalculations.summarize(rows)
      return AppState(
        categories = categories, paymentMethods = paymentMethods, allPaymentMethods = allPaymentMethods,
        monthSummary = summary,
        analysisTransactions = rows,
        categoryBreakdown = LedgerCalculations.breakdown(monthSpending(rows), categories).map {
          it.copy(previousAmountCents = previous[it.categoryName]?.amountCents ?: 0L)
        },
        analysisReport = AnalysisReport(summary, LedgerCalculations.summarize(previousRows), expenseTrend(rows, window),
          incomeTrend = expenseTrend(rows, window, AnalysisMetric.Income),
          incomeBreakdown = LedgerCalculations.breakdown(rows.filter(AnalysisMetric.Income::includes), categories).map {
            it.copy(previousAmountCents = previousIncome[it.categoryName]?.amountCents ?: 0L)
          },
          period = analysisPeriod,
        ),
      )
    }
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
    if (projection == StateProjection.Ledger) return AppState(
      categories = categories,
      paymentMethods = paymentMethods,
      allPaymentMethods = allPaymentMethods,
      transactions = transactions,
      groups = groups,
      todaySummary = LedgerCalculations.summarize(todayTransactions),
      rangeSummary = LedgerCalculations.summarize(transactions),
      monthSummary = LedgerCalculations.summarize(analysisTransactions),
      monthlyBudgetCents = monthlyBudget(monthStart),
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
      monthlyBudgetCents = monthlyBudget(monthStart),
      recurringExpenses = if (projection == StateProjection.All) listRecurringExpenses() else emptyList(),
      previousMonthSummary = LedgerCalculations.summarize(previousTransactions),
      dailySpending = LedgerCalculations.dailySpending(analysisTransactions, monthStart),
      projectedExpenseCents = LedgerCalculations.projectedExpense(analysisSummary.expenseCents, monthStart),
      automationSettings = if (projection == StateProjection.All) automationStore.loadSettings() else AutomationSettings(),
      automationEvents = if (projection == StateProjection.All) automationStore.listRecentEvents() else emptyList(),
      merchantCategoryRules = if (projection == StateProjection.All) listMerchantCategoryRules() else emptyList(),
    )
  }

  suspend fun setMonthlyBudget(amountCents: Long, scope: BudgetScope = BudgetScope.FromThisMonth) {
    require(amountCents > 0) { "月预算金额必须大于 0" }
    writeBudgetRule("", amountCents, scope)
  }

  suspend fun clearMonthlyBudget(scope: BudgetScope = BudgetScope.FromThisMonth) {
    writeBudgetRule("", 0, scope)
  }

  /** 同月例外不会改变后续额度；持续调整会替换当月例外，但不覆盖旧月份。 */
  internal suspend fun writeBudgetRule(
    categoryId: String,
    amountCents: Long,
    scope: BudgetScope,
    month: Long = LedgerFilter.currentMonth().rangeStart,
  ) {
    require(amountCents >= 0) { "预算金额不能小于 0" }
    database.withTransaction {
      val rules = database.budgetRuleDao()
      if (scope == BudgetScope.FromThisMonth) rules.deleteOverride(categoryId, month)
      rules.upsert(BudgetRuleEntity(categoryId, month, scope == BudgetScope.ThisMonth, amountCents))
    }
    notifyChanged()
  }

  suspend fun loadThemePreference(): ThemePreference =
    ThemePreference.fromStorage(database.appSettingDao().findValue(SextantDatabase.THEME_PREFERENCE_KEY))

  suspend fun setThemePreference(preference: ThemePreference) {
    if (loadThemePreference() == preference) return
    database.appSettingDao().upsert(AppSettingEntity(SextantDatabase.THEME_PREFERENCE_KEY, preference.storageValue))
    notifyChanged()
  }

  /** 上次使用的支付来源，跨进程保留；仍以来源是否启用为准，停用后由 QuickEntry 回落到第一个可用来源。 */
  suspend fun loadLastPaymentMethodId(): String? =
    database.appSettingDao().findValue(SextantDatabase.LAST_PAYMENT_METHOD_KEY)?.takeIf { it.isNotBlank() }

  suspend fun setLastPaymentMethodId(id: String?) {
    database.appSettingDao().upsert(
      AppSettingEntity(SextantDatabase.LAST_PAYMENT_METHOD_KEY, id.orEmpty()),
    )
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

  /**
   * [expectedUpdatedAt] 可保护基于旧快照发起的编辑；Agent 等异步写入者应传入读到的值。
   * 校验和更新在同一个事务内，冲突时连商户分类规则也不会写入。
   */
  suspend fun updateTransaction(
    id: String,
    type: TransactionType,
    amountCents: Long,
    categoryId: String?,
    paymentMethodId: String?,
    description: String,
    transactionDate: Long,
    rememberMerchantCategory: Boolean = false,
    expectedUpdatedAt: Long? = null,
  ) {
    require(amountCents > 0) { "流水金额必须大于 0" }
    database.withTransaction {
      val row = requireNotNull(database.transactionDao().findById(id)) { "这笔记录已不存在" }
      require(expectedUpdatedAt == null || row.updatedAt == expectedUpdatedAt) { "这笔记录已被修改，请刷新后重试" }
      if (rememberMerchantCategory) {
        require(type == TransactionType.Expense && row.automationKind == AutomationKind.Purchase) { "仅消费记录可记住商户分类" }
        saveMerchantCategoryRule(requireNotNull(row.merchantName) { "这笔记录没有已识别商户" }, categoryId)
      }
      database.transactionDao().update(
        id, type, amountCents, categoryId, paymentMethodId, description.trim(), transactionDate,
        maxOf(System.currentTimeMillis(), row.updatedAt + 1),
      )
    }
    notifyChanged()
  }

  suspend fun listMerchantCategoryRules(): List<MerchantCategoryRule> =
    database.merchantCategoryRuleDao().listAll().map { MerchantCategoryRule(it.merchantKey, it.merchantName, it.categoryId) }

  private suspend fun saveMerchantCategoryRule(merchantName: String, categoryId: String?) {
    require(normalizeMerchant(merchantName).isNotBlank()) { "商户名称不能为空" }
    require(database.categoryDao().listActive().any { it.id == categoryId && it.type == TransactionType.Expense }) { "请选择有效的支出分类" }
    database.merchantCategoryRuleDao().upsert(MerchantCategoryRuleEntity(normalizeMerchant(merchantName), merchantName.trim(), requireNotNull(categoryId)))
  }

  suspend fun updateMerchantCategoryRule(merchantKey: String, categoryId: String) {
    database.withTransaction {
      val rule = requireNotNull(database.merchantCategoryRuleDao().find(merchantKey)) { "商户规则已不存在" }
      saveMerchantCategoryRule(rule.merchantName, categoryId)
    }
    notifyChanged()
  }

  suspend fun deleteMerchantCategoryRule(merchantKey: String) {
    database.merchantCategoryRuleDao().delete(merchantKey)
    notifyChanged()
  }

  /** 异步删除应传 [expectedUpdatedAt]，避免删除已被其他入口修改的流水。 */
  suspend fun deleteTransaction(id: String, expectedUpdatedAt: Long? = null) {
    database.withTransaction {
      val row = requireNotNull(database.transactionDao().findById(id)) { "这笔记录已不存在" }
      require(expectedUpdatedAt == null || row.updatedAt == expectedUpdatedAt) { "这笔记录已被修改，请刷新后重试" }
      database.transactionDao().deleteById(id)
    }
    notifyChanged()
  }

  /** 删除后的撤销：把原记录按原 id 写回，统计和详情会立刻恢复。 */
  suspend fun restoreTransaction(transaction: Transaction) {
    database.transactionDao().insert(
      TransactionEntity(
        id = transaction.id,
        categoryId = transaction.categoryId,
        paymentMethodId = transaction.paymentMethodId,
        amountCents = transaction.amountCents,
        type = transaction.type,
        description = transaction.description,
        transactionDate = transaction.transactionDate,
        createdAt = transaction.createdAt,
        updatedAt = transaction.updatedAt,
        entryOrigin = transaction.entryOrigin,
        automationKind = transaction.automationKind,
        sourceEventId = null,
        isUserEdited = transaction.isUserEdited,
        merchantName = transaction.merchantName,
      ),
    )
    notifyChanged()
  }

  suspend fun loadAutomationSettings(): AutomationSettings = automationStore.loadSettings()

  suspend fun updateAutomationSettings(settings: AutomationSettings) {
    automationStore.updateSettings(settings)
    notifyChanged()
  }

  suspend fun listAutomationEvents(): List<AutomationEventSummary> = automationStore.listRecentEvents()

  suspend fun clearAutomationDiagnostics() = automationDiagnostics.clear()
  suspend fun cleanupAutomationDiagnostics() = automationDiagnostics.cleanup()

  suspend fun exportAutomationDiagnostics(): String {
    val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    val settings = loadAutomationSettings()
    return automationDiagnostics.export(mapOf(
      "appVersion" to info.versionName.orEmpty(),
      "databaseVersion" to SextantDatabase.DATABASE_VERSION.toString(),
      "androidSdk" to android.os.Build.VERSION.SDK_INT.toString(),
      "manufacturer" to android.os.Build.MANUFACTURER,
      "model" to android.os.Build.MODEL,
      "timezone" to java.util.TimeZone.getDefault().id,
      "notificationConnection" to notificationListenerConnection.status.value.connection.name,
      "notificationAuthorized" to notificationListenerConnection.status.value.authorized.toString(),
      "pageAuthorized" to com.sextant.app.automation.isPaymentPageServiceEnabled(appContext).toString(),
      "automationEnabled" to settings.enabled.toString(),
      "pageEnabled" to settings.paymentPageEnabled.toString(),
      "wechatEnabled" to settings.wechatEnabled.toString(),
      "alipayEnabled" to settings.alipayEnabled.toString(),
      "diagnosticsEnabled" to settings.diagnosticsEnabled.toString(),
      "contentEnabled" to settings.diagnosticContentEnabled.toString(),
    ))
  }

  /**
   * 按 id 找一笔流水，连它的分类与来源展示信息一起返回。
   *
   * 先查这笔流水落在哪个月，再按那个月取一次明细，是为了复用手写的筛选投影：调用方要的是
   * 和列表里一模一样的一行（分类显示名、来源名、计入支出与否），而不是一条裸记录。
   */
  internal suspend fun detailTransactions(detail: AgentLedgerDetail): List<TransactionListItem> = database.withTransaction {
    listTransactions(listCategories(), listPaymentMethods(), detail.filter()).filter {
      detail.type != TransactionType.Expense || it.includeInSpending
    }
  }

  suspend fun findTransaction(id: String): TransactionListItem? {
    val transactionDate = database.transactionDao().findById(id)?.transactionDate ?: return null
    val categories = listCategories()
    val paymentMethods = listPaymentMethods()
    return listTransactions(categories, paymentMethods, LedgerFilter.forMonth(transactionDate))
      .firstOrNull { it.transaction.id == id }
  }

  internal fun notifyChanged() {
    _changes.update { it + 1 }
  }

  suspend fun upsertBudget(categoryId: String, amountCents: Long, scope: BudgetScope = BudgetScope.FromThisMonth) {
    require(categoryId.isNotBlank()) { "预算分类不能为空" }
    require(amountCents > 0) { "预算金额必须大于 0" }
    require(listCategories().any { it.id == categoryId && it.type == TransactionType.Expense && it.includeInSpending }) { "预算分类无效" }
    writeBudgetRule(categoryId, amountCents, scope)
  }

  suspend fun clearBudget(categoryId: String, scope: BudgetScope = BudgetScope.FromThisMonth) {
    require(categoryId.isNotBlank()) { "预算分类不能为空" }
    writeBudgetRule(categoryId, 0, scope)
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

  /** 撤销停用；父分类停用时会连带子分类，恢复也按同一范围还原。 */
  suspend fun restoreCategory(id: String) {
    database.categoryDao().unarchive(id)
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

  internal suspend fun listCategories(): List<Category> =
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
    return transactionItems(database.transactionDao().listFiltered(
      rangeStart = filter.rangeStart,
      rangeEnd = filter.rangeEnd,
      query = filter.query.trim(),
      categoryId = filter.categoryId.orEmpty(),
      paymentMethodId = filter.paymentMethodId.orEmpty(),
      // 方向是「传空即不过滤」的筛选项，所以要绑库里的字面量而不是枚举：枚举会被
      // TypeConverter 绑成 "expense"，`:type = ''` 永远不成立，「全部」就退化成「只看支出」，
      // 连按方向拆分的月汇总也跟着只算支出。字面量取自实体那份唯一映射。
      type = filter.type?.storageValue().orEmpty(),
      minAmountCents = filter.minAmountCents ?: -1L,
      maxAmountCents = filter.maxAmountCents ?: -1L,
    ), categories, paymentMethods)
  }

  private fun transactionItems(
    rows: List<TransactionEntity>, categories: List<Category>, paymentMethods: List<PaymentMethod>,
  ): List<TransactionListItem> {
    val categoryById = categories.associateBy { it.id }
    val paymentById = paymentMethods.associateBy { it.id }
    return rows.map { row ->
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
        merchantName = row.merchantName,
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
    return database.budgetRuleDao().listAll().effectiveBudgets(monthStart).mapNotNull { (categoryId, amountCents) ->
      // 已归档分类的预算不再出现在页面上：分类列表里没有它，说明它已经停用。
      val category = categoryById[categoryId] ?: return@mapNotNull null
      val budgetCategoryIds = category.childInclusiveIds(categories)
      val spent = transactions
        .filter { it.transaction.categoryId in budgetCategoryIds && it.transaction.type == TransactionType.Expense }
        .sumOf { it.transaction.amountCents }
      BudgetProgress(category.displayName(categoryById), category.color, amountCents, spent, category.id)
    }
  }

  private suspend fun monthlyBudget(monthStart: Long): Long? =
    database.budgetRuleDao().listAll().effectiveBudgets(monthStart)[""]

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
