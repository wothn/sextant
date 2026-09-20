package com.sextant.app.automation

import com.sextant.app.data.AutomationKind
import com.sextant.app.data.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Only completed, single-transaction pages. Never infer a payment from a chat or bill list. */
object PaymentPageParser {
  fun parse(sourcePackage: String, text: List<String>, observedAt: Long): ParsedTransactionCandidate? {
    if (sourcePackage !in setOf(WECHAT_PACKAGE, ALIPAY_PACKAGE)) return null
    val lines = text.flatMap { it.lines() }.map { it.trim().replace('￥', '¥') }.filter(String::isNotEmpty)
    if (lines.size !in 3..200) return null
    if (lines.any { it in rejectedPages }) return null
    val detail = lines.take(6).any { it in detailTitles }
    val success = lines.take(16).firstOrNull { it in successTitles } ?: return null
    val hasPaymentField = lines.any { line -> paymentFields.any { line == it || line.startsWith("$it：") || line.startsWith("$it:") } }
    if (!detail && !hasPaymentField) return null

    val amountText = field(lines, listOf("实付金额", "实付款", "支付金额", "付款金额"))
      ?: lines.take(12).filter { amount.matches(it) && it.any { char -> char in "¥元.+-" } }.distinct().singleOrNull()
      ?: return null
    val amountMatch = amount.matchEntire(amountText) ?: return null
    val cents = parseAmountCents(amountMatch.groupValues[2]) ?: return null
    // This first adapter handles purchases; transfers, refunds and repayments need their own rules.
    if (lines.any { line -> excludedKinds.any { line == it || line.startsWith("$it：") || line.startsWith("$it:") } }) return null
    if (amountMatch.groupValues[1] == "+") return null

    val merchant = field(lines, listOf("商户全称", "收款方全称", "商户名称", "收款方", "商户", "收款人"))
      ?: lines.firstNotNullOfOrNull { payTo.matchEntire(it)?.groupValues?.get(1) }
      ?: if (detail) {
        // Both apps also render the merchant as the headline immediately above the signed amount.
        val index = lines.indexOf(amountText)
        lines.getOrNull(index - 1)?.takeIf { amountMatch.groupValues[1] == "-" }
      } else null
    if (merchant == null || !validMerchant(merchant)) return null

    val timeText = field(lines, listOf("付款时间", "支付时间", "交易时间", "创建时间"))
    val transactionTime = timeText?.let(::parseTime)
    if (timeText != null && transactionTime == null) return null
    if (detail && transactionTime == null) return null
    val occurredAt = transactionTime ?: observedAt
    if (occurredAt > observedAt + 60_000) return null
    val reference = field(lines, listOf("交易单号", "交易号", "订单号"))
      ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,80}")) }
    return ParsedTransactionCandidate(
      direction = TransactionType.Expense,
      kind = AutomationKind.Purchase,
      amountCents = cents,
      occurredAt = occurredAt,
      merchant = merchant,
      description = merchant,
      providerReference = reference,
      amountEvidence = amountText,
      directionEvidence = success,
      parser = if (sourcePackage == WECHAT_PACKAGE) "wechat-page-v1" else "alipay-page-v1",
      hasExactTime = transactionTime != null,
    )
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

  private fun validMerchant(value: String): Boolean = value.length in 2..80 &&
    value !in allLabels && value !in successTitles && value !in detailTitles &&
    value !in setOf("微信支付", "支付宝", "完成", "返回", "更多", "支付方式", "付款方式") &&
    !amount.matches(value) && !value.matches(Regex("[\\d.,¥+ -]+"))

  private fun parseTime(value: String): Long? = timeFormats.firstNotNullOfOrNull { pattern ->
    runCatching {
      LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
  }

  private val amount = Regex("([+-]?)\\s*¥?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?")
  private val payTo = Regex("向\\s*(.{2,80}?)\\s*付款")
  private val detailTitles = setOf("账单详情", "交易详情", "支付详情")
  private val successTitles = setOf("支付成功", "付款成功", "交易成功", "已支付")
  private val rejectedPages = setOf("支付失败", "交易关闭", "等待付款", "待付款", "确认付款", "立即付款", "发送", "聊天信息", "账单", "交易记录")
  private val excludedKinds = listOf("退款", "退款成功", "已退款", "退款处理中", "转账", "转账成功", "还款", "信用卡还款", "红包", "充值", "提现", "外币", "美元", "港币", "USD", "HKD")
  private val paymentFields = listOf("付款方式", "支付方式", "收款方", "商户全称", "商户名称", "收款人")
  private val allLabels = (paymentFields + listOf("商户", "实付金额", "实付款", "支付金额", "付款金额", "付款时间", "支付时间", "交易时间", "创建时间", "交易单号", "交易号", "订单号", "商品说明")).toSet()
  private val timeFormats = listOf("uuuu-MM-dd HH:mm:ss", "uuuu/MM/dd HH:mm:ss", "uuuu年M月d日 HH:mm:ss")
}
