package com.sextant.app.automation

import com.sextant.app.data.AutomationKind
import com.sextant.app.data.TransactionType

/** Service-local checkout context. A checkout alone never produces a transaction. */
internal class AlipayPaymentPageContext {
  private data class Checkout(val merchant: String, val amountCents: Long, val observedAt: Long)
  private data class Completed(val checkout: Checkout, val windowId: Int)

  private var pending: Checkout? = null
  private var completed: Completed? = null

  fun clear() {
    pending = null
    completed = null
  }

  fun inspect(
    sourcePackage: String,
    windowId: Int,
    text: List<String>,
    observedAt: Long,
    elapsedMillis: Long,
  ): PaymentPageInspection {
    val inspection = PaymentPageParser.inspect(sourcePackage, text, observedAt)
    if (sourcePackage != ALIPAY_PACKAGE) {
      clear()
      return inspection
    }
    if (pending?.let { elapsedMillis - it.observedAt !in 0..MAX_AGE_MILLIS } == true) pending = null
    if (completed?.let { elapsedMillis - it.checkout.observedAt !in 0..MAX_AGE_MILLIS } == true) completed = null
    val lines = text.flatMap { it.lines() }.map { it.trim().replace('￥', '¥') }.filter(String::isNotEmpty)
    if (lines.any { it in terminalPages } || ("首页" in lines && "扫一扫" in lines)) {
      clear()
      return PaymentPageInspection("page_not_completed")
    }
    // Use the confirmation action's own merchant and amount, and require agreement with
    // the visible paid-total label. Original prices, bank discounts and promotions are ignored.
    val confirmations = lines.mapNotNull { confirmation.matchEntire(it) }.distinctBy { it.value }
    if (lines.any { it.startsWith("付款,确认付款") || it.startsWith("付款，确认付款") }) {
      clear()
      val confirmation = confirmations.singleOrNull()
      val totals = lines.mapNotNull { checkoutTotal.matchEntire(it)?.groupValues?.get(1) }
        .mapNotNull(::parseAmountCents).distinct()
      val compact = lines.mapNotNull { compactConfirmation.matchEntire(it) }.singleOrNull()
      val totalIndex = lines.indexOfFirst { checkoutTotal.matches(it) }
      val merchant = confirmation?.groupValues?.get(1)?.trim()
        ?: compact?.let { lines.getOrNull(totalIndex - 1) }
        ?: return PaymentPageInspection("page_not_completed")
      val amount = parseAmountCents(confirmation?.groupValues?.get(2) ?: compact!!.groupValues[1])
      if (amount != null && totals.singleOrNull() == amount && merchant in lines && merchant.length in 2..80 &&
        ("关闭" in lines || (compact != null && "返回" in lines && "使用密码" in lines)) && "付款" in lines &&
        '¥' !in merchant && merchant !in setOf("返回", "关闭", "使用密码", "付款", "订单金额", "账号")) {
        pending = Checkout(merchant, amount, elapsedMillis)
      }
      return PaymentPageInspection("page_not_completed")
    }
    val candidate = inspection.candidate ?: return inspection
    // Historical details must use their own merchant. A new success page consumes the pending
    // checkout even on mismatch, so it cannot accidentally enrich a later same-amount purchase.
    val checkout = pending.also { pending = null }
      ?: completed?.takeIf { it.windowId == windowId }?.checkout
    if (candidate.hasExactTime || candidate.direction != TransactionType.Expense ||
      candidate.kind != AutomationKind.Purchase || candidate.currency != "CNY" ||
      checkout?.amountCents != candidate.amountCents) {
      completed = null
      return inspection
    }
    if (!candidate.merchant.isNullOrBlank()) {
      completed = null
      return inspection
    }
    completed = Completed(checkout, windowId)
    return inspection.copy(candidate = candidate.copy(merchant = checkout.merchant,
      description = candidate.description.ifBlank { checkout.merchant }))
  }

  private companion object {
    const val MAX_AGE_MILLIS = 90_000L
    val terminalPages = setOf("支付失败", "付款失败", "支付已取消", "已取消", "取消支付", "交易关闭")
    val checkoutTotal = Regex("支付金额([0-9]+(?:\\.[0-9]{1,2})?)元")
    val confirmation = Regex("付款[，,]确认付款[，,]([^，,]{2,80})[，,]付款金额([0-9]+(?:\\.[0-9]{1,2})?)元人民币")
    val compactConfirmation = Regex("付款[，,]确认付款付款金额([0-9]+(?:\\.[0-9]{1,2})?)元人民币")
  }
}
