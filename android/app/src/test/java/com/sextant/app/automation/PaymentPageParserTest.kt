package com.sextant.app.automation

import com.sextant.app.data.AutomationKind
import com.sextant.app.data.TransactionType
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

  /**
   * 真机抓下来的微信扫码付款账单详情页形状（字段名保留页面结构，交易数据已换成示例值）。
   * 收款方只出现在标题里，页面没有「商户全称」这类字段，而「收款方备注：二维码收款」是付款
   * 备注——补记时既不能取不到商户，也不能把备注当商户。时间标签是「转账时间」而不是
   * 「付款时间」，取不到它就只能拿打开页面的时刻去和通知比时间，于是补记永远配不上原来那条通知。
   */
  @Test fun wechatScanPayDetailReadsMerchantTimeAndReference() {
    val observedAt = LocalDateTime.of(2026, 3, 5, 21, 50).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val candidate = PaymentPageParser.parse(WECHAT_PACKAGE, wechatScanPayDetail, observedAt)!!
    assertEquals(1500L, candidate.amountCents)
    assertEquals("某某小吃店", candidate.merchant)
    assertEquals("某某小吃店", candidate.description)
    assertEquals(TransactionType.Expense, candidate.direction)
    assertEquals(AutomationKind.Purchase, candidate.kind)
    assertEquals("2026030500123456", candidate.providerReference)
    assertTrue(candidate.hasExactTime)
    assertEquals(
      LocalDateTime.of(2026, 3, 5, 9, 15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
      candidate.occurredAt,
    )
    assertEquals("wechat-page-v1", candidate.parser)
  }

  /** 标题里的「-给X」只在紧跟在「付款」后面时才算收款方，别把「转账-给张三」记成消费。 */
  @Test fun transferHeadlineIsNotReadAsMerchant() {
    val page = listOf("转账-给张三", "-50.00", "当前状态", "支付成功", "支付方式", "零钱")
    assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, page, now))
  }

  /**
   * 真机抓下来的微信收单商户交易详情页形状（商家收款码走收单机构结算）。字段比个人收款码那版
   * 多：`商品`+`商户全称`+`收单机构`+`商户单号`。个人卖家的「商户全称」会被微信脱敏成
   * 「商户_」加真名，顾客认得的招牌名在「商品」里——补记要和通知里的名字对上，用后者。
   */
  @Test fun wechatAcquirerDetailPrefersStoreNameOverMaskedMerchant() {
    val observedAt = LocalDateTime.of(2026, 3, 7, 23, 10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val candidate = PaymentPageParser.parse(WECHAT_PACKAGE, wechatAcquirerDetail, observedAt)!!
    assertEquals(1700L, candidate.amountCents)
    assertEquals("示例饭店", candidate.merchant)
    assertEquals("示例饭店", candidate.description)
    assertEquals("2026030700654321", candidate.providerReference)
    assertTrue(candidate.hasExactTime)
    assertEquals(
      LocalDateTime.of(2026, 3, 7, 18, 20, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
      candidate.occurredAt,
    )
  }

  /** 招牌名只顶替脱敏的商户全称；商户全称是正常名字时不动它。 */
  @Test fun storeNameDoesNotOverrideRealMerchantName() {
    val page = listOf(
      "支付成功", "¥20.00", "商品", "冰美式", "商户全称", "街角咖啡", "交易单号", "2026091800123456",
      "付款时间", "2026-09-18 08:30:00",
    )
    val candidate = PaymentPageParser.parse(WECHAT_PACKAGE, page, now)!!
    assertEquals("街角咖啡", candidate.merchant)
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

  private val wechatScanPayDetail = listOf(
    "扫二维码付款-给某某小吃店",
    "-15.00",
    "当前状态",
    "支付成功",
    "收款方备注",
    "二维码收款",
    "支付方式",
    "零钱",
    "转账时间",
    "2026年3月5日 09:15:00",
    "转账单号",
    "2026030500123456",
    "账单服务",
    "对订单有疑惑",
    "发起群收款",
    "申请电子凭证",
    "收款方服务",
    "收款方名片",
    "本服务由财付通提供",
  )

  private val wechatAcquirerDetail = listOf(
    "示例饭店",
    "-17.00",
    "当前状态",
    "支付成功",
    "支付时间",
    "2026年3月7日 18:20:30",
    "商品",
    "示例饭店",
    "商户全称",
    "商户_张三",
    "收单机构",
    "示例支付股份有限公司",
    "支付方式",
    "示例银行信用卡(0000)",
    "交易单号",
    "2026030700654321",
    "商户单号",
    "8812345678901234",
    "商家小程序 示例",
    "账单服务",
    "对订单有疑惑",
    "发起群收款",
    "在此商户的交易",
    "申请电子凭证",
    "本服务由财付通提供",
  )
}
