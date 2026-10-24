package com.sextant.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
class DiagnosticExportCoordinatorTest {
  private lateinit var context: Context
  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }
  @After fun tearDown() {
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }

  @Test fun exportWritesUsableJsonAndKeepsCompletionForRecreatedScreen() = runBlocking {
    val repository = SextantRepository(context)
    val exporter = DiagnosticExportCoordinator(context, repository, CoroutineScope(Dispatchers.Unconfined))
    val target = File(context.cacheDir, "diagnostic-export.json")
    exporter.export(Uri.fromFile(target))
    val finished = exporter.state.first { !it.busy && it.message != null }
    assertEquals("诊断日志已导出。", finished.message)
    val report = Json.decodeFromString<DiagnosticReport>(target.readText())
    assertEquals(SextantDatabase.DATABASE_VERSION.toString(), report.environment["databaseVersion"])
    assertEquals("false", report.environment["diagnosticsEnabled"])
    assertTrue(report.entries.isEmpty())
    assertEquals(finished, exporter.state.value)
    exporter.dismissMessage()
    assertNull(exporter.state.value.message)
  }

  @Test fun invalidExportDestinationReportsFailureAndKeepsLedgerIntact() = runBlocking {
    val repository = SextantRepository(context)
    repository.addTransaction(TransactionType.Expense, 1200, null, null, "咖啡", 1000)
    val exporter = DiagnosticExportCoordinator(context, repository, CoroutineScope(Dispatchers.Unconfined))
    exporter.export(Uri.fromFile(context.cacheDir))
    assertEquals("日志导出失败，请重新选择保存位置。", exporter.state.first { !it.busy && it.message != null }.message)
    assertEquals(1, repository.loadState().transactions.size)
  }
}
