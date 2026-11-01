package com.sextant.app.automation

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AlipayPaymentPageContextTest {
  private lateinit var previousZone: TimeZone
  @Before fun setUp() {
    previousZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
  }
  @After fun tearDown() { TimeZone.setDefault(previousZone) }

  private val context = AlipayPaymentPageContext()
  private val now = 1_793_167_000_000L
  private val checkout = listOf(
    "关闭", "使用密码", "示例家常菜馆", "支付金额17.92元", "支付成功可得5次到店立减",
    "订单金额,¥ 18.00", "订单金额", "¥ 18.00", "百次立减,-¥ 0.08", "百次立减", "-¥ 0.08",
    "示例银行信用卡", "银行卡", "查看全部",
    "付款,确认付款，示例家常菜馆，付款金额17.92元人民币", "付款",
  )
  private val success = listOf(
    "支付成功￥17.92", "支付成功", "17.92", "￥18.00", "百次立减", "-￥0.08",
    "付款方式", "示例银行信用卡(0000)", "完成", "回首页",
  )

  private fun inspect(page: List<String>, elapsed: Long = 2_000L, windowId: Int = 2,
    source: String = ALIPAY_PACKAGE) = context.inspect(source, windowId, page, now + elapsed, elapsed)

  @Test fun checkoutNeverPostsAndSuccessInAnotherWindowGetsItsMerchant() {
    assertNull(inspect(checkout, 1_000L, 1).candidate)
    val candidate = inspect(success).candidate!!
    assertEquals("示例家常菜馆", candidate.merchant)
    assertEquals("示例家常菜馆", candidate.description)
    assertEquals(1792L, candidate.amountCents)
    assertEquals("¥17.92", candidate.amountEvidence)
    assertEquals(now + 2_000L, candidate.occurredAt)
    assertFalse(candidate.hasExactTime)
    // Repeated reads of the same result must keep its identity and merchant stable.
    assertEquals(candidate.merchant, inspect(success, 3_000L).candidate!!.merchant)
    assertNull(inspect(success, 4_000L, 3).candidate!!.merchant)
  }

  @Test fun successWithoutCheckoutStillParsesWithoutGuessingMerchant() {
    assertNull(inspect(success).candidate!!.merchant)
  }

  @Test fun mismatchedAmountConsumesCheckoutRatherThanEnrichingLaterPurchase() {
    inspect(checkout, 1_000L)
    assertNull(inspect(success.map { it.replace("17.92", "16.92") }).candidate!!.merchant)
    assertNull(inspect(success, 3_000L).candidate!!.merchant)
  }

  @Test fun ownMerchantAndHistoricalDetailsNeverUseCheckoutMerchant() {
    inspect(checkout, 1_000L)
    assertEquals("其他商店", inspect(success + listOf("商户名称", "其他商店")).candidate!!.merchant)
    assertNull(inspect(success, 3_000L).candidate!!.merchant)
    inspect(checkout, 4_000L)
    val detail = listOf("账单详情", "支出17.92元", "交易成功", "商户名称", "历史商店",
      "支付时间", "2026-10-28 12:00:00")
    val candidate = inspect(detail, 5_000L).candidate!!
    assertEquals("历史商店", candidate.merchant)
    assertTrue(candidate.hasExactTime)
    assertNull(inspect(success, 6_000L).candidate!!.merchant)
  }

  @Test fun expiredOrExplicitlyClearedCheckoutCannotEnrichSuccess() {
    inspect(checkout, 1_000L)
    assertNull(inspect(success, 91_001L).candidate!!.merchant)
    inspect(checkout, 100_000L)
    context.clear()
    assertNull(inspect(success, 101_000L).candidate!!.merchant)
    inspect(checkout, 102_000L)
    assertNull(inspect(success, 101_000L).candidate!!.merchant)
  }

  @Test fun cancellationFailureHomeOrWechatClearsPendingCheckout() {
    for (page in listOf(listOf("支付失败"), listOf("交易关闭"), listOf("取消支付"),
      listOf("首页", "扫一扫"))) {
      inspect(checkout, 1_000L)
      assertNull(inspect(page).candidate)
      assertNull(inspect(success, 3_000L).candidate!!.merchant)
    }
    inspect(checkout, 1_000L)
    val wechat = inspect(listOf("支付成功", "微信商家", "¥17.92", "完成"), source = WECHAT_PACKAGE).candidate!!
    assertEquals("微信商家", wechat.merchant)
    assertNull(inspect(success, 3_000L).candidate!!.merchant)
  }

  @Test fun checkoutAndFailureSignalsCannotPostEvenWithSuccessTextPresent() {
    for (page in listOf(checkout + success, success + "付款失败", success + "取消支付")) {
      assertNull(page.toString(), inspect(page).candidate)
    }
  }

  @Test fun incompleteOrConflictingNewCheckoutDiscardsPreviousMerchant() {
    for (page in listOf(
      checkout.filterNot { it == "示例家常菜馆" },
      checkout.filterNot { it == "支付金额17.92元" },
      checkout + "支付金额18.00元",
      checkout.map { if (it == "支付金额17.92元") "支付金额18.00元" else it },
      checkout.map { if (it.startsWith("付款,确认付款")) "付款,确认付款，另一商家，付款金额17.92元人民币" else it },
      checkout.map { if (it.startsWith("付款,确认付款")) "付款,确认付款，读取中" else it },
    )) {
      inspect(checkout, 1_000L)
      assertNull(inspect(page).candidate)
      assertNull(page.toString(), inspect(success, 3_000L).candidate!!.merchant)
    }
  }

  @Test fun latestCheckoutReplacesOldMerchantAndLoadingPagesDoNotPost() {
    inspect(checkout, 1_000L)
    assertNull(inspect(checkout.map { it.replace("示例家常菜馆", "新商家") }, 2_000L).candidate)
    assertNull(inspect(emptyList(), 3_000L).candidate)
    assertEquals("新商家", inspect(success, 4_000L).candidate!!.merchant)
  }
}
