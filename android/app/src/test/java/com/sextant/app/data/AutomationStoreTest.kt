package com.sextant.app.data

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.runBlocking
import com.sextant.app.automation.CapturedNotification
import com.sextant.app.automation.ALIPAY_PACKAGE
import com.sextant.app.automation.PaymentPageParser
import com.sextant.app.automation.EncryptedValue
import com.sextant.app.automation.ParsedTransactionCandidate
import com.sextant.app.automation.WECHAT_PACKAGE
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 自动记账的持久化行为。
 *
 * 重点全在「同一件事重复发生时会怎样」：同一条通知只能落一条事件、一个事件只能出
 * 一笔流水、已经撤销掉的账不能再被撤销一次。这些性质由库里的唯一约束和事务共同保证，
 * 所以断言直接查原始表，而不是绕过约束去问仓储。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationStoreTest {
  private lateinit var context: Context
  private lateinit var database: SextantDatabase
  private lateinit var store: AutomationStore

  @Before
  fun setUp() = runBlocking {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase(DATABASE_NAME)
    database = SextantDatabase.createForTests(context)
    store = AutomationStore(database)
  }

  @After
  fun tearDown() {
    database.close()
    SextantDatabase.closeForTests()
    context.deleteDatabase(DATABASE_NAME)
  }

  @Test
  fun exactNotificationIsCapturedOnlyOnce() = runBlocking {
    val notification = notification("same-key")
    assertNotNull(store.capture(notification, encrypted()))
    assertNull(store.capture(notification, encrypted()))
    assertEquals(1, store.listRecentEvents().size)
  }

  @Test
  fun postsOneAutomaticTransactionAndSupportsSafeUndo() = runBlocking {
    val eventId = store.capture(notification("post-key"), encrypted())!!
    val posted = store.postCandidate(eventId, WECHAT_PACKAGE, candidate())

    assertNotNull(posted)
    assertNull(store.postCandidate(eventId, WECHAT_PACKAGE, candidate()))
    val row = database.read(
      "SELECT entryOrigin, automationKind, sourceEventId, isUserEdited FROM transactions",
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3).toString())
    }
    assertEquals(listOf("notification", "purchase", eventId, "0"), row)
    assertTrue(store.undoAutomatedTransaction(posted!!.id, posted.updatedAt))
    assertEquals(0L, database.count("transactions"))
    assertEquals(AutomationEventStatus.Undone, store.listRecentEvents().single().status)
  }

  @Test
  fun editedAutomaticTransactionCannotBeUndoneByStaleNotificationAction() = runBlocking {
    val eventId = store.capture(notification("edit-key"), encrypted())!!
    val posted = store.postCandidate(eventId, WECHAT_PACKAGE, candidate())!!
    database.openHelper.writableDatabase.execSQL(
      "UPDATE transactions SET isUserEdited = 1, updatedAt = updatedAt + 1 WHERE id = ?",
      arrayOf(posted.id),
    )

    assertFalse(store.undoAutomatedTransaction(posted.id, posted.updatedAt))
    assertEquals(1L, database.count("transactions"))
  }

  @Test
  fun pageSettingsAreOptInAndPersist() = runBlocking {
    assertFalse(store.loadSettings().paymentPageEnabled)
    store.updateSettings(AutomationSettings(enabled = true, paymentPageEnabled = true))
    assertTrue(store.loadSettings().paymentPageEnabled)
  }

  @Test
  fun pageEnrichesNotificationWithoutAddingTransactionAndRefreshesUndoVersion() = runBlocking {
    val event = store.capture(notification("notification-first"), encrypted())!!
    val posted = store.postCandidate(event, WECHAT_PACKAGE, candidate().copy(description = ""))!!
    val page = pageCandidate()
    val pageEvent = store.capture(pageNotification("page-first", page), encrypted())!!
    val enriched = store.postCandidate(pageEvent, WECHAT_PACKAGE, page)!!
    assertEquals(posted.id, enriched.id)
    assertEquals(1L, database.count("transactions"))
    assertEquals("街角咖啡", database.transactionDao().findById(posted.id)!!.description)
    assertFalse(store.undoAutomatedTransaction(posted.id, posted.updatedAt))
    assertTrue(store.undoAutomatedTransaction(enriched.id, enriched.updatedAt))
    // Reopening a detail after undo must not resurrect it, even from a new event.
    val repeat = store.capture(pageNotification("repeat", page.copy(providerReference = null)), encrypted())!!
    assertNull(store.postCandidate(repeat, WECHAT_PACKAGE, page))
    assertEquals(0L, database.count("transactions"))
  }

  @Test
  fun notificationAfterPageDoesNotDuplicateAndPageOriginSurvivesRoom() = runBlocking {
    val page = pageCandidate()
    val pageEvent = store.capture(pageNotification("page", page), encrypted())!!
    val posted = store.postCandidate(pageEvent, WECHAT_PACKAGE, page)!!
    assertEquals(EntryOrigin.Accessibility, database.transactionDao().findById(posted.id)!!.entryOrigin)
    val event = store.capture(notification("later"), encrypted())!!
    assertNull(store.postCandidate(event, WECHAT_PACKAGE, candidate().copy(description = "")))
    assertEquals(1L, database.count("transactions"))
    assertEquals(AutomationEventStatus.Duplicate, store.getEvent(event)!!.status)
  }

  @Test
  fun alipayMerchantlessSuccessMergesNotificationInEitherOrder() = runBlocking {
    for (pageFirst in listOf(true, false)) {
      val time = if (pageFirst) 1_000L else 1_000_000L
      val page = alipayMerchantlessPage(time)
      val notice = candidate().copy(amountCents = 7720, occurredAt = time, merchant = "便利店", description = "便利店")
      val notification = notification("alipay-$time").copy(sourcePackage = ALIPAY_PACKAGE, postedAt = time)
      val pageCapture = pageNotification("alipay-$time", page).copy(sourcePackage = ALIPAY_PACKAGE)
      val first = if (pageFirst) page else notice
      val second = if (pageFirst) notice else page
      val firstEvent = store.capture(if (pageFirst) pageCapture else notification, encrypted())!!
      val posted = store.postCandidate(firstEvent, ALIPAY_PACKAGE, first)!!
      if (pageFirst) {
        val row = database.transactionDao().findById(posted.id)!!
        assertEquals(7720L, row.amountCents)
        assertEquals("", row.description)
        assertNull(row.merchantName)
        assertEquals(EntryOrigin.Accessibility, row.entryOrigin)
      }
      val secondEvent = store.capture(if (pageFirst) notification else pageCapture, encrypted())!!
      store.postCandidate(secondEvent, ALIPAY_PACKAGE, second)
      val row = database.transactionDao().findById(posted.id)!!
      assertEquals("便利店", row.description)
      assertEquals("便利店", row.merchantName)
      assertEquals(AutomationEventStatus.Duplicate, store.getEvent(secondEvent)!!.status)
    }
    assertEquals(2L, database.count("transactions"))
  }

  @Test
  fun merchantlessPageRepeatsAndNearbyDetailsDoNotCreateDuplicateBills() = runBlocking {
    val page = alipayMerchantlessPage(1_000)
    val first = store.capture(pageNotification("alipay-first", page).copy(sourcePackage = ALIPAY_PACKAGE), encrypted())!!
    store.postCandidate(first, ALIPAY_PACKAGE, page)
    for ((key, next) in listOf(
      "repeat" to page.copy(occurredAt = 2_000),
      "detail" to page.copy(occurredAt = 900, hasExactTime = true, merchant = "便利店", description = "便利店", providerReference = "alipay123456"),
    )) {
      val event = store.capture(pageNotification(key, next).copy(sourcePackage = ALIPAY_PACKAGE), encrypted())!!
      assertNull(store.postCandidate(event, ALIPAY_PACKAGE, next))
      assertEquals(AutomationEventStatus.Failed, store.getEvent(event)!!.status)
      assertEquals("payment_match_ambiguous", store.listRecentEvents().first { it.id == event }.failureCode)
    }
    assertEquals(1L, database.count("transactions"))
  }

  private fun alipayMerchantlessPage(time: Long) = PaymentPageParser.parse(ALIPAY_PACKAGE,
    listOf("支付成功￥77.20", "支付成功", "77.20", "￥79.20", "示例银行立减金", "-￥2.00", "付款方式", "示例银行储蓄卡(0000)"), time)!!

  @Test
  fun repeatedPageCaptureUsesStableReferenceOrSessionInsteadOfScanTime() = runBlocking {
    val page = pageCandidate()
    assertNotNull(store.capture(pageNotification("ref", page), encrypted()))
    assertNull(store.capture(pageNotification("ref", page.copy(occurredAt = 2000)), encrypted()))
    val withoutReference = page.copy(providerReference = null)
    assertNotNull(store.capture(pageNotification("session", withoutReference), encrypted()))
    assertNull(store.capture(pageNotification("session", withoutReference.copy(occurredAt = 2000)), encrypted()))
  }

  @Test
  fun editedDescriptionsArePreservedAndPageOriginEditsDisableUndo() = runBlocking {
    val page = pageCandidate()
    val event = store.capture(pageNotification("edit", page), encrypted())!!
    val posted = store.postCandidate(event, WECHAT_PACKAGE, page)!!
    database.transactionDao().update(posted.id, TransactionType.Expense, 1200, null, null, "用户备注", 1000, posted.updatedAt + 1)
    val next = store.capture(notification("edited-later"), encrypted())!!
    assertNull(store.postCandidate(next, WECHAT_PACKAGE, candidate().copy(providerReference = page.providerReference)))
    assertEquals("用户备注", database.transactionDao().findById(posted.id)!!.description)
    assertTrue(database.transactionDao().findById(posted.id)!!.isUserEdited)
    assertFalse(store.undoAutomatedTransaction(posted.id))
    assertEquals(1L, database.count("transactions"))
  }

  @Test
  fun ambiguousSameAmountNotificationsRemainSeparateAndPageRequiresReview() = runBlocking {
    for (key in listOf("one", "two")) {
      val event = store.capture(notification(key), encrypted())!!
      store.postCandidate(event, WECHAT_PACKAGE, candidate().copy(description = ""))
    }
    val page = pageCandidate()
    val event = store.capture(pageNotification("ambiguous", page), encrypted())!!
    assertNull(store.postCandidate(event, WECHAT_PACKAGE, page))
    assertEquals(2L, database.count("transactions"))
    assertEquals(AutomationEventStatus.Failed, store.getEvent(event)!!.status)
    assertEquals("payment_match_ambiguous", store.listRecentEvents().first { it.id == event }.failureCode)
  }

  @Test
  fun differentReferencesAndHistoricalDatesAreNotMerged() = runBlocking {
    val page = pageCandidate()
    val first = store.capture(pageNotification("first", page), encrypted())!!
    store.postCandidate(first, WECHAT_PACKAGE, page)
    val second = store.capture(notification("different-reference"), encrypted())!!
    store.postCandidate(second, WECHAT_PACKAGE, candidate().copy(description = "", providerReference = "different123"))
    val oldPage = page.copy(occurredAt = -86_400_000, providerReference = "historical123", hasExactTime = true)
    val old = store.capture(pageNotification("old", oldPage), encrypted())!!
    store.postCandidate(old, WECHAT_PACKAGE, oldPage)
    assertEquals(3L, database.count("transactions"))
  }

  private fun pageCandidate() = candidate().copy(description = "街角咖啡", merchant = "街角咖啡", parser = "wechat-page-v1", providerReference = "20260920123456")
  private fun pageNotification(key: String, candidate: ParsedTransactionCandidate) = notification("payment-page:$key").copy(
    postedAt = candidate.occurredAt, pageCandidate = candidate,
  )

  private fun notification(key: String) = CapturedNotification(
    sourcePackage = WECHAT_PACKAGE,
    notificationKey = key,
    postedAt = 1_000,
    title = "微信支付",
    text = "支付成功 ￥12.00",
  )

  private fun candidate() = ParsedTransactionCandidate(
    direction = TransactionType.Expense,
    kind = AutomationKind.Purchase,
    amountCents = 1_200,
    occurredAt = 1_000,
    description = "消费 · 咖啡店",
    amountEvidence = "￥12.00",
    directionEvidence = "支付成功",
    parser = "test",
  )

  private fun encrypted() = EncryptedValue(byteArrayOf(1, 2), byteArrayOf(3, 4))

  private fun SextantDatabase.count(table: String): Long =
    read("SELECT COUNT(*) FROM $table").use { cursor ->
      cursor.moveToFirst()
      cursor.getLong(0)
    }

  /** 直接问底层 SQLite：这些断言看的是约束真的生效了没有，不该经过 DAO 的映射。 */
  private fun SextantDatabase.read(sql: String): Cursor =
    openHelper.writableDatabase.query(SimpleSQLiteQuery(sql))

  private companion object {
    const val DATABASE_NAME = "sextant.db"
  }
}
