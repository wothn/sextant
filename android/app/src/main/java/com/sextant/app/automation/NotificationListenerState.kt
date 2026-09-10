package com.sextant.app.automation

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 通知使用权是否已在系统设置中授予。
 *
 * 注意：这里读到的只是系统设置里的开关状态，并不代表监听服务正在运行。
 * 厂商在清理后台时会杀掉监听进程，而这个开关仍然停留在“已开启”，
 * 所以它不能用来判断监听是否真的有效。
 */
fun interface NotificationListenerAccess {
  fun isGranted(context: Context): Boolean

  companion object {
    val System: NotificationListenerAccess = NotificationListenerAccess { context ->
      runCatching {
        Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS_SETTING)
          .orEmpty()
          .split(':')
          .mapNotNull(ComponentName::unflattenFromString)
          .any { it.packageName == context.packageName }
      }.getOrDefault(false)
    }
  }
}

private const val ENABLED_LISTENERS_SETTING = "enabled_notification_listeners"

enum class ListenerConnectionState {
  /** 尚未主动确认过，进程刚启动时的初始状态。 */
  Unknown,
  Disconnected,
  Connected,
}

data class NotificationListenerStatus(
  val authorized: Boolean = false,
  val connection: ListenerConnectionState = ListenerConnectionState.Unknown,
  val lastCapturedAt: Long? = null,
)

/**
 * 维护监听服务的真实连接状态，并在它被系统或厂商清理掉之后尝试重新绑定。
 *
 * [SextantNotificationListenerService] 在 [onListenerConnected] 时上报 Connected，
 * 应用启动和从设置页返回时调用 [rebind] 主动要求系统重新绑定。
 * 这样“已授权但没在监听”就变成了可以检测、也可以自动修复的状态。
 */
class NotificationListenerConnection(
  private val access: NotificationListenerAccess,
  private val componentName: ComponentName,
) {
  private val _status = MutableStateFlow(NotificationListenerStatus())
  val status: StateFlow<NotificationListenerStatus> = _status.asStateFlow()

  private val rebindInFlight = AtomicBoolean(false)
  private val lastRebindAt = AtomicLong(0L)
  private val rebindStreak = AtomicLong(0L)

  @Volatile
  private var firstRebindAt = 0L

  private fun isConnected() = _status.value.connection == ListenerConnectionState.Connected

  fun markConnected() {
    // 每次重新连上都重置退避，避免把“刚恢复又断开”误判成持续失败。
    rebindStreak.set(0L)
    _status.update { it.copy(authorized = true, connection = ListenerConnectionState.Connected) }
  }

  fun markDisconnected() {
    _status.update { it.copy(connection = ListenerConnectionState.Disconnected) }
  }

  fun noteCaptured() {
    _status.update { it.copy(lastCapturedAt = System.currentTimeMillis()) }
  }

  /**
   * 异步请求恢复监听，供 [SextantNotificationListenerService.onListenerDisconnected] 之类的
   * 回调使用——那些回调运行在主线程上，不能阻塞。
   *
   * 连接成功会重置退避计数，所以“连上又被断开”的抖动不会累积成放弃重试。
   */
  fun scheduleRebind(context: Context, scope: CoroutineScope) {
    scope.launch {
      // 绑定刚断开时立刻重绑通常会被系统直接忽略，等一小会儿再试。
      delay(REBIND_SETTLE_DELAY_MILLIS)
      if (isConnected()) return@launch
      runCatching { rebind(context) }.onFailure { Log.w(LOG_TAG, "自动恢复监听失败", it) }
    }
  }

  /**
   * 重新读取系统授权并尝试让服务真正跑起来。
   *
   * @param context 任意 Context，内部会自动取 applicationContext，可安全长持有。
   * @return 重新检查之后监听服务是否处于连接状态。
   */
  suspend fun rebind(context: Context): Boolean {
    val appContext = context.applicationContext
    val authorized = access.isGranted(appContext)
    _status.update { current -> current.copy(authorized = authorized) }
    if (!authorized) {
      _status.update { it.copy(connection = ListenerConnectionState.Unknown) }
      return false
    }
    if (isConnected()) return true
    return withContext(Dispatchers.IO) { attemptRebind(appContext) }
  }

  private suspend fun attemptRebind(context: Context): Boolean {
    val now = System.currentTimeMillis()
    // 退避：持续失败时逐渐拉长重试间隔，并在超过窗口后停手，
    // 交给下一次启动、回到前台或系统重新绑定来兜底，避免一直骚扰系统服务。
    val streak = rebindStreak.get()
    if (streak > 0) {
      val wait = (REBIND_BACKOFF_BASE_MILLIS shl streak.coerceAtMost(8).toInt())
        .coerceAtMost(REBIND_BACKOFF_MAX_MILLIS)
      if (now - lastRebindAt.get() < wait) return isConnected()
      if (now - firstRebindAt > REBIND_STREAK_WINDOW_MILLIS) return isConnected()
    }
    if (!rebindInFlight.compareAndSet(false, true)) return isConnected()
    lastRebindAt.set(now)
    if (rebindStreak.get() == 0L) firstRebindAt = now
    return try {
      if (isConnected()) return true

      // 第一招：直接要求系统重新绑定。如果组件被停用过，这一步可以把它拉回来。
      runCatching { NotificationListenerService.requestRebind(componentName) }
        .onFailure { Log.w(LOG_TAG, "requestRebind 失败", it) }
      if (awaitConnection()) return true

      // 第二招：部分 OEM 会在组件层面把服务禁用掉，requestRebind 就成了空操作。
      // 这里把组件显式启用一次再重绑；组件原本是启用状态时不做任何改动。
      if (isComponentDisabled(context)) {
        enableComponent(context)
        runCatching { NotificationListenerService.requestRebind(componentName) }
        if (awaitConnection()) return true
      }

      val connected = awaitConnection() || isConnected()
      if (connected) {
        rebindStreak.set(0L)
      } else {
        if (rebindStreak.getAndIncrement() == 0L) firstRebindAt = System.currentTimeMillis()
      }
      connected
    } finally {
      rebindInFlight.set(false)
    }
  }

  private suspend fun awaitConnection(): Boolean {
    repeat(CONNECTION_POLL_ATTEMPTS) {
      if (isConnected()) return true
      delay(CONNECTION_POLL_INTERVAL_MILLIS)
    }
    return isConnected()
  }

  private fun isComponentDisabled(context: Context): Boolean {
    val state = runCatching {
      context.packageManager.getComponentEnabledSetting(componentName)
    }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
      state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
      state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
  }

  private fun enableComponent(context: Context) {
    runCatching {
      context.packageManager.setComponentEnabledSetting(
        componentName,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP,
      )
    }.onFailure { Log.w(LOG_TAG, "启用监听组件失败", it) }
  }

  companion object {
    private const val LOG_TAG = "SextantListener"
    private const val REBIND_SETTLE_DELAY_MILLIS = 700L
    private const val REBIND_BACKOFF_BASE_MILLIS = 500L
    private const val REBIND_BACKOFF_MAX_MILLIS = 3_000L
    private const val REBIND_STREAK_WINDOW_MILLIS = 60_000L
    private const val CONNECTION_POLL_ATTEMPTS = 15
    private const val CONNECTION_POLL_INTERVAL_MILLIS = 200L

    fun create(context: Context, access: NotificationListenerAccess = NotificationListenerAccess.System): NotificationListenerConnection =
      NotificationListenerConnection(
        access = access,
        componentName = ComponentName(context.packageName, SextantNotificationListenerService::class.java.name),
      )
  }
}
