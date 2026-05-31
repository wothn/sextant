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
)

data class Transaction(
  val id: String,
  val categoryId: String?,
  val paymentMethodId: String?,
  val amount: Double,
  val type: TransactionType,
  val description: String,
  val transactionDate: Long,
  val createdAt: Long,
  val updatedAt: Long,
)

data class Budget(
  val id: String,
  val categoryId: String,
  val amount: Double,
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
  val income: Double,
  val expense: Double,
) {
  val net: Double
    get() = income - expense
}

data class CategoryBreakdown(
  val categoryName: String,
  val color: Long,
  val amount: Double,
)

data class BudgetProgress(
  val categoryName: String,
  val color: Long,
  val amount: Double,
  val spent: Double,
)

data class AppState(
  val categories: List<Category> = emptyList(),
  val paymentMethods: List<PaymentMethod> = emptyList(),
  val transactions: List<TransactionListItem> = emptyList(),
  val groups: List<DailyTransactionGroup> = emptyList(),
  val todaySummary: Summary = Summary(0.0, 0.0),
  val monthSummary: Summary = Summary(0.0, 0.0),
  val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
  val budgets: List<BudgetProgress> = emptyList(),
)
