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
  // Read compatibility for old encrypted payloads; new captures expand child notifications.
  val groupChildText: String = "",
  val pageCandidate: ParsedTransactionCandidate? = null,
  // Notification.when identifies the original notification across reposts; postTime changes on updates.
  val notificationWhen: Long? = null,
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

@Serializable
data class ParsedTransactionCandidate(
  val direction: TransactionType,
  val kind: AutomationKind,
  val amountCents: Long,
  val currency: String = "CNY",
  val occurredAt: Long,
  val merchant: String? = null,
  val counterparty: String? = null,
  val description: String,
  val providerReference: String? = null,
  val amountEvidence: String,
  val directionEvidence: String,
  val parser: String,
  val hasExactTime: Boolean = false,
)

sealed interface LocalParseOutcome {
  data object NotFinancial : LocalParseOutcome
  data class Parsed(val candidate: ParsedTransactionCandidate) : LocalParseOutcome
  data class Insufficient(val reason: String) : LocalParseOutcome
}

internal const val MAX_NOTIFICATION_TEXT_LENGTH = 8_000
