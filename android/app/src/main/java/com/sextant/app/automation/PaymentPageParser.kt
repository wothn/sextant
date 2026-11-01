package com.sextant.app.automation

import com.sextant.app.data.AutomationKind
import com.sextant.app.data.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Only completed, single-transaction pages. Never infer a payment from a chat or bill list. */
object PaymentPageParser {
  fun parse(sourcePackage: String, text: List<String>, observedAt: Long): ParsedTransactionCandidate? =
    inspect(sourcePackage, text, observedAt).candidate

  fun inspect(sourcePackage: String, text: List<String>, observedAt: Long): PaymentPageInspection {
    fun reject(reason: String) = PaymentPageInspection(reason = reason)
    if (sourcePackage !in setOf(WECHAT_PACKAGE, ALIPAY_PACKAGE)) return reject("unsupported_source")
    val lines = text.flatMap { it.lines() }.map { it.trim().replace('￥', '¥') }.filter(String::isNotEmpty)
    if (lines.size !in 3..200) return reject("page_text_incomplete")
    if (lines.any { it in rejectedPages }) return reject("page_not_completed")
    // Alipay's WebView content precedes the native toolbar, so there the title sits anywhere on the
    // page. WeChat's native detail page keeps the title in the toolbar at the very top, while the
    // payment card embedded in a mini-program puts it on the row right above the amount. WeChat's
    // WebView page exposes no title text at all — there the card's own field names are the evidence.
    // A title that only appears elsewhere belongs to another layout — Alipay's WebView page, for
    // instance — and must not switch this adapter into detail mode.
    val detailTitleIndex = lines.indexOfFirst { it in detailTitles }
    val detail = when {
      detailTitleIndex < 0 -> sourcePackage == WECHAT_PACKAGE && lines.any { it in wechatDetailFields }
      sourcePackage == ALIPAY_PACKAGE -> true
      detailTitleIndex < 6 -> true
      else -> lines.getOrNull(detailTitleIndex + 1)?.let { amount.matches(it) } == true
    }
    val expenseLines = if (sourcePackage == ALIPAY_PACKAGE && detail) {
      lines.filter { alipayExpenseAmount.matches(it) }.distinct()
    } else emptyList()
    if (expenseLines.size > 1) return reject("amount_ambiguous")
    val expenseLine = expenseLines.singleOrNull()
    // An income or malformed spoken total must not fall back to a discount's negative amount.
    if (sourcePackage == ALIPAY_PACKAGE && detail && expenseLine == null &&
      lines.take(12).any { it.startsWith("支出") || it.startsWith("收入") }) return reject("unsupported_direction")
    val detailTotal = expenseLine?.let { alipayExpenseAmount.matchEntire(it)!!.groupValues[1] }
    // Alipay exposes the actual total in a parent content description, while child nodes
    // also contain the original price and bank discount. Do not infer it from their order.
    val alipayTotals = if (sourcePackage == ALIPAY_PACKAGE && !detail) {
      lines.take(16).mapNotNull { alipaySuccessAmount.matchEntire(it)?.groupValues?.get(1) }.distinct()
    } else emptyList()
    if (alipayTotals.size > 1) return reject("amount_ambiguous")
    val alipayTotal = alipayTotals.singleOrNull()
    val success = lines.take(16).firstOrNull { it in successTitles }
      ?: alipayTotal?.let { "支付成功" } ?: return reject("success_missing")
    val resultHeader = if (!detail) resultHeader(sourcePackage, lines) else null
    val hasPaymentField = lines.any { line -> paymentFields.any { line == it || line.startsWith("$it：") || line.startsWith("$it:") } }
    if (!detail && !hasPaymentField && resultHeader == null) return reject("payment_fields_missing")

    val labeledAmount = field(lines, listOf("实付金额", "实付款", "支付金额", "付款金额"))
    // A standalone currency node immediately after the success heading is Alipay's actual
    // paid total. Original prices and discounts later in the card must not compete with it.
    val splitTotal = resultHeader?.takeIf { it.splitAmount }?.amountText
    if (alipayTotal != null && splitTotal != null &&
      parseAmountCents(alipayTotal) != parseAmountCents(splitTotal)) return reject("amount_conflict")
    val explicitTotal = alipayTotal ?: detailTotal ?: splitTotal
    if (explicitTotal != null && labeledAmount != null) {
      val labeledMatch = amount.matchEntire(labeledAmount) ?: return reject("amount_invalid")
      val explicitMatch = amount.matchEntire(explicitTotal) ?: return reject("amount_invalid")
      if (parseAmountCents(labeledMatch.groupValues[2]) != parseAmountCents(explicitMatch.groupValues[2])) return reject("amount_conflict")
    }
    val amountText = explicitTotal ?: labeledAmount
      ?: lines.take(12).filter { amount.matches(it) && it.any { char -> char in "¥元.+-" } }.distinct().singleOrNull()
      ?: return reject("amount_missing_or_ambiguous")
    val amountMatch = amount.matchEntire(amountText) ?: return reject("amount_invalid")
    val cents = parseAmountCents(amountMatch.groupValues[2]) ?: return reject("amount_invalid")
    // This first adapter handles purchases; transfers, refunds and repayments need their own rules.
    if (lines.any { line -> excludedKinds.any { line == it || line.startsWith("$it：") || line.startsWith("$it:") } }) return reject("unsupported_kind")
    if (amountMatch.groupValues[1] == "+") return reject("income_page")

    // 微信交易详情里，个人卖家的「商户全称」会被脱敏成「商户_」加真名的形式，招牌名则同时出现在
    // 「商品」和金额上方那行标题里。招牌名才是顾客认得的、通知里会写的那个名字，补记时要靠它和
    // 原来那条通知对上，所以脱敏的商户全称让位给招牌名；商户全称是正常名字时不换；没有商户全称
    // 字段时也不拿「商品」硬凑。
    val declaredMerchant = field(lines, listOf("商户全称", "收款方全称", "商户名称", "商户简称", "收款方", "商户", "收款人"))
    val storeName = field(lines, listOf("商品"))
    val merchant = (if (isMaskedMerchant(declaredMerchant)) storeName ?: declaredMerchant else declaredMerchant)
      ?: lines.firstNotNullOfOrNull { payTo.matchEntire(it)?.groupValues?.get(1) }
      ?: lines.firstNotNullOfOrNull { paidToHeadline.matchEntire(it)?.groupValues?.get(1) }
      ?: resultHeader?.takeIf { amountText == it.amountText }?.merchant
      ?: if (detail) {
        // 原生详情页把商户写成金额上方的那行标题；微信在小程序里的支付卡片则写在金额下面。
        // 两个位置都看，但每一行都要先过 validMerchant，标题和字段名不会被当成商户；
        // 「转账-给张三」这类转账标题同样不是商户。
        val index = lines.indexOf(expenseLine ?: amountText)
        val signedExpense = expenseLine != null || amountMatch.groupValues[1] == "-"
        listOfNotNull(lines.getOrNull(index - 1), lines.getOrNull(index + 1))
          .firstOrNull { signedExpense && validMerchant(it) && excludedKinds.none(it::startsWith) }
      } else null
    if (merchant != null && !validMerchant(merchant)) return reject("merchant_invalid")
    // Only this explicit Alipay success total permits an absent merchant. Keep the
    // description empty so notifications can match and later enrich the transaction.
    if (merchant == null && alipayTotal == null) return reject("merchant_missing")

    val timeText = field(lines, listOf("付款时间", "支付时间", "交易时间", "创建时间", "转账时间"))
    val transactionTime = timeText?.let(::parseTime)
    if (timeText != null && transactionTime == null) return reject("time_invalid")
    if (detail && transactionTime == null) return reject("time_missing")
    val occurredAt = transactionTime ?: observedAt
    if (occurredAt > observedAt + 60_000) return reject("time_future")
    val reference = field(lines, listOf("交易单号", "交易号", "订单号", "转账单号"))
      ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,80}")) }
    return PaymentPageInspection(reason = "parsed", candidate = ParsedTransactionCandidate(
      direction = TransactionType.Expense,
      kind = AutomationKind.Purchase,
      amountCents = cents,
      occurredAt = occurredAt,
      merchant = merchant,
      description = merchant.orEmpty(),
      providerReference = reference,
      amountEvidence = expenseLine ?: amountText,
      directionEvidence = success,
      parser = if (sourcePackage == WECHAT_PACKAGE) "wechat-page-v1" else "alipay-page-v1",
      hasExactTime = transactionTime != null,
    ))
  }

  private data class ResultHeader(val merchant: String, val amountText: String, val splitAmount: Boolean = false)

  private fun resultHeader(sourcePackage: String, lines: List<String>): ResultHeader? {
    if (lines.first() != "支付成功") return null
    val header = lines.dropWhile { it == "支付成功" }.dropWhile { it == "回首页" }
    fun merchant(value: String) = validMerchant(value) && excludedKinds.none(value::startsWith)
    if (sourcePackage == WECHAT_PACKAGE) {
      if (lines.none { it in wechatResultActions }) return null
    } else {
      // These controls identify Alipay's compact result card, including the variant without
      // payment fields. Do not apply its amount-first layout to arbitrary success text.
      if ("完成" !in lines || lines.none { it == "回首页" || it == "使用说明" }) return null
      if (header.size >= 3 && header[0] == "¥" &&
        unsignedAmount.matches(header[1]) && merchant(header[2])) {
        return ResultHeader(header[2], "¥${header[1]}", splitAmount = true)
      }
    }
    if (header.size < 2 || !merchant(header[0]) || !wechatResultAmount.matches(header[1])) return null
    return ResultHeader(header[0], header[1])
  }

  private fun field(lines: List<String>, labels: List<String>): String? {
    for (label in labels) {
      lines.forEachIndexed { index, line ->
        if (line == label || line == "$label：" || line == "$label:") {
          return lines.getOrNull(index + 1)?.takeUnless { it in allLabels }
        }
        if (line.startsWith("$label：") || line.startsWith("$label:")) {
          return line.substring(label.length + 1).trim().takeIf(String::isNotEmpty)
        }
      }
    }
    return null
  }

  /** 微信把个人卖家的商户全称脱敏成 `商户_真名`；这不是顾客在店里看到的名字。 */
  private fun isMaskedMerchant(value: String?): Boolean = value?.startsWith("商户_") == true

  /** 微信结果页的结束动作：原生页是「完成」，小程序支付结果页是「返回商家」。 */
  private val wechatResultActions = setOf("完成", "返回商家")

  /** 微信详情卡独有的字段名：WebView 页和小程序卡片常常不暴露标题，靠这些字段认出详情页。 */
  private val wechatDetailFields = setOf("当前状态", "收单机构", "商户简称", "商户单号")

  private fun validMerchant(value: String): Boolean = value.length in 2..80 &&
    value !in allLabels && value !in successTitles && value !in detailTitles &&
    value !in wechatResultActions &&
    value !in setOf("微信支付", "支付宝", "返回", "更多", "支付方式", "付款方式") &&
    !amount.matches(value) && !value.matches(Regex("[\\d.,¥+ -]+"))

  private fun parseTime(value: String): Long? = timeFormats.firstNotNullOfOrNull { pattern ->
    runCatching {
      LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
  }

  private val amount = Regex("([+-]?)\\s*¥?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?")
  private val alipaySuccessAmount = Regex("支付成功\\s*(¥\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?")
  private val alipayExpenseAmount = Regex("支出\\s*(¥?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*元)")
  private val wechatResultAmount = Regex("¥\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*元?")
  private val unsignedAmount = Regex("[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?")
  private val payTo = Regex("向\\s*(.{2,80}?)\\s*付款")

  /**
   * 微信扫码付款的账单详情页把收款方只写在标题里（`扫二维码付款-给XX`）——页面上没有
   * `商户全称`/`收款方` 这类字段，而 `收款方备注：二维码收款` 是付款备注，取它只会得到
   * 「二维码收款」这种不是商户的值。破折号前要求紧挨着「付款」，`转账-给张三` 这类转账标题
   * 因此不会被当成消费。
   */
  private val paidToHeadline = Regex("^.{0,20}付款\\s*[-－—]?\\s*给\\s*(?<merchant>.{2,60})$")
  private val detailTitles = setOf("账单详情", "交易详情", "支付详情")
  private val successTitles = setOf("支付成功", "付款成功", "交易成功", "已支付")
  private val rejectedPages = setOf("支付失败", "交易关闭", "等待付款", "待付款", "确认付款", "立即付款", "发送", "聊天信息", "账单", "交易记录")
  private val excludedKinds = listOf("退款", "退款成功", "已退款", "退款处理中", "转账", "转账成功", "还款", "信用卡还款", "红包", "充值", "提现", "外币", "美元", "港币", "USD", "HKD")
  private val paymentFields = listOf("付款方式", "支付方式", "收款方", "商户全称", "商户名称", "收款人")
  private val allLabels = (paymentFields + listOf(
    "商户", "商品", "实付金额", "实付款", "支付金额", "付款金额", "付款时间", "支付时间", "交易时间", "创建时间",
    "转账时间", "交易单号", "交易号", "订单号", "转账单号", "商品说明",
    // 详情卡里紧挨着金额的字段名和分组标题；它们不是商户，也不能当作别的字段的值。
    "当前状态", "收单机构", "商户简称", "商户单号", "交易服务", "收款方备注",
  )).toSet()
  private val timeFormats = listOf("uuuu-MM-dd HH:mm:ss", "uuuu/MM/dd HH:mm:ss", "uuuu年M月d日 HH:mm:ss")
}

data class PaymentPageInspection(val reason: String, val candidate: ParsedTransactionCandidate? = null)
