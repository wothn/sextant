package com.sextant.app.data

import java.time.LocalDate

data class WidgetCharge(val expense: RecurringExpense, val date: LocalDate)

data class WidgetSnapshot(
  val updatedAt: Long,
  val todaySummary: Summary,
  val todayCount: Int,
  val monthSummary: Summary,
  val monthlyBudgetCents: Long?,
  val recent: List<TransactionListItem>,
  val upcoming: List<WidgetCharge>,
  val theme: ThemePreference,
)

/** 含今天、不含第8天；同一计划在窗口内的多次扣费分别展示。 */
internal fun upcomingWidgetCharges(expenses: List<RecurringExpense>, today: LocalDate): List<WidgetCharge> =
  expenses.flatMap { expense ->
    expense.occurrencesBetween(today, today.plusDays(7)).map { WidgetCharge(expense, it) }
  }.sortedWith(compareBy<WidgetCharge> { it.date }.thenBy { it.expense.name }.thenBy { it.expense.id })
