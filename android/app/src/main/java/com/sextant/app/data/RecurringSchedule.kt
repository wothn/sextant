package com.sextant.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * 周期推算。
 *
 * 全部是纯函数，输入 `LocalDate` 而不是 `System.currentTimeMillis()`，这样月末、闰年这类
 * 边界可以在单测里直接钉死日期，不依赖运行时的当前时间。
 */

/** [RecurringCycle.CustomDays] 没填天数时的兜底周期，避免坏数据让推算退化成死循环。 */
private const val DEFAULT_CUSTOM_DAYS = 30

/** 折算「每月」用的平均天数 365.25 / 12，放大 10000 倍做整数运算。 */
private const val DAYS_PER_MONTH_X10000 = 304_375L

/** 四舍五入的整数除法；分母非法时返回 0，不抛异常。 */
private fun roundedDiv(numerator: Long, denominator: Long): Long =
  if (denominator <= 0L) 0L else (numerator + denominator / 2L) / denominator

internal fun RecurringExpense.effectiveCustomDays(): Int =
  customDays?.takeIf { it > 0 } ?: DEFAULT_CUSTOM_DAYS

/**
 * 第 [index] 次扣费日，`index = 0` 就是 [RecurringExpense.anchorDate] 当天。
 *
 * 每次都从 anchor 直接推算，**不在上一次的结果上继续加**。`1/31` 的月度支出第 1 次落在
 * `2/28`；如果拿 `2/28` 再加一个月就会永久漂到 28 号，而 `anchor.plusMonths(2)` 直接得到
 * `3/31`，月末锚定语义才是对的。季付、半年付、年付同理，乘完再推一次。自定义天数用
 * `天数 × index` 一次算完，同样是为了不累积漂移。
 */
internal fun RecurringExpense.occurrence(index: Int): LocalDate {
  val anchor = anchorDate.toLocalDate()
  val step = max(index, 0).toLong()
  return when (cycle) {
    RecurringCycle.Weekly -> anchor.plusWeeks(step)
    RecurringCycle.Monthly -> anchor.plusMonths(step)
    RecurringCycle.Quarterly -> anchor.plusMonths(3L * step)
    RecurringCycle.Semiannual -> anchor.plusMonths(6L * step)
    RecurringCycle.Yearly -> anchor.plusYears(step)
    RecurringCycle.CustomDays -> anchor.plusDays(effectiveCustomDays().toLong() * step)
  }
}

/**
 * 不早于 [from] 的最小 occurrence 下标，`from` 早于 anchor 时为 0。
 *
 * 先用 `ChronoUnit` 估一个接近的下标再向前修正：`occurrence` 对 index 单调不减，所以
 * 循环一定会停；估计值最多偏一个周期，实际只走一两步。
 */
private fun RecurringExpense.startIndexOnOrAfter(from: LocalDate): Int {
  val anchor = anchorDate.toLocalDate()
  if (!from.isAfter(anchor)) return 0
  val estimate = when (cycle) {
    RecurringCycle.Weekly -> ChronoUnit.WEEKS.between(anchor, from)
    RecurringCycle.Monthly -> ChronoUnit.MONTHS.between(anchor, from)
    RecurringCycle.Quarterly -> ChronoUnit.MONTHS.between(anchor, from) / 3
    RecurringCycle.Semiannual -> ChronoUnit.MONTHS.between(anchor, from) / 6
    RecurringCycle.Yearly -> ChronoUnit.YEARS.between(anchor, from)
    RecurringCycle.CustomDays -> ChronoUnit.DAYS.between(anchor, from) / effectiveCustomDays()
  }
  var index = max(estimate - 1, 0L).toInt()
  while (occurrence(index) < from) index++
  return index
}

/**
 * 从 [from] 起（含当天）的下一次扣费日。
 *
 * 已暂停、或下一次已经落在 [RecurringExpense.endedAt] 之后时返回 null——调用方据此
 * 决定「不显示下次扣费」，而不是拿到一个不该发生的日期。
 */
fun RecurringExpense.nextChargeOnOrAfter(from: LocalDate): LocalDate? {
  if (!isActive) return null
  val date = occurrence(startIndexOnOrAfter(from))
  val limit = endedAt?.toLocalDate()
  return if (limit == null || date < limit) date else null
}

/**
 * `[from, to)` 区间内的全部扣费日，左闭右开，和账本的区间约定一致。
 *
 * 早于 anchor 的区间返回空列表；[RecurringExpense.endedAt] 及之后的日期不会出现。
 */
fun RecurringExpense.occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
  if (!isActive) return emptyList()
  val limit = endedAt?.toLocalDate()
  val end = if (limit != null && limit < to) limit else to
  if (!end.isAfter(from)) return emptyList()

  val dates = mutableListOf<LocalDate>()
  var index = startIndexOnOrAfter(from)
  while (true) {
    val date = occurrence(index)
    if (!date.isBefore(end)) break
    dates += date
    index++
  }
  return dates
}

/**
 * 折算成每月金额（分），用于把年付、季付拉到同一条水平线上汇总。
 *
 * 它和真实流水无关，只是计划值的折算，月付以外的取整会有 1 分以内的误差。
 */
fun RecurringExpense.monthlyEquivalentCents(): Long = when (cycle) {
  RecurringCycle.Weekly -> roundedDiv(amountCents * 52L, 12L)
  RecurringCycle.Monthly -> amountCents
  RecurringCycle.Quarterly -> roundedDiv(amountCents, 3L)
  RecurringCycle.Semiannual -> roundedDiv(amountCents, 6L)
  RecurringCycle.Yearly -> roundedDiv(amountCents, 12L)
  RecurringCycle.CustomDays ->
    roundedDiv(amountCents * DAYS_PER_MONTH_X10000, effectiveCustomDays().toLong() * 10_000L)
}

fun RecurringExpense.stateAt(today: LocalDate): RecurringState {
  val limit = endedAt?.toLocalDate()
  return when {
    limit != null && !limit.isAfter(today) -> RecurringState.Ended
    !isActive -> RecurringState.Paused
    limit != null -> RecurringState.Ending
    else -> RecurringState.Active
  }
}

/** 是否仍会继续扣费：正常重复，或已取消但尚未到停止日期。 */
fun RecurringExpense.countsTowardPlanAt(today: LocalDate): Boolean =
  when (stateAt(today)) {
    RecurringState.Active, RecurringState.Ending -> true
    RecurringState.Ended, RecurringState.Paused -> false
  }
