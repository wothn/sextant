package com.sextant.app.automation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sextant.app.SextantApplication

class SextantNotificationListenerService : NotificationListenerService() {
  private val connection: NotificationListenerConnection
    get() = (application as SextantApplication).notificationListenerConnection

  override fun onListenerConnected() {
    super.onListenerConnected()
    connection.markConnected()
  }

  override fun onListenerDisconnected() {
    // 系统或厂商的后台清理会断开这个绑定，系统设置里的授权开关却仍然是开着的。
    // 这里主动要求重新绑定，避免用户“重新打开应用之后还是监听不到”。
    // 注意：requestRebind() 在 onListenerDisconnected 之后立刻调用会被系统丢弃，
    // 所以交给 connection 延迟一小段时间再试，并带上失败退避。
    connection.markDisconnected()
    connection.scheduleRebind(this, (application as SextantApplication).applicationScope)
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    val posted = sbn ?: return
    if (posted.packageName != WECHAT_PACKAGE && posted.packageName != ALIPAY_PACKAGE) return
    val captured = notificationsForCapture(posted) { activeNotifications }
    captured.forEach {
      (application as SextantApplication).automationCoordinator.capture(it)
    }
  }
}

/** A group summary describes its children, not another payment. Keep each child's identity and time. */
internal fun notificationsForCapture(
  posted: StatusBarNotification,
  activeNotifications: () -> Array<StatusBarNotification>,
): List<CapturedNotification> {
  val notifications = if (posted.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
    runCatching {
      activeNotifications().filter {
        it.key != posted.key && it.packageName == posted.packageName && it.groupKey == posted.groupKey &&
          it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
      }
    }.getOrDefault(emptyList())
  } else {
    listOf(posted)
  }
  return notifications.map { child ->
    val notification = child.notification
    val extras = notification.extras
    CapturedNotification(
      sourcePackage = child.packageName,
      notificationKey = child.key,
      postedAt = child.postTime,
      title = extras.charSequence(Notification.EXTRA_TITLE),
      titleBig = extras.charSequence(Notification.EXTRA_TITLE_BIG),
      text = extras.charSequence(Notification.EXTRA_TEXT),
      bigText = extras.charSequence(Notification.EXTRA_BIG_TEXT),
      textLines = extras.charSequenceArray(Notification.EXTRA_TEXT_LINES),
      infoText = extras.charSequence(Notification.EXTRA_INFO_TEXT),
      subText = extras.charSequence(Notification.EXTRA_SUB_TEXT),
      summaryText = extras.charSequence(Notification.EXTRA_SUMMARY_TEXT),
      tickerText = notification.tickerText?.toString().orEmpty(),
      notificationWhen = notification.`when`.takeIf { it > 0 },
    )
  }
}

private fun android.os.Bundle.charSequence(key: String): String = getCharSequence(key)?.toString().orEmpty()
private fun android.os.Bundle.charSequenceArray(key: String): List<String> =
  getCharSequenceArray(key)?.map(CharSequence::toString).orEmpty()
