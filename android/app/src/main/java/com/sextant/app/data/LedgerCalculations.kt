package com.sextant.app.data

import java.util.Calendar

internal object LedgerCalculations {
  fun summarize(items: List<TransactionListItem>): Summary {
    val income = items
      .filter { it.transaction.type == TransactionType.Income }
      .sumOf { it.transaction.amountCents }
    val expense = items
      .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
      .sumOf { it.transaction.amountCents }
    return Summary(income, expense)
  }

  fun breakdown(items: List<TransactionListItem>): List<CategoryBreakdown> {
    return items.groupBy { it.categoryName }
      .map { (categoryName, rows) ->
        CategoryBreakdown(categoryName, rows.first().categoryColor, rows.sumOf { it.transaction.amountCents })
      }
      .sortedByDescending { it.amountCents }
  }

  fun dailySpending(items: List<TransactionListItem>, monthStart: Long): List<DailySpending> {
    val calendar = Calendar.getInstance().apply { timeInMillis = monthStart }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val spendingByDay = items
      .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
      .groupBy {
        Calendar.getInstance().run {
          timeInMillis = it.transaction.transactionDate
          get(Calendar.DAY_OF_MONTH)
        }
      }
      .mapValues { (_, rows) -> rows.sumOf { it.transaction.amountCents } }
    return (1..daysInMonth).map { day -> DailySpending(day, spendingByDay[day] ?: 0) }
  }

  fun projectedExpense(expenseCents: Long, monthStart: Long, nowMillis: Long = System.currentTimeMillis()): Long {
    val selected = Calendar.getInstance().apply { timeInMillis = monthStart }
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val isCurrentMonth = selected.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
      selected.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    if (!isCurrentMonth) return expenseCents
    val elapsedDays = now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
    return (expenseCents.toDouble() / elapsedDays * daysInMonth).toLong()
  }
}
