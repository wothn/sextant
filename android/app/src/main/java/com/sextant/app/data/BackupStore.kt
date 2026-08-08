package com.sextant.app.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class BackupSummary(
  val transactionCount: Int,
  val categoryCount: Int,
  val paymentMethodCount: Int,
  val budgetCount: Int,
)

class BackupException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

internal class BackupStore(private val database: SextantDatabase) {
  fun export(): String {
    val db = database.readableDatabase
    val document = BackupDocument(
      exportedAt = System.currentTimeMillis(),
      databaseVersion = SextantDatabase.DATABASE_VERSION,
      categories = db.readCategories(),
      paymentMethods = db.readPaymentMethods(),
      transactions = db.readTransactions(),
      budgets = db.readBudgets(),
      settings = db.readSettings(),
    )
    return BackupCodec.encode(document)
  }

  fun restore(content: String): BackupSummary {
    val document = BackupCodec.decodeAndValidate(content)
    val db = database.writableDatabase
    db.beginTransaction()
    try {
      db.delete("budgets", null, null)
      db.delete("transactions", null, null)
      db.delete("app_settings", null, null)
      db.delete("payment_methods", null, null)
      db.delete("categories", null, null)

      document.categories.forEach { item ->
        db.execSQL(
          """
          INSERT INTO categories
            (id, name, type, color, includeInSpending, parentCategoryId, isBuiltIn, isActive, createdAt)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """.trimIndent(),
          arrayOf<Any?>(item.id, item.name, item.type, item.color, item.includeInSpending.asInt(), item.parentCategoryId, item.isBuiltIn.asInt(), item.isActive.asInt(), item.createdAt),
        )
      }
      document.paymentMethods.forEach { item ->
        db.execSQL(
          "INSERT INTO payment_methods (id, name, color, isBuiltIn, isActive, createdAt) VALUES (?, ?, ?, ?, ?, ?)",
          arrayOf<Any>(item.id, item.name, item.color, item.isBuiltIn.asInt(), item.isActive.asInt(), item.createdAt),
        )
      }
      document.transactions.forEach { item ->
        db.execSQL(
          """
          INSERT INTO transactions
            (id, categoryId, paymentMethodId, amountCents, type, description, transactionDate, createdAt, updatedAt)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """.trimIndent(),
          arrayOf<Any?>(item.id, item.categoryId, item.paymentMethodId, item.amountCents, item.type, item.description, item.transactionDate, item.createdAt, item.updatedAt),
        )
      }
      document.budgets.forEach { item ->
        db.execSQL(
          """
          INSERT INTO budgets
            (id, categoryId, amountCents, period, startDate, alertThreshold, isActive)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """.trimIndent(),
          arrayOf<Any>(item.id, item.categoryId, item.amountCents, item.period, item.startDate, item.alertThreshold, item.isActive.asInt()),
        )
      }
      document.settings.forEach { item ->
        db.execSQL(
          "INSERT INTO app_settings (key, value) VALUES (?, ?)",
          arrayOf<Any>(item.key, item.value),
        )
      }
      db.setTransactionSuccessful()
    } catch (error: Exception) {
      throw BackupException("恢复失败，原有数据未被修改", error)
    } finally {
      db.endTransaction()
    }

    return document.summary()
  }
}

internal object BackupCodec {
  private val json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = true
  }

  fun encode(document: BackupDocument): String = json.encodeToString(document)

  fun decodeAndValidate(content: String): BackupDocument {
    val document = try {
      json.decodeFromString<BackupDocument>(content)
    } catch (error: SerializationException) {
      throw BackupException("无法识别这个备份文件", error)
    } catch (error: IllegalArgumentException) {
      throw BackupException("无法识别这个备份文件", error)
    }
    validate(document)
    return document
  }

  private fun validate(document: BackupDocument) {
    requireBackup(document.format == BACKUP_FORMAT) { "这不是 Sextant 备份文件" }
    requireBackup(document.version in 1..BACKUP_VERSION) { "暂不支持此备份版本" }
    requireBackup(document.databaseVersion in 1..SextantDatabase.DATABASE_VERSION) { "备份来自更新版本的 Sextant" }

    requireUnique(document.categories.map { it.id }, "分类 ID")
    requireUnique(document.paymentMethods.map { it.id }, "来源 ID")
    requireUnique(document.transactions.map { it.id }, "交易 ID")
    requireUnique(document.budgets.map { it.id }, "预算 ID")
    requireUnique(document.settings.map { it.key }, "设置项")

    val categoryIds = document.categories.mapTo(mutableSetOf()) { it.id }
    val paymentMethodIds = document.paymentMethods.mapTo(mutableSetOf()) { it.id }
    document.categories.forEach { item ->
      requireBackup(item.id.isNotBlank() && item.name.isNotBlank()) { "分类信息不完整" }
      requireBackup(item.type == "expense" || item.type == "income") { "分类类型无效" }
      requireBackup(item.parentCategoryId == null || item.parentCategoryId in categoryIds) { "分类层级引用无效" }
      requireBackup(item.parentCategoryId != item.id) { "分类不能引用自身" }
    }
    requireBackup(!document.categories.hasParentCycle()) { "分类层级存在循环" }

    document.paymentMethods.forEach { item ->
      requireBackup(item.id.isNotBlank() && item.name.isNotBlank()) { "来源信息不完整" }
    }
    document.transactions.forEach { item ->
      requireBackup(item.id.isNotBlank()) { "交易 ID 不能为空" }
      requireBackup(item.type in TRANSACTION_TYPES) { "交易类型无效" }
      requireBackup(item.amountCents >= 0) { "交易金额无效" }
      requireBackup(item.categoryId == null || item.categoryId in categoryIds) { "交易分类引用无效" }
      requireBackup(item.paymentMethodId == null || item.paymentMethodId in paymentMethodIds) { "交易来源引用无效" }
    }
    document.budgets.forEach { item ->
      requireBackup(item.id.isNotBlank() && item.categoryId in categoryIds) { "预算分类引用无效" }
      requireBackup(item.amountCents >= 0 && item.period.isNotBlank()) { "预算信息无效" }
      requireBackup(item.alertThreshold.isFinite() && item.alertThreshold >= 0) { "预算提醒阈值无效" }
    }
    document.settings.forEach { item ->
      requireBackup(item.key.isNotBlank()) { "设置项名称不能为空" }
    }
  }
}

@Serializable
internal data class BackupDocument(
  val format: String = BACKUP_FORMAT,
  val version: Int = BACKUP_VERSION,
  val exportedAt: Long,
  val databaseVersion: Int,
  val categories: List<BackupCategory> = emptyList(),
  @SerialName("paymentMethods") val paymentMethods: List<BackupPaymentMethod> = emptyList(),
  val transactions: List<BackupTransaction> = emptyList(),
  val budgets: List<BackupBudget> = emptyList(),
  val settings: List<BackupSetting> = emptyList(),
) {
  fun summary() = BackupSummary(transactions.size, categories.size, paymentMethods.size, budgets.size)
}

@Serializable
internal data class BackupCategory(
  val id: String,
  val name: String,
  val type: String,
  val color: Long,
  val includeInSpending: Boolean,
  val parentCategoryId: String? = null,
  val isBuiltIn: Boolean,
  val isActive: Boolean,
  val createdAt: Long,
)

@Serializable
internal data class BackupPaymentMethod(
  val id: String,
  val name: String,
  val color: Long,
  val isBuiltIn: Boolean,
  val isActive: Boolean,
  val createdAt: Long,
)

@Serializable
internal data class BackupTransaction(
  val id: String,
  val categoryId: String? = null,
  val paymentMethodId: String? = null,
  val amountCents: Long,
  val type: String,
  val description: String,
  val transactionDate: Long,
  val createdAt: Long,
  val updatedAt: Long,
)

@Serializable
internal data class BackupBudget(
  val id: String,
  val categoryId: String,
  val amountCents: Long,
  val period: String,
  val startDate: Long,
  val alertThreshold: Double,
  val isActive: Boolean,
)

@Serializable
internal data class BackupSetting(val key: String, val value: String)

private fun SQLiteDatabase.readCategories(): List<BackupCategory> = readRows(
  "SELECT id, name, type, color, includeInSpending, parentCategoryId, isBuiltIn, isActive, createdAt FROM categories ORDER BY createdAt ASC",
) { cursor ->
  BackupCategory(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3), cursor.getInt(4) == 1, cursor.stringOrNull(5), cursor.getInt(6) == 1, cursor.getInt(7) == 1, cursor.getLong(8))
}

private fun SQLiteDatabase.readPaymentMethods(): List<BackupPaymentMethod> = readRows(
  "SELECT id, name, color, isBuiltIn, isActive, createdAt FROM payment_methods ORDER BY createdAt ASC",
) { cursor ->
  BackupPaymentMethod(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getInt(3) == 1, cursor.getInt(4) == 1, cursor.getLong(5))
}

private fun SQLiteDatabase.readTransactions(): List<BackupTransaction> = readRows(
  "SELECT id, categoryId, paymentMethodId, amountCents, type, description, transactionDate, createdAt, updatedAt FROM transactions ORDER BY transactionDate ASC, createdAt ASC",
) { cursor ->
  BackupTransaction(cursor.getString(0), cursor.stringOrNull(1), cursor.stringOrNull(2), cursor.getLong(3), cursor.getString(4), cursor.getString(5), cursor.getLong(6), cursor.getLong(7), cursor.getLong(8))
}

private fun SQLiteDatabase.readBudgets(): List<BackupBudget> = readRows(
  "SELECT id, categoryId, amountCents, period, startDate, alertThreshold, isActive FROM budgets ORDER BY startDate ASC",
) { cursor ->
  BackupBudget(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3), cursor.getLong(4), cursor.getDouble(5), cursor.getInt(6) == 1)
}

private fun SQLiteDatabase.readSettings(): List<BackupSetting> = readRows(
  "SELECT key, value FROM app_settings ORDER BY key ASC",
) { cursor -> BackupSetting(cursor.getString(0), cursor.getString(1)) }

private inline fun <T> SQLiteDatabase.readRows(sql: String, transform: (Cursor) -> T): List<T> {
  val rows = mutableListOf<T>()
  rawQuery(sql, emptyArray()).use { cursor ->
    while (cursor.moveToNext()) rows += transform(cursor)
  }
  return rows
}

private fun List<BackupCategory>.hasParentCycle(): Boolean {
  val parents = associate { it.id to it.parentCategoryId }
  return any { category ->
    val seen = mutableSetOf<String>()
    var current: String? = category.id
    while (current != null && seen.add(current)) current = parents[current]
    current != null
  }
}

private fun requireUnique(values: List<String>, label: String) {
  requireBackup(values.size == values.toSet().size) { "$label 重复" }
}

private inline fun requireBackup(condition: Boolean, message: () -> String) {
  if (!condition) throw BackupException(message())
}

private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
private fun Boolean.asInt(): Int = if (this) 1 else 0

private const val BACKUP_FORMAT = "sextant-backup"
private const val BACKUP_VERSION = 1
private val TRANSACTION_TYPES = setOf("expense", "income", "transfer")
