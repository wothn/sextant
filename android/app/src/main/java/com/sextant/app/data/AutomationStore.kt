package com.sextant.app.data

import androidx.room.withTransaction
import com.sextant.app.automation.ALIPAY_PACKAGE
import com.sextant.app.automation.CapturedNotification
import com.sextant.app.automation.EncryptedValue
import com.sextant.app.automation.ParsedTransactionCandidate
import com.sextant.app.automation.WECHAT_PACKAGE
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.sync.withLock

/**
 * 自动记账的持久化：通知事件，以及「把一条通知落成流水」这个动作本身。
 *
 * 每个方法都是 `suspend`，写入路径都包在事务里：自动记账在监听服务和 Worker 两个后台
 * 上下文里被调用，落一笔账要同时改 `transactions` 和 `automation_events`。中途失败必须
 * 整体回滚，否则会留下「流水有了、事件还停在待处理」的一半成功状态，重试时再插一笔重复的账。
 */
internal class AutomationStore(private val database: SextantDatabase) {
  suspend fun loadSettings(): AutomationSettings {
    val values = database.appSettingDao().listAutomationSettings().associate { it.key to it.value }
    return AutomationSettings(
      enabled = values[KEY_ENABLED].toBooleanSetting(),
      wechatEnabled = values[KEY_WECHAT].toBooleanSetting(default = true),
      alipayEnabled = values[KEY_ALIPAY].toBooleanSetting(default = true),
      resultNotificationsEnabled = values[KEY_RESULT_NOTIFICATIONS].toBooleanSetting(default = true),
      paymentPageEnabled = values[KEY_PAYMENT_PAGE].toBooleanSetting(),
      diagnosticsEnabled = values[AutomationDiagnostics.ENABLED_KEY].toBooleanSetting(),
      diagnosticContentEnabled = values[AutomationDiagnostics.CONTENT_KEY].toBooleanSetting(),
    )
  }

  suspend fun updateSettings(settings: AutomationSettings) {
    val values = mapOf(
      KEY_ENABLED to settings.enabled.asSetting(),
      KEY_WECHAT to settings.wechatEnabled.asSetting(),
      KEY_ALIPAY to settings.alipayEnabled.asSetting(),
      KEY_RESULT_NOTIFICATIONS to settings.resultNotificationsEnabled.asSetting(),
      KEY_PAYMENT_PAGE to settings.paymentPageEnabled.asSetting(),
      AutomationDiagnostics.ENABLED_KEY to settings.diagnosticsEnabled.asSetting(),
      AutomationDiagnostics.CONTENT_KEY to (settings.diagnosticsEnabled && settings.diagnosticContentEnabled).asSetting(),
    )
    database.diagnosticPolicy.mutex.withLock {
      database.withTransaction {
        values.forEach { (key, value) ->
          database.appSettingDao().upsert(AppSettingEntity(key, value))
        }
      }
      database.diagnosticPolicy.settings = settings.copy(
        diagnosticContentEnabled = settings.diagnosticsEnabled && settings.diagnosticContentEnabled)
    }
  }

  /**
   * 记下一条通知，返回事件 id；同一身份、相同内容的通知重复投递时返回 null。
   *
   * 幂等靠 `eventKey` 上的唯一索引加 `INSERT OR IGNORE`，而不是「先查再插」：
   * 监听服务可能在短时间内被系统连着重启，先查再插会在这两步之间漏进第二行。
   */
  suspend fun capture(notification: CapturedNotification, encrypted: EncryptedValue): String? {
    val id = UUID.randomUUID().toString()
    val page = notification.pageCandidate
    val payloadHash = sha256(notification.normalizedText())
    val originalTime = notification.notificationWhen?.takeIf { it > 0 }
    val eventKey = when {
      page?.providerReference != null -> sha256("${notification.sourcePackage}|${notification.notificationKey}")
      page?.hasExactTime == true -> sha256("${notification.sourcePackage}|page|${page.occurredAt}|${page.amountCents}|${page.description}")
      page != null -> sha256("${notification.sourcePackage}|${notification.notificationKey}")
      else -> {
        val notificationTime = originalTime ?: notification.postedAt
        val identity = sha256("${notification.sourcePackage}|${notification.notificationKey}|$notificationTime")
        // Let richer updates be parsed even if an earlier payload was missing its amount.
        // Successfully parsed variants are merged by this stable identity in postCandidate.
        if (originalTime != null) "$identity:$payloadHash" else identity
      }
    }
    val inserted = database.automationEventDao().insertIgnoringDuplicates(
      AutomationEventEntity(
        id = id,
        eventKey = eventKey,
        sourcePackage = notification.sourcePackage,
        notificationKey = notification.notificationKey,
        postedAt = notification.postedAt,
        payloadHash = payloadHash,
        encryptedPayload = encrypted.ciphertext,
        payloadIv = encrypted.iv,
        status = AutomationEventStatus.Captured,
        parser = null,
        providerReference = null,
        failureCode = null,
        parsedSummary = null,
        transactionId = null,
        createdAt = System.currentTimeMillis(),
        processedAt = null,
      ),
    )
    return if (inserted == -1L) null else id
  }

  suspend fun getEvent(id: String): StoredAutomationEvent? =
    database.automationEventDao().findById(id)?.toStoredEvent()

  suspend fun findNearbyPendingPages(notification: StoredAutomationEvent): List<StoredAutomationEvent> =
    database.automationEventDao().findNearbyPendingPages(
      notification.sourcePackage,
      notification.postedAt - PAYMENT_MATCH_WINDOW_MILLIS,
      notification.postedAt + PAYMENT_MATCH_WINDOW_MILLIS,
    ).map { it.toStoredEvent() }

  suspend fun markFailed(id: String, failureCode: String, clearPayload: Boolean = false) {
    updateEvent(id, AutomationEventStatus.Failed, failureCode = failureCode, clearPayload = clearPayload)
  }

  /**
   * 把失败事件退回待处理。
   *
   * 原始载荷已经被保留期清掉的事件不会退回：那种重试注定失败，不如让这里返回 false，
   * 界面上直接告诉用户「原通知已过期」。
   */
  suspend fun markCapturedForRetry(id: String): Boolean =
    database.automationEventDao().markCapturedForRetry(
      id = id,
      capturedStatus = AutomationEventStatus.Captured,
      failedStatus = AutomationEventStatus.Failed,
    ) == 1

  /**
   * 把已解析的通知落成一笔流水。
   *
   * 先按交易号和通知原始身份去重，再尝试支付页面匹配；事件和流水的写入都在同一事务内。
   *
   * 返回 null 表示「这次没有新增流水」（重复或已处理），调用方据此决定要不要发结果通知。
   */
  suspend fun postCandidate(
    eventId: String,
    sourcePackage: String,
    candidate: ParsedTransactionCandidate,
    originalNotificationTime: Long? = null,
    expectedTransactionId: String? = null,
  ): PostedTransaction? = database.withTransaction {
    val events = database.automationEventDao()
    val now = System.currentTimeMillis()

    val event = events.findById(eventId) ?: return@withTransaction null
    if (event.status != AutomationEventStatus.Captured || event.sourcePackage != sourcePackage) return@withTransaction null
    val summary = "${candidate.direction.storageValue()}|${candidate.kind.storageValue()}|${candidate.amountCents}"
    val isPage = candidate.parser.contains("-page-")
    val referenceMatch = candidate.providerReference?.let { events.findByReference(sourcePackage, it, eventId) }
    val notificationMatch = if (!isPage && ':' in event.eventKey) {
      val identity = event.eventKey.substringBefore(':')
      // v13 had only delivery time and could classify an expense's recipient as Receipt.
      // Match differing legacy times only with the same key/summary and within this notification's
      // lifetime, including richer payload updates. A later purchase reusing the same key starts after the old delivery time.
      val legacySummary = if (candidate.direction == TransactionType.Expense && candidate.kind == AutomationKind.Purchase)
        "expense|receipt|${candidate.amountCents}" else summary
      val originalTime = originalNotificationTime?.takeIf { it > 0 } ?: event.postedAt
      events.findByNotificationIdentity("$identity:%", identity, summary, legacySummary,
        sourcePackage, event.notificationKey, originalTime, event.postedAt,
        candidate.providerReference, eventId)
    } else null
    val identityMatch = referenceMatch ?: notificationMatch
    val matches = if (identityMatch != null) listOf(identityMatch) else {
      events.findNearby(sourcePackage, candidate.occurredAt - PAYMENT_MATCH_WINDOW_MILLIS,
        candidate.occurredAt + PAYMENT_MATCH_WINDOW_MILLIS, summary, eventId)
        .filter { previous ->
          val previousPage = previous.notificationKey.startsWith("payment-page:")
          if (candidate.enrichmentOnly && (previousPage || previous.status != AutomationEventStatus.Posted)) return@filter false
          // Same-channel notifications can be two legitimate purchases. Include pages
          // with an unknown merchant so the ambiguity guard below prevents duplicate bills.
          val row = previous.transactionId?.let { database.transactionDao().findById(it) }
          val compatibleParty = row == null || row.description.isBlank() || candidate.description.isBlank() ||
            row.description == candidate.description || row.isUserEdited
          val compatibleReference = previous.providerReference == null || candidate.providerReference == null ||
            previous.providerReference == candidate.providerReference
          compatibleReference && compatibleParty && (previousPage != isPage ||
            (isPage && row != null && (row.description == candidate.description ||
              row.description.isBlank() || candidate.description.isBlank())))
        }
    }
    // A notification-triggered pass only acts on a page that uniquely matches that transaction.
    // Leave unresolved pages to their scheduled worker.
    if (expectedTransactionId != null && matches.singleOrNull()?.transactionId != expectedTransactionId) {
      return@withTransaction null
    }
    if (candidate.enrichmentOnly && matches.isEmpty()) {
      // Page and notification workers can run in either order. Keep the encrypted card
      // briefly so WorkManager can retry after the notification has committed.
      if (now - event.createdAt in 0..90_000L) return@withTransaction null
      updateEvent(eventId, AutomationEventStatus.Failed, candidate.parser, "payment_fields_missing", clearPayload = true)
      return@withTransaction null
    }
    if (matches.size > 1) {
      updateEvent(eventId, AutomationEventStatus.Failed, candidate.parser, "payment_match_ambiguous", clearPayload = true)
      return@withTransaction null
    }
    matches.singleOrNull()?.let { previous ->
      if (identityMatch == null && events.hasMergedChannel(previous.transactionId, if (isPage) "payment-page:%" else "%", isPage)) {
        updateEvent(eventId, AutomationEventStatus.Failed, candidate.parser, "payment_match_ambiguous", clearPayload = true)
        return@withTransaction null
      }
      if (identityMatch == null && isPage && previous.notificationKey.startsWith("payment-page:") &&
        !(candidate.hasExactTime && previous.postedAt == candidate.occurredAt)) {
        updateEvent(eventId, AutomationEventStatus.Failed, candidate.parser, "payment_match_ambiguous", clearPayload = true)
        return@withTransaction null
      }
      val row = previous.transactionId?.let { database.transactionDao().findById(it) }
      var updated: PostedTransaction? = null
      if (row != null && !row.isUserEdited) {
        val merchant = row.merchantName ?: candidate.merchant?.trim()?.takeIf { it.isNotBlank() }
        val category = row.categoryId ?: if (row.merchantName == null) MerchantClassifier(database).classify(merchant, row.type, row.automationKind) else null
        val description = row.description.ifBlank { candidate.description.trim().take(120) }
        if (merchant != row.merchantName || category != row.categoryId || description != row.description) {
          val updatedAt = maxOf(now, row.updatedAt + 1)
          database.transactionDao().enrichMerchant(row.id, merchant, category, description, updatedAt)
          updated = PostedTransaction(row.id, row.type, row.amountCents, description, updatedAt)
        }
      }
      events.updateStatus(eventId, AutomationEventStatus.Duplicate, now, candidate.parser,
        "payment_merged", true, summary, previous.transactionId, true, null, true)
      if (candidate.providerReference != null) events.attachReference(previous.id, candidate.providerReference)
      return@withTransaction updated
    }

    if (candidate.providerReference != null &&
      events.providerReferenceExists(sourcePackage, candidate.providerReference, eventId)
    ) {
      events.updateStatus(
        id = eventId,
        status = AutomationEventStatus.Duplicate,
        processedAt = now,
        parser = candidate.parser,
        failureCode = "provider_reference_duplicate",
        setFailureCode = true,
        parsedSummary = null,
        transactionId = null,
        setTransactionId = false,
        providerReference = null,
        clearPayload = true,
      )
      return@withTransaction null
    }

    if (database.transactionDao().existsForSourceEvent(eventId)) {
      return@withTransaction null
    }

    val paymentMethodId = findPaymentMethodId(sourcePackage)
    val transactionId = UUID.randomUUID().toString()
    database.transactionDao().insert(
      TransactionEntity(
        id = transactionId,
        categoryId = MerchantClassifier(database).classify(candidate.merchant, candidate.direction, candidate.kind),
        merchantName = candidate.merchant?.trim()?.takeIf { it.isNotBlank() },
        paymentMethodId = paymentMethodId,
        amountCents = candidate.amountCents,
        type = candidate.direction,
        description = candidate.description.trim().take(120),
        transactionDate = candidate.occurredAt,
        createdAt = now,
        updatedAt = now,
        entryOrigin = if (isPage) EntryOrigin.Accessibility else EntryOrigin.Notification,
        automationKind = candidate.kind,
        sourceEventId = eventId,
        isUserEdited = false,
      ),
    )
    events.updateStatus(
      id = eventId,
      status = AutomationEventStatus.Posted,
      processedAt = now,
      parser = candidate.parser,
      failureCode = null,
      setFailureCode = true,
      parsedSummary = "${candidate.direction.storageValue()}|${candidate.kind.storageValue()}|${candidate.amountCents}",
      transactionId = transactionId,
      setTransactionId = true,
      providerReference = candidate.providerReference,
      clearPayload = true,
    )
    PostedTransaction(transactionId, candidate.direction, candidate.amountCents, candidate.description, now)
  }

  /**
   * 撤销一笔自动入账的流水。
   *
   * 三个前提缺一不可：它确实来自通知、用户没有手动改过、`updatedAt` 与通知里那份一致。
   * 最后一条是给「通知里点了撤销，但用户刚在界面上编辑过这笔账」准备的——时间戳对不上
   * 就拒绝，宁可撤销失败，也不能删掉用户刚改过的数据。
   */
  suspend fun undoAutomatedTransaction(transactionId: String, expectedUpdatedAt: Long? = null): Boolean =
    database.withTransaction {
      val row = database.transactionDao().findById(transactionId) ?: return@withTransaction false
      if (row.entryOrigin == EntryOrigin.Manual || row.isUserEdited) return@withTransaction false
      if (expectedUpdatedAt != null && row.updatedAt != expectedUpdatedAt) return@withTransaction false

      database.transactionDao().deleteById(transactionId)
      // sourceEventId 一定非空：能走到这里的行都带通知来源。
      row.sourceEventId?.let { eventId ->
        database.automationEventDao().markUndone(
          id = eventId,
          status = AutomationEventStatus.Undone,
          processedAt = System.currentTimeMillis(),
        )
      }
      true
    }

  suspend fun listRecentEvents(limit: Int = 30): List<AutomationEventSummary> =
    database.automationEventDao().listRecentSummaries(limit)

  /**
   * 按保留期清理历史事件。
   *
   * 分两步：先丢掉超过 7 天的原始载荷（通知内容里最敏感的部分），再删掉超过 90 天的
   * 已结案事件。仍停在 `captured` 的事件不删——它还在等重试，删了就再补不回来。
   */
  suspend fun cleanup(now: Long = System.currentTimeMillis()) {
    val events = database.automationEventDao()
    database.withTransaction {
      events.clearPayloadsOlderThan(now - RAW_RETENTION_MILLIS)
      events.deleteOlderThan(now - AUDIT_RETENTION_MILLIS, keepStatus = AutomationEventStatus.Captured)
    }
  }

  private suspend fun updateEvent(
    id: String,
    status: AutomationEventStatus,
    parser: String? = null,
    failureCode: String? = null,
    clearPayload: Boolean = false,
  ) {
    database.automationEventDao().updateStatus(
      id = id,
      status = status,
      processedAt = System.currentTimeMillis(),
      parser = parser,
      failureCode = failureCode,
      // 只有显式给了 failureCode 才写它；没给就保留原值。
      setFailureCode = failureCode != null,
      parsedSummary = null,
      transactionId = null,
      setTransactionId = false,
      providerReference = null,
      clearPayload = clearPayload,
    )
  }

  private suspend fun findPaymentMethodId(sourcePackage: String): String? {
    val name = when (sourcePackage) {
      WECHAT_PACKAGE -> "微信"
      ALIPAY_PACKAGE -> "支付宝"
      else -> return null
    }
    return database.paymentMethodDao().findIdForAutomation(name)
  }

  private companion object {
    const val KEY_ENABLED = "automation_enabled"
    const val KEY_WECHAT = "automation_wechat_enabled"
    const val KEY_ALIPAY = "automation_alipay_enabled"
    const val KEY_RESULT_NOTIFICATIONS = "automation_result_notifications_enabled"
    const val KEY_PAYMENT_PAGE = "automation_payment_page_enabled"
    const val PAYMENT_MATCH_WINDOW_MILLIS = 90_000L
    const val RAW_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    const val AUDIT_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000
  }
}

internal data class StoredAutomationEvent(
  val id: String,
  val sourcePackage: String,
  val notificationKey: String,
  val postedAt: Long,
  val encrypted: EncryptedValue?,
  val status: AutomationEventStatus,
  val failureCode: String?,
  val transactionId: String?,
)

internal data class PostedTransaction(
  val id: String,
  val type: TransactionType,
  val amountCents: Long,
  val description: String,
  val updatedAt: Long,
)

private fun AutomationEventEntity.toStoredEvent() = StoredAutomationEvent(
  id = id,
  sourcePackage = sourcePackage,
  notificationKey = notificationKey,
  postedAt = postedAt,
  encrypted = if (encryptedPayload == null || payloadIv == null) {
    null
  } else {
    EncryptedValue(encryptedPayload, payloadIv)
  },
  status = status,
  failureCode = failureCode,
  transactionId = transactionId,
)

private fun AutomationKind.storageValue(): String = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

private fun String?.toBooleanSetting(default: Boolean = false) = this?.toBooleanStrictOrNull() ?: default
private fun Boolean.asSetting() = toString()
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
  .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
