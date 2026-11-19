package com.sextant.app.automation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PaymentPageScanTest {
  @Test fun completedAlipayDiscountAndBillHeadersSurviveAnImmediateWindowChange() = runBlocking {
    val merchant = "示例饭店"
    val billFields = listOf("交易成功", "订单金额", "17.00", "百次立减", "-0.01",
      "支付时间", "2026-09-18 08:30:00", "账单详情")
    val pages = listOf(
      listOf("支付成功", "回首页", "￥", "16.99", merchant, "￥17.00", "百次立减", "-￥0.01", "付款方式", "完成"),
      listOf(merchant, "支出16.99元") + billFields,
      listOf(merchant, "-16.99") + billFields,
    )
    for (page in pages) {
      var reads = 0
      val result = awaitPaymentPageCandidate(pause = {}, settle = true) {
        check(++reads == 1) { "Window already changed" }
        PaymentPageParser.parse(ALIPAY_PACKAGE, page, observedAt)
      }!!
      assertEquals(1699L, result.amountCents)
      assertEquals(merchant, result.merchant)
    }
  }

  @Test fun alipayWaitsForDiscountedTotalToSettle() = runBlocking {
    val original = PaymentPageParser.parse(ALIPAY_PACKAGE,
      listOf("支付成功", "回首页", "示例饭店", "￥15.00", "完成"), observedAt)!!
    val discounted = original.copy(amountCents = 1485, amountEvidence = "¥14.85")
    var reads = 0
    val result = awaitPaymentPageCandidate(pause = {}, settle = true) {
      if (reads++ == 0) original else discounted.copy(occurredAt = observedAt + reads)
    }!!
    assertEquals(1485L, result.amountCents)
    assertEquals(3, reads)
  }

  @Test fun repeatGuardSurvivesOverlayButExpiresAndSeparatesWindows() {
    val guard = PaymentPageRepeatGuard()
    val window = WECHAT_PACKAGE to 750
    guard.record(window, "merchant|550", 1000)
    assertTrue(guard.isRepeated(window, "merchant|550", 14000))
    assertFalse(guard.isRepeated(WECHAT_PACKAGE to 751, "merchant|550", 14000))
    assertFalse(guard.isRepeated(window, "merchant|600", 14000))
    assertFalse(guard.isRepeated(window, "merchant|550", 91001))
  }

  // Current device nodes: merchant and closing action are content descriptions in a mini-program.
  private val completedPage = listOf("支付成功", "示例交通服务", "￥20.00", "摇一摇，有优惠", "返回商家")
  private val observedAt = 1_791_817_200_000L

  @Test fun lateMiniProgramNodesAreReadWithoutAnotherAccessibilityEvent() = runBlocking {
    val pages = listOf(emptyList(), completedPage.take(3), completedPage)
    val waits = mutableListOf<Long>()
    var reads = 0
    val candidate = awaitPaymentPageCandidate(pause = { waits.add(it) }) {
      PaymentPageParser.parse(WECHAT_PACKAGE, pages[reads++], observedAt)
    }!!
    assertEquals("示例交通服务", candidate.merchant)
    assertEquals("示例交通服务", candidate.description)
    assertEquals(2000L, candidate.amountCents)
    assertEquals(observedAt, candidate.occurredAt)
    assertFalse(candidate.hasExactTime)
    assertEquals(3, reads)
    assertEquals(listOf(350L, 500L, 1000L), waits)
  }

  @Test fun completePageStopsAfterFirstRead() = runBlocking {
    var reads = 0
    val candidate = awaitPaymentPageCandidate(pause = {}) {
      reads++
      PaymentPageParser.parse(WECHAT_PACKAGE, completedPage, observedAt)
    }!!
    assertEquals("示例交通服务", candidate.merchant)
    assertEquals(1, reads)
  }

  @Test fun unrelatedPageHasBoundedRetriesAndProducesNoTransaction() = runBlocking {
    var reads = 0
    val waits = mutableListOf<Long>()
    val candidate = awaitPaymentPageCandidate(pause = { waits.add(it) }) {
      reads++
      PaymentPageParser.parse(WECHAT_PACKAGE, listOf("示例交通服务", "￥20.00", "确认付款"), observedAt)
    }
    assertNull(candidate)
    assertEquals(4, reads)
    assertEquals(3850L, waits.sum())
  }

  @Test fun cancelledScanDoesNotReadOrPostTheOldWindow() = runBlocking {
    var reads = 0
    var pauses = 0
    try {
      awaitPaymentPageCandidate(pause = {
        if (++pauses == 2) throw CancellationException("window changed")
      }) {
        reads++
        PaymentPageParser.parse(WECHAT_PACKAGE, completedPage.take(3), observedAt)
      }
      fail("Cancellation must stop the old scan")
    } catch (_: CancellationException) {
      assertEquals(1, reads)
    }
  }
}
