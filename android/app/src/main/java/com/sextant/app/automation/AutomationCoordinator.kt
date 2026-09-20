package com.sextant.app.automation

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sextant.app.data.AutomationEventStatus
import com.sextant.app.data.PostedTransaction
import com.sextant.app.data.SextantRepository
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

  suspend fun receivePaymentPage(sourcePackage: String, session: String, candidate: ParsedTransactionCandidate) {
    val settings = repository.loadAutomationSettings()
    if (!settings.enabled || !settings.paymentPageEnabled || !settings.sourceEnabled(sourcePackage)) return
    // Persist only extracted payment fields, never the surrounding screen content.
    val captured = CapturedNotification(
      sourcePackage = sourcePackage,
      notificationKey = "payment-page:${candidate.providerReference ?: "$session|${candidate.amountCents}|${candidate.description}"}",
      postedAt = candidate.occurredAt,
      text = candidate.description,
      pageCandidate = candidate,
    )
    val encrypted = repository.automationSecretStore.encrypt(json.encodeToString(captured).toByteArray(Charsets.UTF_8))
    val eventId = repository.automationStore.capture(captured, encrypted) ?: return
    enqueueLocal(eventId)
    repository.notifyChanged()
  }

  /** 真正处理一条通知：[capture] 的挂起版本，测试可以等它结束。 */
  suspend fun receive(notification: CapturedNotification) {
    val settings = repository.loadAutomationSettings()
    if (!settings.enabled || !settings.sourceEnabled(notification.sourcePackage)) return
    if (!NotificationParserRegistry.isPlausibleFinancial(notification)) return
    val encrypted = repository.automationSecretStore.encrypt(
      json.encodeToString(notification).toByteArray(Charsets.UTF_8),
    )
    val eventId = repository.automationStore.capture(notification, encrypted) ?: return
    repository.notificationListenerConnection.noteCaptured()
    enqueueLocal(eventId)
    repository.notifyChanged()
  }

  internal suspend fun processLocal(eventId: String): ProcessingResult {
    val stored = repository.automationStore.getEvent(eventId) ?: return ProcessingResult.Failure
    if (stored.status != AutomationEventStatus.Captured) return ProcessingResult.Success
    val notification = decryptNotification(stored.encrypted) ?: run {
      repository.automationStore.markFailed(eventId, "payload_unavailable", clearPayload = true)
      repository.notifyChanged()
      return ProcessingResult.Failure
    }
    val settings = repository.loadAutomationSettings()
    if (!settings.enabled || !settings.sourceEnabled(stored.sourcePackage) ||
      (notification.pageCandidate != null && !settings.paymentPageEnabled)) {
      repository.automationStore.markFailed(eventId, "automation_disabled", clearPayload = true)
      repository.notifyChanged()
      return ProcessingResult.Success
    }
    return when (val outcome = notification.pageCandidate?.let { LocalParseOutcome.Parsed(it) }
      ?: NotificationParserRegistry.parse(notification)) {
      LocalParseOutcome.NotFinancial -> {
        repository.automationStore.markFailed(eventId, "not_financial", clearPayload = true)
        repository.notifyChanged()
        ProcessingResult.Success
      }
      is LocalParseOutcome.Insufficient -> {
        repository.automationStore.markFailed(eventId, outcome.reason)
        repository.notifyChanged()
        ProcessingResult.Success
      }
      is LocalParseOutcome.Parsed -> {
        val posted = repository.automationStore.postCandidate(eventId, stored.sourcePackage, outcome.candidate)
        onPosted(posted)
        ProcessingResult.Success
      }
    }
  }

  suspend fun undo(transactionId: String, expectedUpdatedAt: Long?): Boolean {
    val undone = repository.automationStore.undoAutomatedTransaction(transactionId, expectedUpdatedAt)
    if (undone) repository.notifyChanged()
    return undone
  }

  suspend fun retry(eventId: String): Boolean {
    val queued = repository.automationStore.markCapturedForRetry(eventId)
    if (queued) {
      enqueueLocal(eventId)
      repository.notifyChanged()
    }
    return queued
  }

  suspend fun cleanup() = repository.automationStore.cleanup()

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
