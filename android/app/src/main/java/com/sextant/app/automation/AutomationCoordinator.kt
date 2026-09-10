package com.sextant.app.automation

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sextant.app.data.AutomationEventStatus
import com.sextant.app.data.PostedTransaction
import com.sextant.app.data.SextantRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AutomationCoordinator(
  private val context: Context,
  private val repository: SextantRepository,
) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val notifier = AutomationNotifier(context)

  fun capture(notification: CapturedNotification) {
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

  internal fun processLocal(eventId: String): ProcessingResult {
    val stored = repository.automationStore.getEvent(eventId) ?: return ProcessingResult.Failure
    if (stored.status != AutomationEventStatus.Captured) return ProcessingResult.Success
    val notification = decryptNotification(stored.encrypted) ?: run {
      repository.automationStore.markFailed(eventId, "payload_unavailable", clearPayload = true)
      repository.notifyChanged()
      return ProcessingResult.Failure
    }
    return when (val outcome = NotificationParserRegistry.parse(notification)) {
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

  fun undo(transactionId: String, expectedUpdatedAt: Long?): Boolean {
    val undone = repository.automationStore.undoAutomatedTransaction(transactionId, expectedUpdatedAt)
    if (undone) repository.notifyChanged()
    return undone
  }

  fun retry(eventId: String): Boolean {
    val queued = repository.automationStore.markCapturedForRetry(eventId)
    if (queued) {
      enqueueLocal(eventId)
      repository.notifyChanged()
    }
    return queued
  }

  fun cleanup() = repository.automationStore.cleanup()

  private fun onPosted(posted: PostedTransaction?) {
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
