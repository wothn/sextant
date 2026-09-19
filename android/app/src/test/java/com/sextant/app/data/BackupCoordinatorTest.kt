package com.sextant.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 备份协调者的行为。
 *
 * 这里不重复校验备份文件的内容（那是 [BackupCodecTest] 与 [SextantRepositoryTest] 的事），
 * 只看它自己那条约定：工作挂在传进来的作用域上，所以「没人在看」不会让备份半途而废，
 * 界面重建后重新订阅还能读到进行状态和最终结果。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupCoordinatorTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase(DATABASE_NAME)
  }

  @After
  fun tearDown() {
    SextantDatabase.closeForTests()
    context.deleteDatabase(DATABASE_NAME)
  }

  @Test
  fun exportFinishesOnTheInjectedScopeAndKeepsItsOutcomeForARecreatedScreen() = runBlocking {
    val repository = SextantRepository(context)
    repository.addTransaction(TransactionType.Expense, 1_200, null, null, "咖啡", System.currentTimeMillis())
    val coordinator = BackupCoordinator(context, repository, CoroutineScope(Dispatchers.Unconfined))
    val target = backupFile("export.json")

    coordinator.export(Uri.fromFile(target))
    val finished = coordinator.state.first { !it.isBusy && it.outcome != null }

    assertEquals(BackupOutcome.ExportSucceeded, finished.outcome)
    assertTrue(target.readText().contains("\"format\": \"sextant-backup\""))
    assertTrue(target.readText().contains("咖啡"))
    // 界面重建等于换一个订阅者：结果必须还在，否则用户旋转屏幕后就再也看不到这次导出的结局。
    assertEquals(BackupOutcome.ExportSucceeded, coordinator.state.value.outcome)
  }

  @Test
  fun restoreOfAnUnreadableFileReportsRestoreFailureAndLeavesTheLedgerAlone() = runBlocking {
    val repository = SextantRepository(context)
    repository.addTransaction(TransactionType.Expense, 1_200, null, null, "咖啡", System.currentTimeMillis())
    val coordinator = BackupCoordinator(context, repository, CoroutineScope(Dispatchers.Unconfined))
    val broken = backupFile("broken.json").apply { writeText("这不是备份") }

    coordinator.restore(Uri.fromFile(broken))
    val finished = coordinator.state.first { !it.isBusy && it.outcome != null }

    val failure = finished.outcome as BackupOutcome.RestoreFailed
    assertTrue(failure.cause is BackupException)
    // 校验失败发生在事务之前：原账本必须原封不动。
    assertEquals(1, repository.loadState().transactions.size)
  }

  @Test
  fun restoreReportsTheSummaryOfTheRestoredBackup() = runBlocking {
    val repository = SextantRepository(context)
    repository.addTransaction(TransactionType.Expense, 1_200, null, null, "咖啡", System.currentTimeMillis())
    val coordinator = BackupCoordinator(context, repository, CoroutineScope(Dispatchers.Unconfined))
    val source = backupFile("source.json")

    coordinator.export(Uri.fromFile(source))
    coordinator.state.first { !it.isBusy && it.outcome != null }
    coordinator.dismissOutcome()

    coordinator.restore(Uri.fromFile(source))
    val finished = coordinator.state.first { !it.isBusy && it.outcome != null }

    val restored = finished.outcome as BackupOutcome.RestoreSucceeded
    assertEquals(1, restored.summary.transactionCount)
    assertNotNull(repository.loadState().transactions.firstOrNull())
  }

  private fun backupFile(name: String): File =
    File(context.cacheDir, name).apply { parentFile?.mkdirs(); delete() }

  private companion object {
    const val DATABASE_NAME = "sextant.db"
  }
}
