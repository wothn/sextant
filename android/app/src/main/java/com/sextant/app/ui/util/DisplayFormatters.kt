package com.sextant.app.ui.util

import com.sextant.app.data.Category
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.RecurringCycle
import com.sextant.app.data.TransactionListItem
import com.sextant.app.data.TransactionType
import com.sextant.app.data.toLocalDate
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Date
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

/** 列表行金额：保留正负号，但带千分位，右对齐的金额列才能逐行扫读。 */
fun signedAmountGrouped(item: TransactionListItem): String {
  val sign = if (item.transaction.type == TransactionType.Expense) "-" else "+"
  return sign + formatAmountGrouped(item.transaction.amountCents)
}

fun formatAmountGrouped(valueCents: Long): String =
  NumberFormat.getNumberInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
  }.format(valueCents / 100.0)

/** 图表轴/圆心用的紧凑金额；仍带货币符号，避免手写 "¥"。 */
fun formatCompactCurrency(valueCents: Long): String {
  val yuan = valueCents / 100.0
  val absolute = kotlin.math.abs(yuan)
  return when {
    absolute >= 10000 -> "¥%.1f万".format(Locale.CHINA, yuan / 10000)
    absolute >= 1000 -> "¥%.1f千".format(Locale.CHINA, yuan / 1000)
    else -> formatCurrency(valueCents)
  }
}

fun transactionSubtitle(item: TransactionListItem): String {
  val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(item.transaction.transactionDate)
  return listOf(time, item.paymentMethodName)
    .filter { it.isNotBlank() }
    .joinToString("  |  ")
}

private val genericDescriptions = setOf("消费", "交易提醒", "微信支付", "支付宝")

/**
 * 明细里的备注。通知解析出来的通用摘要（「消费」「交易提醒」）和与来源重复的平台名
 * 没有信息量，展示前去掉；首页明细和分类明细共用这一条规则。
 */
fun displayDescription(item: TransactionListItem): String = item.transaction.description
  .split("·")
  .map(String::trim)
  .filter { it.isNotBlank() && it !in genericDescriptions && it != item.paymentMethodName }
  .joinToString(" · ")

/**
 * 单行明细的时间戳。分类明细按金额或时间排序，没有按天分组，所以每行自带日期；
 * 只有跨年才补年份，和日期头保持同一套规则。
 */
fun timestampLabel(millis: Long, today: LocalDate = LocalDate.now()): String {
  val date = millis.toLocalDate()
  val year = if (date.year == today.year) "" else "${date.year}年"
  return "$year${date.monthValue}月${date.dayOfMonth}日 ${SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))}"
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

private val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 明细日期头。最近三天说「今天 / 昨天 / 前天」，其余用「2月14日 周六」；
 * 只有跨年时才补年份——每个月视图里把「2026年」重复三十遍没有信息量，
 * 而月份和星期仍然保留，方便回想「那天是周末吗」。
 *
 * [today] 显式传入是为了可测；默认取当前日期。
 */
fun dayGroupLabel(dayStartMillis: Long, today: LocalDate = LocalDate.now()): String {
  val date = dayStartMillis.toLocalDate()
  return when (ChronoUnit.DAYS.between(date, today)) {
    0L -> "今天"
    1L -> "昨天"
    2L -> "前天"
    else -> buildString {
      if (date.year != today.year) append(date.year).append('年')
      append(date.monthValue).append('月').append(date.dayOfMonth).append('日')
      append(' ').append(weekdayLabels[date.dayOfWeek.value - 1])
    }
  }
}

fun isCurrentMonth(millis: Long): Boolean = YearMonth.from(millis.toLocalDate()) == YearMonth.now()

/** 未来某天：今天 / 明天 / 后天 / 3月5日 周四。规则与 [dayGroupLabel] 对称，只是朝向未来。 */
fun futureDayLabel(dayStartMillis: Long, today: LocalDate = LocalDate.now()): String {
  val date = dayStartMillis.toLocalDate()
  return when (ChronoUnit.DAYS.between(today, date)) {
    0L -> "今天"
    1L -> "明天"
    2L -> "后天"
    else -> buildString {
      if (date.year != today.year) append(date.year).append('年')
      append(date.monthValue).append('月').append(date.dayOfMonth).append('日')
      append(' ').append(weekdayLabels[date.dayOfWeek.value - 1])
    }
  }
}

/** 周期支出的展示文案。自定义周期把天数带出来，否则「自定义」三个字读不出节奏。 */
fun RecurringCycle.label(customDays: Int?): String = when (this) {
  RecurringCycle.Weekly -> "每周"
  RecurringCycle.Monthly -> "每月"
  RecurringCycle.Quarterly -> "每季度"
  RecurringCycle.Semiannual -> "每半年"
  RecurringCycle.Yearly -> "每年"
  RecurringCycle.CustomDays -> "每 ${customDays?.takeIf { it > 0 } ?: 30} 天"
}

/** 筛选面板里显示的完整起止文案。 */
fun LedgerFilter.rangeLabel(): String {
  if (isAllTime) return "全部时间"
  val start = rangeStart.toLocalDate()
  val end = (rangeEnd - 1).coerceAtLeast(rangeStart).toLocalDate()
  return "${start.year}年${start.monthValue}月${start.dayOfMonth}日 - ${end.year}年${end.monthValue}月${end.dayOfMonth}日"
}

fun LedgerFilter.isCurrentMonth(): Boolean = !isAllTime && YearMonth.from(rangeStart.toLocalDate()) == YearMonth.now() &&
  YearMonth.from((rangeEnd - 1).coerceAtLeast(rangeStart).toLocalDate()) == YearMonth.now()

/** Compact calendar amounts; exact currency remains visible in the selected period totals. */
fun calendarAmount(cents: Long): String = when {
  cents >= 10_000_000_000L -> "%.1f亿".format(Locale.CHINA, cents / 10_000_000_000.0)
  cents >= 1_000_000L -> "%.1f万".format(Locale.CHINA, cents / 1_000_000.0)
  cents >= 100_000L -> "%.1f千".format(Locale.CHINA, cents / 100_000.0)
  else -> java.math.BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()
}

fun calendarDateLabel(date: LocalDate): String = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

fun spendingShareLabel(amountCents: Long, totalCents: Long): String {
  if (totalCents <= 0) return "—"
  val percent = amountCents.toDouble() / totalCents * 100
  return if (percent > 0 && percent < 0.1) "<0.1%" else percentageLabel(percent)
}

fun spendingChangeLabel(current: Long, previous: Long, previousLabel: String = "上月", metricLabel: String = "支出"): String {
  if (previous <= 0) return "${previousLabel}未记录${metricLabel}"
  if (current == previous) return "与${previousLabel}相同"
  val change = (current - previous).toDouble() / previous * 100
  val magnitude = kotlin.math.abs(change)
  val percent = if (magnitude < 0.1) "<0.1%" else percentageLabel(magnitude)
  return "较${previousLabel}${if (change > 0) "增加" else "减少"} $percent"
}

fun percentageLabel(percent: Double): String = NumberFormat.getNumberInstance(Locale.CHINA).apply {
  maximumFractionDigits = 1
}.format(percent) + "%"

fun calendarPeriodLabel(date: LocalDate, showMonth: Boolean, firstYear: Int? = null): String = when {
  firstYear != null -> "${firstYear}–${firstYear + 11}年"
  showMonth -> "${date.year}年${date.monthValue}月"
  else -> "${date.year}年"
}
