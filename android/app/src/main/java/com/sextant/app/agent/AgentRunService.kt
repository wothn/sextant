package com.sextant.app.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sextant.app.MainActivity
import com.sextant.app.SextantApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 从用户可见界面启动，独立于 Activity；系统重启服务时恢复协议检查点。 */
class AgentRunService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var task: Job? = null
  private var updates: Job? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private var activeRunId: String? = null
  private var activeAttemptId: String? = null
  private val coordinator get() = (application as SextantApplication).agentCoordinator
  private val notifications get() = getSystemService(NotificationManager::class.java)

  override fun onCreate() {
    super.onCreate()
    notifications.createNotificationChannel(NotificationChannel(CHANNEL, "对话任务", NotificationManager.IMPORTANCE_LOW))
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == STOP) {
      val stoppedId = intent.getStringExtra("runId")
      val stoppedAttempt = intent.getStringExtra("attemptId")
      if (stoppedId == null || stoppedId != activeRunId || stoppedAttempt != activeAttemptId) {
        if (task?.isActive != true) stopSelf(startId)
        return START_NOT_STICKY
      }
      val stoppedTask = task
      scope.launch {
        try { coordinator.recordStopped(stoppedId, stoppedAttempt) }
        finally {
          stoppedTask?.cancelAndJoin()
          if (activeRunId == stoppedId && activeAttemptId == stoppedAttempt) stopSelf(startId)
        }
      }
      return START_NOT_STICKY
    }
    val runId = intent?.getStringExtra("runId") ?: run { stopSelf(); return START_NOT_STICKY }
    val attemptId = intent.getStringExtra("attemptId")
    ServiceCompat.startForeground(this, NOTIFICATION, notification("正在连接与分析…", true),
      if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    if (task?.isActive == true && activeRunId == runId && activeAttemptId == attemptId) return START_REDELIVER_INTENT
    val previous = task
    activeRunId = runId
    activeAttemptId = attemptId
    task = scope.launch {
      previous?.cancelAndJoin()
      ServiceCompat.startForeground(this@AgentRunService, NOTIFICATION, notification("正在连接与分析…", true),
        if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
      wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:agent").apply {
        acquire(16 * 60 * 1000L)
      }
      updates = scope.launch { coordinator.state.map { it.running to it.activity }.distinctUntilChanged().collect { (running, activity) ->
        if (running) notifications.notify(NOTIFICATION, notification(activity.ifBlank { "正在分析…" }, true))
      } }
      try { coordinator.execute(runId, attemptId) }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { /* 对话读取失败由协调者展示，服务仍必须退出。 */ }
      finally {
        updates?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        val ownsService = activeRunId == runId && activeAttemptId == attemptId
        if (ownsService) ServiceCompat.stopForeground(this@AgentRunService, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val state = coordinator.state.value
        if (ownsService && state.runId == runId && state.attemptId == attemptId && state.status != "cancelled") runCatching {
          notifications.notify(NOTIFICATION + 1, notification(when (state.status) {
            "awaiting_confirmation" -> "变更已准备好，等待你确认"
            "completed" -> "分析完成，点击查看"
            else -> "任务未完成，点击查看并重试"
          }, false))
        }
        stopSelf(startId)
      }
    }
    return START_REDELIVER_INTENT
  }

  private fun notification(text: String, ongoing: Boolean): Notification {
    val open = PendingIntent.getActivity(this, 200, Intent(this, MainActivity::class.java)
      .putExtra("openAgent", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("Sextant · 对话助手").setContentText(text).setContentIntent(open)
      .setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing).apply {
        if (ongoing) addAction(0, "停止", PendingIntent.getService(this@AgentRunService, 201,
          Intent(this@AgentRunService, AgentRunService::class.java).setAction(STOP)
            .putExtra("runId", activeRunId).putExtra("attemptId", activeAttemptId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
      }.build()
  }

  override fun onTimeout(startId: Int, fgsType: Int) { task?.cancel(); stopSelf() }
  override fun onDestroy() { scope.cancel(); wakeLock?.takeIf { it.isHeld }?.release(); super.onDestroy() }
  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val STOP = "com.sextant.app.agent.STOP"
    private const val CHANNEL = "agent-runs"
    private const val NOTIFICATION = 4300
  }
}
