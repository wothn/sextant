package com.sextant.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.sextant.app.automation.ALIPAY_PACKAGE
import com.sextant.app.automation.CapturedNotification
import com.sextant.app.automation.EncryptedValue
import com.sextant.app.automation.ParsedTransactionCandidate
import com.sextant.app.automation.WECHAT_PACKAGE
import java.security.MessageDigest
import java.util.UUID

internal class AutomationStore(private val database: SextantDatabase) {
  fun loadSettings(): AutomationSettings {
    val values = mutableMapOf<String, String>()
    database.readableDatabase.rawQuery(
      "SELECT key, value FROM app_settings WHERE key LIKE 'automation_%'",
      emptyArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) values[cursor.getString(0)] = cursor.getString(1)
    }
    return AutomationSettings(
      enabled = values[KEY_ENABLED].toBooleanSetting(),
      wechatEnabled = values[KEY_WECHAT].toBooleanSetting(default = true),
      alipayEnabled = values[KEY_ALIPAY].toBooleanSetting(default = true),
      resultNotificationsEnabled = values[KEY_RESULT_NOTIFICATIONS].toBooleanSetting(default = true),
    )
  }

  fun updateSettings(settings: AutomationSettings) {
    val values = mapOf(
      KEY_ENABLED to settings.enabled.asSetting(),
      KEY_WECHAT to settings.wechatEnabled.asSetting(),
      KEY_ALIPAY to settings.alipayEnabled.asSetting(),
      KEY_RESULT_NOTIFICATIONS to settings.resultNotificationsEnabled.asSetting(),
    )
    val db = database.writableDatabase
    db.beginTransaction()
    try {
      values.forEach { (key, value) ->
        db.execSQL("INSERT OR REPLACE INTO app_settings (key, value) VALUES (?, ?)", arrayOf(key, value))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun capture(notification: CapturedNotification, encrypted: EncryptedValue): String? {
    val id = UUID.randomUUID().toString()
    val eventKey = sha256("${notification.sourcePackage}|${notification.notificationKey}|${notification.postedAt}")
    val payloadHash = sha256(notification.normalizedText())
    val values = ContentValues().apply {
      put("id", id)
      put("eventKey", eventKey)
      put("sourcePackage", notification.sourcePackage)
      put("notificationKey", notification.notificationKey)
      put("postedAt", notification.postedAt)
      put("payloadHash", payloadHash)
      put("encryptedPayload", encrypted.ciphertext)
      put("payloadIv", encrypted.iv)
      put("status", AutomationEventStatus.Captured.dbValue)
      put("createdAt", System.currentTimeMillis())
    }
    val result = database.writableDatabase.insertWithOnConflict(
      "automation_events",
      null,
      values,
      SQLiteDatabase.CONFLICT_IGNORE,
    )
    return if (result == -1L) null else id
  }

  fun getEvent(id: String): StoredAutomationEvent? = database.readableDatabase.rawQuery(
    """
    SELECT id, sourcePackage, notificationKey, postedAt, encryptedPayload, payloadIv, status
    FROM automation_events WHERE id = ?
    """.trimIndent(),
    arrayOf(id),
  ).use { cursor ->
    if (!cursor.moveToFirst()) return@use null
    StoredAutomationEvent(
      id = cursor.getString(0),
      sourcePackage = cursor.getString(1),
      notificationKey = cursor.getString(2),
      postedAt = cursor.getLong(3),
      encrypted = if (cursor.isNull(4) || cursor.isNull(5)) null else EncryptedValue(cursor.getBlob(4), cursor.getBlob(5)),
      status = cursor.getString(6).toAutomationEventStatus(),
    )
  }

  fun markFailed(id: String, failureCode: String, clearPayload: Boolean = false) {
    updateEvent(id, AutomationEventStatus.Failed, failureCode = failureCode, clearPayload = clearPayload)
  }

  fun markCapturedForRetry(id: String): Boolean {
    val values = ContentValues().apply {
      put("status", AutomationEventStatus.Captured.dbValue)
      putNull("failureCode")
      putNull("processedAt")
    }
    return database.writableDatabase.update(
      "automation_events",
      values,
      "id = ? AND status = ? AND encryptedPayload IS NOT NULL",
      arrayOf(id, AutomationEventStatus.Failed.dbValue),
    ) == 1
  }

  fun postCandidate(eventId: String, sourcePackage: String, candidate: ParsedTransactionCandidate): PostedTransaction? {
    val db = database.writableDatabase
    val now = System.currentTimeMillis()
    db.beginTransaction()
    try {
      if (candidate.providerReference != null && providerReferenceExists(db, sourcePackage, candidate.providerReference, eventId)) {
        updateEventInTransaction(db, eventId, AutomationEventStatus.Duplicate, candidate.parser, "provider_reference_duplicate", null, null, true)
        db.setTransactionSuccessful()
        return null
      }
      if (eventAlreadyPosted(db, eventId)) {
        db.setTransactionSuccessful()
        return null
      }

      val paymentMethodId = findPaymentMethodId(db, sourcePackage)
      val categoryId = findCategoryId(db, sourcePackage, candidate)
      val transactionId = UUID.randomUUID().toString()
      db.execSQL(
        """
        INSERT INTO transactions
          (id, categoryId, paymentMethodId, amountCents, type, description, transactionDate,
           createdAt, updatedAt, entryOrigin, automationKind, sourceEventId, isUserEdited)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'notification', ?, ?, 0)
        """.trimIndent(),
        arrayOf<Any?>(
          transactionId,
          categoryId,
          paymentMethodId,
          candidate.amountCents,
          candidate.direction.dbValue,
          candidate.description.trim().take(120),
          candidate.occurredAt,
          now,
          now,
          candidate.kind.dbValue,
          eventId,
        ),
      )
      val categoryName = categoryId?.let { findCategoryName(db, it) } ?: "未分类"
      val parsedSummary = "${candidate.direction.dbValue}|${candidate.kind.dbValue}|${candidate.amountCents}|$categoryName"
      updateEventInTransaction(
        db,
        eventId,
        AutomationEventStatus.Posted,
        candidate.parser,
        null,
        parsedSummary,
        transactionId,
        true,
        candidate.providerReference,
      )
      db.setTransactionSuccessful()
      return PostedTransaction(transactionId, candidate.direction, candidate.amountCents, candidate.description, categoryName, now)
    } finally {
      db.endTransaction()
    }
  }

  fun undoAutomatedTransaction(transactionId: String, expectedUpdatedAt: Long? = null): Boolean {
    val db = database.writableDatabase
    db.beginTransaction()
    try {
      val row = db.rawQuery(
        "SELECT sourceEventId, updatedAt, isUserEdited, entryOrigin FROM transactions WHERE id = ?",
        arrayOf(transactionId),
      ).use { cursor ->
        if (!cursor.moveToFirst()) null else UndoRow(
          cursor.getString(0), cursor.getLong(1), cursor.getInt(2) == 1, cursor.getString(3),
        )
      } ?: return false
      if (row.origin != "notification" || row.isUserEdited || (expectedUpdatedAt != null && row.updatedAt != expectedUpdatedAt)) return false
      db.delete("transactions", "id = ?", arrayOf(transactionId))
      db.execSQL(
        "UPDATE automation_events SET status = ?, transactionId = NULL, processedAt = ? WHERE id = ?",
        arrayOf<Any>(AutomationEventStatus.Undone.dbValue, System.currentTimeMillis(), row.eventId),
      )
      db.setTransactionSuccessful()
      return true
    } finally {
      db.endTransaction()
    }
  }

  fun saveRule(
    sourcePackage: String,
    direction: TransactionType,
    kind: AutomationKind,
    pattern: String,
    categoryId: String,
  ) {
    val normalized = normalizePattern(pattern)
    require(normalized.isNotBlank()) { "分类规则不能为空" }
    val now = System.currentTimeMillis()
    database.writableDatabase.execSQL(
      """
      INSERT INTO classification_rules
        (id, sourcePackage, direction, automationKind, matchMode, pattern, categoryId, priority, createdAt, updatedAt)
      VALUES (?, ?, ?, ?, 'contains', ?, ?, 100, ?, ?)
      """.trimIndent(),
      arrayOf<Any>(UUID.randomUUID().toString(), sourcePackage, direction.dbValue, kind.dbValue, normalized, categoryId, now, now),
    )
  }

  fun listRecentEvents(limit: Int = 30): List<AutomationEventSummary> {
    val rows = mutableListOf<AutomationEventSummary>()
    database.readableDatabase.rawQuery(
      """
      SELECT id, sourcePackage, postedAt, status, parser, failureCode, parsedSummary, transactionId
      FROM automation_events ORDER BY createdAt DESC LIMIT ?
      """.trimIndent(),
      arrayOf(limit.toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        rows += AutomationEventSummary(
          cursor.getString(0),
          cursor.getString(1),
          cursor.getLong(2),
          cursor.getString(3).toAutomationEventStatus(),
          cursor.stringOrNull(4),
          cursor.stringOrNull(5),
          cursor.stringOrNull(6),
          cursor.stringOrNull(7),
        )
      }
    }
    return rows
  }

  fun cleanup(now: Long = System.currentTimeMillis()) {
    val db = database.writableDatabase
    db.execSQL(
      "UPDATE automation_events SET encryptedPayload = NULL, payloadIv = NULL WHERE createdAt < ?",
      arrayOf(now - RAW_RETENTION_MILLIS),
    )
    db.delete("automation_events", "createdAt < ? AND status != ?", arrayOf((now - AUDIT_RETENTION_MILLIS).toString(), AutomationEventStatus.Captured.dbValue))
  }

  private fun updateEvent(
    id: String,
    status: AutomationEventStatus,
    parser: String? = null,
    failureCode: String? = null,
    clearPayload: Boolean = false,
  ) {
    val db = database.writableDatabase
    updateEventInTransaction(db, id, status, parser, failureCode, null, null, clearPayload)
  }

  private fun updateEventInTransaction(
    db: SQLiteDatabase,
    id: String,
    status: AutomationEventStatus,
    parser: String?,
    failureCode: String?,
    parsedSummary: String?,
    transactionId: String?,
    clearPayload: Boolean,
    providerReference: String? = null,
  ) {
    val values = ContentValues().apply {
      put("status", status.dbValue)
      put("processedAt", System.currentTimeMillis())
      if (parser != null) put("parser", parser)
      if (failureCode == null) putNull("failureCode") else put("failureCode", failureCode)
      if (parsedSummary != null) put("parsedSummary", parsedSummary)
      if (transactionId != null) put("transactionId", transactionId)
      if (providerReference != null) put("providerReference", providerReference)
      if (clearPayload) {
        putNull("encryptedPayload")
        putNull("payloadIv")
      }
    }
    db.update("automation_events", values, "id = ?", arrayOf(id))
  }

  private fun eventAlreadyPosted(db: SQLiteDatabase, eventId: String): Boolean = db.rawQuery(
    "SELECT 1 FROM transactions WHERE sourceEventId = ? LIMIT 1",
    arrayOf(eventId),
  ).use { it.moveToFirst() }

  private fun providerReferenceExists(db: SQLiteDatabase, sourcePackage: String, reference: String, eventId: String): Boolean = db.rawQuery(
    "SELECT 1 FROM automation_events WHERE sourcePackage = ? AND providerReference = ? AND id != ? LIMIT 1",
    arrayOf(sourcePackage, reference, eventId),
  ).use { it.moveToFirst() }

  private fun findPaymentMethodId(db: SQLiteDatabase, sourcePackage: String): String? {
    val name = when (sourcePackage) {
      WECHAT_PACKAGE -> "微信"
      ALIPAY_PACKAGE -> "支付宝"
      else -> return null
    }
    return db.rawQuery(
      "SELECT id FROM payment_methods WHERE name = ? ORDER BY isActive DESC, createdAt ASC LIMIT 1",
      arrayOf(name),
    ).use { if (it.moveToFirst()) it.getString(0) else null }
  }

  private fun findCategoryId(db: SQLiteDatabase, sourcePackage: String, candidate: ParsedTransactionCandidate): String? {
    val matchText = normalizePattern(listOfNotNull(candidate.merchant, candidate.counterparty, candidate.description).joinToString(" "))
    db.rawQuery(
      """
      SELECT r.categoryId, r.pattern, r.matchMode
      FROM classification_rules r
      JOIN categories c ON c.id = r.categoryId AND c.isActive = 1
      WHERE r.sourcePackage = ? AND r.direction = ? AND r.automationKind = ? AND c.type = ?
      ORDER BY r.priority DESC, r.updatedAt DESC
      """.trimIndent(),
      arrayOf(sourcePackage, candidate.direction.dbValue, candidate.kind.dbValue, candidate.direction.dbValue),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val pattern = cursor.getString(1)
        val matches = if (cursor.getString(2) == "exact") matchText == pattern else matchText.contains(pattern)
        if (matches) return cursor.getString(0)
      }
    }
    val hint = candidate.categoryHint?.trim()?.takeIf(String::isNotBlank) ?: return null
    return db.rawQuery(
      "SELECT id FROM categories WHERE name = ? AND type = ? AND isActive = 1 ORDER BY parentCategoryId IS NULL ASC LIMIT 1",
      arrayOf(hint, candidate.direction.dbValue),
    ).use { if (it.moveToFirst()) it.getString(0) else null }
  }

  private fun findCategoryName(db: SQLiteDatabase, categoryId: String): String? = db.rawQuery(
    "SELECT name FROM categories WHERE id = ?",
    arrayOf(categoryId),
  ).use { if (it.moveToFirst()) it.getString(0) else null }

  private data class UndoRow(val eventId: String, val updatedAt: Long, val isUserEdited: Boolean, val origin: String)

  private companion object {
    const val KEY_ENABLED = "automation_enabled"
    const val KEY_WECHAT = "automation_wechat_enabled"
    const val KEY_ALIPAY = "automation_alipay_enabled"
    const val KEY_RESULT_NOTIFICATIONS = "automation_result_notifications_enabled"
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
)

internal data class PostedTransaction(
  val id: String,
  val type: TransactionType,
  val amountCents: Long,
  val description: String,
  val categoryName: String,
  val updatedAt: Long,
)

private val TransactionType.dbValue: String
  get() = when (this) {
    TransactionType.Expense -> "expense"
    TransactionType.Income -> "income"
    TransactionType.Transfer -> "transfer"
  }

private val AutomationKind.dbValue: String get() = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
private val AutomationEventStatus.dbValue: String get() = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
private fun String.toAutomationEventStatus(): AutomationEventStatus = AutomationEventStatus.entries.firstOrNull {
  it.dbValue == this
} ?: AutomationEventStatus.Failed
private fun String?.toBooleanSetting(default: Boolean = false) = this?.toBooleanStrictOrNull() ?: default
private fun Boolean.asSetting() = toString()
private fun normalizePattern(value: String) = value.lowercase().replace(Regex("\\s+"), "").trim()
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
  .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
private fun android.database.Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
