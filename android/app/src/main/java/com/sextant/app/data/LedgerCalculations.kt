package com.sextant.app.data

import java.util.Calendar
import java.time.LocalDate

internal object LedgerCalculations {
  /** 与每日支出图同口径，按本地日期筛选可核对的原始流水。 */
  fun spendingOnDay(items: List<TransactionListItem>, day: LocalDate): List<TransactionListItem> = items.filter {
    it.transaction.type == TransactionType.Expense && it.includeInSpending &&
      it.transaction.transactionDate.toLocalDate() == day
  }

  fun summarize(items: List<TransactionListItem>): Summary {
    val income = items
      .filter { it.transaction.type == TransactionType.Income }
      .sumOf { it.transaction.amountCents }
    val expense = items
      .filter { it.transaction.type == TransactionType.Expense && it.includeInSpending }
      .sumOf { it.transaction.amountCents }
    return Summary(income, expense)
  }

  /**
   * 支出结构：按顶层分类汇总。
   *
   * 子分类不再单独成行，而是并进所属的顶层分类；父分类自己的流水也归入同一行。
   * [categories] 用来解析父级，分类已归档或解析不到时退回展示名分组，不凭空造分类。
   */
  fun breakdown(items: List<TransactionListItem>, categories: List<Category>): List<CategoryBreakdown> {
    val categoryById = categories.associateBy { it.id }
    return items.groupBy { it.breakdownGroup(categoryById) }
      .map { (group, rows) ->
        val root = group.rootId?.let(categoryById::get)
        CategoryBreakdown(
          categoryName = root?.name ?: group.displayName,
          color = root?.color ?: rows.first().categoryColor,
          amountCents = rows.sumOf { it.transaction.amountCents },
          memberCategoryIds = rows.mapTo(mutableSetOf()) { it.transaction.categoryId ?: UNCATEGORIZED_CATEGORY_ID },
        )
      }
      .sortedByDescending { it.amountCents }
  }

  /** 分组键：能解析出顶层分类时用分类 id，否则退回展示名。 */
  private data class BreakdownGroup(val rootId: String?, val displayName: String)

  private fun TransactionListItem.breakdownGroup(categoryById: Map<String, Category>): BreakdownGroup {
    val rootId = rootCategoryId(transaction.categoryId, categoryById)
    return if (rootId == null) BreakdownGroup(null, categoryName) else BreakdownGroup(rootId, "")
  }

  /** 顺着 parentCategoryId 走到顶层分类；分类缺失或数据成环时停在上一个能解析到的分类。 */
  private fun rootCategoryId(categoryId: String?, categoryById: Map<String, Category>): String? {
    var current = categoryId?.let(categoryById::get) ?: return null
    val visited = mutableSetOf(current.id)
    while (true) {
      val parent = current.parentCategoryId?.let(categoryById::get) ?: return current.id
      if (!visited.add(parent.id)) return current.id
      current = parent
    }
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
