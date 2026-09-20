package com.sextant.app.data

import android.content.Context
import com.sextant.app.automation.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MerchantCategoryRulesTest {
  private lateinit var context: Context
  private lateinit var repository: SextantRepository
  private lateinit var db: SextantDatabase
  private var sequence = 0

  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
    repository = SextantRepository(context)
    db = repository.database
  }

  @After fun tearDown() {
    SextantDatabase.closeForTests()
    context.deleteDatabase("sextant.db")
  }

  @Test fun builtInsAreConservativeAndDoNotInferMerchantFromNotes() = runBlocking {
    assertEquals(category("奶茶咖啡"), post(" 瑞幸咖啡（测试店） ").categoryId)
    assertEquals(category("正餐"), post("肯德基").categoryId)
    assertEquals(category("打车"), post("滴滴出行").categoryId)
    listOf("京东", "淘宝", "美团", "星巴克代购", "假星巴克", "未知商户").forEach { assertNull(post(it).categoryId) }
    assertNull(post(null, description = "星巴克").categoryId)
    assertNull(post("星巴克", kind = AutomationKind.Refund, type = TransactionType.Income).categoryId)
    assertNull(post("星巴克", kind = AutomationKind.TransferOut, type = TransactionType.Transfer).categoryId)
  }

  @Test fun rememberingIsExplicitOverridesBuiltInsAndDoesNotRewriteHistory() = runBlocking {
    val first = post("星巴克")
    val custom = category("正餐")
    edit(first, custom, remember = false)
    assertTrue(repository.listMerchantCategoryRules().isEmpty())
    edit(db.transactionDao().findById(first.id)!!, custom, remember = true)
    assertEquals(custom, post(" 星 巴 克 ").categoryId)
    assertEquals(category("奶茶咖啡"), post("星巴克（另一店）").categoryId)
    repository.updateMerchantCategoryRule(normalizeMerchant("星巴克"), category("打车"))
    assertEquals(custom, db.transactionDao().findById(first.id)!!.categoryId)
    assertEquals(category("打车"), post("星巴克").categoryId)
    repository.deleteMerchantCategoryRule(normalizeMerchant("星巴克"))
    assertEquals(category("奶茶咖啡"), post("星巴克").categoryId)
  }

  @Test fun editedNoteNeverBecomesMerchantIdentityAndInvalidRuleRollsBackEdit() = runBlocking {
    val row = post("街角咖啡")
    repository.updateTransaction(row.id, row.type, row.amountCents, category("正餐"), row.paymentMethodId,
      "和朋友聚餐", row.transactionDate, true)
    assertEquals("街角咖啡", repository.findTransaction(row.id)!!.transaction.merchantName)
    assertEquals("街角咖啡", repository.listMerchantCategoryRules().single().merchantName)
    assertEquals(category("正餐"), post("街角咖啡").categoryId)
    val before = db.transactionDao().findById(row.id)!!
    try {
      edit(before, "missing", true)
      fail("Invalid category must fail")
    } catch (error: IllegalArgumentException) {
      assertEquals("请选择有效的支出分类", error.message)
    }
    assertEquals(before, db.transactionDao().findById(row.id))
    assertEquals(category("正餐"), repository.listMerchantCategoryRules().single().categoryId)
  }

  @Test fun archivedCategoriesDoNotCauseSilentFallback() = runBlocking {
    val row = post("星巴克")
    edit(row, category("正餐"), true)
    db.categoryDao().archive(category("正餐"))
    assertNull(post("星巴克").categoryId)
    repository.deleteMerchantCategoryRule(normalizeMerchant("星巴克"))
    db.categoryDao().archive(category("奶茶咖啡"))
    assertNull(post("星巴克").categoryId)
  }

  @Test fun laterPageClassifiesOnceAndPreservesUserChanges() = runBlocking {
    val row = post(null, description = "")
    val page = candidate("瑞幸咖啡", row.transactionDate).copy(parser = "wechat-page-v1", providerReference = "ref-1")
    val updated = deliver(page, "payment-page:one")!!
    assertEquals(row.id, updated.id)
    assertEquals(1L, db.transactionDao().count())
    assertEquals(category("奶茶咖啡"), db.transactionDao().findById(row.id)!!.categoryId)
    assertFalse(repository.automationStore.undoAutomatedTransaction(row.id, row.updatedAt))
    edit(db.transactionDao().findById(row.id)!!, category("正餐"), false)
    deliver(page, "payment-page:two")
    assertEquals(category("正餐"), db.transactionDao().findById(row.id)!!.categoryId)
    assertEquals(1L, db.transactionDao().count())
  }

  @Test fun pageCannotOverwriteManuallyEditedUnclassifiedTransaction() = runBlocking {
    val row = post(null, description = "")
    repository.updateTransaction(row.id, row.type, row.amountCents, null, row.paymentMethodId, "手动备注", row.transactionDate)
    deliver(candidate("瑞幸咖啡", row.transactionDate).copy(parser = "wechat-page-v1"), "payment-page:edited")
    val after = db.transactionDao().findById(row.id)!!
    assertNull(after.categoryId)
    assertEquals("手动备注", after.description)
  }

  @Test fun backupRoundTripIncludesMerchantAndRulesAndOlderBackupClearsRules() = runBlocking {
    val row = post("测试咖啡")
    edit(row, category("奶茶咖啡"), true)
    val store = BackupStore(db)
    val backup = store.export()
    val document = BackupCodec.decodeAndValidate(backup)
    assertEquals("测试咖啡", document.transactions.single().merchantName)
    assertEquals(1, document.merchantCategoryRules.size)
    repository.deleteMerchantCategoryRule("测试咖啡")
    store.restore(backup)
    assertEquals("测试咖啡", db.transactionDao().findById(row.id)!!.merchantName)
    assertEquals(category("奶茶咖啡"), post("测试咖啡").categoryId)
    val legacy = BackupCodec.encode(document.copy(version = 3, databaseVersion = 12, merchantCategoryRules = emptyList(),
      transactions = document.transactions.map { it.copy(merchantName = null) }))
      .replace("\"merchantCategoryRules\"", "\"unusedRules\"").replace("\"merchantName\"", "\"unusedMerchant\"")
    store.restore(legacy)
    assertTrue(repository.listMerchantCategoryRules().isEmpty())
    assertNull(db.transactionDao().findById(row.id)!!.merchantName)
  }

  @Test fun invalidBackupRulesAreRejectedBeforeChangingLedger() = runBlocking {
    val row = post("测试商户")
    edit(row, category("正餐"), true)
    val document = BackupCodec.decodeAndValidate(BackupStore(db).export())
    val rule = document.merchantCategoryRules.single()
    for (rules in listOf(listOf(rule, rule), listOf(rule.copy(categoryId = "missing")), listOf(rule.copy(merchantKey = "different")))) {
      try {
        BackupStore(db).restore(BackupCodec.encode(document.copy(merchantCategoryRules = rules)))
        fail("Invalid backup must fail")
      } catch (_: BackupException) { }
      assertEquals(1L, db.transactionDao().count())
      assertEquals(1, repository.listMerchantCategoryRules().size)
    }
  }

  @Test fun failureWritingTransactionRollsBackRememberedRule() = runBlocking {
    val row = post("回滚测试商户")
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_edit BEFORE UPDATE ON transactions BEGIN SELECT RAISE(ABORT, 'test write failure'); END")
    }
    try {
      edit(row, category("正餐"), true)
      fail("Transaction write must fail")
    } catch (_: android.database.sqlite.SQLiteConstraintException) { }
    assertTrue(repository.listMerchantCategoryRules().isEmpty())
    assertEquals(row, db.transactionDao().findById(row.id))
  }

  @Test fun backupPreservesInactiveRulesAfterCategoryTypeChanges() = runBlocking {
    val row = post("星巴克")
    val category = db.categoryDao().listActive().single { it.name == "正餐" }
    edit(row, category.id, true)
    repository.updateCategory(category.id, category.name, TransactionType.Income, category.color, true, null)
    val backup = BackupStore(db).export()
    BackupStore(db).restore(backup)
    assertEquals(1, repository.listMerchantCategoryRules().size)
    assertNull(post("星巴克").categoryId)
  }

  private suspend fun category(name: String) = db.categoryDao().listActive().single { it.name == name }.id

  private suspend fun edit(row: TransactionEntity, categoryId: String, remember: Boolean) =
    repository.updateTransaction(row.id, row.type, row.amountCents, categoryId, row.paymentMethodId, row.description, row.transactionDate, remember)

  private fun candidate(merchant: String?, time: Long) = ParsedTransactionCandidate(
    direction = TransactionType.Expense, kind = AutomationKind.Purchase, amountCents = 1200,
    occurredAt = time, description = merchant.orEmpty(), merchant = merchant,
    amountEvidence = "12.00", directionEvidence = "付款", parser = "test",
  )

  private suspend fun post(merchant: String?, description: String = merchant.orEmpty(),
    kind: AutomationKind = AutomationKind.Purchase, type: TransactionType = TransactionType.Expense): TransactionEntity {
    val parsed = candidate(merchant, (++sequence) * 1_000_000L).copy(description = description, kind = kind, direction = type)
    return db.transactionDao().findById(deliver(parsed, "notification:$sequence")!!.id)!!
  }

  private suspend fun deliver(parsed: ParsedTransactionCandidate, key: String): PostedTransaction? {
    val captured = CapturedNotification(sourcePackage = WECHAT_PACKAGE, notificationKey = key,
      postedAt = parsed.occurredAt, text = parsed.description,
      pageCandidate = parsed.takeIf { key.startsWith("payment-page:") })
    val event = repository.automationStore.capture(captured, EncryptedValue(byteArrayOf(1), byteArrayOf(2)))!!
    return repository.automationStore.postCandidate(event, WECHAT_PACKAGE, parsed)
  }
}
