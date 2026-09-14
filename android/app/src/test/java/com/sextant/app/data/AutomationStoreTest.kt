package com.sextant.app.data

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.runBlocking
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
