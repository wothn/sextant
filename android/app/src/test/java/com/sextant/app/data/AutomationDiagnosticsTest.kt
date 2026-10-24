package com.sextant.app.data

import android.content.Context
import androidx.room.withTransaction
import com.sextant.app.automation.EncryptedValue
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationDiagnosticsTest {
  private lateinit var context: Context
  private lateinit var database: SextantDatabase
  private lateinit var diagnostics: AutomationDiagnostics
  private var now = 1_000_000_000L
  private val key = SecretKeySpec(ByteArray(16) { it.toByte() }, "AES")

  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    database = SextantDatabase.createForTests(context)
    diagnostics = newDiagnostics()
  }

  @After fun tearDown() {
    database.close()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }

  private fun newDiagnostics() = AutomationDiagnostics(database, ::encrypt, ::decrypt) { now }
  private fun encrypt(bytes: ByteArray): EncryptedValue {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return EncryptedValue(cipher.doFinal(bytes), cipher.iv)
  }
  private fun decrypt(value: EncryptedValue): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, value.iv))
    return cipher.doFinal(value.ciphertext)
  }
  private suspend fun settings(enabled: Boolean, content: Boolean = false) =
    AutomationStore(database).updateSettings(AutomationSettings(diagnosticsEnabled = enabled, diagnosticContentEnabled = content))
  private suspend fun report() = Json.decodeFromString<DiagnosticReport>(diagnostics.export(mapOf("version" to "test")))

  @Test fun defaultsOffAndDoesNotEvaluateContent() = runBlocking {
    assertFalse(AutomationStore(database).loadSettings().diagnosticsEnabled)
    diagnostics.record("page", "parse", "merchant_missing", content = { error("Must stay unread") })
    assertTrue(report().entries.isEmpty())
  }

  @Test fun initializedDisabledRecorderDoesNotAccessClosedDatabase() = runBlocking {
    diagnostics.initialize()
    database.close()
    repeat(3) {
      diagnostics.record("notification", "receive", "not_financial", content = { error("Disabled") })
    }
    assertNull(diagnostics.lastError.value)
  }

  @Test fun revokingContentPermissionAppliesToExistingRecorderImmediately() = runBlocking {
    settings(true, content = true)
    diagnostics.record("notification", "post", "posted", content = { listOf("secret") })
    settings(true, content = false)
    diagnostics.record("notification", "post", "posted", content = { error("Permission revoked") })
    val entries = report().entries
    assertEquals(listOf("secret"), entries.first().content)
    assertNull(entries.last().content)
  }

  @Test fun restoredBackupDisablesTheCachedRecorder() = runBlocking {
    val backup = BackupStore(database).export()
    settings(true, content = true)
    diagnostics.record("notification", "post", "posted", content = { listOf("secret") })
    BackupStore(database).restore(backup)
    diagnostics.record("notification", "post", "posted", content = { error("Restore disables recording") })
    assertTrue(report().entries.isEmpty())
  }

  @Test fun reopenedRecorderLoadsPersistedPermissionBeforeFirstWrite() = runBlocking {
    settings(true)
    database.close()
    database = SextantDatabase.createForTests(context)
    diagnostics = newDiagnostics()
    diagnostics.record("notification", "post", "posted", content = { error("Metadata only") })
    assertEquals("posted", report().entries.single().reason)
  }

  @Test fun metadataModeDoesNotRetainContentAndPersistsAcrossReopen() = runBlocking {
    settings(true)
    diagnostics.record("page", "parse", "merchant_missing", traceId = "scan-1",
      details = mapOf("textCount" to "5"), content = { error("Content permission is off") })
    database.close()
    database = SextantDatabase.createForTests(context)
    diagnostics = newDiagnostics()
    val entry = report().entries.single()
    assertNull(entry.content)
    assertEquals("merchant_missing", entry.reason)
    assertEquals("scan-1", entry.traceId)
    assertEquals("5", entry.details["textCount"])
  }

  @Test fun optInContentIsBoundedEncryptedAndCanBeCleared() = runBlocking {
    settings(true, content = true)
    val secret = "支付成功，商户秘密名称，交易单号123456789"
    diagnostics.record("page", "parse", "parsed", content = { List(300) { secret.repeat(50) } })
    val entry = report().entries.single()
    assertTrue(entry.content!!.size <= 200)
    assertTrue(entry.content.sumOf { it.length } <= 8_000)
    assertTrue(entry.content.first().contains(secret))
    val row = database.automationDiagnosticDao().listAll().single()
    assertFalse(row.ciphertext.toString(Charsets.UTF_8).contains(secret))
    diagnostics.clear()
    assertTrue(report().entries.isEmpty())
  }

  @Test fun stoppingRecordingKeepsOldLogsButDoesNotWriteNewOnes() = runBlocking {
    settings(true, content = true)
    diagnostics.record("notification", "capture", "captured")
    settings(false, content = true)
    assertFalse(AutomationStore(database).loadSettings().diagnosticContentEnabled)
    diagnostics.record("notification", "capture", "captured", content = { error("Disabled") })
    assertEquals(1, report().entries.size)
  }

  @Test fun retentionAndCountLimitAreAppliedOnWriteAndExport() = runBlocking {
    settings(true)
    diagnostics.record("page", "read", "old")
    now += AutomationDiagnostics.RETENTION_MILLIS + 1
    assertTrue(report().entries.isEmpty())
    diagnostics.record("page", "read", "seed")
    val row = database.automationDiagnosticDao().listAll().single()
    database.withTransaction {
      repeat(AutomationDiagnostics.MAX_ENTRIES + 3) {
        database.automationDiagnosticDao().insert(row.copy(id = 0))
      }
    }
    diagnostics.record("page", "read", "newest")
    assertEquals(AutomationDiagnostics.MAX_ENTRIES, report().entries.size)
    assertEquals("newest", report().entries.last().reason)
  }

  @Test fun concurrentRecordingDoesNotLoseEntries() = runBlocking {
    settings(true)
    (1..20).map { index -> async {
      diagnostics.record("notification", "capture", "captured", traceId = "event-$index")
    } }.awaitAll()
    assertEquals(20, report().entries.map { it.traceId }.distinct().size)
  }

  @Test fun loggingFailuresAreReportedWithoutBreakingCaptureAndCancellationPropagates() = runBlocking {
    settings(true)
    val broken = AutomationDiagnostics(database, { throw IllegalStateException("keystore unavailable") }, ::decrypt) { now }
    broken.record("notification", "capture", "captured")
    assertEquals("诊断日志写入失败，请检查设备存储空间后重试。", broken.lastError.value)
    assertTrue(database.automationDiagnosticDao().listAll().isEmpty())
    val cancelled = AutomationDiagnostics(database, { throw CancellationException("cancelled") }, ::decrypt) { now }
    try {
      cancelled.record("page", "read", "parsed")
      fail("Cancellation must propagate")
    } catch (_: CancellationException) { }
    assertTrue(database.automationDiagnosticDao().listAll().isEmpty())
  }

  @Test fun unreadableLogsAreCountedAndOmittedFromExport() = runBlocking {
    settings(true)
    diagnostics.record("page", "parse", "parsed")
    database.automationDiagnosticDao().insert(AutomationDiagnosticEntity(createdAt = now,
      ciphertext = byteArrayOf(1, 2), iv = ByteArray(12)))
    assertEquals(1, report().entries.size)
    assertEquals(1, report().unreadableEntries)
  }

  @Test fun diagnosticSettingsAndRecordsAreExcludedFromLedgerBackup() = runBlocking {
    settings(true, content = true)
    diagnostics.record("page", "parse", "parsed", content = { listOf("PRIVATE-PAYMENT") })
    val backup = BackupStore(database).export()
    assertFalse(backup.contains("PRIVATE-PAYMENT"))
    assertFalse(backup.contains(AutomationDiagnostics.ENABLED_KEY))
    BackupStore(database).restore(backup)
    assertFalse(AutomationStore(database).loadSettings().diagnosticsEnabled)
    assertTrue(report().entries.isEmpty())
  }
}
