package com.sextant.app.data

import androidx.room.withTransaction
import com.sextant.app.automation.EncryptedValue
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class DiagnosticEntry(
  val at: Long,
  val channel: String,
  val stage: String,
  val reason: String,
  val sourcePackage: String? = null,
  val traceId: String? = null,
  val details: Map<String, String> = emptyMap(),
  val content: List<String>? = null,
)

@Serializable
internal data class DiagnosticReport(
  val formatVersion: Int = 1,
  val exportedAt: Long,
  val environment: Map<String, String>,
  val unreadableEntries: Int,
  val entries: List<DiagnosticEntry>,
)

// Shared by stores on the same database; disable/restore waits for in-flight recording.
internal class DiagnosticPolicy {
  val mutex = Mutex()
  @Volatile var settings: AutomationSettings? = null
}

/** Best-effort, local diagnostic recording must never interrupt capture or bookkeeping. */
internal class AutomationDiagnostics(
  private val database: SextantDatabase,
  private val encrypt: (ByteArray) -> EncryptedValue,
  private val decrypt: (EncryptedValue) -> ByteArray,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }
  private val error = MutableStateFlow<String?>(null)
  val lastError = error.asStateFlow()
  val count = database.automationDiagnosticDao().observeCount()
  val recent = database.automationDiagnosticDao().observeRecent().map { rows ->
    rows.map { row ->
      val entry = decode(row)
      AutomationDiagnosticSummary(row.createdAt, entry?.channel ?: "system", entry?.reason ?: "log_unreadable", entry?.stage.orEmpty())
    }
  }.flowOn(Dispatchers.Default)

  // Load once on first use; avoid opening Room merely to start the application.
  suspend fun initialize() {
    val policy = database.diagnosticPolicy
    if (policy.settings != null) return
    policy.mutex.withLock {
      if (policy.settings == null) policy.settings = AutomationStore(database).loadSettings()
    }
  }

  suspend fun isEnabled(): Boolean = try {
    initialize()
    database.diagnosticPolicy.settings?.diagnosticsEnabled == true
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    error.value = "诊断日志写入失败，请检查设备存储空间后重试。"
    false
  }

  suspend fun record(
    channel: String,
    stage: String,
    reason: String,
    sourcePackage: String? = null,
    traceId: String? = null,
    details: Map<String, String> = emptyMap(),
    content: () -> List<String> = { emptyList() },
  ) {
    try {
      if (!isEnabled()) return
      withContext(Dispatchers.Default) {
        database.diagnosticPolicy.mutex.withLock {
          val settings = database.diagnosticPolicy.settings
          if (settings?.diagnosticsEnabled != true) return@withLock
          database.withTransaction {
            val timestamp = now()
            val snapshot = if (settings.diagnosticContentEnabled) boundedContent(content()) else null
            val entry = DiagnosticEntry(timestamp, channel, stage, reason, sourcePackage, traceId,
              details.entries.take(32).associate { it.key.take(80) to it.value.take(256) }, snapshot)
            val encrypted = encrypt(json.encodeToString(entry).toByteArray(Charsets.UTF_8))
            database.automationDiagnosticDao().insert(AutomationDiagnosticEntity(
              createdAt = timestamp, ciphertext = encrypted.ciphertext, iv = encrypted.iv,
            ))
            database.automationDiagnosticDao().prune(timestamp - RETENTION_MILLIS, MAX_ENTRIES)
            error.value = null
          }
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      error.value = "诊断日志写入失败，请检查设备存储空间后重试。"
    }
  }

  suspend fun cleanup() = database.withTransaction {
    database.automationDiagnosticDao().prune(now() - RETENTION_MILLIS, MAX_ENTRIES)
  }

  suspend fun clear() {
    database.automationDiagnosticDao().deleteAll()
    error.value = null
  }

  suspend fun export(environment: Map<String, String>): String = withContext(Dispatchers.Default) {
    val rows = database.withTransaction {
      database.automationDiagnosticDao().prune(now() - RETENTION_MILLIS, MAX_ENTRIES)
      database.automationDiagnosticDao().listAll()
    }
    val entries = rows.mapNotNull(::decode)
    json.encodeToString(DiagnosticReport(exportedAt = now(), environment = environment,
      unreadableEntries = rows.size - entries.size, entries = entries))
  }

  private fun decode(row: AutomationDiagnosticEntity): DiagnosticEntry? = try {
    json.decodeFromString<DiagnosticEntry>(decrypt(EncryptedValue(row.ciphertext, row.iv)).toString(Charsets.UTF_8))
  } catch (cancelled: CancellationException) { throw cancelled }
  catch (_: Exception) { null }

  companion object {
    const val ENABLED_KEY = "automation_diagnostics_enabled"
    const val CONTENT_KEY = "automation_diagnostic_content_enabled"
    const val MAX_ENTRIES = 5_000
    const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

    // Process-local correlation only. Never persist/export the key, including for low-entropy inputs.
    private val correlationKey = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "HmacSHA256")
    fun fingerprint(value: String): String = Mac.getInstance("HmacSHA256").run {
      init(correlationKey)
      doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun boundedContent(lines: List<String>): List<String> {
      var remaining = 8_000
      return lines.asSequence().take(200).map { line ->
        line.take(minOf(remaining, 500)).also { remaining -= it.length }
      }.filter { it.isNotEmpty() }.toList()
    }
  }
}
