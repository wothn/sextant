package com.sextant.app.automation

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PaymentPageParserTest {
  private lateinit var previousZone: TimeZone
  private val now get() = LocalDateTime.of(2026, 9, 20, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

  @Before fun setUp() {
    previousZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
  }
  @After fun tearDown() { TimeZone.setDefault(previousZone) }

  @Test fun wechatResultExtractsMerchantAndActualAmount() {
    val candidate = PaymentPageParser.parse(WECHAT_PACKAGE,
      listOf("支付成功", "¥12.00", "实付金额", "¥12.00", "收款方", "街角咖啡", "付款方式", "零钱", "优惠券", "5.00元"), now)!!
    assertEquals(1200L, candidate.amountCents)
    assertEquals("街角咖啡", candidate.merchant)
  }

  @Test fun alipayActualPaidOverridesOriginalAmount() {
    val candidate = PaymentPageParser.parse(ALIPAY_PACKAGE,
      listOf("付款成功", "¥20.00", "实付金额：18.00元", "商户名称：便利店", "支付方式", "余额"), now)!!
    assertEquals(1800L, candidate.amountCents)
    assertEquals("便利店", candidate.description)
    assertEquals("alipay-page-v1", candidate.parser)
  }

  @Test fun historicalDetailUsesPaymentTimeAndReferenceForBothApps() {
    for (source in listOf(WECHAT_PACKAGE, ALIPAY_PACKAGE)) {
      val candidate = PaymentPageParser.parse(source, detail, now)!!
      assertEquals("街角咖啡", candidate.merchant)
      assertEquals("2026091800123456", candidate.providerReference)
      assertTrue(candidate.hasExactTime)
      assertEquals(LocalDateTime.of(2026, 9, 18, 8, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), candidate.occurredAt)
    }
  }

  @Test fun merchantHeadlineIsSupportedInSignedBillDetail() {
    val candidate = PaymentPageParser.parse(ALIPAY_PACKAGE,
      listOf("账单详情", "便利店", "-18.00", "交易成功", "付款时间", "2026-09-18 08:30:00"), now)!!
    assertEquals("便利店", candidate.merchant)
  }

  @Test fun refundActionDoesNotTurnPurchaseIntoRefund() {
    assertNotNull(PaymentPageParser.parse(ALIPAY_PACKAGE, detail + "申请退款", now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, detail + "退款成功", now))
  }

  @Test fun rejectsUnknownPagesIncompleteAndNonPurchaseTransactions() {
    val base = listOf("支付成功", "¥12.00", "收款方", "街角咖啡")
    assertNull(PaymentPageParser.parse("other.app", base, now))
    assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, listOf("张三", "支付成功", "¥12.00", "发送"), now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, base + "待付款", now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, base + "转账", now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, base + "USD", now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, base + "5.00元", now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, listOf("支付成功", "¥12.00", "付款方式", "余额"), now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, detail.filterNot { it.startsWith("付款时间") }, now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, detail.map { it.replace("2026-09-18", "2026-02-30") }, now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, detail.map { it.replace("2026-09-18", "2027-09-18") }, now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, detail.map { it.replace("-12.00", "+12.00") }, now))
  }

  private val detail = listOf("账单详情", "-12.00", "交易成功", "商户全称", "街角咖啡", "付款时间：2026-09-18 08:30:00", "交易单号", "2026091800123456")
}
