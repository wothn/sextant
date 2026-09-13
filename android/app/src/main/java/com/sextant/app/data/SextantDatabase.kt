package com.sextant.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class SextantDatabase(context: Context) : SQLiteOpenHelper(context, "sextant.db", null, DATABASE_VERSION) {
  companion object {
    const val DATABASE_VERSION = 10
  }
  init {
    setWriteAheadLoggingEnabled(true)
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE categories (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        type TEXT NOT NULL,
        color INTEGER NOT NULL,
        includeInSpending INTEGER NOT NULL DEFAULT 1,
        parentCategoryId TEXT,
        isBuiltIn INTEGER NOT NULL DEFAULT 0,
        isActive INTEGER NOT NULL DEFAULT 1,
        createdAt INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE app_settings (
        key TEXT PRIMARY KEY NOT NULL,
        value TEXT NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE payment_methods (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        color INTEGER NOT NULL,
        isBuiltIn INTEGER NOT NULL DEFAULT 0,
        isActive INTEGER NOT NULL DEFAULT 1,
        createdAt INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE transactions (
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
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE budgets (
        id TEXT PRIMARY KEY NOT NULL,
        categoryId TEXT NOT NULL,
        amountCents INTEGER NOT NULL,
        period TEXT NOT NULL,
        startDate INTEGER NOT NULL,
        alertThreshold REAL NOT NULL DEFAULT 0.8,
        isActive INTEGER NOT NULL DEFAULT 1
      )
      """.trimIndent(),
    )
    db.execSQL("CREATE INDEX idx_transactions_date ON transactions(transactionDate)")
    db.execSQL("CREATE INDEX idx_transactions_type_date ON transactions(type, transactionDate)")
    db.execSQL("CREATE INDEX idx_transactions_category_date ON transactions(categoryId, transactionDate)")
    db.execSQL("CREATE INDEX idx_transactions_payment_date ON transactions(paymentMethodId, transactionDate)")
    createRecurringSchema(db)
    createAutomationSchema(db)
    seedDefaults(db)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 3) {
      db.execSQL("DROP TABLE IF EXISTS accounts")
    }
    if (oldVersion < 4) {
      db.execSQL("ALTER TABLE categories ADD COLUMN parentCategoryId TEXT")
    }
    if (oldVersion < 5) {
      db.execSQL(
        "UPDATE categories SET color = ? WHERE isBuiltIn = 1 AND color = ?",
        arrayOf<Any>(0xFFF97316.toInt(), 0xFFAB47BC.toInt()),
      )
      db.execSQL(
        "UPDATE categories SET color = ? WHERE isBuiltIn = 1 AND color = ?",
        arrayOf<Any>(0xFF0F9F9A.toInt(), 0xFF5C6BC0.toInt()),
      )
    }
    if (oldVersion < 6) {
      seedDefaultCategories(db)
    }
    if (oldVersion < 7) {
      db.execSQL("ALTER TABLE transactions ADD COLUMN amountCents INTEGER NOT NULL DEFAULT 0")
      db.execSQL("UPDATE transactions SET amountCents = CAST(ROUND(amount * 100) AS INTEGER)")
      db.execSQL("ALTER TABLE budgets ADD COLUMN amountCents INTEGER NOT NULL DEFAULT 0")
      db.execSQL("UPDATE budgets SET amountCents = CAST(ROUND(amount * 100) AS INTEGER)")
      db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_type_date ON transactions(type, transactionDate)")
      db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_category_date ON transactions(categoryId, transactionDate)")
      db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_payment_date ON transactions(paymentMethodId, transactionDate)")
    }
    if (oldVersion < 8) {
      db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
    }
    if (oldVersion < 9) {
      db.execSQL("ALTER TABLE transactions ADD COLUMN entryOrigin TEXT NOT NULL DEFAULT 'manual'")
      db.execSQL("ALTER TABLE transactions ADD COLUMN automationKind TEXT")
      db.execSQL("ALTER TABLE transactions ADD COLUMN sourceEventId TEXT")
      db.execSQL("ALTER TABLE transactions ADD COLUMN isUserEdited INTEGER NOT NULL DEFAULT 0")
      createAutomationSchema(db)
    }
    if (oldVersion < 10) {
      createRecurringSchema(db)
    }
  }

  /**
   * 周期支出（房租、会员、宽带这类固定节奏的金额）。
   *
   * 它不参与流水统计，只是「计划」：真实扣款仍然记成普通流水。因此这里没有外键，
   * 也不触发任何账本重算——`categoryId` / `paymentMethodId` 是弱引用，指向已被归档的
   * 分类或来源时仍然可用，只是在展示上退化成「未分类」。
   */
  private fun createRecurringSchema(db: SQLiteDatabase) {
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

  private fun createAutomationSchema(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS automation_events (
        id TEXT PRIMARY KEY NOT NULL,
        eventKey TEXT NOT NULL UNIQUE,
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
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_source_event ON transactions(sourceEventId) WHERE sourceEventId IS NOT NULL")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_automation_provider_reference ON automation_events(sourcePackage, providerReference) WHERE providerReference IS NOT NULL")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_automation_status_created ON automation_events(status, createdAt)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_classification_rules_match ON classification_rules(sourcePackage, direction, automationKind, priority DESC)")
  }

  private fun seedDefaults(db: SQLiteDatabase) {
    seedDefaultCategories(db)
    defaultPaymentMethods.forEach { item ->
      db.execSQL(
        "INSERT INTO payment_methods (id, name, color, isBuiltIn, isActive, createdAt) VALUES (?, ?, ?, 1, 1, ?)",
        arrayOf<Any>(id(), item.name, item.color.toLong(), System.currentTimeMillis()),
      )
    }
  }

  private fun seedDefaultCategories(db: SQLiteDatabase) {
    val categoryIds = mutableMapOf<CategorySeedKey, String>()
    defaultCategories.forEachIndexed { index, item ->
      val parentId = item.parentName?.let { parentName ->
        categoryIds[CategorySeedKey(parentName, item.type)]
          ?: findCategoryId(db, parentName, item.type, null)
      }
      val existingId = findCategoryId(db, item.name, item.type, parentId)
      if (existingId != null) {
        categoryIds[CategorySeedKey(item.name, item.type)] = existingId
        return@forEachIndexed
      }

      val categoryId = id()
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
          System.currentTimeMillis() + index,
        ),
      )
      categoryIds[CategorySeedKey(item.name, item.type)] = categoryId
    }
  }

  private fun findCategoryId(db: SQLiteDatabase, name: String, type: String, parentCategoryId: String?): String? {
    val sql = if (parentCategoryId == null) {
      "SELECT id FROM categories WHERE name = ? AND type = ? AND parentCategoryId IS NULL LIMIT 1"
    } else {
      "SELECT id FROM categories WHERE name = ? AND type = ? AND parentCategoryId = ? LIMIT 1"
    }
    val args = if (parentCategoryId == null) arrayOf(name, type) else arrayOf(name, type, parentCategoryId)
    db.rawQuery(sql, args).use { cursor ->
      return if (cursor.moveToFirst()) cursor.getString(0) else null
    }
  }

  private fun id() = UUID.randomUUID().toString()
}

private data class CategorySeedKey(
  val name: String,
  val type: String,
)

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
