package com.sextant.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 表结构、索引与种子数据。
 *
 * Room 会在打开库时按导出的 schema（`android/app/schemas` 下的 JSON）校验实体与磁盘上
 * 实际结构是否一致，所以「迁移之后结构对不对」有一部分已经被运行时守住了。这里保留的
 * 断言是另一部分：schema 校验只看「实体声明的那些表」，看不出「本该存在的索引漏建了」
 * 或「种子数据没写进去」。
 *
 * 原始 SQL 通过 `openHelper.writableDatabase` 拿到底层连接：这些断言问的正是磁盘上的
 * 真相，走 DAO 反而绕远。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SextantDatabaseTest {
  private lateinit var context: Context
  private var database: SextantDatabase? = null

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    SextantDatabase.closeForTests()
    context.deleteDatabase(DATABASE_NAME)
  }

  @After
  fun tearDown() {
    database?.close()
    SextantDatabase.closeForTests()
    context.deleteDatabase(DATABASE_NAME)
  }

  @Test
  fun freshDatabaseHasCompleteSchemaIndexesAndSeedData() {
    val db = openCurrentDatabase()

    assertTrue(db.hasTable("categories"))
    assertTrue(db.hasTable("payment_methods"))
    assertTrue(db.hasTable("transactions"))
    assertTrue(db.hasTable("budgets"))
    assertTrue(db.hasTable("app_settings"))
    assertTrue(db.hasTable("automation_events"))
    assertTrue(db.hasTable("automation_diagnostics"))
    assertTrue(db.hasIndex("idx_diagnostics_created"))
    assertFalse(db.hasTable("classification_rules"))
    assertTrue(db.hasTable("recurring_expenses"))
    assertTrue(db.hasTable("merchant_category_rules"))
    assertTrue(db.hasColumn("transactions", "merchantName"))
    assertTrue(db.hasColumn("transactions", "amountCents"))
    assertTrue(db.hasColumn("transactions", "entryOrigin"))
    assertTrue(db.hasColumn("transactions", "automationKind"))
    assertTrue(db.hasColumn("transactions", "sourceEventId"))
    assertTrue(db.hasColumn("transactions", "isUserEdited"))
    assertTrue(db.hasColumn("categories", "parentCategoryId"))
    assertTrue(db.hasColumn("recurring_expenses", "customDays"))
    assertTrue(db.hasColumn("recurring_expenses", "anchorDate"))
    assertTrue(db.hasColumn("recurring_expenses", "endedAt"))
    assertTrue(db.hasIndex("idx_transactions_date"))
    assertTrue(db.hasIndex("idx_transactions_type_date"))
    assertTrue(db.hasIndex("idx_transactions_category_date"))
    assertTrue(db.hasIndex("idx_transactions_payment_date"))
    assertTrue(db.hasIndex("idx_transactions_source_event"))
    assertTrue(db.hasIndex("idx_recurring_expenses_active"))
    assertTrue(db.hasIndex("idx_automation_provider_reference"))
    assertTrue(db.hasIndex("idx_automation_status_created"))
    assertFalse(db.hasIndex("idx_classification_rules_match"))
    assertEquals(setOf("银行卡", "微信", "支付宝", "现金"), db.stringSet("SELECT name FROM payment_methods"))
    assertEquals(4L, db.longValue("SELECT COUNT(*) FROM payment_methods WHERE isBuiltIn = 1 AND isActive = 1"))
    assertTrue(db.longValue("SELECT COUNT(*) FROM categories") > 7)
  }

  @Test
  fun versionThirteenUpgradePreservesLedgerAndCreatesEmptyDiagnosticLog() {
    // Exact sqlite_master statements captured from the existing v13 emulator database.
    val schema = javaClass.getResource("/database/v13-device-schema.json")!!.readText()
    context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
      Json.decodeFromString<List<String>>(schema).forEach { db.execSQL(it) }
      db.execSQL("INSERT INTO transactions (id, amountCents, type, description, transactionDate, createdAt, updatedAt, merchantName) VALUES ('v13-row', 1234, 'expense', '保留备注', 10, 11, 12, '咖啡店')")
      db.execSQL("INSERT INTO app_settings VALUES ('automation_enabled', 'true')")
      db.version = 13
    }
    val db = openCurrentDatabase()
    assertEquals(SextantDatabase.DATABASE_VERSION, db.version)
    assertEquals(1234L, db.longValue("SELECT amountCents FROM transactions WHERE id = 'v13-row'"))
    assertEquals("咖啡店", db.stringValue("SELECT merchantName FROM transactions WHERE id = 'v13-row'"))
    assertEquals("true", db.stringValue("SELECT value FROM app_settings WHERE key = 'automation_enabled'"))
    assertTrue(db.hasIndex("idx_diagnostics_created"))
    assertEquals(0L, db.longValue("SELECT COUNT(*) FROM automation_diagnostics"))
  }

  @Test
  fun versionOneUpgradePreservesLedgerDataAndConvertsAmountsToCents() {
    createVersionOneDatabase()

    val db = openCurrentDatabase()

    assertFalse(db.hasTable("accounts"))
    assertTrue(db.hasColumn("categories", "parentCategoryId"))
    assertEquals("我的分类", db.stringValue("SELECT name FROM categories WHERE id = 'category-old'"))
    assertEquals("旧来源", db.stringValue("SELECT name FROM payment_methods WHERE id = 'source-old'"))
    assertEquals(1_234L, db.longValue("SELECT amountCents FROM transactions WHERE id = 'transaction-old'"))
    assertEquals("旧流水", db.stringValue("SELECT description FROM transactions WHERE id = 'transaction-old'"))
    assertEquals(56_789L, db.longValue("SELECT amountCents FROM budgets WHERE id = 'budget-old'"))
    assertTrue(db.hasTable("app_settings"))
    assertTrue(db.hasTable("automation_events"))
    assertFalse(db.hasTable("classification_rules"))
    assertTrue(db.hasTable("recurring_expenses"))
    assertEquals("manual", db.stringValue("SELECT entryOrigin FROM transactions WHERE id = 'transaction-old'"))
    assertTrue(db.hasIndex("idx_transactions_payment_date"))
  }

  /**
   * v10 是迁移前的最后一个版本。这条用例守的是「真机上真的升得上去」。
   *
   * 它曾经是过的——因为当时用的「v10 库」是手抄的，而手抄时把 `eventKey` 的
   * 列内联 UNIQUE 抄成了独立索引，正好和 Room 的期望一致。真实设备上的库不是那样，
   * 于是 v10 → v11 在真机抛「Migration didn't properly handle: automation_events」，
   * 而这里一路绿灯。修好之后这条用例会真实地走过 [SextantDatabase.MIGRATION_10_11]。
   */
  @Test
  fun versionTenUpgradeKeepsSchemaAndData() {
    createVersionTenDatabase()

    val db = openCurrentDatabase()

    assertTrue(db.hasTable("automation_events"))
    assertTrue(db.hasIndex("idx_automation_provider_reference"))
    // 迁移的核心：内联 UNIQUE 的自动索引要变成 Room 期望的那个独立索引。
    assertTrue(db.hasIndex("index_automation_events_eventKey"))
    assertEquals("十版分类", db.stringValue("SELECT name FROM categories WHERE id = 'category-v10'"))
    assertEquals(990L, db.longValue("SELECT amountCents FROM transactions WHERE id = 'transaction-v10'"))
    assertEquals("key-v10", db.stringValue("SELECT eventKey FROM automation_events WHERE id = 'event-v10'"))
    // 10 → 12 是连着两条迁移：索引对齐之后，分类规则表在第二步被丢掉。
    assertFalse(db.hasTable("classification_rules"))
  }

  /** 迁移不能让唯一约束失效：同一份 eventKey 仍然只能插一行。 */
  @Test
  fun versionTenUpgradeKeepsEventKeyUnique() {
    createVersionTenDatabase()

    val db = openCurrentDatabase()

    val error = org.junit.Assert.assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
      db.execSQL(
        "INSERT INTO automation_events (id, eventKey, sourcePackage, notificationKey, postedAt, payloadHash, status, createdAt) VALUES ('event-dup', 'key-v10', 'com.tencent.mm', 'n-dup', 8, 'h', 'captured', 8)",
      )
    }
    assertTrue(error.message.orEmpty().contains("UNIQUE", ignoreCase = true))
  }

  /**
   * v11 → v12：`classification_rules` 整张表被删掉，账本数据原样保留。
   *
   * 起点是真实的 v11 库——建表语句逐字抄自 `android/app/schemas/…/11.json` 的 `createSql`，
   * 也就是 Room 自己在 v11 上建出来的样子。手抄 schema 时「抄成我以为的样子」是这条用例
   * 唯一会骗人的方式（见上面 v10 那段）。
   */
  @Test
  fun versionElevenUpgradeDropsClassificationRulesAndKeepsLedgerData() {
    createVersionElevenDatabase()

    val db = openCurrentDatabase()

    assertFalse("分类规则表必须被删掉", db.hasTable("classification_rules"))
    assertFalse("它的索引不能留下", db.hasIndex("idx_classification_rules_match"))
    assertEquals("十一版分类", db.stringValue("SELECT name FROM categories WHERE id = 'category-v11'"))
    assertEquals(1_100L, db.longValue("SELECT amountCents FROM transactions WHERE id = 'transaction-v11'"))
    assertEquals("key-v11", db.stringValue("SELECT eventKey FROM automation_events WHERE id = 'event-v11'"))
    assertTrue(db.hasIndex("idx_transactions_source_event"))
  }

  /** Exact sqlite_master statements captured from the existing v12 emulator database. */
  @Test
  fun versionTwelveUpgradePreservesRowsAndCreatesEmptyMerchantRules() {
    val schema = javaClass.getResource("/database/v12-device-schema.json")!!.readText()
    context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
      Json.decodeFromString<List<String>>(schema).forEach { db.execSQL(it) }
      db.execSQL("INSERT INTO transactions (id, amountCents, type, description, transactionDate, createdAt, updatedAt) VALUES ('v12-row', 1234, 'expense', '旧备注不是商户', 10, 11, 12)")
      db.version = 12
    }
    val db = openCurrentDatabase()
    assertEquals(SextantDatabase.DATABASE_VERSION, db.version)
    assertTrue(db.hasTable("merchant_category_rules"))
    assertEquals(0L, db.longValue("SELECT COUNT(*) FROM merchant_category_rules"))
    assertEquals(1234L, db.longValue("SELECT amountCents FROM transactions WHERE id = 'v12-row'"))
    assertEquals("旧备注不是商户", db.stringValue("SELECT description FROM transactions WHERE id = 'v12-row'"))
    assertEquals(1L, db.longValue("SELECT COUNT(*) FROM transactions WHERE id = 'v12-row' AND merchantName IS NULL"))
    assertTrue(db.hasIndex("idx_transactions_source_event"))
  }

  private fun openCurrentDatabase(): SupportSQLiteDatabase {
    val opened = SextantDatabase.createForTests(context)
    database = opened
    return opened.openHelper.writableDatabase
  }

  private fun createVersionOneDatabase() {
    context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
      db.execSQL(
        "CREATE TABLE accounts (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, balance REAL NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT 'CNY', isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE categories (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, color INTEGER NOT NULL, includeInSpending INTEGER NOT NULL DEFAULT 1, isBuiltIn INTEGER NOT NULL DEFAULT 0, isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE payment_methods (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL DEFAULT 0, isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE transactions (id TEXT PRIMARY KEY NOT NULL, categoryId TEXT, paymentMethodId TEXT, amount REAL NOT NULL, type TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', transactionDate INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE budgets (id TEXT PRIMARY KEY NOT NULL, categoryId TEXT NOT NULL, amount REAL NOT NULL, period TEXT NOT NULL, startDate INTEGER NOT NULL, alertThreshold REAL NOT NULL DEFAULT 0.8, isActive INTEGER NOT NULL DEFAULT 1)",
      )
      db.execSQL("CREATE INDEX idx_transactions_date ON transactions(transactionDate)")
      db.execSQL("INSERT INTO accounts VALUES ('account-old', '旧账户', 'cash', 0, 'CNY', 1, 1, 1)")
      db.execSQL("INSERT INTO categories VALUES ('category-old', '我的分类', 'expense', 1, 1, 0, 1, 1)")
      db.execSQL("INSERT INTO payment_methods VALUES ('source-old', '旧来源', 2, 0, 1, 1)")
      db.execSQL("INSERT INTO transactions VALUES ('transaction-old', 'category-old', 'source-old', 12.34, 'expense', '旧流水', 10, 11, 12)")
      db.execSQL("INSERT INTO budgets VALUES ('budget-old', 'category-old', 567.89, 'month', 1, 0.8, 1)")
      db.version = 1
    }
  }

  /**
   * 一个真实的 v10 库：表结构和索引照抄 `SQLiteOpenHelper` 时代 `onCreate` 建出来的样子。
   *
   * **必须逐字复刻，尤其是 `eventKey` 那一列的写法。** v9 建表时用的是列内联 UNIQUE
   * （`eventKey TEXT NOT NULL UNIQUE`），SQLite 会为它生成名为
   * `sqlite_autoindex_automation_events_1` 的自动索引，而不是 Room 期望的
   * `index_automation_events_eventKey`。
   *
   * 这里曾经写成「`eventKey TEXT NOT NULL` + 一条 `CREATE UNIQUE INDEX
   * index_automation_events_eventKey`」——那是一个**并不存在**的库：结构和 Room 一致，
   * 于是 v10 → v11 的空迁移顺利通过测试，却在真机上抛
   * 「Migration didn't properly handle: automation_events」。
   *
   * 教训记在这里：手抄 schema 时抄成了「我以为它是什么样」而不是「它实际是什么样」，
   * 测试就只是在验证自己的假设。
   */
  private fun createVersionTenDatabase() {
    context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
      db.execSQL(
        "CREATE TABLE categories (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, color INTEGER NOT NULL, includeInSpending INTEGER NOT NULL DEFAULT 1, parentCategoryId TEXT, isBuiltIn INTEGER NOT NULL DEFAULT 0, isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)",
      )
      db.execSQL("CREATE TABLE app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
      db.execSQL(
        "CREATE TABLE payment_methods (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL DEFAULT 0, isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE transactions (id TEXT PRIMARY KEY NOT NULL, categoryId TEXT, paymentMethodId TEXT, amountCents INTEGER NOT NULL, type TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', transactionDate INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, entryOrigin TEXT NOT NULL DEFAULT 'manual', automationKind TEXT, sourceEventId TEXT, isUserEdited INTEGER NOT NULL DEFAULT 0)",
      )
      db.execSQL(
        "CREATE TABLE budgets (id TEXT PRIMARY KEY NOT NULL, categoryId TEXT NOT NULL, amountCents INTEGER NOT NULL, period TEXT NOT NULL, startDate INTEGER NOT NULL, alertThreshold REAL NOT NULL DEFAULT 0.8, isActive INTEGER NOT NULL DEFAULT 1)",
      )
      db.execSQL(
        "CREATE TABLE recurring_expenses (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, amountCents INTEGER NOT NULL, cycle TEXT NOT NULL, customDays INTEGER, anchorDate INTEGER NOT NULL, categoryId TEXT, paymentMethodId TEXT, endedAt INTEGER, isActive INTEGER NOT NULL DEFAULT 1, note TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
      )
      // 唯一约束写在列上，和 v9 的 `createAutomationSchema` 完全一致——这是本用例的重点。
      db.execSQL(
        "CREATE TABLE automation_events (id TEXT PRIMARY KEY NOT NULL, eventKey TEXT NOT NULL UNIQUE, sourcePackage TEXT NOT NULL, notificationKey TEXT NOT NULL, postedAt INTEGER NOT NULL, payloadHash TEXT NOT NULL, encryptedPayload BLOB, payloadIv BLOB, status TEXT NOT NULL, parser TEXT, providerReference TEXT, failureCode TEXT, parsedSummary TEXT, transactionId TEXT, createdAt INTEGER NOT NULL, processedAt INTEGER)",
      )
      db.execSQL(
        "CREATE TABLE classification_rules (id TEXT PRIMARY KEY NOT NULL, sourcePackage TEXT NOT NULL, direction TEXT NOT NULL, automationKind TEXT NOT NULL, matchMode TEXT NOT NULL, pattern TEXT NOT NULL, categoryId TEXT NOT NULL, priority INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
      )
      db.execSQL("CREATE INDEX idx_transactions_date ON transactions(transactionDate)")
      db.execSQL("CREATE INDEX idx_transactions_type_date ON transactions(type, transactionDate)")
      db.execSQL("CREATE INDEX idx_transactions_category_date ON transactions(categoryId, transactionDate)")
      db.execSQL("CREATE INDEX idx_transactions_payment_date ON transactions(paymentMethodId, transactionDate)")
      // 三处「和 Room 期望不一致」的细节都要照抄，否则这条用例又只是在验证自己的假设：
      // 部分索引的 WHERE、分类规则索引的 priority DESC（v12 会把那张表整个删掉，但 v10 的
      // 库里它就长这样）、以及上面 eventKey 的列内联 UNIQUE。
      db.execSQL("CREATE UNIQUE INDEX idx_transactions_source_event ON transactions(sourceEventId) WHERE sourceEventId IS NOT NULL")
      db.execSQL("CREATE INDEX idx_recurring_expenses_active ON recurring_expenses(isActive, anchorDate)")
      db.execSQL("CREATE UNIQUE INDEX idx_automation_provider_reference ON automation_events(sourcePackage, providerReference) WHERE providerReference IS NOT NULL")
      db.execSQL("CREATE INDEX idx_automation_status_created ON automation_events(status, createdAt)")
      db.execSQL("CREATE INDEX idx_classification_rules_match ON classification_rules(sourcePackage, direction, automationKind, priority DESC)")
      db.execSQL("INSERT INTO categories VALUES ('category-v10', '十版分类', 'expense', 1, 1, NULL, 0, 1, 1)")
      db.execSQL("INSERT INTO transactions (id, categoryId, amountCents, type, transactionDate, createdAt, updatedAt) VALUES ('transaction-v10', 'category-v10', 990, 'expense', 5, 5, 5)")
      db.execSQL("INSERT INTO automation_events (id, eventKey, sourcePackage, notificationKey, postedAt, payloadHash, status, createdAt) VALUES ('event-v10', 'key-v10', 'com.tencent.mm', 'n-v10', 7, 'hash-v10', 'captured', 7)")
      db.version = 10
    }
  }

  /**
   * 一个真实的 v11 库：建表语句逐字抄自 `android/app/schemas/…/11.json` 的 `createSql`。
   *
   * Room 是从 v11 开始接管 schema 的，所以真机上的 v11 库要么是新装时由这些语句建出来的，
   * 要么是从 v10 迁移过来、再被 Room 的 schema 校验放行的——两条路的结构一致，抄这一份
   * 就是抄磁盘上的真相。
   */
  private fun createVersionElevenDatabase() {
    context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
      db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `color` INTEGER NOT NULL, `includeInSpending` INTEGER NOT NULL DEFAULT 1, `parentCategoryId` TEXT, `isBuiltIn` INTEGER NOT NULL DEFAULT 0, `isActive` INTEGER NOT NULL DEFAULT 1, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
      db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `categoryId` TEXT, `paymentMethodId` TEXT, `amountCents` INTEGER NOT NULL, `type` TEXT NOT NULL, `description` TEXT NOT NULL DEFAULT '', `transactionDate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `entryOrigin` TEXT NOT NULL DEFAULT 'manual', `automationKind` TEXT, `sourceEventId` TEXT, `isUserEdited` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
      db.execSQL("CREATE INDEX IF NOT EXISTS `idx_transactions_date` ON `transactions` (`transactionDate`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `idx_transactions_type_date` ON `transactions` (`type`, `transactionDate`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `idx_transactions_category_date` ON `transactions` (`categoryId`, `transactionDate`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `idx_transactions_payment_date` ON `transactions` (`paymentMethodId`, `transactionDate`)")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_transactions_source_event` ON `transactions` (`sourceEventId`)")
      db.execSQL("CREATE TABLE IF NOT EXISTS `payment_methods` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL DEFAULT 0, `isActive` INTEGER NOT NULL DEFAULT 1, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
      db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `period` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `alertThreshold` REAL NOT NULL DEFAULT 0.8, `isActive` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`id`))")
      db.execSQL("CREATE TABLE IF NOT EXISTS `app_settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
      db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_expenses` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `cycle` TEXT NOT NULL, `customDays` INTEGER, `anchorDate` INTEGER NOT NULL, `categoryId` TEXT, `paymentMethodId` TEXT, `endedAt` INTEGER, `isActive` INTEGER NOT NULL DEFAULT 1, `note` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
      db.execSQL("CREATE INDEX IF NOT EXISTS `idx_recurring_expenses_active` ON `recurring_expenses` (`isActive`, `anchorDate`)")
      db.execSQL("CREATE TABLE IF NOT EXISTS `automation_events` (`id` TEXT NOT NULL, `eventKey` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `notificationKey` TEXT NOT NULL, `postedAt` INTEGER NOT NULL, `payloadHash` TEXT NOT NULL, `encryptedPayload` BLOB, `payloadIv` BLOB, `status` TEXT NOT NULL, `parser` TEXT, `providerReference` TEXT, `failureCode` TEXT, `parsedSummary` TEXT, `transactionId` TEXT, `createdAt` INTEGER NOT NULL, `processedAt` INTEGER, PRIMARY KEY(`id`))")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_automation_events_eventKey` ON `automation_events` (`eventKey`)")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_automation_provider_reference` ON `automation_events` (`sourcePackage`, `providerReference`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `idx_automation_status_created` ON `automation_events` (`status`, `createdAt`)")
      db.execSQL("CREATE TABLE IF NOT EXISTS `classification_rules` (`id` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `direction` TEXT NOT NULL, `automationKind` TEXT NOT NULL, `matchMode` TEXT NOT NULL, `pattern` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `priority` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
      db.execSQL("CREATE INDEX IF NOT EXISTS `idx_classification_rules_match` ON `classification_rules` (`sourcePackage`, `direction`, `automationKind`, `priority`)")
      db.execSQL("INSERT INTO categories VALUES ('category-v11', '十一版分类', 'expense', 1, 1, NULL, 0, 1, 1)")
      db.execSQL("INSERT INTO transactions (id, categoryId, amountCents, type, transactionDate, createdAt, updatedAt) VALUES ('transaction-v11', 'category-v11', 1100, 'expense', 5, 5, 5)")
      db.execSQL("INSERT INTO automation_events (id, eventKey, sourcePackage, notificationKey, postedAt, payloadHash, status, createdAt) VALUES ('event-v11', 'key-v11', 'com.tencent.mm', 'n-v11', 7, 'hash-v11', 'captured', 7)")
      db.execSQL("INSERT INTO classification_rules VALUES ('rule-v11', 'com.tencent.mm', 'expense', 'purchase', 'contains', '咖啡', 'category-v11', 100, 9, 9)")
      db.version = 11
    }
  }

  private fun SupportSQLiteDatabase.hasTable(name: String): Boolean =
    read("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", name).use { it.moveToFirst() }

  private fun SupportSQLiteDatabase.hasIndex(name: String): Boolean =
    read("SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?", name).use { it.moveToFirst() }

  private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    read("PRAGMA table_info($table)").use { cursor ->
      while (cursor.moveToNext()) {
        if (cursor.getString(1) == column) return@use true
      }
      false
    }

  private fun SupportSQLiteDatabase.stringSet(sql: String): Set<String> = read(sql).use { cursor ->
    buildSet {
      while (cursor.moveToNext()) add(cursor.getString(0))
    }
  }

  private fun SupportSQLiteDatabase.stringValue(sql: String): String = read(sql).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getString(0)
  }

  private fun SupportSQLiteDatabase.longValue(sql: String): Long = read(sql).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getLong(0)
  }

  /**
   * 直接问底层 SQLite。
   *
   * 这些断言问的正是磁盘上的真相（表、索引、种子行），走 DAO 反而绕远——
   * 而且 DAO 看不到「索引漏建了」这类问题。
   */
  private fun SupportSQLiteDatabase.read(sql: String, vararg args: Any?): Cursor =
    query(SimpleSQLiteQuery(sql, args))

  private companion object {
    const val DATABASE_NAME = "sextant.db"
  }
}
