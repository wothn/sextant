package com.sextant.app.data

enum class TransactionType {
  Expense,
  Income,
  Transfer,
}

enum class EntryOrigin {
  Manual,
  Notification,
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
  val label: String,
  val items: List<TransactionListItem>,
)

data class Summary(
  val incomeCents: Long,
  val expenseCents: Long,
) {
  val netCents: Long
    get() = incomeCents - expenseCents
}

data class CategoryBreakdown(
  val categoryName: String,
  val color: Long,
  val amountCents: Long,
  val previousAmountCents: Long = 0,
)

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
  val budgets: List<BudgetProgress> = emptyList(),
  val monthlyBudgetCents: Long? = null,
  val previousMonthSummary: Summary = Summary(0, 0),
  val dailySpending: List<DailySpending> = emptyList(),
  val projectedExpenseCents: Long = 0,
  val automationSettings: AutomationSettings = AutomationSettings(),
  val automationEvents: List<AutomationEventSummary> = emptyList(),
)
