package com.sextant.app.automation

import com.sextant.app.data.AutomationKind
import com.sextant.app.data.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode

object NotificationParserRegistry {
  fun parse(notification: CapturedNotification): LocalParseOutcome {
    if (notification.sourcePackage !in SUPPORTED_PACKAGES) return LocalParseOutcome.NotFinancial
    val content = normalizeContent(notification.normalizedText())
    if (!isPlausibleFinancial(notification)) return LocalParseOutcome.NotFinancial

    val ruleMatch = findRuleMatch(notification.sourcePackage, content)
    val amount = ruleMatch?.let { parseAmountCents(it.amountText)?.let { cents -> cents to it.amountText } }
      ?: selectFallbackAmount(content)
      ?: return if (AMOUNT_REGEX.containsMatchIn(content)) {
        LocalParseOutcome.Insufficient("amount_ambiguous")
      } else {
        LocalParseOutcome.Insufficient("amount_missing")
      }
    val directionMatch = ruleMatch?.let { it.direction to it.directionEvidence }
      ?: findDirection(content)
      ?: return LocalParseOutcome.Insufficient("direction_missing")
    val kind = findKind(content, directionMatch.first)
    val reference = REFERENCE_REGEX.find(content)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
    val merchant = ruleMatch?.merchant.cleanParty()
      ?: extractMerchant(content, directionMatch.first)
    val counterparty = ruleMatch?.counterparty.cleanParty()
      ?: extractCounterparty(content, directionMatch.first)
    val description = (merchant ?: counterparty).orEmpty()

    return LocalParseOutcome.Parsed(
      ParsedTransactionCandidate(
        direction = directionMatch.first,
        kind = kind,
        amountCents = amount.first,
        occurredAt = notification.postedAt,
        merchant = merchant,
        counterparty = counterparty,
        description = description,
        providerReference = reference,
        amountEvidence = amount.second,
        directionEvidence = directionMatch.second,
        parser = localParserName(notification.sourcePackage),
      ),
    )
  }

  internal fun hasPaymentClues(notification: CapturedNotification): Boolean =
    listOf("支付", "付款", "收款", "到账", "转账", "退款", "交易").any(notification.normalizedText()::contains)

  fun isPlausibleFinancial(notification: CapturedNotification): Boolean {
    if (notification.sourcePackage !in SUPPORTED_PACKAGES) return false
    val title = notification.title.trim()
    val content = normalizeContent(notification.normalizedText())
    if (content.isBlank()) return false
    val ruleMatch = findRuleMatch(notification.sourcePackage, content)
    if (isPromotionalOrNonPayment(content) && !hasConfirmedPaymentSignal(content)) return false
    if (ruleMatch != null) return true

    val trustedSource = when (notification.sourcePackage) {
      WECHAT_PACKAGE -> WECHAT_FINANCIAL_TITLES.any(title::contains)
      ALIPAY_PACKAGE -> ALIPAY_FINANCIAL_TITLES.any(title::contains)
      else -> false
    }
    val strongSignal = STRONG_FINANCIAL_SIGNALS.any(content::contains) ||
      PAY_TO_MERCHANT_REGEX.containsMatchIn(content) ||
      AMOUNTLESS_INCOMING_REGEX.containsMatchIn(content)
    return (trustedSource || strongSignal) && strongSignal
  }

  private fun findDirection(content: String): Pair<TransactionType, String>? {
    AMOUNTLESS_INCOMING_REGEX.find(content)?.value?.let { return TransactionType.Income to it }
    PAY_TO_MERCHANT_REGEX.find(content)?.value?.let { return TransactionType.Expense to it }
    INCOMING_KEYWORDS.firstOrNull(content::contains)?.let { return TransactionType.Income to it }
    OUTGOING_KEYWORDS.firstOrNull(content::contains)?.let { return TransactionType.Expense to it }
    return null
  }

  private fun findRuleMatch(packageName: String, content: String): PaymentRuleMatch? {
    val rules = when (packageName) {
      WECHAT_PACKAGE -> WECHAT_RULES
      ALIPAY_PACKAGE -> ALIPAY_RULES
      else -> return null
    }
    return rules.firstNotNullOfOrNull { rule ->
      val match = rule.pattern.find(content) ?: return@firstNotNullOfOrNull null
      val amountText = match.groups["amount"]?.value ?: return@firstNotNullOfOrNull null
      PaymentRuleMatch(
        direction = rule.direction,
        amountText = amountText,
        directionEvidence = DIRECTION_EVIDENCE_REGEX.find(match.value)?.value ?: match.value.take(60),
        merchant = rule.merchantGroup?.let { match.groups[it]?.value },
        counterparty = rule.counterpartyGroup?.let { match.groups[it]?.value },
      )
    }
  }

  private fun selectFallbackAmount(content: String): Pair<Long, String>? {
    val amounts = AMOUNT_REGEX.findAll(content)
      .mapNotNull { match -> match.amountText()?.let(::parseAmountCents)?.let { it to match.value } }
      .distinctBy { it.first }
      .toList()
    return amounts.singleOrNull()
  }

  private fun findKind(content: String, direction: TransactionType): AutomationKind = when {
    content.contains("退款") || content.contains("退回") -> AutomationKind.Refund
    content.contains("还款") -> AutomationKind.Repayment
    content.contains("充值") -> AutomationKind.Recharge
    content.contains("红包") -> AutomationKind.RedPacket
    content.contains("转账") || content.contains("转入") || content.contains("转出") -> {
      if (direction == TransactionType.Income) AutomationKind.TransferIn else AutomationKind.TransferOut
    }
    direction == TransactionType.Income && (content.contains("收款") || content.contains("到账")) -> AutomationKind.Receipt
    direction == TransactionType.Expense && content.contains("提现") -> AutomationKind.Withdrawal
    else -> AutomationKind.Purchase
  }

  private fun extractMerchant(content: String, direction: TransactionType): String? {
    if (direction != TransactionType.Expense) return null
    return EXPENSE_MERCHANT_PATTERNS.firstNotNullOfOrNull { pattern ->
      pattern.find(content)?.groups?.get("merchant")?.value.cleanParty()
    }
  }

  private fun extractCounterparty(content: String, direction: TransactionType): String? {
    if (direction != TransactionType.Income) return null
    return INCOME_COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { pattern ->
      pattern.find(content)?.groups?.get("counterparty")?.value.cleanParty()
    }
  }
}

private data class PaymentRule(
  val direction: TransactionType,
  val pattern: Regex,
  val merchantGroup: String? = null,
  val counterpartyGroup: String? = null,
)

private data class PaymentRuleMatch(
  val direction: TransactionType,
  val amountText: String,
  val directionEvidence: String,
  val merchant: String? = null,
  val counterparty: String? = null,
)

fun parseAmountCents(value: String): Long? = runCatching {
  val normalized = value.replace(",", "").replace("￥", "").replace("¥", "").trim()
  val decimal = BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY)
  if (decimal <= BigDecimal.ZERO) return null
  decimal.movePointRight(2).longValueExact()
}.getOrNull()

private fun String?.clean(): String? = this?.trim()?.take(MAX_DESCRIPTION_LENGTH)?.takeIf(String::isNotBlank)

private fun String?.cleanParty(): String? = this
  ?.replace(Regex("^[（(][^）)]{1,12}[）)]"), "")
  ?.replace(Regex("[，,。；;：:\\s]+$"), "")
  ?.trim()
  ?.take(60)
  ?.takeIf { it.length in 2..60 && !it.matches(Regex("[0-9.,]+")) }

private fun normalizeContent(value: String): String {
  val normalizedDigits = buildString(value.length) {
    value.forEach { character ->
      append(if (character in '０'..'９') ('0'.code + character.code - '０'.code).toChar() else character)
    }
  }
  return normalizedDigits
    .replace('￥', '¥')
    .replace(Regex("[\\[［【]\\d+条[\\]］】]"), "")
    .replace(Regex("[（(]\\d+条[）)]"), "")
    .replace(Regex("\\s+"), " ")
    .trim()
}

private fun MatchResult.amountText(): String? = groupValues.drop(1).firstOrNull(String::isNotBlank)

private fun localParserName(packageName: String) = when (packageName) {
  WECHAT_PACKAGE -> "wechat-local-v1"
  ALIPAY_PACKAGE -> "alipay-local-v1"
  else -> "local-v1"
}

private val SUPPORTED_PACKAGES = setOf(WECHAT_PACKAGE, ALIPAY_PACKAGE)
private val AMOUNT_REGEX = Regex("(?:CNY|RMB|人民币|[￥¥])\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?|([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元")
private val REFERENCE_REGEX = Regex("(?:交易号|订单号|流水号)[:：\\s]*([A-Za-z0-9_-]{6,})")
private const val AMOUNT_CAPTURE = "(?<amount>[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"
private const val OPTIONAL_CURRENCY = "[¥￥]?\\s*"
private val PAY_TO_MERCHANT_REGEX = Regex("向\\s*(?!你|您)([^，,。；;]{1,40}?)\\s*付款")
private val AMOUNTLESS_INCOMING_REGEX = Regex(
  "(?:向你|向您)(?:付款|转账)|(?:给你|给您)转了一笔钱|收到[^，,。；;]{0,30}(?:转账|付款)|发来(?:一笔转账|一个红包)",
)
private val DIRECTION_EVIDENCE_REGEX = Regex(
  "退款成功|退款到账|收到退款|已退款|成功收款|收款成功|收款到账|已收款|向你付款|向您付款|收到转账|到账|转入|" +
    "付款码|已支付|已扣费|已续费|支付成功|付款成功|成功付款|已付款|向[^，,。；;]{1,40}付款|支出|消费|扣款|扣费|充值成功|还款成功",
)

private val WECHAT_RULES = listOf(
  PaymentRule(TransactionType.Income, Regex("(?:退款成功|退款到账|收到退款|已退款|退款)[^0-9¥￥]{0,30}$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Income, Regex("(?:成功收款|收款成功|收款到账|已收款|您已收款)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Income, Regex("(?:向你|向您)\\s*付款\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(
    TransactionType.Income,
    Regex("收到\\s*(?<counterparty>[^，,。；;]{1,40}?)\\s*的?(?:转账|付款)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?"),
    counterpartyGroup = "counterparty",
  ),
  PaymentRule(TransactionType.Income, Regex("(?:收到红包|红包到账|红包已存入零钱)[^0-9¥￥]{0,30}$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Income, Regex("(?:转账到账|转入|到账)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(
    TransactionType.Expense,
    Regex("向\\s*(?!你|您)(?<merchant>[^，,。；;]{1,40}?)\\s*付款\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?"),
    merchantGroup = "merchant",
  ),
  PaymentRule(TransactionType.Expense, Regex("付款码[^0-9]{0,30}(?:已支付|付款成功|付款)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Expense, Regex("已续费\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Expense, Regex("(?:已支付|已扣费|支付成功|付款成功|成功付款|已付款|您已支付|您已付款)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Expense, Regex("(?:支付|付款)\\s*[¥￥]\\s*$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Expense, Regex("一笔\\s*$AMOUNT_CAPTURE\\s*元的支出")),
)

private val ALIPAY_RULES = listOf(
  PaymentRule(TransactionType.Income, Regex("(?:退款成功|退款到账|收到退款|已退款|退款)[^0-9¥￥]{0,30}$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Income, Regex("(?:成功收款|收款成功|收款到账|已收款|您已收款)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Income, Regex("(?:转账到账|转入|到账|收入)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(
    TransactionType.Income,
    Regex("(?:来自|收到)\\s*(?<counterparty>[^，,。；;]{1,40}?)\\s*(?:的)?(?:转账|付款)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?"),
    counterpartyGroup = "counterparty",
  ),
  PaymentRule(
    TransactionType.Expense,
    Regex("向\\s*(?<merchant>[^，,。；;]{1,40}?)\\s*付款\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?"),
    merchantGroup = "merchant",
  ),
  PaymentRule(TransactionType.Expense, Regex("(?:付款成功|成功付款|支付成功|已支付|已扣费|已付款)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Expense, Regex("(?:你有)?一笔\\s*$AMOUNT_CAPTURE\\s*元的支出")),
  PaymentRule(TransactionType.Expense, Regex("(?:支出|消费|扣款)\\s*$OPTIONAL_CURRENCY$AMOUNT_CAPTURE\\s*元?")),
  PaymentRule(TransactionType.Expense, Regex("(?:支付|付款)\\s*[¥￥]\\s*$AMOUNT_CAPTURE\\s*元?")),
)

private val INCOMING_KEYWORDS = listOf(
  "退款成功", "已退款", "退款到账", "成功收款", "收款成功", "已收款", "收款到账", "到账", "转入", "收到", "退回",
)
private val OUTGOING_KEYWORDS = listOf(
  "已支付", "已扣费", "支付成功", "付款成功", "成功付款", "已付款", "扣款", "消费", "转出", "充值成功", "还款成功", "发出",
)
private val STRONG_FINANCIAL_SIGNALS = INCOMING_KEYWORDS + OUTGOING_KEYWORDS + listOf(
  "向你付款", "向您付款", "给你转了一笔钱", "给您转了一笔钱", "转账成功", "一笔转账", "一笔钱", "收款通知", "付款通知",
)
private val PROMOTIONAL_KEYWORDS = listOf(
  "优惠券", "领券", "签到", "积分", "活动", "福利", "营销", "推广", "额度", "借钱", "贷款", "利率", "验证码", "校验码",
  "登录", "安全提醒", "物流", "发货", "待收货", "即将过期", "红包封面", "点击领取",
)
private val CONFIRMED_PAYMENT_PHRASES = listOf(
  "支付成功", "付款成功", "成功付款", "已支付", "已扣费", "已续费", "已付款", "成功收款", "收款成功", "收款到账", "退款成功", "退款到账", "收到退款", "已退款",
)
private val WECHAT_FINANCIAL_TITLES = listOf("微信支付", "微信收款助手", "支付通知", "收款通知", "服务通知")
private val ALIPAY_FINANCIAL_TITLES = listOf("支付宝", "付款成功", "支付通知", "收款通知", "账单", "资金", "服务提醒")
private val EXPENSE_MERCHANT_PATTERNS = listOf(
  Regex("商户[:：]\\s*(?<merchant>[^，,。；;|]{2,60})"),
  Regex("收款方[:：]\\s*(?<merchant>[^，,。；;|]{2,60})"),
  Regex("向\\s*(?!你|您)(?<merchant>[^，,。；;]{1,40}?)\\s*付款"),
  Regex("(?:付款给|付给)\\s*(?<merchant>[^，,。；;]{2,40})"),
  Regex("付款成功\\s*[¥￥]\\s*[0-9.,]+\\s+(?<merchant>[^，,。；;|]{2,60})"),
)
private val INCOME_COUNTERPARTY_PATTERNS = listOf(
  Regex("收到\\s*(?<counterparty>[^，,。；;]{1,40}?)\\s*的?(?:转账|付款)"),
  Regex("来自\\s*(?<counterparty>[^，,。；;]{1,40}?)(?:的)?(?:转账|付款|收款|到账)"),
  Regex("付款方[:：]\\s*(?<counterparty>[^，,。；;|]{2,60})"),
)

private fun isPromotionalOrNonPayment(content: String): Boolean = PROMOTIONAL_KEYWORDS.any(content::contains)
private fun hasConfirmedPaymentSignal(content: String): Boolean =
  CONFIRMED_PAYMENT_PHRASES.any(content::contains) ||
    Regex("一笔\\s*[0-9.,]+\\s*元的(?:支出|收入)").containsMatchIn(content) ||
    Regex("向\\s*[^，,。；;]{1,40}\\s*付款\\s*[¥￥]?\\s*[0-9.,]+").containsMatchIn(content)
private const val MAX_DESCRIPTION_LENGTH = 120
