package com.sextant.app.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sextant.app.SextantApplication

/**
 * 解析一条已捕获的支付通知。
 *
 * 用 [CoroutineWorker] 而不是同步的 `Worker`：解析结果要落库，而仓储层已经是挂起 API。
 * 同步 `Worker` 里只能 `runBlocking` 硬等，那等于把协程调度器省下的线程又借回来占着。
 */
class LocalParseWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    val eventId = inputData.getString(EVENT_ID_KEY) ?: return Result.failure()
    return applicationContext.coordinator().processLocal(eventId).toWorkResult()
  }
}

class AutomationCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    applicationContext.coordinator().cleanup()
    return Result.success()
  }
}

private fun Context.coordinator(): AutomationCoordinator =
  (applicationContext as SextantApplication).automationCoordinator

private fun ProcessingResult.toWorkResult(): ListenableWorker.Result = when (this) {
  ProcessingResult.Success -> ListenableWorker.Result.success()
  ProcessingResult.Retry -> ListenableWorker.Result.retry()
  ProcessingResult.Failure -> ListenableWorker.Result.failure()
}
