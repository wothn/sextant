package com.sextant.app.data

enum class TransactionType {
  Expense,
  Income,
  Transfer,
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
  val monthStart: Long,
  val query: String = "",
  val categoryId: String? = null,
  val paymentMethodId: String? = null,
  val type: TransactionType? = null,
)

data class AppState(
  val categories: List<Category> = emptyList(),
  val paymentMethods: List<PaymentMethod> = emptyList(),
  val allPaymentMethods: List<PaymentMethod> = emptyList(),
  val transactions: List<TransactionListItem> = emptyList(),
  val groups: List<DailyTransactionGroup> = emptyList(),
  val todaySummary: Summary = Summary(0, 0),
  val monthSummary: Summary = Summary(0, 0),
  val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
  val budgets: List<BudgetProgress> = emptyList(),
  val monthlyBudgetCents: Long? = null,
  val previousMonthSummary: Summary = Summary(0, 0),
  val dailySpending: List<DailySpending> = emptyList(),
  val projectedExpenseCents: Long = 0,
)
