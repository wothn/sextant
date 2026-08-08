package com.sextant.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayFormattersTest {
  @Test
  fun parsesPositiveAmountsExactlyToCents() {
    assertEquals(1L, parseAmountCents("0.01"))
    assertEquals(1_234L, parseAmountCents(" 12.34 "))
    assertEquals(100L, parseAmountCents("1"))
  }

  @Test
  fun rejectsZeroNegativeOverPrecisionAndOverflow() {
    assertNull(parseAmountCents("0"))
    assertNull(parseAmountCents("-1"))
    assertNull(parseAmountCents("1.001"))
    assertNull(parseAmountCents("999999999999999999999999"))
    assertNull(parseAmountCents("not-a-number"))
  }
}
