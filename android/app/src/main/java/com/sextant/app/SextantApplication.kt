package com.sextant.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sextant.app.automation.AutomationCleanupWorker
import com.sextant.app.automation.AutomationCoordinator
import com.sextant.app.automation.NotificationListenerConnection
import com.sextant.app.data.BackupCoordinator
import com.sextant.app.data.SextantRepository
import com.sextant.app.data.DiagnosticExportCoordinator
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
  lateinit var backupCoordinator: BackupCoordinator
    private set
  lateinit var diagnosticExporter: DiagnosticExportCoordinator
    private set
  lateinit var agentCoordinator: com.sextant.app.agent.AgentCoordinator
    private set
  lateinit var updateCoordinator: com.sextant.app.update.UpdateCoordinator
    private set

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** 供监听服务在断连回调里提交恢复任务使用。 */
  internal val applicationScope: CoroutineScope get() = appScope

  override fun onCreate() {
    super.onCreate()
    repository = SextantRepository(this)
    updateCoordinator = com.sextant.app.update.UpdateCoordinator(this, appScope)
    agentCoordinator = com.sextant.app.agent.AgentCoordinator(this, repository, appScope,
      store = com.sextant.app.data.AgentConversationStore(this),
      settings = com.sextant.app.data.AgentSettingsStore(this),
      images = com.sextant.app.data.AgentImageStore(this))
    automationCoordinator = AutomationCoordinator(this, repository, appScope)
    notificationListenerConnection = repository.notificationListenerConnection
    // 备份读写挂在应用级作用域上：它要比设置页活得久，旋转或分屏重建界面时不能把正在
    // 写文件的导出、正在替换账本的恢复一起取消掉。
    backupCoordinator = BackupCoordinator(this, repository, appScope)
    diagnosticExporter = DiagnosticExportCoordinator(this, repository, appScope)
    appScope.launch {
      repository.changes.collect { runCatching { com.sextant.app.widget.WidgetUpdates.enqueue(this@SextantApplication) } }
    }
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

  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
    super.onConfigurationChanged(newConfig)
    com.sextant.app.widget.WidgetUpdates.enqueue(this)
  }
}
