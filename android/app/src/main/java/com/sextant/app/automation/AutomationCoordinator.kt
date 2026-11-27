package com.sextant.app.automation

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sextant.app.data.AutomationEventStatus
import com.sextant.app.data.AutomationDiagnostics
import com.sextant.app.data.PostedTransaction
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.StoredAutomationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 自动记账的协调者：把「收到通知」翻译成「库里多一条事件」，再把「事件」翻译成「一笔流水」。
 *
 * 所有入口都是 `suspend` 或「发射到 [scope] 再返回」：这一层现在做的是数据库工作，
 * 而通知回调本身跑在主线程上——在回调里直接落库会在 Room 的线程检查上直接崩，
 * 所以 [capture] 之类的入口只负责把工作交给协程，立刻返回。
 */
class AutomationCoordinator(
  private val context: Context,
  private val repository: SextantRepository,
  private val scope: CoroutineScope,
) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val notifier = AutomationNotifier(context)

  /**
   * 收到一条候选通知时调用，来自 [SextantNotificationListenerService.onNotificationPosted]，
   * 也就是主线程。
   */
  fun capture(notification: CapturedNotification) {
    scope.launch { receive(notification) }
  }

  internal fun noteServiceEvent(channel: String, reason: String, sourcePackage: String? = null,
    details: Map<String, String> = emptyMap()) {
    scope.launch { repository.automationDiagnostics.record(channel, "service", reason, sourcePackage, details = details) }
  }

  suspend fun receivePaymentPage(sourcePackage: String, session: String, candidate: ParsedTransactionCandidate) {
    val settings = repository.loadAutomationSettings()
    if (!settings.enabled || !settings.paymentPageEnabled || !settings.sourceEnabled(sourcePackage)) {
      repository.automationDiagnostics.record("page", "capture", "automation_disabled", sourcePackage, session)
      return
    }
    // Persist only extracted payment fields, never the surrounding screen content.
    val captured = CapturedNotification(
      sourcePackage = sourcePackage,
      notificationKey = "payment-page:${candidate.providerReference ?: "$session|${candidate.amountCents}|${candidate.description}"}",
      postedAt = candidate.occurredAt,
      text = candidate.description,
      pageCandidate = candidate,
    )
    val encrypted = repository.automationSecretStore.encrypt(json.encodeToString(captured).toByteArray(Charsets.UTF_8))
    val eventId = repository.automationStore.capture(captured, encrypted)
    noteCapture(captured, "capture", if (eventId == null) "capture_duplicate" else "captured", eventId, session = session)
    if (eventId == null) return
    enqueueLocal(eventId)
    noteCapture(captured, "queue", "enqueue_requested", eventId, session = session)
    repository.notifyChanged()
  }

  /** 真正处理一条通知：[capture] 的挂起版本，测试可以等它结束。 */
  suspend fun receive(notification: CapturedNotification) {
    val settings = repository.loadAutomationSettings()
    if (!settings.enabled || !settings.sourceEnabled(notification.sourcePackage)) {
      if (NotificationParserRegistry.hasPaymentClues(notification)) noteCapture(notification, "receive", "automation_disabled")
      return
    }
    if (!NotificationParserRegistry.isPlausibleFinancial(notification)) {
      if (NotificationParserRegistry.hasPaymentClues(notification)) noteCapture(notification, "receive", "not_financial")
      return
    }
    noteCapture(notification, "receive", "financial_notification")
    val encrypted = repository.automationSecretStore.encrypt(
      json.encodeToString(notification).toByteArray(Charsets.UTF_8),
    )
    val eventId = repository.automationStore.capture(notification, encrypted)
    noteCapture(notification, "capture", if (eventId == null) "capture_duplicate" else "captured", eventId)
    if (eventId == null) return
    repository.notificationListenerConnection.noteCaptured()
    enqueueLocal(eventId)
    noteCapture(notification, "queue", "enqueue_requested", eventId)
    repository.notifyChanged()
  }

  internal suspend fun processLocal(
    eventId: String,
    decrypt: (EncryptedValue?) -> CapturedNotification? = ::decryptNotification,
  ): ProcessingResult {
    repository.automationDiagnostics.record("worker", "process", "process_started", traceId = eventId)
    val stored = repository.automationStore.getEvent(eventId) ?: return ProcessingResult.Failure
    if (stored.status != AutomationEventStatus.Captured) {
      repository.automationDiagnostics.record("worker", "process", "already_processed", stored.sourcePackage, eventId)
      return ProcessingResult.Success
    }
    val notification = decrypt(stored.encrypted) ?: run {
      repository.automationStore.markFailed(eventId, "payload_unavailable", clearPayload = true)
      repository.automationDiagnostics.record("worker", "process", "payload_unavailable", stored.sourcePackage, eventId)
      repository.notifyChanged()
      return ProcessingResult.Failure
    }
    val settings = repository.loadAutomationSettings()
    if (!settings.enabled || !settings.sourceEnabled(stored.sourcePackage) ||
      (notification.pageCandidate != null && !settings.paymentPageEnabled)) {
      repository.automationStore.markFailed(eventId, "automation_disabled", clearPayload = true)
      noteCapture(notification, "process", "automation_disabled", eventId)
      repository.notifyChanged()
      return ProcessingResult.Success
    }
    return when (val outcome = notification.pageCandidate?.let { LocalParseOutcome.Parsed(it) }
      ?: NotificationParserRegistry.parse(notification)) {
      LocalParseOutcome.NotFinancial -> {
        repository.automationStore.markFailed(eventId, "not_financial", clearPayload = true)
        noteCapture(notification, "parse", "not_financial", eventId)
        repository.notifyChanged()
        ProcessingResult.Success
      }
      is LocalParseOutcome.Insufficient -> {
        repository.automationStore.markFailed(eventId, outcome.reason)
        noteCapture(notification, "parse", outcome.reason, eventId, includeContent = true)
        repository.notifyChanged()
        ProcessingResult.Success
      }
      is LocalParseOutcome.Parsed -> {
        noteCapture(notification, "parse", "parsed", eventId, outcome.candidate)
        val posted = repository.automationStore.postCandidate(eventId, stored.sourcePackage, outcome.candidate, notification.notificationWhen)
        val result = repository.automationStore.getEvent(eventId)
        if (outcome.candidate.enrichmentOnly && result?.status == AutomationEventStatus.Captured) {
          return ProcessingResult.Retry
        }
        notePost(notification, eventId, outcome.candidate, result)
        val finalPosted = if (posted != null && result?.status == AutomationEventStatus.Posted &&
          notification.pageCandidate == null && settings.paymentPageEnabled) {
          enrichFromPendingPages(stored, posted, decrypt)
        } else posted
        onPosted(finalPosted)
        ProcessingResult.Success
      }
    }
  }

  private suspend fun enrichFromPendingPages(
    notification: StoredAutomationEvent,
    posted: PostedTransaction,
    decrypt: (EncryptedValue?) -> CapturedNotification?,
  ): PostedTransaction {
    val store = repository.automationStore
    var latest = posted
    for (event in store.findNearbyPendingPages(notification)) {
      val page = decrypt(event.encrypted) ?: continue
      val candidate = page.pageCandidate?.takeIf { it.enrichmentOnly } ?: continue
      val updated = store.postCandidate(event.id, event.sourcePackage, candidate, expectedTransactionId = posted.id)
      val result = store.getEvent(event.id)
      if (result != null && result.status != AutomationEventStatus.Captured) {
        notePost(page, event.id, candidate, result)
      }
      // The result notification must use the timestamp after enrichment so Undo remains valid.
      if (updated != null) latest = updated
    }
    return latest
  }

  private suspend fun notePost(
    notification: CapturedNotification,
    eventId: String,
    candidate: ParsedTransactionCandidate,
    result: StoredAutomationEvent?,
  ) {
    noteCapture(notification, "post", result?.failureCode ?: result?.status?.name?.lowercase() ?: "event_missing",
      eventId, candidate, includeContent = true, transactionId = result?.transactionId)
  }

  suspend fun undo(transactionId: String, expectedUpdatedAt: Long?): Boolean {
    val undone = repository.automationStore.undoAutomatedTransaction(transactionId, expectedUpdatedAt)
    repository.automationDiagnostics.record("worker", "undo", if (undone) "undone" else "undo_rejected",
      traceId = transactionId)
    if (undone) repository.notifyChanged()
    return undone
  }

  suspend fun retry(eventId: String): Boolean {
    val queued = repository.automationStore.markCapturedForRetry(eventId)
    repository.automationDiagnostics.record("worker", "retry", if (queued) "retry_queued" else "retry_rejected", traceId = eventId)
    if (queued) {
      enqueueLocal(eventId)
      repository.notifyChanged()
    }
    return queued
  }

  suspend fun cleanup() {
    repository.automationStore.cleanup()
    repository.automationDiagnostics.cleanup()
  }

  private suspend fun noteCapture(
    notification: CapturedNotification,
    stage: String,
    reason: String,
    eventId: String? = null,
    candidate: ParsedTransactionCandidate? = null,
    includeContent: Boolean = false,
    transactionId: String? = null,
    session: String? = null,
  ) {
    if (!repository.automationDiagnostics.isEnabled()) return
    val details = buildMap {
      put("notificationKeyHash", AutomationDiagnostics.fingerprint(notification.notificationKey))
      put("payloadHash", AutomationDiagnostics.fingerprint(notification.normalizedText()))
      put("postedAt", notification.postedAt.toString())
      put("notificationWhen", notification.notificationWhen.toString())
      transactionId?.let { put("transactionId", it) }
      session?.let { put("scanSession", it) }
      candidate?.let {
        put("parser", it.parser)
        put("direction", it.direction.name)
        put("kind", it.kind.name)
        put("occurredAt", it.occurredAt.toString())
        put("hasExactTime", it.hasExactTime.toString())
        put("hasMerchant", (!it.merchant.isNullOrBlank()).toString())
        it.providerReference?.let { ref -> put("referenceHash", AutomationDiagnostics.fingerprint(ref)) }
      }
    }
    repository.automationDiagnostics.record(if (notification.pageCandidate == null) "notification" else "page",
      stage, reason, notification.sourcePackage, eventId, details,
      content = {
        if (!includeContent) emptyList()
        else notification.pageCandidate?.let { page ->
          listOfNotNull(page.directionEvidence, page.amountEvidence, page.merchant,
            page.description, page.providerReference)
        } ?: notification.normalizedText().lines()
      })
  }

  private suspend fun onPosted(posted: PostedTransaction?) {
    repository.notifyChanged()
    if (posted != null && repository.loadAutomationSettings().resultNotificationsEnabled) {
      notifier.showPosted(posted)
    }
  }

  private fun decryptNotification(encrypted: EncryptedValue?): CapturedNotification? {
    if (encrypted == null) return null
    return runCatching {
      val bytes = repository.automationSecretStore.decrypt(encrypted)
      json.decodeFromString<CapturedNotification>(bytes.toString(Charsets.UTF_8))
    }.getOrNull()
  }

  private fun enqueueLocal(eventId: String) {
    val request = OneTimeWorkRequestBuilder<LocalParseWorker>()
      .setInputData(eventData(eventId))
      .addTag(AUTOMATION_WORK_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork("automation-local-$eventId", ExistingWorkPolicy.KEEP, request)
  }

  private fun eventData(eventId: String) = Data.Builder().putString(EVENT_ID_KEY, eventId).build()

  private fun com.sextant.app.data.AutomationSettings.sourceEnabled(packageName: String): Boolean = when (packageName) {
    WECHAT_PACKAGE -> wechatEnabled
    ALIPAY_PACKAGE -> alipayEnabled
    else -> false
  }
}

internal enum class ProcessingResult { Success, Retry, Failure }
internal const val EVENT_ID_KEY = "automation_event_id"
const val AUTOMATION_WORK_TAG = "sextant-automation"
