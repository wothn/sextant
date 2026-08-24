package com.sextant.app.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sextant.app.data.AutomationEventStatus
import com.sextant.app.data.PostedTransaction
import com.sextant.app.data.SextantRepository
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AutomationCoordinator(
  private val context: Context,
  private val repository: SextantRepository,
) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val cloudClient = CloudParseClient()
  private val notifier = AutomationNotifier(context)

  fun capture(notification: CapturedNotification) {
    val settings = repository.loadAutomationSettings()
    if (!settings.enabled || !settings.sourceEnabled(notification.sourcePackage)) return
    if (!NotificationParserRegistry.isPlausibleFinancial(notification)) return
    val encrypted = repository.automationSecretStore.encrypt(
      json.encodeToString(notification).toByteArray(Charsets.UTF_8),
    )
    val eventId = repository.automationStore.capture(notification, encrypted) ?: return
    enqueueLocal(eventId)
    repository.notifyChanged()
  }

  internal fun processLocal(eventId: String): ProcessingResult {
    val stored = repository.automationStore.getEvent(eventId) ?: return ProcessingResult.Failure
    if (stored.status !in setOf(AutomationEventStatus.Captured, AutomationEventStatus.WaitingCloud)) return ProcessingResult.Success
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
      is LocalParseOutcome.NeedsEnhancement -> {
        val settings = repository.loadAutomationSettings()
        if (settings.cloudEnabled && settings.hasCloudApiKey) {
          repository.automationStore.markWaitingCloud(eventId, outcome.reason)
          enqueueCloud(eventId)
        } else {
          repository.automationStore.markFailed(eventId, outcome.reason)
        }
        repository.notifyChanged()
        ProcessingResult.Success
      }
      is LocalParseOutcome.Parsed -> {
        val settings = repository.loadAutomationSettings()
        if (settings.cloudEnabled && settings.hasCloudApiKey) {
          repository.automationStore.markWaitingCloud(eventId, "ai_enhancement")
          enqueueCloud(eventId)
          repository.notifyChanged()
        } else {
          val posted = repository.automationStore.postCandidate(eventId, stored.sourcePackage, outcome.candidate)
          onPosted(posted)
        }
        ProcessingResult.Success
      }
    }
  }

  internal fun processCloud(eventId: String, attempt: Int): ProcessingResult {
    val stored = repository.automationStore.getEvent(eventId) ?: return ProcessingResult.Failure
    if (stored.status != AutomationEventStatus.WaitingCloud) return ProcessingResult.Success
    val notification = decryptNotification(stored.encrypted) ?: run {
      repository.automationStore.markFailed(eventId, "payload_unavailable", clearPayload = true)
      repository.notifyChanged()
      return ProcessingResult.Failure
    }
    val localOutcome = NotificationParserRegistry.parse(notification)
    val fallbackCandidate = (localOutcome as? LocalParseOutcome.Parsed)?.candidate
    val enhancementContext = when (localOutcome) {
      is LocalParseOutcome.Parsed -> AiEnhancementContext(
        amountCents = localOutcome.candidate.amountCents,
        amountEvidence = localOutcome.candidate.amountEvidence,
        localCandidate = localOutcome.candidate,
      )
      is LocalParseOutcome.NeedsEnhancement -> localOutcome.context
      is LocalParseOutcome.Insufficient -> {
        repository.automationStore.markFailed(eventId, localOutcome.reason)
        repository.notifyChanged()
        return ProcessingResult.Failure
      }
      LocalParseOutcome.NotFinancial -> {
        repository.automationStore.markFailed(eventId, "not_financial", clearPayload = true)
        repository.notifyChanged()
        return ProcessingResult.Failure
      }
    }.copy(categoryHints = repository.listActiveCategoryNames())
    val settings = repository.loadAutomationSettings()
    val apiKey = repository.automationSecretStore.getApiKey()
    if (!settings.cloudEnabled || apiKey.isNullOrBlank()) {
      return fallbackOrFail(eventId, stored.sourcePackage, fallbackCandidate, "ai_not_configured")
    }
    return try {
      val payload = cloudClient.parse(notification, enhancementContext, settings, apiKey)
        ?: throw CloudParseException("empty_payload")
      val candidate = NotificationParserRegistry.validateCloudPayload(
        payload,
        notification,
        expectedAmountCents = enhancementContext.amountCents,
        allowedCategoryHints = enhancementContext.categoryHints.toSet(),
      )
      if (candidate == null) {
        fallbackOrFail(eventId, stored.sourcePackage, fallbackCandidate, "ai_validation_failed")
      } else {
        val posted = repository.automationStore.postCandidate(eventId, stored.sourcePackage, candidate)
        onPosted(posted)
        ProcessingResult.Success
      }
    } catch (error: Exception) {
      if (attempt < MAX_CLOUD_ATTEMPTS) {
        ProcessingResult.Retry
      } else {
        val code = (error as? CloudParseException)?.code ?: "cloud_request_failed"
        fallbackOrFail(eventId, stored.sourcePackage, fallbackCandidate, code)
      }
    }
  }

  private fun fallbackOrFail(
    eventId: String,
    sourcePackage: String,
    fallbackCandidate: ParsedTransactionCandidate?,
    failureCode: String,
  ): ProcessingResult {
    if (fallbackCandidate == null) {
      repository.automationStore.markFailed(eventId, failureCode)
      repository.notifyChanged()
      return ProcessingResult.Failure
    }
    val posted = repository.automationStore.postCandidate(eventId, sourcePackage, fallbackCandidate)
    onPosted(posted)
    return ProcessingResult.Success
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

  fun testCloudConnection(): Boolean {
    val settings = repository.loadAutomationSettings()
    val apiKey = repository.automationSecretStore.getApiKey() ?: return false
    return cloudClient.test(settings, apiKey)
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

  private fun enqueueCloud(eventId: String) {
    val request = OneTimeWorkRequestBuilder<CloudParseWorker>()
      .setInputData(eventData(eventId))
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
      .addTag(AUTOMATION_WORK_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork("automation-cloud-$eventId", ExistingWorkPolicy.KEEP, request)
  }

  private fun eventData(eventId: String) = Data.Builder().putString(EVENT_ID_KEY, eventId).build()

  private fun com.sextant.app.data.AutomationSettings.sourceEnabled(packageName: String): Boolean = when (packageName) {
    WECHAT_PACKAGE -> wechatEnabled
    ALIPAY_PACKAGE -> alipayEnabled
    else -> false
  }

  private companion object {
    const val MAX_CLOUD_ATTEMPTS = 3
  }
}

internal enum class ProcessingResult { Success, Retry, Failure }
internal const val EVENT_ID_KEY = "automation_event_id"
const val AUTOMATION_WORK_TAG = "sextant-automation"
