package com.sextant.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 流水统计统一使用「左闭右开」毫秒区间：[rangeStart, rangeEnd)。
 * 这样某一天的结束就是下一天的开始，不需要处理 23:59:59.999。
 */
internal const val DAY_MILLIS = 86_400_000L

private fun zone(): ZoneId = ZoneId.systemDefault()

internal fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone()).toLocalDate()

internal fun LocalDate.toStartOfDayMillis(): Long = atStartOfDay(zone()).toInstant().toEpochMilli()

internal fun YearMonth.toRangeStart(): Long = atDay(1).toStartOfDayMillis()

/** 该月对应的左闭右开区间。 */
internal fun YearMonth.toRange(): Pair<Long, Long> = toRangeStart() to plusMonths(1).toRangeStart()

/** 毫秒时间戳所在月份的左闭右开区间。 */
internal fun monthRangeOf(millis: Long): Pair<Long, Long> = YearMonth.from(millis.toLocalDate()).toRange()

/** 今天的左闭右开区间。 */
internal fun todayRange(): Pair<Long, Long> {
  val today = LocalDate.now()
  return today.toStartOfDayMillis() to today.plusDays(1).toStartOfDayMillis()
}

/** 这一天的左闭右开区间。 */
internal fun dayRangeOf(millis: Long): Pair<Long, Long> {
  val date = millis.toLocalDate()
  return date.toStartOfDayMillis() to date.plusDays(1).toStartOfDayMillis()
}

/** Material3 日期选择器返回的是 UTC 当天零点，换算成本地时区的当天零点。 */
internal fun utcPickerMillisToLocalStartOfDay(utcMillis: Long): Long =
  Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().toStartOfDayMillis()

/** 本地时间戳换算成日期选择器需要的 UTC 当天零点。 */
internal fun localMillisToUtcPickerMillis(millis: Long): Long =
  millis.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
