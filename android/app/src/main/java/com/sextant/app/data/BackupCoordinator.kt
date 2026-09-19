package com.sextant.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 备份操作的结果。
 *
 * 成功与失败各自带上「是哪一次操作」：界面上的标题是「导出失败」还是「恢复失败」由它决定，
 * 而用户看到的正文来自 [BackupException]（校验失败的具体原因）或统一的读写失败文案。
 */
sealed interface BackupOutcome {
  data object ExportSucceeded : BackupOutcome
  data class ExportFailed(val cause: Throwable) : BackupOutcome
  data class RestoreSucceeded(val summary: BackupSummary) : BackupOutcome
  data class RestoreFailed(val cause: Throwable) : BackupOutcome
}

data class BackupOperationState(
  val isBusy: Boolean = false,
  val outcome: BackupOutcome? = null,
)

/**
 * 备份导出与恢复的协调者。
 *
 * 这里的工作挂在应用级 [scope] 上，而不是页面的组合作用域：旋转屏幕、分屏改变窗口大小、
 * 切换深浅色都会重建 Activity 并销毁组合，挂在组合上的协程会在写文件或替换账本的中途被
 * 取消——用户拿到的可能是一份没写完就没人负责的备份文件，或者一个已经关掉的界面里弹出的
 * 「失败」。
 *
 * 进行状态和结果用 [StateFlow] 暴露：界面重建后重新订阅就能看到「还在进行」和最终提示，
 * 不需要把这些中间态塞进 saved instance state，也就不会出现「旋转后按钮又能点、于是同一份
 * 备份被导出两次」。
 */
class BackupCoordinator(
  context: Context,
  private val repository: SextantRepository,
  private val scope: CoroutineScope,
) {
  private val appContext = context.applicationContext
  private val _state = MutableStateFlow(BackupOperationState())

  val state: StateFlow<BackupOperationState> = _state.asStateFlow()

  /** 导出完整账本到用户选定的文件。重复调用会被忽略：同一时刻只允许一次备份操作。 */
  fun export(uri: Uri) {
    start(onFailure = BackupOutcome::ExportFailed) {
      val content = repository.exportBackup()
      // 只有读写文件这一小段要显式切到 IO：导出本身由 Room 在自己的线程上完成。
      withContext(Dispatchers.IO) { appContext.writeText(uri, content) }
      BackupOutcome.ExportSucceeded
    }
  }

  /** 用用户选定的备份文件覆盖当前账本。重复调用会被忽略。 */
  fun restore(uri: Uri) {
    start(onFailure = BackupOutcome::RestoreFailed) {
      val content = withContext(Dispatchers.IO) { appContext.readBackupText(uri) }
      BackupOutcome.RestoreSucceeded(repository.restoreBackup(content))
    }
  }

  /** 用户看过结果提示后调用。 */
  fun dismissOutcome() {
    _state.update { it.copy(outcome = null) }
  }

  private fun start(onFailure: (Throwable) -> BackupOutcome, work: suspend () -> BackupOutcome) {
    if (_state.value.isBusy) return
    _state.value = BackupOperationState(isBusy = true)
    scope.launch {
      val outcome = try {
        work()
      } catch (cancelled: CancellationException) {
        // 应用级作用域只会在进程退出时被取消。取消不是业务失败，不该被翻译成一条提示。
        _state.value = BackupOperationState()
        throw cancelled
      } catch (error: Throwable) {
        onFailure(error)
      }
      _state.value = BackupOperationState(isBusy = false, outcome = outcome)
    }
  }
}

private fun Context.writeText(uri: Uri, content: String) {
  val stream = contentResolver.openOutputStream(uri, "wt")
    ?: throw IllegalStateException("无法打开目标文件")
  stream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(content) }
}

private fun Context.readBackupText(uri: Uri): String {
  val stream = contentResolver.openInputStream(uri)
    ?: throw IllegalStateException("无法打开备份文件")
  return stream.bufferedReader(Charsets.UTF_8).use { reader ->
    val output = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
      val count = reader.read(buffer)
      if (count < 0) break
      output.append(buffer, 0, count)
      if (output.length > MAX_BACKUP_CHARACTERS) throw BackupException("备份文件过大")
    }
    output.toString()
  }
}

private const val MAX_BACKUP_CHARACTERS = 25 * 1024 * 1024
