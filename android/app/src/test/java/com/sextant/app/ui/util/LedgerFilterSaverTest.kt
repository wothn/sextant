package com.sextant.app.ui.util

import androidx.compose.runtime.saveable.SaverScope
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 筛选条件的存档行为。
 *
 * 守的是「转个屏幕还在原来的筛选里」这条：存档器要把每个字段原样带过去，写出去的值还必须
 * 是 Bundle 认得的类型——否则 `rememberSaveable` 会在运行时拒绝保存，而不是在编译期报错。
 */
class LedgerFilterSaverTest {
  private val scope = object : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
  }

  @Test
  fun roundTripsEveryField() {
    val filter = LedgerFilter(
      rangeStart = 1_700_000_000_000,
      rangeEnd = 1_700_200_000_000,
      query = "咖啡",
      categoryId = "cat-food",
      paymentMethodId = "pm-cash",
      type = TransactionType.Expense,
      minAmountCents = 100,
      maxAmountCents = 9_900,
    )

    assertEquals(filter, restore(save(filter)))
  }

  @Test
  fun keepsAllTimeRangeAndEmptyConditions() {
    val filter = LedgerFilter.allTime()

    val restored = restore(save(filter))

    assertEquals(filter, restored)
    assertTrue(restored.isAllTime)
    assertNull(restored.type)
  }

  @Test
  fun savesEnumByNameAndOnlyPrimitives() {
    val filter = LedgerFilter(
      rangeStart = 1,
      rangeEnd = 2,
      type = TransactionType.Transfer,
      minAmountCents = 5,
    )

    val saved = save(filter)

    // 存名字而不是序号：往后往 TransactionType 中间插值，旧存档也不会被解读成另一种类型。
    assertEquals("Transfer", saved[5])
    saved.forEach { value ->
      assertTrue("只应保存 Bundle 认得的类型：$value", value == null || value is Long || value is String)
    }
  }

  private fun save(filter: LedgerFilter): List<Any?> =
    requireNotNull(with(LedgerFilterSaver) { scope.save(filter) }) as List<Any?>

  private fun restore(saved: List<Any?>): LedgerFilter = requireNotNull(LedgerFilterSaver.restore(saved))
}
