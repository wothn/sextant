package com.sextant.app.automation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PaymentPageScanTest {
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
