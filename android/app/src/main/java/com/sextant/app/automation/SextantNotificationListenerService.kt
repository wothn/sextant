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
    val notification = posted.notification
    val extras = notification.extras
    val captured = CapturedNotification(
      sourcePackage = posted.packageName,
      notificationKey = posted.key,
      postedAt = posted.postTime,
      title = extras.charSequence(Notification.EXTRA_TITLE),
      titleBig = extras.charSequence(Notification.EXTRA_TITLE_BIG),
      text = extras.charSequence(Notification.EXTRA_TEXT),
      bigText = extras.charSequence(Notification.EXTRA_BIG_TEXT),
      textLines = extras.charSequenceArray(Notification.EXTRA_TEXT_LINES),
      infoText = extras.charSequence(Notification.EXTRA_INFO_TEXT),
      subText = extras.charSequence(Notification.EXTRA_SUB_TEXT),
      summaryText = extras.charSequence(Notification.EXTRA_SUMMARY_TEXT),
      tickerText = notification.tickerText?.toString().orEmpty(),
      groupChildText = latestGroupChildText(posted),
    )
    (application as SextantApplication).automationCoordinator.capture(captured)
  }

  private fun latestGroupChildText(summary: StatusBarNotification): String {
    if (summary.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) return ""
    return runCatching {
      activeNotifications
        .asSequence()
        .filter { it.key != summary.key && it.packageName == summary.packageName && it.groupKey == summary.groupKey }
        .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        .sortedByDescending { it.postTime }
        .map(::notificationText)
        .firstOrNull(String::isNotBlank)
        .orEmpty()
    }.getOrDefault("")
  }

  private fun notificationText(posted: StatusBarNotification): String {
    val notification = posted.notification
    val extras = notification.extras
    return buildList {
      add(extras.charSequence(Notification.EXTRA_TITLE))
      add(extras.charSequence(Notification.EXTRA_TITLE_BIG))
      add(extras.charSequence(Notification.EXTRA_TEXT))
      add(extras.charSequence(Notification.EXTRA_BIG_TEXT))
      addAll(extras.charSequenceArray(Notification.EXTRA_TEXT_LINES))
      add(extras.charSequence(Notification.EXTRA_INFO_TEXT))
      add(extras.charSequence(Notification.EXTRA_SUB_TEXT))
      add(extras.charSequence(Notification.EXTRA_SUMMARY_TEXT))
      add(notification.tickerText?.toString().orEmpty())
    }.asSequence()
      .map(String::trim)
      .filter(String::isNotBlank)
      .distinct()
      .joinToString("\n")
  }
}

private fun android.os.Bundle.charSequence(key: String): String = getCharSequence(key)?.toString().orEmpty()
private fun android.os.Bundle.charSequenceArray(key: String): List<String> =
  getCharSequenceArray(key)?.map(CharSequence::toString).orEmpty()
