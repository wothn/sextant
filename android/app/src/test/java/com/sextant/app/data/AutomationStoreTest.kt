package com.sextant.app.data

import android.content.Context
import com.sextant.app.automation.CapturedNotification
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationStoreTest {
  private lateinit var context: Context
  private lateinit var database: SextantDatabase
  private lateinit var store: AutomationStore

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    context.deleteDatabase(DATABASE_NAME)
    database = SextantDatabase(context)
    store = AutomationStore(database)
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(DATABASE_NAME)
  }

  @Test
  fun exactNotificationIsCapturedOnlyOnce() {
    val notification = notification("same-key")
    assertNotNull(store.capture(notification, encrypted()))
    assertNull(store.capture(notification, encrypted()))
    assertEquals(1, store.listRecentEvents().size)
  }

  @Test
  fun postsOneAutomaticTransactionAndSupportsSafeUndo() {
    val eventId = store.capture(notification("post-key"), encrypted())!!
    val posted = store.postCandidate(eventId, WECHAT_PACKAGE, candidate())

    assertNotNull(posted)
    assertNull(store.postCandidate(eventId, WECHAT_PACKAGE, candidate()))
    val row = database.readableDatabase.rawQuery(
      "SELECT entryOrigin, automationKind, sourceEventId, isUserEdited FROM transactions",
      emptyArray(),
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3).toString())
    }
    assertEquals(listOf("notification", "purchase", eventId, "0"), row)
    assertTrue(store.undoAutomatedTransaction(posted!!.id, posted.updatedAt))
    assertEquals(0L, database.readableDatabase.count("transactions"))
    assertEquals(AutomationEventStatus.Undone, store.listRecentEvents().single().status)
  }

  @Test
  fun editedAutomaticTransactionCannotBeUndoneByStaleNotificationAction() {
    val eventId = store.capture(notification("edit-key"), encrypted())!!
    val posted = store.postCandidate(eventId, WECHAT_PACKAGE, candidate())!!
    database.writableDatabase.execSQL(
      "UPDATE transactions SET isUserEdited = 1, updatedAt = updatedAt + 1 WHERE id = ?",
      arrayOf(posted.id),
    )

    assertFalse(store.undoAutomatedTransaction(posted.id, posted.updatedAt))
    assertEquals(1L, database.readableDatabase.count("transactions"))
  }

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

  private fun android.database.sqlite.SQLiteDatabase.count(table: String): Long = rawQuery(
    "SELECT COUNT(*) FROM $table",
    emptyArray(),
  ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

  private companion object {
    const val DATABASE_NAME = "sextant.db"
  }
}
