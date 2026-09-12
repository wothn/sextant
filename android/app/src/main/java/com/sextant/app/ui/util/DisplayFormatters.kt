package com.sextant.app.ui.util

import com.sextant.app.data.Category
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.data.toLocalDate
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.util.Locale

val TransactionType.label: String
  get() = when (this) {
    TransactionType.Expense -> "支出"
    TransactionType.Income -> "收入"
    TransactionType.Transfer -> "转账"
  }

fun Category.displayName(categories: List<Category>): String {
  val parentName = parentCategoryId?.let { parentId -> categories.firstOrNull { it.id == parentId }?.name }
  return if (parentName == null) name else "$parentName / $name"
}

val categoryColorOptions = listOf(
  0xFFFF7043L to "橙",
  0xFF42A5F5L to "蓝",
  0xFFF97316L to "橘",
  0xFF66BB6AL to "绿",
  0xFF26A69AL to "青",
  0xFFD97706L to "金",
)

fun signedAmount(item: TransactionListItem): String {
  val sign = if (item.transaction.type == TransactionType.Expense) "-" else "+"
  return sign + formatCurrency(item.transaction.amountCents)
}

fun signedAmountPlain(item: TransactionListItem): String {
  val sign = if (item.transaction.type == TransactionType.Expense) "-" else "+"
  return sign + formatAmountPlain(item.transaction.amountCents)
}

fun transactionSubtitle(item: TransactionListItem): String {
  val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(item.transaction.transactionDate)
  return listOf(time, item.paymentMethodName)
    .filter { it.isNotBlank() }
    .joinToString("  |  ")
}

fun formatCurrency(valueCents: Long): String {
  return NumberFormat.getCurrencyInstance(Locale.CHINA).format(valueCents / 100.0)
}

fun formatAmountPlain(valueCents: Long): String {
  return "%.2f".format(Locale.CHINA, valueCents / 100.0)
}

fun parseAmountCents(value: String): Long? = value.trim().toBigDecimalOrNull()
  ?.takeIf { it.signum() > 0 && it.scale() <= 2 }
  ?.movePointRight(2)
  ?.let { cents -> runCatching { cents.longValueExact() }.getOrNull() }

fun monthLabel(millis: Long): String {
  val date = millis.toLocalDate()
  return "${date.year}年${date.monthValue}月"
}

fun isCurrentMonth(millis: Long): Boolean = YearMonth.from(millis.toLocalDate()) == YearMonth.now()

/** 筛选面板里显示的完整起止文案。 */
fun LedgerFilter.rangeLabel(): String {
  if (isAllTime) return "全部时间"
  val start = rangeStart.toLocalDate()
  val end = (rangeEnd - 1).coerceAtLeast(rangeStart).toLocalDate()
  return "${start.year}年${start.monthValue}月${start.dayOfMonth}日 - ${end.year}年${end.monthValue}月${end.dayOfMonth}日"
}

fun LedgerFilter.isCurrentMonth(): Boolean = !isAllTime && YearMonth.from(rangeStart.toLocalDate()) == YearMonth.now() &&
  YearMonth.from((rangeEnd - 1).coerceAtLeast(rangeStart).toLocalDate()) == YearMonth.now()
