package com.sextant.app.automation

import android.content.Context
import com.sextant.app.data.AutomationEventStatus
import com.sextant.app.data.SextantDatabase
import com.sextant.app.data.SextantRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationCoordinatorTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }

  @After
  fun tearDown() {
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }

  @Test
  fun postingNotificationImmediatelyEnrichesItFromTheMatchingPendingPage() = runBlocking {
    val repository = SextantRepository(context)
    val store = repository.automationStore
    store.updateSettings(store.loadSettings().copy(enabled = true, paymentPageEnabled = true, resultNotificationsEnabled = false))
    val coordinator = AutomationCoordinator(context, repository, this)
    val now = System.currentTimeMillis()
    val page = PaymentPageParser.parse(WECHAT_PACKAGE, listOf("拼多多", "￥16.40", "微信支付", "关闭"), now)!!

    suspend fun capture(notification: CapturedNotification): String =
      store.capture(notification, EncryptedValue(Json.encodeToString(notification).encodeToByteArray(), byteArrayOf()))!!

    fun decode(payload: EncryptedValue?): CapturedNotification? =
      payload?.let { Json.decodeFromString<CapturedNotification>(it.ciphertext.decodeToString()) }

    val pageId = capture(CapturedNotification(WECHAT_PACKAGE, "payment-page:matching", now, pageCandidate = page))
    val otherPageId = capture(CapturedNotification(WECHAT_PACKAGE, "payment-page:other", now, pageCandidate = page.copy(amountCents = 1700)))
    assertEquals(ProcessingResult.Retry, coordinator.processLocal(pageId, ::decode))
    val noticeId = capture(CapturedNotification(WECHAT_PACKAGE, "payment-notice", now + 2000,
      title = "微信支付", text = "已支付¥16.40"))

    assertEquals(ProcessingResult.Success, coordinator.processLocal(noticeId, ::decode))

    val transactionId = store.getEvent(noticeId)!!.transactionId
    assertNotNull(transactionId)
    val merged = store.getEvent(pageId)!!
    assertEquals("payment_merged", merged.failureCode)
    assertEquals(transactionId, merged.transactionId)
    assertEquals("拼多多", repository.database.transactionDao().findById(transactionId!!)!!.merchantName)
    assertEquals(AutomationEventStatus.Captured, store.getEvent(otherPageId)!!.status)
    assertEquals(ProcessingResult.Success, coordinator.processLocal(pageId, ::decode))
    assertEquals(1L, repository.database.transactionDao().count())
  }
}
