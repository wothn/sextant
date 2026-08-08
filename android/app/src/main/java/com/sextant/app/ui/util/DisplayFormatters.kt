package com.sextant.app.ui.util

import com.sextant.app.data.Category
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
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
