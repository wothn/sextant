package com.sextant.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sextant.app.automation.AutomationCleanupWorker
import com.sextant.app.automation.AutomationCoordinator
import com.sextant.app.data.SextantRepository
import java.util.concurrent.TimeUnit

class SextantApplication : Application() {
  lateinit var repository: SextantRepository
    private set
  lateinit var automationCoordinator: AutomationCoordinator
    private set

  override fun onCreate() {
    super.onCreate()
    repository = SextantRepository(this)
    automationCoordinator = AutomationCoordinator(this, repository)
    runCatching {
      val cleanup = PeriodicWorkRequestBuilder<AutomationCleanupWorker>(24, TimeUnit.HOURS).build()
      WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        "automation-cleanup",
        ExistingPeriodicWorkPolicy.KEEP,
        cleanup,
      )
    }
  }
}
