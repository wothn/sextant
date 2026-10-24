package com.sextant.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticExportState(val busy: Boolean = false, val message: String? = null)

/** Finish an explicitly requested export even when the settings screen is recreated. */
class DiagnosticExportCoordinator(
  context: Context,
  private val repository: SextantRepository,
  private val scope: CoroutineScope,
) {
  private val appContext = context.applicationContext
  private val mutableState = MutableStateFlow(DiagnosticExportState())
  val state = mutableState.asStateFlow()

  fun export(uri: Uri) {
    val current = mutableState.value
    if (current.busy || !mutableState.compareAndSet(current, DiagnosticExportState(busy = true))) return
    scope.launch {
      try {
        val report = repository.exportAutomationDiagnostics()
        withContext(Dispatchers.IO) {
          val stream = appContext.contentResolver.openOutputStream(uri, "wt") ?: error("output unavailable")
          stream.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
        }
        mutableState.value = DiagnosticExportState(message = "诊断日志已导出。")
      } catch (cancelled: CancellationException) {
        mutableState.value = DiagnosticExportState()
        throw cancelled
      } catch (_: Exception) {
        mutableState.value = DiagnosticExportState(message = "日志导出失败，请重新选择保存位置。")
      }
    }
  }

  fun dismissMessage() { mutableState.update { it.copy(message = null) } }
}
