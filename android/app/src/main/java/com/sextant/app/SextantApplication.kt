package com.sextant.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sextant.app.automation.AutomationCleanupWorker
import com.sextant.app.automation.AutomationCoordinator
import com.sextant.app.automation.NotificationListenerConnection
import com.sextant.app.data.SextantRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SextantApplication : Application() {
  lateinit var repository: SextantRepository
    private set
  lateinit var automationCoordinator: AutomationCoordinator
    private set
  lateinit var notificationListenerConnection: NotificationListenerConnection
    private set

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** 供监听服务在断连回调里提交恢复任务使用。 */
  internal val applicationScope: CoroutineScope get() = appScope

  override fun onCreate() {
    super.onCreate()
    repository = SextantRepository(this)
    automationCoordinator = AutomationCoordinator(this, repository)
    notificationListenerConnection = repository.notificationListenerConnection
    // 进程每次重建都主动要求系统重新绑定监听服务。
    // 厂商清理后台会杀掉监听进程，但系统设置里的授权开关还留着，
    // 光靠读那个开关无法发现“已授权但没在监听”，只会表现为静默漏记。
    appScope.launch {
      runCatching { notificationListenerConnection.rebind(this@SextantApplication) }
    }
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
