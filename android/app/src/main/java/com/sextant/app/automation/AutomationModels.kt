package com.sextant.app.automation

import com.sextant.app.data.AutomationKind
import com.sextant.app.data.TransactionType
import kotlinx.serialization.Serializable

const val WECHAT_PACKAGE = "com.tencent.mm"
const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

@Serializable
data class CapturedNotification(
  val sourcePackage: String,
  val notificationKey: String,
  val postedAt: Long,
  val title: String = "",
  val titleBig: String = "",
  val text: String = "",
  val bigText: String = "",
  val textLines: List<String> = emptyList(),
  val infoText: String = "",
  val subText: String = "",
  val summaryText: String = "",
  val tickerText: String = "",
  val groupChildText: String = "",
) {
  fun normalizedText(): String = buildList {
    add(title)
    add(titleBig)
    add(text)
    add(bigText)
    addAll(textLines)
    add(infoText)
    add(subText)
    add(summaryText)
    add(tickerText)
    add(groupChildText)
  }
    .asSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .joinToString("\n")
    .take(MAX_NOTIFICATION_TEXT_LENGTH)
}

data class EncryptedValue(
  val ciphertext: ByteArray,
  val iv: ByteArray,
)

data class ParsedTransactionCandidate(
  val direction: TransactionType,
  val kind: AutomationKind,
  val amountCents: Long,
  val currency: String = "CNY",
  val occurredAt: Long,
  val merchant: String? = null,
  val counterparty: String? = null,
  val description: String,
  val categoryHint: String? = null,
  val providerReference: String? = null,
  val amountEvidence: String,
  val directionEvidence: String,
  val parser: String,
)

sealed interface LocalParseOutcome {
  data object NotFinancial : LocalParseOutcome
  data class Parsed(val candidate: ParsedTransactionCandidate) : LocalParseOutcome
  data class NeedsEnhancement(val reason: String, val context: AiEnhancementContext) : LocalParseOutcome
  data class Insufficient(val reason: String) : LocalParseOutcome
}

data class AiEnhancementContext(
  val amountCents: Long,
  val amountEvidence: String,
  val localCandidate: ParsedTransactionCandidate? = null,
  val categoryHints: List<String> = emptyList(),
)

@Serializable
data class CloudParsePayload(
  val direction: String,
  val kind: String,
  val amount: String,
  val currency: String = "CNY",
  val occurredAt: String? = null,
  val merchant: String? = null,
  val counterparty: String? = null,
  val description: String = "",
  val categoryHint: String? = null,
  val providerReference: String? = null,
  val evidence: CloudEvidence,
)

@Serializable
data class CloudEvidence(
  val amount: String,
  val direction: String,
  val merchant: String? = null,
  val counterparty: String? = null,
)

internal const val MAX_NOTIFICATION_TEXT_LENGTH = 8_000
