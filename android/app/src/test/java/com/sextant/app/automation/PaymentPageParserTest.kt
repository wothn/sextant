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

  @Test fun wechatNativeSuccessReadsMerchantHeadlineWithoutPaymentFields() {
    val candidate = PaymentPageParser.parse(WECHAT_PACKAGE, wechatNativeSuccess, now)!!
    assertEquals(3000L, candidate.amountCents)
    assertEquals("示例网络商店", candidate.merchant)
    assertEquals("示例网络商店", candidate.description)
    assertEquals("¥30.00", candidate.amountEvidence)
    assertEquals("支付成功", candidate.directionEvidence)
    assertEquals(TransactionType.Expense, candidate.direction)
    assertEquals(AutomationKind.Purchase, candidate.kind)
    assertEquals("wechat-page-v1", candidate.parser)
    assertEquals(now, candidate.occurredAt)
    assertFalse(candidate.hasExactTime)
    assertNull(candidate.providerReference)
  }

  @Test fun wechatNativeSuccessRequiresCompleteHeaderAndRejectsAmbiguousAmounts() {
    for (page in listOf(
      wechatNativeSuccess.filterNot { it == "完成" },
      wechatNativeSuccess.filterNot { it == "示例网络商店" },
      wechatNativeSuccess.filterNot { it == "支付成功" },
      listOf("示例网络商店", "支付成功", "￥30.00", "完成"),
      wechatNativeSuccess + "5.00元",
      wechatNativeSuccess + "发送",
      wechatNativeSuccess + "待付款",
      wechatNativeSuccess + "退款成功",
      wechatNativeSuccess + "转账",
      wechatNativeSuccess + "USD",
    )) assertNull(page.toString(), PaymentPageParser.parse(WECHAT_PACKAGE, page, now))
    for (merchant in listOf("付款方式", "完成", "交易成功", "30.00", "转账-给张三", "退款给张三")) {
      assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, wechatNativeSuccess.map {
        if (it == "示例网络商店") merchant else it
      }, now))
    }
    for (total in listOf("30.00", "￥-30.00", "+￥30.00", "￥0.00", "￥30.001")) {
      assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, wechatNativeSuccess.map {
        if (it == "￥30.00") total else it
      }, now))
    }
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, wechatNativeSuccess, now))
  }

  /**
   * 真机抓下来的微信小程序支付结果页形状（商户与交易数据均使用虚构示例值）。窗口是 WxaLiteAppTransparentLiteUI，
   * 整页只暴露四个 content description：成功、商户、无符号元金额、结束动作。小程序页的结束按钮是
   * 「返回商家」而不是原生结果页的「完成」，只认「完成」会把整页判成非支付页，商户和金额一起丢掉。
   */
  @Test fun wechatMiniProgramSuccessReadsMerchantWithReturnToMerchantButton() {
    val candidate = PaymentPageParser.parse(WECHAT_PACKAGE, wechatMiniProgramSuccess, now)!!
    assertEquals(7689L, candidate.amountCents)
    assertEquals("淘宝平台商户", candidate.merchant)
    assertEquals("淘宝平台商户", candidate.description)
    assertEquals("¥76.89", candidate.amountEvidence)
    assertEquals("支付成功", candidate.directionEvidence)
    assertEquals(TransactionType.Expense, candidate.direction)
    assertEquals(AutomationKind.Purchase, candidate.kind)
    assertEquals("wechat-page-v1", candidate.parser)
    assertEquals(now, candidate.occurredAt)
    assertFalse(candidate.hasExactTime)
    assertNull(candidate.providerReference)
  }

  @Test fun wechatResultPageStillRequiresTerminalActionAndMerchant() {
    assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, wechatMiniProgramSuccess.filterNot { it == "返回商家" }, now))
    // 结束动作占着商户位置时，不能把按钮文字当成商户名。
    for (action in listOf("完成", "返回商家")) {
      assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, wechatMiniProgramSuccess.map {
        if (it == "淘宝平台商户") action else it
      }, now))
    }
    for (total in listOf("76.89", "￥-76.89", "+￥76.89", "￥0.00", "￥76.891")) {
      assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, wechatMiniProgramSuccess.map {
        if (it == "￥76.89") total else it
      }, now))
    }
  }

  @Test fun alipayActualPaidOverridesOriginalAmount() {
    val candidate = PaymentPageParser.parse(ALIPAY_PACKAGE,
      listOf("付款成功", "¥20.00", "实付金额：18.00元", "商户名称：便利店", "支付方式", "余额"), now)!!
    assertEquals(1800L, candidate.amountCents)
    assertEquals("便利店", candidate.description)
    assertEquals("alipay-page-v1", candidate.parser)
  }

  /** Real device accessibility nodes; identifying values are replaced with examples. */
  @Test fun alipayDiscountedSuccessReadsActualTotalWithoutMerchant() {
    val candidate = PaymentPageParser.parse(ALIPAY_PACKAGE, alipayDiscountedSuccess, now)!!
    assertEquals(7720L, candidate.amountCents)
    assertEquals("¥77.20", candidate.amountEvidence)
    assertEquals("支付成功", candidate.directionEvidence)
    assertEquals(TransactionType.Expense, candidate.direction)
    assertEquals(AutomationKind.Purchase, candidate.kind)
    assertNull(candidate.merchant)
    assertEquals("", candidate.description)
    assertNull(candidate.providerReference)
    assertEquals(now, candidate.occurredAt)
    assertFalse(candidate.hasExactTime)
  }

  @Test fun alipaySuccessTotalWorksWithoutSeparateSuccessTitleAndWithMerchant() {
    val page = alipayDiscountedSuccess.filterNot { it == "支付成功" } + listOf("商户名称", "便利店")
    val candidate = PaymentPageParser.parse(ALIPAY_PACKAGE, page, now)!!
    assertEquals(7720L, candidate.amountCents)
    assertEquals("便利店", candidate.merchant)
    assertEquals("便利店", candidate.description)
  }

  @Test fun alipaySuccessTotalRejectsConflictingOrIncompleteEvidence() {
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, alipayDiscountedSuccess + "支付成功￥79.20", now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, alipayDiscountedSuccess + "实付金额：79.20元", now))
    assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, alipayDiscountedSuccess.filterNot { it == "付款方式" }, now))
    assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, alipayDiscountedSuccess, now))
    for (value in listOf("支付成功￥0.00", "支付成功￥-77.20", "支付成功￥77.201", "支付成功77.20", "支付成功￥77.20领券")) {
      assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, alipayDiscountedSuccess.map {
        if (it == "支付成功￥77.20") value else it
      }, now))
    }
    for (kind in listOf("待付款", "转账", "退款成功", "充值", "USD")) {
      assertNull(PaymentPageParser.parse(ALIPAY_PACKAGE, alipayDiscountedSuccess + kind, now))
    }
  }

  @Test fun alipayWebDetailReadsExpenseMerchantAndHistoricalTimeWithToolbarLast() {
    val observedAt = LocalDateTime.of(2026, 9, 26, 15, 52).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val candidate = PaymentPageParser.parse(ALIPAY_PACKAGE, alipayWebDetail, observedAt)!!
    assertEquals(7720L, candidate.amountCents)
    assertEquals("支出77.2元", candidate.amountEvidence)
    assertEquals("Steam", candidate.merchant)
    assertEquals("Steam", candidate.description)
    assertEquals(TransactionType.Expense, candidate.direction)
    assertTrue(candidate.hasExactTime)
    assertEquals(LocalDateTime.of(2026, 9, 26, 15, 12, 4).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), candidate.occurredAt)
    assertNull(candidate.providerReference)
    assertEquals("alipay-page-v1", candidate.parser)
  }

  @Test fun alipayWebDetailRejectsIncompleteConflictingAndNonExpensePages() {
    val observedAt = LocalDateTime.of(2026, 9, 26, 16, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    for (page in listOf(
      alipayWebDetail.filterNot { it == "账单详情" },
      alipayWebDetail.filterNot { it == "支付时间" || it == "2026-09-26 15:12:04" },
      alipayWebDetail.filterNot { it == "Steam" },
      alipayWebDetail + "支出79.2元",
      alipayWebDetail + "实付金额：79.20元",
      alipayWebDetail.map { it.replace("支出77.2元", "收入77.2元") },
      alipayWebDetail.map { it.replace("支出77.2元", "支出77.201元") },
      alipayWebDetail.map { it.replace("支出77.2元", "支出0元") },
      alipayWebDetail.map { it.replace("2026-09-26", "2026-02-30") },
      alipayWebDetail + "转账",
      alipayWebDetail + "退款成功",
    )) assertNull(page.toString(), PaymentPageParser.parse(ALIPAY_PACKAGE, page, observedAt))
    assertNull(PaymentPageParser.parse(WECHAT_PACKAGE, alipayWebDetail, observedAt))
    val withReference = PaymentPageParser.parse(ALIPAY_PACKAGE, alipayWebDetail + listOf("交易号", "2026092612345678"), observedAt)!!
    assertEquals("2026092612345678", withReference.providerReference)
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

  /** Real device content descriptions, including repeated promotional nodes. */
  private val wechatNativeSuccess = listOf(
    "支付成功", "示例网络商店", "￥30.00", "摇一摇有优惠", "摇一摇有优惠",
    "点击领取优惠", "点击领取优惠", "领取", "为乡村孩子上优质体育课", "完成",
  )

  /** Real device content descriptions of a mini-program payment result page, in accessibility order. */
  private val wechatMiniProgramSuccess = listOf("支付成功", "淘宝平台商户", "￥76.89", "返回商家")

  private val alipayDiscountedSuccess = listOf(
    "支付成功￥77.20", "支付成功", "77.20", "￥79.20", "示例银行立减金", "-￥2.00",
    "付款方式", "示例银行储蓄卡(0000)", "去看看", "完成", "回首页", "回首页",
  )

  /** Real accessibility order, including duplicate WebView values and the toolbar at the end. */
  private val alipayWebDetail = listOf(
    "Steam", "支出77.2元", "交易成功", "示例银行立减金", "-2.00 ",
    "支付时间", "2026-09-26 15:12:04", "2026-09-26 15:12:04",
    "付款方式", "示例银行储蓄卡(0000) ", "商品说明", "Steam Purchase", "Steam Purchase",
    "支付奖励", "立即领取3积分", "更多", "账单管理", "已解锁\"休闲娱乐\"贴纸，去兑换好礼",
    "账单分类", "文化休闲", "标签", "请选择", "为您推荐", "买软件", "游戏充值", "买包",
    "计入收支", "开关", "备注", "添加", "查看往来记录", "AA收款", "往来流水证明",
    "对此订单有疑问", "返回", "账单详情", "全部账单",
  )

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
