package com.sextant.app.automation

import android.app.Notification
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationCaptureTest {
  @Test
  fun summaryAndDirectDeliveryCaptureExactlyTheSameChildren() {
    val first = posted(1, 1_000)
    val second = posted(2, 2_000)
    val summary = posted(3, 3_000, summary = true, text = "已支付￥99.00")
    val otherGroup = posted(4, 4_000, group = "other")
    val otherPackage = posted(5, 5_000, packageName = ALIPAY_PACKAGE)
    val direct = listOf(first, second).flatMap { child ->
      notificationsForCapture(child) { error("Only summaries need active notifications") }
    }
    val grouped = notificationsForCapture(summary) {
      arrayOf(first, second, summary, otherGroup, otherPackage)
    }

    assertEquals(direct, grouped)
    assertEquals(listOf(1_000L, 2_000L), grouped.map { it.postedAt })
    assertTrue(grouped.all { it.text == "已支付￥12.00" })
  }

  @Test
  fun summaryDoesNotMixItsTextWithChildOrHidePaymentsBehindOrdinaryChat() {
    val payment = posted(1, 1_000)
    val chat = posted(2, 2_000, text = "今晚吃饭吗")
    val summary = posted(3, 3_000, summary = true, text = "已支付￥99.00")
    val candidates = notificationsForCapture(summary) { arrayOf(payment, chat) }
      .map(NotificationParserRegistry::parse)
      .filterIsInstance<LocalParseOutcome.Parsed>()

    assertEquals(1, candidates.size)
    assertEquals(1_200L, candidates.single().candidate.amountCents)
    assertEquals(1_000L, candidates.single().candidate.occurredAt)
  }

  @Test
  fun summaryWithoutAccessibleChildrenNeverBecomesAnExtraPayment() {
    val summary = posted(3, 3_000, summary = true)
    assertTrue(notificationsForCapture(summary) { emptyArray() }.isEmpty())
    assertTrue(notificationsForCapture(summary) { throw SecurityException("disconnected") }.isEmpty())
  }

  @Test
  fun updatesPreserveOriginalNotificationTimeWhileKeepingDeliveryTime() {
    val original = notificationsForCapture(posted(1, 1_000, notificationWhen = 900)) { emptyArray() }.single()
    val update = notificationsForCapture(posted(1, 2_000, notificationWhen = 900)) { emptyArray() }.single()
    assertEquals(original.notificationKey, update.notificationKey)
    assertEquals(900L, update.notificationWhen)
    assertEquals(2_000L, update.postedAt)
  }

  private fun posted(
    id: Int,
    postTime: Long,
    notificationWhen: Long = postTime,
    summary: Boolean = false,
    group: String = "payments",
    packageName: String = WECHAT_PACKAGE,
    text: String = "已支付￥12.00",
  ): StatusBarNotification {
    val notification = Notification.Builder(RuntimeEnvironment.getApplication(), "payments")
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("微信支付")
      .setContentText(text)
      .setWhen(notificationWhen)
      .setGroup(group)
      .setGroupSummary(summary)
      .build()
    return StatusBarNotification(packageName, packageName, id, null, 1000, 0, 0, notification,
      UserHandle.getUserHandleForUid(1000), postTime)
  }
}
