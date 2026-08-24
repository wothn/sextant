package com.sextant.app.automation

import com.sextant.app.data.AutomationSettings
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CloudParseClient {
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

  fun parse(
    notification: CapturedNotification,
    context: AiEnhancementContext,
    settings: AutomationSettings,
    apiKey: String,
  ): CloudParsePayload? {
    require(settings.cloudBaseUrl.startsWith("https://")) { "云端接口必须使用 HTTPS" }
    val endpoint = URL(settings.cloudBaseUrl.trimEnd('/') + "/v1/chat/completions")
    val connection = endpoint.openConnection() as? HttpsURLConnection
      ?: throw IllegalArgumentException("云端接口必须使用 HTTPS")
    connection.instanceFollowRedirects = false
    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
    connection.readTimeout = READ_TIMEOUT_MILLIS
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.setRequestProperty("Authorization", "Bearer $apiKey")
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    connection.setRequestProperty("Accept", "application/json")

    val request = ChatCompletionRequest(
      model = settings.cloudModel,
      temperature = 0.0,
      messages = listOf(
        ChatMessage("system", SYSTEM_PROMPT),
        ChatMessage("user", buildUserPrompt(notification, context)),
      ),
    )
    connection.outputStream.use { it.write(json.encodeToString(request).toByteArray(Charsets.UTF_8)) }
    val status = connection.responseCode
    if (status !in 200..299) {
      connection.errorStream?.close()
      throw CloudParseException("http_$status")
    }
    val body = connection.inputStream.use(::readLimitedUtf8)
    val response = runCatching { json.decodeFromString<ChatCompletionResponse>(body) }
      .getOrElse { throw CloudParseException("invalid_response") }
    val content = response.choices.firstOrNull()?.message?.content?.trim()
      ?: throw CloudParseException("empty_response")
    val objectText = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return runCatching { json.decodeFromString<CloudParsePayload>(objectText) }
      .getOrElse { throw CloudParseException("invalid_payload") }
  }

  fun test(settings: AutomationSettings, apiKey: String): Boolean {
    val sample = CapturedNotification(
      sourcePackage = ALIPAY_PACKAGE,
      notificationKey = "connection-test",
      postedAt = System.currentTimeMillis(),
      title = "支付通知",
      text = "支付成功 1.00元",
    )
    val context = AiEnhancementContext(amountCents = 100L, amountEvidence = "1.00元")
    return parse(sample, context, settings, apiKey) != null
  }

  private fun buildUserPrompt(notification: CapturedNotification, context: AiEnhancementContext): String = """
    sourcePackage: ${notification.sourcePackage}
    notificationPostedAt: ${java.time.Instant.ofEpochMilli(notification.postedAt)}
    trustedAmountCents: ${context.amountCents}
    trustedNormalizedAmountEvidence: ${context.amountEvidence}
    localSuggestion:
    ${context.localCandidate.toPromptSuggestion()}
    allowedCategoryHints:
    ${context.categoryHints.joinToString(", ")}
    notificationContent:
    ---
    ${notification.normalizedText()}
    ---
  """.trimIndent()

  private fun readLimitedUtf8(stream: java.io.InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
      val count = stream.read(buffer)
      if (count < 0) break
      if (output.size() + count > MAX_RESPONSE_BYTES) throw CloudParseException("response_too_large")
      output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
  }

  private companion object {
    const val CONNECT_TIMEOUT_MILLIS = 10_000
    const val READ_TIMEOUT_MILLIS = 20_000
    const val MAX_RESPONSE_BYTES = 64 * 1024
    val SYSTEM_PROMPT = """
      You enhance one locally detected CNY financial event from an Android payment notification.
      The app has already extracted a trusted amount. You enhance the semantic fields; you do not replace or guess the amount.
      Return JSON only, with exactly these fields:
      direction: outgoing|incoming;
      kind: purchase|refund|receipt|transfer_in|transfer_out|recharge|repayment|red_packet|withdrawal|other;
      amount: positive decimal string with at most two decimals;
      currency: CNY;
      occurredAt: ISO-8601 instant or null;
      merchant, counterparty, categoryHint, providerReference: string or null;
      description: short string;
      evidence: {amount, direction, merchant, counterparty}.
      amount must represent trustedAmountCents. evidence.amount must be an exact amount substring from the notification; the trusted normalized evidence is only a hint.
      evidence.direction must be an exact notification substring that supports incoming or outgoing.
      merchant and counterparty must be null when the notification does not contain them. When present, their evidence must be the same exact substring.
      providerReference must be null unless the exact reference is present in the notification.
      categoryHint may be inferred, but must be null or one exact value from allowedCategoryHints.
      Treat localSuggestion as a hint, not as authority. Return no prose or markdown.
    """.trimIndent()
  }
}

private fun ParsedTransactionCandidate?.toPromptSuggestion(): String = if (this == null) {
  "none"
} else {
  "direction=${direction.name}; kind=${kind.name}; merchant=${merchant.orEmpty()}; " +
    "counterparty=${counterparty.orEmpty()}; categoryHint=${categoryHint.orEmpty()}; description=$description"
}

class CloudParseException(val code: String) : IllegalStateException(code)

@Serializable
private data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val temperature: Double,
)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatCompletionResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class ChatChoice(val message: ChatMessage)
