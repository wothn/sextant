package com.sextant.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Sextant 的本地库。
 *
 * Room 负责建表、版本管理和 schema 校验；[DATABASE_VERSION] 与 `android/app/schemas`
 * 下导出的 JSON 是表结构的权威记录。改实体字段时，除了抬版本号，还要补一条
 * [MIGRATION_10_11] 那样的迁移，并在 `SextantDatabaseTest` 里覆盖。
 *
 * 版本号沿用 `SQLiteOpenHelper` 时代的历史：v10 是迁移前的最后一个版本，表和列都和 v11
 * 一致，差的只有索引——Room 接管的是同一份 schema，没有重建任何表。v10 的老库走
 * [MIGRATION_10_11]（按 Room 的期望重建三条索引，见下），更老的库走 [MIGRATION_LEGACY]，
 * 它重放原来 `onUpgrade` 里的分支，把 v1 一路补到当前结构。
 */
@Database(
  entities = [
    CategoryEntity::class,
    TransactionEntity::class,
    PaymentMethodEntity::class,
    BudgetEntity::class,
    AppSettingEntity::class,
    RecurringExpenseEntity::class,
    AutomationEventEntity::class,
    ClassificationRuleEntity::class,
  ],
  version = SextantDatabase.DATABASE_VERSION,
  exportSchema = true,
)
@TypeConverters(SextantTypeConverters::class)
abstract class SextantDatabase : RoomDatabase() {
  internal abstract fun categoryDao(): CategoryDao
  internal abstract fun transactionDao(): TransactionDao
  internal abstract fun paymentMethodDao(): PaymentMethodDao
  internal abstract fun budgetDao(): BudgetDao
  internal abstract fun appSettingDao(): AppSettingDao
  internal abstract fun recurringExpenseDao(): RecurringExpenseDao
  internal abstract fun automationEventDao(): AutomationEventDao
  internal abstract fun classificationRuleDao(): ClassificationRuleDao

  companion object {
    const val DATABASE_VERSION = 11

    private const val DATABASE_NAME = "sextant.db"
    const val THEME_PREFERENCE_KEY = "theme_preference"
    const val MONTHLY_BUDGET_KEY = "monthly_budget_cents"

    @Volatile
    private var instance: SextantDatabase? = null

    /**
     * 进程内单例。
     *
     * Room 实例本身线程安全，但每次 `build()` 都会新开一套连接池；清单页、监听服务和
     * Worker 各建一个的话，写入会互相等待。双检锁保证只建一次。
     *
     * 测试里不要用它：测试有自己的 `@Before` 清理逻辑，需要能反复重建实例。
     */
    fun get(context: Context): SextantDatabase = instance ?: synchronized(this) {
      instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(context: Context): SextantDatabase = Room
      .databaseBuilder(context, SextantDatabase::class.java, DATABASE_NAME)
      .addMigrations(MIGRATION_10_11, MIGRATION_LEGACY)
      .addCallback(SeedCallback)
      // Room 的 AUTOMATIC 在真机上本来就会选 WAL，但那是运行时的选择：显式写下来，
      // 「监听服务写流水的同时页面在读书、两边不互相阻塞」才是这个库的既定行为。
      .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
      .build()

    /**
     * 建一个测试用的独立实例。
     *
     * 测试需要能反复重建数据库（每个用例都从干净状态开始），所以这里绕开 [get] 的单例，
     * 并且允许在主线程上直接查询——Robolectric 的测试线程就是主线程，而 Room 的线程检查
     * 在生产环境里是有价值的护栏，在单线程测试里只会逼出满屏 `runBlocking`。
     */
    internal fun createForTests(context: Context, name: String = DATABASE_NAME): SextantDatabase =
      Room.databaseBuilder(context.applicationContext, SextantDatabase::class.java, name)
        .addMigrations(MIGRATION_10_11, MIGRATION_LEGACY)
        .addCallback(SeedCallback)
        .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
        .allowMainThreadQueries()
        .build()

    /**
     * 忘掉单例并关掉它。
     *
     * 测试之间必须真的断开连接：`deleteDatabase` 删掉的是文件，已打开的连接仍然握着删掉的
     * inode，后续写入会落进一个再也看不见的库里。下一个用例重新打开同名文件时拿到的是空库，
     * 而上一个用例的连接还在往旧 inode 写——数据看起来「串」到了别的用例里。
     */
    internal fun closeForTests() {
      synchronized(this) {
        instance?.close()
        instance = null
      }
    }

    /**
     * v10 → v11：把 v9 建出来的索引对齐 Room 由实体生成的那一套。
     *
     * v10 的表结构和 v11 **不是**完全一致，差的全在索引上，而且只有真机升级才会暴露——
     * 新装的库走 `onCreate`，由 Room 自己建索引，一切正常。三处差异：
     *
     * 1. `automation_events.eventKey` 在 v9 是**列内联 UNIQUE**，SQLite 为它生成
     *    `sqlite_autoindex_automation_events_1`；实体声明的是一个叫
     *    `index_automation_events_eventKey` 的独立索引。两者语义等价，但 Room 按名字比对
     *    索引集合，对不上就拒绝打开库。改名来回一次会让约束的自动索引被重建，随后新建
     *    同名索引就能真正插进去。
     * 2. `idx_classification_rules_match` 当年写的是 `priority DESC`，实体上的 `Index`
     *    声明不出排序方向，Room 生成的是升序。
     * 3. `idx_transactions_source_event` 与 `idx_automation_provider_reference` 当年是
     *    **部分索引**（带 `WHERE … IS NOT NULL`）。`Index` 也表达不了谓词，Room 期望的是
     *    无谓词的版本。
     *
     * 这三条都不是「结构其实一样、只是校验太严」：Room 的校验逐项比对列序、排序方向和
     * 谓词，而它无法表达的东西一律按「没有」来要求。既然表结构由实体定义，索引就以实体
     * 为准——老库这边按 Room 的期望重建。
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE automation_events RENAME COLUMN eventKey TO eventKey_legacy")
        db.execSQL("ALTER TABLE automation_events RENAME COLUMN eventKey_legacy TO eventKey")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_automation_events_eventKey ON automation_events(eventKey)")
        recreateIndex(
          db = db,
          table = "classification_rules",
          name = "idx_classification_rules_match",
          columns = "sourcePackage, direction, automationKind, priority",
        )
        recreateIndex(
          db = db,
          table = "transactions",
          name = "idx_transactions_source_event",
          columns = "sourceEventId",
          unique = true,
        )
        recreateIndex(
          db = db,
          table = "automation_events",
          name = "idx_automation_provider_reference",
          columns = "sourcePackage, providerReference",
          unique = true,
        )
      }
    }

    /**
     * 按给定的列序/排序重建一个索引：先删掉老的定义（如果有），再照 Room 的期望建。
     *
     * 表名显式传进来，不从索引名反推。索引名里带下划线（`idx_classification_rules_match`），
     * 想靠前缀加第一个下划线切出表名只会得到 `classification`——「按命名约定猜」这种省一个
     * 参数的写法，代价是多一个只在特定命名下才成立的隐式约定。
     */
    private fun recreateIndex(
      db: SupportSQLiteDatabase,
      table: String,
      name: String,
      columns: String,
      unique: Boolean = false,
    ) {
      val exists = db.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
        arrayOf<Any>(name),
      ).use { it.moveToFirst() }
      if (exists) db.execSQL("DROP INDEX $name")
      db.execSQL("CREATE ${if (unique) "UNIQUE " else ""}INDEX IF NOT EXISTS $name ON $table($columns)")
    }

    /**
     * `transactions` / `budgets` 的当前建表语句，只给 [MIGRATION_LEGACY] 重建旧表用。
     *
     * 必须和 Room 由实体生成的那份逐字一致（列顺序无关，但列名、类型、NOT NULL、默认值都
     * 要对得上），否则迁移之后的 schema 校验会失败。测试里的 `freshDatabaseHasCompleteSchema…`
     * 和 `versionOneUpgrade…` 两条用例分别盯着这两条路径，改动其中一个就必须同时改另一个。
     */
    private val TRANSACTIONS_TABLE = """
      CREATE TABLE IF NOT EXISTS transactions (
        id TEXT PRIMARY KEY NOT NULL,
        categoryId TEXT,
        paymentMethodId TEXT,
        amountCents INTEGER NOT NULL,
        type TEXT NOT NULL,
        description TEXT NOT NULL DEFAULT '',
        transactionDate INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        entryOrigin TEXT NOT NULL DEFAULT 'manual',
        automationKind TEXT,
        sourceEventId TEXT,
        isUserEdited INTEGER NOT NULL DEFAULT 0
      )
    """.trimIndent()

    private val BUDGETS_TABLE = """
      CREATE TABLE IF NOT EXISTS budgets (
        id TEXT PRIMARY KEY NOT NULL,
        categoryId TEXT NOT NULL,
        amountCents INTEGER NOT NULL,
        period TEXT NOT NULL,
        startDate INTEGER NOT NULL,
        alertThreshold REAL NOT NULL DEFAULT 0.8,
        isActive INTEGER NOT NULL DEFAULT 1
      )
    """.trimIndent()

    /**
     * 任意旧版本 → 11：重放 `SQLiteOpenHelper` 时代的 `onUpgrade` 分支，再补齐后续版本
     * 才引入的表。
     *
     * 加列走 [addColumnIfMissing]（先 `PRAGMA table_info` 判断），而不是无脑 `ALTER TABLE
     * ADD COLUMN`：这段迁移的起点只保证「小于 10」，不保证具体是哪一版——v1 的
     * `categories` 没有 `parentCategoryId`，v9 的已经有；无脑 `ALTER` 会在后者上直接失败，
     * 把用户挡在升级流程外面。建表同理，一律 `IF NOT EXISTS`。
     *
     * v1/v2 的 `accounts` 表在 v3 就被删掉了，这里照样删一次，保证中间状态不残留。
     *
     * **重建两步必须排在补列之前**：`hadAmountCents` 问的是「用户手里的库原本有没有金额列」，
     * 一旦我们先补上这一列，它就会恒为 true，v1 的库会带着一堆 0 通过迁移——而 Room 的
     * schema 校验只看结构不看数据，这种错会一路静默到用户打开账本。
     */
    val MIGRATION_LEGACY = object : Migration(1, DATABASE_VERSION) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS accounts")
        addColumnIfMissing(db, "categories", "parentCategoryId", "TEXT")
        db.execSQL(
          "UPDATE categories SET color = ? WHERE isBuiltIn = 1 AND color = ?",
          arrayOf<Any>(0xFFF97316.toInt(), 0xFFAB47BC.toInt()),
        )
        db.execSQL(
          "UPDATE categories SET color = ? WHERE isBuiltIn = 1 AND color = ?",
          arrayOf<Any>(0xFF0F9F9A.toInt(), 0xFF5C6BC0.toInt()),
        )
        addColumnIfMissing(db, "transactions", "entryOrigin", "TEXT NOT NULL DEFAULT 'manual'")
        addColumnIfMissing(db, "transactions", "automationKind", "TEXT")
        addColumnIfMissing(db, "transactions", "sourceEventId", "TEXT")
        addColumnIfMissing(db, "transactions", "isUserEdited", "INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        createRecurringSchema(db)
        createAutomationSchema(db)
        // 早期版本遗留的 `amount` 列在历史迁移里只是「不再使用」，没有真的删掉。Room 之后会
        // 拿实体和磁盘结构逐列对比，多出来的列会让它判定「迁移没做对」并拒绝打开库，
        // 所以这两张表要重建一次：结构对齐当前实体，金额换算也在这里一次做完。
        //
        // 重建必须排在所有 `addColumnIfMissing` 之后（否则 SELECT 会引用还不存在的列），
        // 而且「原本有没有金额列」要在补列之前就问清楚——补完再问必然为真，v1 的库会带着
        // 一堆 0 通过迁移，而 Room 的 schema 校验只看结构不看数据，这种错会一路静默到
        // 用户打开账本。
        val transactionsHadCents = db.hasColumn("transactions", "amountCents")
        val budgetsHadCents = db.hasColumn("budgets", "amountCents")
        rebuildTransactions(db, convertAmounts = !transactionsHadCents)
        rebuildBudgets(db, convertAmounts = !budgetsHadCents)

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transactionDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_type_date ON transactions(type, transactionDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_category_date ON transactions(categoryId, transactionDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_payment_date ON transactions(paymentMethodId, transactionDate)")
        db.execSQL(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_source_event
          ON transactions(sourceEventId)
          """.trimIndent(),
        )
        // 这里不补种默认分类：v1 库自己带着用户改过的分类，凭空补一批反而会和它们重名。
        // 各版本的默认分类由原来的迁移分支负责，到 v6 就已经补齐了。
      }
    }

    /**
     * 重建 `transactions`：对齐当前实体（丢掉 `amount`），并在旧库缺 `amountCents` 时
     * 按 `amount × 100` 一次性换算。
     *
     * 换算只发生在 [convertAmounts] 为真（也就是 `amountCents` 原本根本不存在）的时候：
     * v7 之后的库里金额确实可能是 0（比如一笔 0 元的手续费），重算虽然值一样，
     * 但没必要去覆写用户的数据。
     */
    private fun rebuildTransactions(db: SupportSQLiteDatabase, convertAmounts: Boolean) {
      db.execSQL("ALTER TABLE transactions RENAME TO transactions_legacy")
      db.execSQL(TRANSACTIONS_TABLE)
      db.execSQL(
        """
        INSERT INTO transactions
          (id, categoryId, paymentMethodId, amountCents, type, description, transactionDate,
           createdAt, updatedAt, entryOrigin, automationKind, sourceEventId, isUserEdited)
        SELECT id, categoryId, paymentMethodId, ${if (convertAmounts) "CAST(ROUND(amount * 100) AS INTEGER)" else "amountCents"},
          type, description, transactionDate, createdAt, updatedAt, entryOrigin, automationKind,
          sourceEventId, isUserEdited
        FROM transactions_legacy
        """.trimIndent(),
      )
      db.execSQL("DROP TABLE transactions_legacy")
    }

    /** 重建 `budgets`：同样对齐当前实体，并在旧库缺 `amountCents` 时按 `amount × 100` 换算。 */
    private fun rebuildBudgets(db: SupportSQLiteDatabase, convertAmounts: Boolean) {
      db.execSQL("ALTER TABLE budgets RENAME TO budgets_legacy")
      db.execSQL(BUDGETS_TABLE)
      db.execSQL(
        """
        INSERT INTO budgets (id, categoryId, amountCents, period, startDate, alertThreshold, isActive)
        SELECT id, categoryId, ${if (convertAmounts) "CAST(ROUND(amount * 100) AS INTEGER)" else "amountCents"},
          period, startDate, alertThreshold, isActive
        FROM budgets_legacy
        """.trimIndent(),
      )
      db.execSQL("DROP TABLE budgets_legacy")
    }

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
      query("PRAGMA table_info($table)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        var found = false
        while (cursor.moveToNext()) {
          if (cursor.getString(nameIndex) == column) {
            found = true
            break
          }
        }
        found
      }

    private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, definition: String) {
      if (!db.hasColumn(table, column)) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }

    private fun createRecurringSchema(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS recurring_expenses (
          id TEXT PRIMARY KEY NOT NULL,
          name TEXT NOT NULL,
          amountCents INTEGER NOT NULL,
          cycle TEXT NOT NULL,
          customDays INTEGER,
          anchorDate INTEGER NOT NULL,
          categoryId TEXT,
          paymentMethodId TEXT,
          endedAt INTEGER,
          isActive INTEGER NOT NULL DEFAULT 1,
          note TEXT NOT NULL DEFAULT '',
          createdAt INTEGER NOT NULL,
          updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS idx_recurring_expenses_active ON recurring_expenses(isActive, anchorDate)")
    }

    private fun createAutomationSchema(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS automation_events (
          id TEXT PRIMARY KEY NOT NULL,
          eventKey TEXT NOT NULL,
          sourcePackage TEXT NOT NULL,
          notificationKey TEXT NOT NULL,
          postedAt INTEGER NOT NULL,
          payloadHash TEXT NOT NULL,
          encryptedPayload BLOB,
          payloadIv BLOB,
          status TEXT NOT NULL,
          parser TEXT,
          providerReference TEXT,
          failureCode TEXT,
          parsedSummary TEXT,
          transactionId TEXT,
          createdAt INTEGER NOT NULL,
          processedAt INTEGER
        )
        """.trimIndent(),
      )
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS classification_rules (
          id TEXT PRIMARY KEY NOT NULL,
          sourcePackage TEXT NOT NULL,
          direction TEXT NOT NULL,
          automationKind TEXT NOT NULL,
          matchMode TEXT NOT NULL,
          pattern TEXT NOT NULL,
          categoryId TEXT NOT NULL,
          priority INTEGER NOT NULL DEFAULT 0,
          createdAt INTEGER NOT NULL,
          updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
      )
      // 这三条索引的列序与排序方向必须和实体上 `Index` 生成的完全一致：`Index` 表达不出
      // DESC，也表达不出部分索引的谓词，所以 Room 一律按「无排序方向、无谓词」来要求。
      // 迁移里 [MIGRATION_10_11] 会把老库上带 DESC / WHERE 的版本照这个样子重建。
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_automation_events_eventKey ON automation_events(eventKey)")
      db.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS idx_automation_provider_reference
        ON automation_events(sourcePackage, providerReference)
        """.trimIndent(),
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS idx_automation_status_created ON automation_events(status, createdAt)")
      db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS idx_classification_rules_match
        ON classification_rules(sourcePackage, direction, automationKind, priority)
        """.trimIndent(),
      )
    }

    /**
     * 首次建库时的种子数据。
     *
     * 直接写 SQL 而不是走 DAO：这个回调在数据库自己的初始化流程里执行，此时再取
     * `SextantDatabase` 实例会重入 `build()`。种子数据是「表建立后的固定内容」，
     * 用 SQL 表达反而更直白。
     *
     * Room 的 `onCreate` 已经包在事务里，所以这里不再自己开事务。
     */
    private object SeedCallback : Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val categoryIds = mutableMapOf<Pair<String, String>, String>()
        val now = System.currentTimeMillis()

        defaultCategories.forEachIndexed { index, item ->
          val parentId = item.parentName?.let { parentName ->
            categoryIds[parentName to item.type] ?: findCategoryId(db, parentName, item.type, null)
          }
          val existingId = findCategoryId(db, item.name, item.type, parentId)
          if (existingId != null) {
            categoryIds[item.name to item.type] = existingId
            return@forEachIndexed
          }
          val categoryId = UUID.randomUUID().toString()
          db.execSQL(
            """
            INSERT INTO categories
              (id, name, type, color, includeInSpending, parentCategoryId, isBuiltIn, isActive, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, 1, 1, ?)
            """.trimIndent(),
            arrayOf<Any?>(
              categoryId,
              item.name,
              item.type,
              item.color.toLong(),
              item.includeInSpending,
              parentId,
              now + index,
            ),
          )
          categoryIds[item.name to item.type] = categoryId
        }

        defaultPaymentMethods.forEach { item ->
          db.execSQL(
            "INSERT INTO payment_methods (id, name, color, isBuiltIn, isActive, createdAt) VALUES (?, ?, ?, 1, 1, ?)",
            arrayOf<Any>(UUID.randomUUID().toString(), item.name, item.color.toLong(), now),
          )
        }
      }

      private fun findCategoryId(
        db: SupportSQLiteDatabase,
        name: String,
        type: String,
        parentCategoryId: String?,
      ): String? {
        val sql = if (parentCategoryId == null) {
          "SELECT id FROM categories WHERE name = ? AND type = ? AND parentCategoryId IS NULL LIMIT 1"
        } else {
          "SELECT id FROM categories WHERE name = ? AND type = ? AND parentCategoryId = ? LIMIT 1"
        }
        val args = if (parentCategoryId == null) arrayOf(name, type) else arrayOf(name, type, parentCategoryId)
        db.query(sql, args).use { cursor ->
          return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
      }
    }
  }
}

private data class SeedCategory(
  val name: String,
  val type: String,
  val color: Int,
  val includeInSpending: Int,
  val parentName: String? = null,
)

private data class SeedPaymentMethod(
  val name: String,
  val color: Int,
)

private val defaultCategories = listOf(
  SeedCategory("餐饮", "expense", 0xFFFF7043.toInt(), 1),
  SeedCategory("正餐", "expense", 0xFFFF7043.toInt(), 1, "餐饮"),
  SeedCategory("奶茶咖啡", "expense", 0xFFFF7043.toInt(), 1, "餐饮"),
  SeedCategory("零食水果", "expense", 0xFFFF7043.toInt(), 1, "餐饮"),
  SeedCategory("聚餐外卖", "expense", 0xFFFF7043.toInt(), 1, "餐饮"),
  SeedCategory("交通", "expense", 0xFF42A5F5.toInt(), 1),
  SeedCategory("公交地铁", "expense", 0xFF42A5F5.toInt(), 1, "交通"),
  SeedCategory("打车", "expense", 0xFF42A5F5.toInt(), 1, "交通"),
  SeedCategory("油费充电", "expense", 0xFF42A5F5.toInt(), 1, "交通"),
  SeedCategory("停车过路", "expense", 0xFF42A5F5.toInt(), 1, "交通"),
  SeedCategory("火车机票", "expense", 0xFF42A5F5.toInt(), 1, "交通"),
  SeedCategory("购物", "expense", 0xFFF97316.toInt(), 1),
  SeedCategory("日用品", "expense", 0xFFF97316.toInt(), 1, "购物"),
  SeedCategory("服饰鞋包", "expense", 0xFFF97316.toInt(), 1, "购物"),
  SeedCategory("数码家电", "expense", 0xFFF97316.toInt(), 1, "购物"),
  SeedCategory("美妆个护", "expense", 0xFFF97316.toInt(), 1, "购物"),
  SeedCategory("礼物", "expense", 0xFFF97316.toInt(), 1, "购物"),
  SeedCategory("住房", "expense", 0xFFD97706.toInt(), 1),
  SeedCategory("房租房贷", "expense", 0xFFD97706.toInt(), 1, "住房"),
  SeedCategory("水电燃气", "expense", 0xFFD97706.toInt(), 1, "住房"),
  SeedCategory("物业维修", "expense", 0xFFD97706.toInt(), 1, "住房"),
  SeedCategory("家居家装", "expense", 0xFFD97706.toInt(), 1, "住房"),
  SeedCategory("生活服务", "expense", 0xFF26A69A.toInt(), 1),
  SeedCategory("通讯网络", "expense", 0xFF26A69A.toInt(), 1, "生活服务"),
  SeedCategory("医疗药品", "expense", 0xFF26A69A.toInt(), 1, "生活服务"),
  SeedCategory("理发护理", "expense", 0xFF26A69A.toInt(), 1, "生活服务"),
  SeedCategory("快递洗衣", "expense", 0xFF26A69A.toInt(), 1, "生活服务"),
  SeedCategory("娱乐休闲", "expense", 0xFFAB47BC.toInt(), 1),
  SeedCategory("电影演出", "expense", 0xFFAB47BC.toInt(), 1, "娱乐休闲"),
  SeedCategory("游戏会员", "expense", 0xFFAB47BC.toInt(), 1, "娱乐休闲"),
  SeedCategory("运动健身", "expense", 0xFFAB47BC.toInt(), 1, "娱乐休闲"),
  SeedCategory("旅行", "expense", 0xFFAB47BC.toInt(), 1, "娱乐休闲"),
  SeedCategory("学习成长", "expense", 0xFF5C6BC0.toInt(), 1),
  SeedCategory("课程培训", "expense", 0xFF5C6BC0.toInt(), 1, "学习成长"),
  SeedCategory("书籍资料", "expense", 0xFF5C6BC0.toInt(), 1, "学习成长"),
  SeedCategory("考试证书", "expense", 0xFF5C6BC0.toInt(), 1, "学习成长"),
  SeedCategory("人情往来", "expense", 0xFFEC407A.toInt(), 1),
  SeedCategory("红包礼金", "expense", 0xFFEC407A.toInt(), 1, "人情往来"),
  SeedCategory("请客送礼", "expense", 0xFFEC407A.toInt(), 1, "人情往来"),
  SeedCategory("家庭支出", "expense", 0xFFEC407A.toInt(), 1, "人情往来"),
  SeedCategory("工资", "income", 0xFF66BB6A.toInt(), 1),
  SeedCategory("基本工资", "income", 0xFF66BB6A.toInt(), 1, "工资"),
  SeedCategory("奖金补贴", "income", 0xFF66BB6A.toInt(), 1, "工资"),
  SeedCategory("报销", "income", 0xFF66BB6A.toInt(), 1, "工资"),
  SeedCategory("兼职", "income", 0xFF26A69A.toInt(), 1),
  SeedCategory("副业", "income", 0xFF26A69A.toInt(), 1, "兼职"),
  SeedCategory("项目收入", "income", 0xFF26A69A.toInt(), 1, "兼职"),
  SeedCategory("投资收益", "income", 0xFF0F9F9A.toInt(), 1),
  SeedCategory("利息", "income", 0xFF0F9F9A.toInt(), 1, "投资收益"),
  SeedCategory("分红", "income", 0xFF0F9F9A.toInt(), 1, "投资收益"),
  SeedCategory("基金股票", "income", 0xFF0F9F9A.toInt(), 1, "投资收益"),
  SeedCategory("其他收入", "income", 0xFF42A5F5.toInt(), 1),
  SeedCategory("礼金红包", "income", 0xFF42A5F5.toInt(), 1, "其他收入"),
  SeedCategory("退款返现", "income", 0xFF42A5F5.toInt(), 1, "其他收入"),
  SeedCategory("基金定投", "expense", 0xFF0F9F9A.toInt(), 0),
  SeedCategory("基金", "expense", 0xFF0F9F9A.toInt(), 0, "基金定投"),
  SeedCategory("股票", "expense", 0xFF0F9F9A.toInt(), 0, "基金定投"),
  SeedCategory("养老金", "expense", 0xFF0F9F9A.toInt(), 0, "基金定投"),
  SeedCategory("储蓄", "expense", 0xFF26A69A.toInt(), 0),
  SeedCategory("应急金", "expense", 0xFF26A69A.toInt(), 0, "储蓄"),
  SeedCategory("目标储蓄", "expense", 0xFF26A69A.toInt(), 0, "储蓄"),
  SeedCategory("还款", "expense", 0xFFEF5350.toInt(), 0),
  SeedCategory("信用卡还款", "expense", 0xFFEF5350.toInt(), 0, "还款"),
  SeedCategory("贷款还款", "expense", 0xFFEF5350.toInt(), 0, "还款"),
)

private val defaultPaymentMethods = listOf(
  SeedPaymentMethod("银行卡", 0xFF2563EB.toInt()),
  SeedPaymentMethod("微信", 0xFF1F9D66.toInt()),
  SeedPaymentMethod("支付宝", 0xFF0F9F9A.toInt()),
  SeedPaymentMethod("现金", 0xFFD97706.toInt()),
)
