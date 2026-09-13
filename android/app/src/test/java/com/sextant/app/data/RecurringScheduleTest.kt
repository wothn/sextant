package com.sextant.app.data

import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 周期推算的边界。
 *
 * 日期全部钉死成 [LocalDate]，时区固定为 UTC 再在 [restoreTimeZone] 还原——anchor 存的是
 * 毫秒时间戳，换算成「哪一天」依赖默认时区，不固定就没法断言月末行为。
 */
class RecurringScheduleTest {
  private lateinit var originalTimeZone: TimeZone

  @Before
  fun useUtc() {
    originalTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  }

  @After
  fun restoreTimeZone() {
    TimeZone.setDefault(originalTimeZone)
  }

  @Test
  fun monthlyOccurrenceClampsToMonthEndWithoutDriftingAwayFromTheAnchorDay() {
    val rent = recurring(cycle = RecurringCycle.Monthly, anchor = LocalDate.of(2026, 1, 31))

    assertEquals(LocalDate.of(2026, 1, 31), rent.occurrence(0))
    assertEquals(LocalDate.of(2026, 2, 28), rent.occurrence(1))
    // 关键：第 3 次回到 31 号。若在上一次结果上继续加一个月，会算成 3/28 并从此永久漂移。
    assertEquals(LocalDate.of(2026, 3, 31), rent.occurrence(2))
    assertEquals(LocalDate.of(2026, 4, 30), rent.occurrence(3))
    assertEquals(LocalDate.of(2026, 5, 31), rent.occurrence(4))
  }

  @Test
  fun yearlyOccurrenceKeepsTheLeapDayAnchor() {
    val item = recurring(cycle = RecurringCycle.Yearly, anchor = LocalDate.of(2024, 2, 29))

    assertEquals(LocalDate.of(2024, 2, 29), item.occurrence(0))
    assertEquals(LocalDate.of(2025, 2, 28), item.occurrence(1))
    assertEquals(LocalDate.of(2027, 2, 28), item.occurrence(3))
    assertEquals(LocalDate.of(2028, 2, 29), item.occurrence(4))
  }

  @Test
  fun customDaysNeverAccumulateDrift() {
    val item = recurring(
      cycle = RecurringCycle.CustomDays,
      anchor = LocalDate.of(2026, 1, 1),
      customDays = 45,
    )

    assertEquals(LocalDate.of(2026, 1, 1), item.occurrence(0))
    assertEquals(LocalDate.of(2026, 2, 15), item.occurrence(1))
    assertEquals(LocalDate.of(2026, 4, 1), item.occurrence(2))
  }

  @Test
  fun nextChargeOnOrAfterIncludesTodayAndSkipsToTheFollowingCycle() {
    val item = recurring(cycle = RecurringCycle.Monthly, anchor = LocalDate.of(2026, 1, 10))

    // 当天算「今天要扣」，不是跳过。
    assertEquals(LocalDate.of(2026, 1, 10), item.nextChargeOnOrAfter(LocalDate.of(2026, 1, 10)))
    assertEquals(LocalDate.of(2026, 2, 10), item.nextChargeOnOrAfter(LocalDate.of(2026, 1, 11)))
    assertEquals(LocalDate.of(2026, 2, 10), item.nextChargeOnOrAfter(LocalDate.of(2026, 2, 10)))
    // 区间起点早于首次扣费日时落在首次扣费日上。
    assertEquals(LocalDate.of(2026, 1, 10), item.nextChargeOnOrAfter(LocalDate.of(2025, 12, 1)))
  }

  @Test
  fun nextChargeIsNullWhenPausedOrPastTheEndDate() {
    val paused = recurring(
      cycle = RecurringCycle.Monthly,
      anchor = LocalDate.of(2026, 1, 10),
      isActive = false,
    )
    val ending = recurring(
      cycle = RecurringCycle.Monthly,
      anchor = LocalDate.of(2026, 1, 10),
      endedAt = LocalDate.of(2026, 2, 1),
    )

    assertNull(paused.nextChargeOnOrAfter(LocalDate.of(2026, 1, 1)))
    // 已取消但停止日期还没到，最后一笔仍然会发生。
    assertEquals(LocalDate.of(2026, 1, 10), ending.nextChargeOnOrAfter(LocalDate.of(2026, 1, 1)))
    assertNull(ending.nextChargeOnOrAfter(LocalDate.of(2026, 1, 11)))
  }

  @Test
  fun occurrencesBetweenIsHalfOpenAndStopsAtTheEndDate() {
    val item = recurring(cycle = RecurringCycle.Monthly, anchor = LocalDate.of(2026, 1, 10))

    assertEquals(
      listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10)),
      item.occurrencesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
    )
    // 右端不含：正好落在 2/10 的区间取不到它。
    assertEquals(
      listOf(LocalDate.of(2026, 1, 10)),
      item.occurrencesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 10)),
    )
    assertEquals(emptyList<LocalDate>(), item.occurrencesBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1)))

    val ending = item.copy(endedAt = LocalDate.of(2026, 2, 5).toStartOfDayMillis())
    assertEquals(
      listOf(LocalDate.of(2026, 1, 10)),
      ending.occurrencesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1)),
    )

    val paused = item.copy(isActive = false)
    assertEquals(emptyList<LocalDate>(), paused.occurrencesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1)))
  }

  @Test
  fun monthlyEquivalentNormalisesEveryCycleToAMonth() {
    assertEquals(2_500L, recurring(RecurringCycle.Monthly, amount = 2_500).monthlyEquivalentCents())
    assertEquals(2_500L, recurring(RecurringCycle.Quarterly, amount = 7_500).monthlyEquivalentCents())
    assertEquals(2_500L, recurring(RecurringCycle.Semiannual, amount = 15_000).monthlyEquivalentCents())
    assertEquals(2_500L, recurring(RecurringCycle.Yearly, amount = 30_000).monthlyEquivalentCents())
    // 每周 25 元 ≈ 每月 108.33 元
    assertEquals(10_833L, recurring(RecurringCycle.Weekly, amount = 2_500).monthlyEquivalentCents())
    // 每 30 天 30 元 ≈ 每月 30.44 元
    assertEquals(
      3_044L,
      recurring(RecurringCycle.CustomDays, amount = 3_000, customDays = 30).monthlyEquivalentCents(),
    )
  }

  @Test
  fun stateSeparatesCanceledButStillValidFromActuallyEnded() {
    val today = LocalDate.of(2026, 6, 1)
    val active = recurring(cycle = RecurringCycle.Monthly, anchor = today)
    val paused = active.copy(isActive = false)
    val ending = active.copy(endedAt = LocalDate.of(2026, 7, 1).toStartOfDayMillis())
    val ended = active.copy(endedAt = today.toStartOfDayMillis())

    assertEquals(RecurringState.Active, active.stateAt(today))
    assertEquals(RecurringState.Paused, paused.stateAt(today))
    assertEquals(RecurringState.Ending, ending.stateAt(today))
    assertEquals(RecurringState.Ended, ended.stateAt(today))

    assertTrue(active.countsTowardPlanAt(today))
    // 取消后仍有有效期，这段时间它还会扣最后一笔，所以照样计入。
    assertTrue(ending.countsTowardPlanAt(today))
    assertFalse(ended.countsTowardPlanAt(today))
    assertFalse(paused.countsTowardPlanAt(today))
  }

  @Test
  fun illegalCustomDaysFallsBackToThirtyInsteadOfLoopingForever() {
    val broken = recurring(
      cycle = RecurringCycle.CustomDays,
      anchor = LocalDate.of(2026, 1, 1),
      customDays = 0,
    )

    assertEquals(LocalDate.of(2026, 1, 31), broken.occurrence(1))
    assertEquals(LocalDate.of(2026, 3, 2), broken.nextChargeOnOrAfter(LocalDate.of(2026, 2, 1)))
  }

  private fun recurring(
    cycle: RecurringCycle,
    anchor: LocalDate = LocalDate.of(2026, 1, 1),
    amount: Long = 10_000,
    customDays: Int? = null,
    endedAt: LocalDate? = null,
    isActive: Boolean = true,
  ) = RecurringExpense(
    id = "recurring-1",
    name = "房租",
    amountCents = amount,
    cycle = cycle,
    customDays = customDays,
    anchorDate = anchor.toStartOfDayMillis(),
    categoryId = null,
    paymentMethodId = null,
    endedAt = endedAt?.toStartOfDayMillis(),
    isActive = isActive,
    note = "",
    createdAt = 0,
    updatedAt = 0,
  )
}
