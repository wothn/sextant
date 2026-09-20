package com.sextant.app.automation

import com.sextant.app.data.AutomationKind
import com.sextant.app.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParsersTest {
  @Test
  fun parsesWechatExpenseWithoutTreatingOrderNumberAsAmount() {
    val result = NotificationParserRegistry.parse(
      notification(
        packageName = WECHAT_PACKAGE,
        title = "微信支付",
        text = "支付成功 ￥23.50 商户：咖啡店 订单号：1234567890",
      ),
    )

    val candidate = (result as LocalParseOutcome.Parsed).candidate
    assertEquals(TransactionType.Expense, candidate.direction)
    assertEquals(AutomationKind.Purchase, candidate.kind)
    assertEquals(2_350L, candidate.amountCents)
    assertEquals("1234567890", candidate.providerReference)
  }

  @Test
  fun parsesCurrentWechatPaymentPhrasesLocally() {
    val paid = NotificationParserRegistry.parse(
      notification(WECHAT_PACKAGE, "微信支付", "已支付¥16.00"),
    ) as LocalParseOutcome.Parsed
    val paidToMerchant = NotificationParserRegistry.parse(
      notification(WECHAT_PACKAGE, "微信支付", "向 麦当劳 付款 12.30 元"),
    ) as LocalParseOutcome.Parsed

    assertEquals(TransactionType.Expense, paid.candidate.direction)
    assertEquals(1_600L, paid.candidate.amountCents)
    assertEquals(TransactionType.Expense, paidToMerchant.candidate.direction)
    assertEquals(1_230L, paidToMerchant.candidate.amountCents)
    assertEquals("麦当劳", paidToMerchant.candidate.merchant)
  }

  @Test
  fun parsesWechatRenewalNotificationAsExpense() {
    val notification = notification(WECHAT_PACKAGE, "微信支付", "已续费¥8.00")

    assertTrue(NotificationParserRegistry.isPlausibleFinancial(notification))
    val result = NotificationParserRegistry.parse(notification) as LocalParseOutcome.Parsed
    assertEquals(TransactionType.Expense, result.candidate.direction)
    assertEquals(AutomationKind.Purchase, result.candidate.kind)
    assertEquals(800L, result.candidate.amountCents)
  }

  @Test
  fun parsesWechatIncomingPaymentAndTransferCounterparty() {
    val paidToUser = NotificationParserRegistry.parse(
      notification(WECHAT_PACKAGE, "微信支付", "向你付款 100.00 元"),
    ) as LocalParseOutcome.Parsed
    val transfer = NotificationParserRegistry.parse(
      notification(WECHAT_PACKAGE, "微信支付", "收到 张三 的转账 88.00 元"),
    ) as LocalParseOutcome.Parsed

    assertEquals(TransactionType.Income, paidToUser.candidate.direction)
    assertEquals(10_000L, paidToUser.candidate.amountCents)
    assertEquals(AutomationKind.TransferIn, transfer.candidate.kind)
    assertEquals("张三", transfer.candidate.counterparty)
  }

  @Test
  fun parsesAlipayRefundAsIncome() {
    val result = NotificationParserRegistry.parse(
      notification(
        packageName = ALIPAY_PACKAGE,
        title = "支付宝",
        text = "退款成功，18.80元已到账",
      ),
    )

    val candidate = (result as LocalParseOutcome.Parsed).candidate
    assertEquals(TransactionType.Income, candidate.direction)
    assertEquals(AutomationKind.Refund, candidate.kind)
    assertEquals(1_880L, candidate.amountCents)
  }

  @Test
  fun acceptsAlipayPaymentSuccessTitleAndExtractsMerchantFromBody() {
    val result = NotificationParserRegistry.parse(
      notification(
        packageName = ALIPAY_PACKAGE,
        title = "付款成功",
        text = "付款成功￥3.00 (AB)湖北武汉黄陂汉口北服装城美宜佳",
      ),
    ) as LocalParseOutcome.Parsed

    assertEquals(TransactionType.Expense, result.candidate.direction)
    assertEquals(300L, result.candidate.amountCents)
    assertEquals("湖北武汉黄陂汉口北服装城美宜佳", result.candidate.merchant)
  }

  @Test
  fun parsesAlipayReceiptAndGenericExpense() {
    val receipt = NotificationParserRegistry.parse(
      notification(ALIPAY_PACKAGE, "支付宝支付", "成功收款 1.00 元。享免费提现等更多专属服务"),
    ) as LocalParseOutcome.Parsed
    val expense = NotificationParserRegistry.parse(
      notification(ALIPAY_PACKAGE, "支付宝", "你有一笔 88.00 元的支出，点此查看详情"),
    ) as LocalParseOutcome.Parsed

    assertEquals(TransactionType.Income, receipt.candidate.direction)
    assertEquals(100L, receipt.candidate.amountCents)
    assertEquals(AutomationKind.Receipt, receipt.candidate.kind)
    assertEquals(TransactionType.Expense, expense.candidate.direction)
    assertEquals(8_800L, expense.candidate.amountCents)
  }

  @Test
  fun doesNotUseNotificationTitleAsMerchantFallback() {
    val result = NotificationParserRegistry.parse(
      notification(ALIPAY_PACKAGE, "交易提醒", "你有一笔 88.00 元的支出"),
    ) as LocalParseOutcome.Parsed

    assertNull(result.candidate.merchant)
  }

  @Test
  fun ignoresOrdinaryChatAndMarketingNotifications() {
    assertTrue(
      NotificationParserRegistry.parse(
        notification(WECHAT_PACKAGE, "朋友", "今晚8点一起吃饭吗？"),
      ) is LocalParseOutcome.NotFinancial,
    )
    assertTrue(
      NotificationParserRegistry.parse(
        notification(ALIPAY_PACKAGE, "优惠提醒", "签到领红包，没有消费通知"),
      ) is LocalParseOutcome.NotFinancial,
    )
    assertTrue(
      NotificationParserRegistry.parse(
        notification(ALIPAY_PACKAGE, "支付宝", "消费5元可领优惠券，点击领取"),
      ) is LocalParseOutcome.NotFinancial,
    )
  }

  @Test
  fun contextualRuleSelectsPaidAmountWhenNotificationContainsDiscountAmount() {
    val result = NotificationParserRegistry.parse(
      notification(WECHAT_PACKAGE, "微信支付", "支付成功 12.00元，优惠前15.00元"),
    )
    val candidate = (result as LocalParseOutcome.Parsed).candidate
    assertEquals(1_200L, candidate.amountCents)
  }

  @Test
  fun amountlessTransferIsInsufficientAndMustNotUseAi() {
    val notification = notification(ALIPAY_PACKAGE, "支付宝", "张三给你转了一笔钱，点击查看详细信息")

    assertTrue(NotificationParserRegistry.isPlausibleFinancial(notification))
    val result = NotificationParserRegistry.parse(notification)
    assertTrue(result is LocalParseOutcome.Insufficient)
    assertEquals("amount_missing", (result as LocalParseOutcome.Insufficient).reason)
  }

  @Test
  fun parsesLatestGroupedChildAndNormalizesFullWidthDigits() {
    val result = NotificationParserRegistry.parse(
      CapturedNotification(
        sourcePackage = WECHAT_PACKAGE,
        notificationKey = "summary",
        postedAt = System.currentTimeMillis(),
        title = "微信支付",
        text = "[2条]",
        groupChildText = "微信支付：已支付￥１６.００",
      ),
    ) as LocalParseOutcome.Parsed

    assertEquals(1_600L, result.candidate.amountCents)
    assertEquals(TransactionType.Expense, result.candidate.direction)
  }

  @Test
  fun normalizedTextIncludesExpandedLinesAndTicker() {
    val content = CapturedNotification(
      sourcePackage = WECHAT_PACKAGE,
      notificationKey = "key",
      postedAt = 1L,
      title = "微信支付",
      textLines = listOf("第一行", "第二行"),
      infoText = "附加信息",
      tickerText = "已支付¥2.00",
    ).normalizedText()

    assertTrue(content.contains("第一行"))
    assertTrue(content.contains("第二行"))
    assertTrue(content.contains("附加信息"))
    assertTrue(content.contains("已支付¥2.00"))
  }

  private fun notification(packageName: String, title: String, text: String) = CapturedNotification(
    sourcePackage = packageName,
    notificationKey = "key",
    postedAt = System.currentTimeMillis(),
    title = title,
    text = text,
  )
}
