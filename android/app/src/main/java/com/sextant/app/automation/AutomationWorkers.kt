package com.sextant.app.automation

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.sextant.app.SextantApplication

class LocalParseWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
  override fun doWork(): Result {
    val eventId = inputData.getString(EVENT_ID_KEY) ?: return Result.failure()
    return applicationContext.coordinator().processLocal(eventId).toWorkResult()
  }
}

class AutomationCleanupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
  override fun doWork(): Result {
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
