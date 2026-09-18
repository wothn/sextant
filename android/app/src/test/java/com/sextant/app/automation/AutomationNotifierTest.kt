package com.sextant.app.automation

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import com.sextant.app.R
import com.sextant.app.data.PostedTransaction
import com.sextant.app.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationNotifierTest {
  private lateinit var application: Application

  @Before
  fun setUp() {
    application = RuntimeEnvironment.getApplication()
    // API 33 起没有这个权限 showPosted 会直接返回，一条通知都不会发。
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  /**
   * 通知小图标必须是本应用自己的资源。
   *
   * `Notification.Builder.setSmallIcon(int)` 的契约是「本应用包内的 drawable」，实现是
   * `Icon.createWithResource(context, resId)`：它把资源 ID 和**应用包名**绑在一起交给系统，
   * SystemUI 再按应用包取 Resources 去解析这个 ID。以前这里借的是
   * `android.R.drawable.ic_menu_save`——一个属于 android 包的废弃软盘图标。包名与资源 ID
   * 分属两个包时，`Icon.loadDrawableInner` 会走进 `getResources().getDrawable(...)` 抛异常的
   * 分支，只写一行日志然后返回 null，也就是一个空白图标：不崩溃、不提示，lint 也查不出来。
   *
   * 注意 `Icon.getResPackage()` **不能**用来判断这件事：它返回的是 createWithResource 盖上去的
   * 应用包名，哪怕资源 ID 其实属于 android 包也照样返回应用包名。真正能区分的是资源 ID 自己
   * 内嵌的包，用 `Resources.getResourcePackageName` 读出来（实测框架资源返回 "android"）。
   */
  @Test
  fun smallIconResolvesInsideThisApplicationInsteadOfTheFramework() {
    AutomationNotifier(application).showPosted(posted())

    val notification = shadowOf(application.getSystemService(NotificationManager::class.java))
      .allNotifications.single()
    val resources = application.resources

    // 先断言包名再断言 ID：ID 不相同时 AssertionError 只会打印两个十进制数
    // （0x7f0800c6 vs 0x0108004e），读不出问题在哪；包名这条会把原因直接写出来。
    assertNotEquals(
      "小图标不能借用 android 包的框架资源",
      "android",
      resources.getResourcePackageName(notification.smallIcon.resId),
    )
    assertEquals(
      "小图标必须和同一个 APK 里的其它资源同包",
      resources.getResourcePackageName(R.drawable.ic_notification),
      resources.getResourcePackageName(notification.smallIcon.resId),
    )
    assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
    assertNotNull(
      "小图标必须能在本应用内解析出 drawable，否则通知栏上是一片空白",
      notification.smallIcon.loadDrawable(application),
    )
  }

  @Test
  fun postedNotificationCarriesTheUndoAndEditActions() {
    AutomationNotifier(application).showPosted(posted())

    val notification = shadowOf(application.getSystemService(NotificationManager::class.java))
      .allNotifications.single()

    assertEquals("已自动记录支出 ¥36.80", notification.extras.getString("android.title"))
    assertEquals(
      listOf("撤销", "编辑"),
      notification.actions.map { it.title.toString() },
    )
  }

  private fun posted() = PostedTransaction(
    id = "transaction-1",
    type = TransactionType.Expense,
    amountCents = 3_680,
    description = "梧桐小馆",
    updatedAt = 1_700_000_000_000,
  )
}
