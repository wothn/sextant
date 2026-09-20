package com.sextant.app.data

enum class TransactionType {
  Expense,
  Income,
  Transfer,
}

enum class EntryOrigin {
  Manual,
  Notification,
  Accessibility,
}

enum class AutomationKind {
  Purchase,
  Refund,
  Receipt,
  TransferIn,
  TransferOut,
  Recharge,
  Repayment,
  RedPacket,
  Withdrawal,
  Other,
}

data class Category(
  val id: String,
  val name: String,
  val type: TransactionType,
  val color: Long,
  val includeInSpending: Boolean,
  val parentCategoryId: String?,
)

data class PaymentMethod(
  val id: String,
  val name: String,
  val color: Long,
  val isBuiltIn: Boolean = false,
  val isActive: Boolean = true,
  val transactionCount: Long = 0,
)

data class Transaction(
  val id: String,
  val categoryId: String?,
  val paymentMethodId: String?,
  val amountCents: Long,
  val type: TransactionType,
  val description: String,
  val transactionDate: Long,
  val createdAt: Long,
  val updatedAt: Long,
  val entryOrigin: EntryOrigin = EntryOrigin.Manual,
  val automationKind: AutomationKind? = null,
  val isUserEdited: Boolean = false,
)

enum class AutomationEventStatus {
  Captured,
  Posted,
  Duplicate,
  Failed,
  Undone,
}

data class AutomationEventSummary(
  val id: String,
  val sourcePackage: String,
  val postedAt: Long,
  val status: AutomationEventStatus,
  val parser: String?,
  val failureCode: String?,
  val parsedSummary: String?,
  val transactionId: String?,
)

data class AutomationSettings(
  val enabled: Boolean = false,
  val wechatEnabled: Boolean = true,
  val alipayEnabled: Boolean = true,
  val resultNotificationsEnabled: Boolean = true,
  val paymentPageEnabled: Boolean = false,
)

data class Budget(
  val id: String,
  val categoryId: String,
  val amountCents: Long,
  val period: String,
  val startDate: Long,
  val alertThreshold: Double,
  val isActive: Boolean,
)

data class TransactionListItem(
  val transaction: Transaction,
  val categoryName: String,
  val categoryColor: Long,
  val paymentMethodName: String,
  val includeInSpending: Boolean,
)

data class DailyTransactionGroup(
  /** 本地时区当天零点。分组与列表 key 都用它，展示文案由 UI 层格式化。 */
  val dayStartMillis: Long,
  val items: List<TransactionListItem>,
)

data class Summary(
  val incomeCents: Long,
  val expenseCents: Long,
) {
  val netCents: Long
    get() = incomeCents - expenseCents
}

/**
 * 支出结构里的一行。
 *
 * 一行就是「一个顶层分类」：子分类的流水并进父分类，子分类不单独成行。
 * [memberCategoryIds] 记住这行到底由哪些分类的流水组成（含子分类），
 * 点开分类看明细时才能原样还原这一行的金额。
 */
data class CategoryBreakdown(
  val categoryName: String,
  val color: Long,
  val amountCents: Long,
  val previousAmountCents: Long = 0,
  val memberCategoryIds: Set<String> = emptySet(),
) {
  /** 这笔流水是否算进这一行；没有分类的支出用 [UNCATEGORIZED_CATEGORY_ID] 表示。 */
  fun includes(categoryId: String?): Boolean =
    (categoryId ?: UNCATEGORIZED_CATEGORY_ID) in memberCategoryIds
}

/** 未分类流水在 [CategoryBreakdown.memberCategoryIds] 里的占位 id；真实分类 id 都是 UUID。 */
const val UNCATEGORIZED_CATEGORY_ID = ""

/** 分类明细的排序方式；默认按金额从大到小。 */
enum class TransactionSortOrder {
  Amount,
  Time,
}

/**
 * 分类明细的排序。金额从大到小时用时间兜底（新的在前），保证同一批数据顺序稳定。
 */
fun List<TransactionListItem>.sortedForDetail(order: TransactionSortOrder): List<TransactionListItem> = when (order) {
  TransactionSortOrder.Amount -> sortedWith(
    compareByDescending<TransactionListItem> { it.transaction.amountCents }
      .thenByDescending { it.transaction.transactionDate },
  )
  TransactionSortOrder.Time -> sortedByDescending { it.transaction.transactionDate }
}

data class DailySpending(
  val dayOfMonth: Int,
  val amountCents: Long,
)

data class BudgetProgress(
  val categoryName: String,
  val color: Long,
  val amountCents: Long,
  val spentCents: Long,
)

data class LedgerFilter(
  /** 左闭右开区间的开始毫秒。 */
  val rangeStart: Long,
  /** 左闭右开区间的结束毫秒（不含）。 */
  val rangeEnd: Long,
  val query: String = "",
  val categoryId: String? = null,
  val paymentMethodId: String? = null,
  val type: TransactionType? = null,
  val minAmountCents: Long? = null,
  val maxAmountCents: Long? = null,
) {
  /** 分析口径锚定的月份：区间最后一天所在的月份。 */
  val anchorMonthStart: Long
    get() = if (isAllTime) monthRangeOf(System.currentTimeMillis()).first
      else monthRangeOf((rangeEnd - 1).coerceAtLeast(rangeStart)).first

  val isAllTime: Boolean
    get() = rangeStart == Long.MIN_VALUE && rangeEnd == Long.MAX_VALUE

  /** 除时间范围以外的条件数量，用于概览入口的角标。 */
  val conditionCount: Int
    get() = listOf(
      query.isNotBlank(),
      categoryId != null,
      paymentMethodId != null,
      type != null,
      minAmountCents != null || maxAmountCents != null,
    ).count { it }

  val hasConditions: Boolean
    get() = conditionCount > 0

  /** 只保留时间范围，清掉其它筛选条件。 */
  fun cleared(): LedgerFilter = LedgerFilter(rangeStart = rangeStart, rangeEnd = rangeEnd)

  companion object {
    fun forMonth(monthMillis: Long): LedgerFilter = monthRangeOf(monthMillis).let { (start, end) ->
      LedgerFilter(rangeStart = start, rangeEnd = end)
    }

    fun forDay(dayMillis: Long): LedgerFilter = dayRangeOf(dayMillis).let { (start, end) ->
      LedgerFilter(rangeStart = start, rangeEnd = end)
    }

    fun allTime(): LedgerFilter = LedgerFilter(Long.MIN_VALUE, Long.MAX_VALUE)

    fun currentMonth(): LedgerFilter = forMonth(System.currentTimeMillis())
  }
}

data class AppState(
  val categories: List<Category> = emptyList(),
  val paymentMethods: List<PaymentMethod> = emptyList(),
  val allPaymentMethods: List<PaymentMethod> = emptyList(),
  val transactions: List<TransactionListItem> = emptyList(),
  val groups: List<DailyTransactionGroup> = emptyList(),
  val todaySummary: Summary = Summary(0, 0),
  /** 当前筛选时间范围内的合计。 */
  val rangeSummary: Summary = Summary(0, 0),
  val monthSummary: Summary = Summary(0, 0),
  val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
  /** 分析月份的全部流水，不受明细筛选条件影响；分类明细从这里取当月流水。 */
  val analysisTransactions: List<TransactionListItem> = emptyList(),
  val budgets: List<BudgetProgress> = emptyList(),
  val monthlyBudgetCents: Long? = null,
  /** 「计划」页里的周期支出。它是计划值，不参与 [monthSummary] 等任何流水统计口径。 */
  val recurringExpenses: List<RecurringExpense> = emptyList(),
  val previousMonthSummary: Summary = Summary(0, 0),
  val dailySpending: List<DailySpending> = emptyList(),
  val projectedExpenseCents: Long = 0,
  val automationSettings: AutomationSettings = AutomationSettings(),
  val automationEvents: List<AutomationEventSummary> = emptyList(),
)
