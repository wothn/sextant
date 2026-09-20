package com.sextant.app.data

import androidx.room.withTransaction
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
  val recurringExpenseCount: Int,
)

class BackupException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

internal class BackupStore(private val database: SextantDatabase) {
  suspend fun export(): String {
    val document = BackupDocument(
      exportedAt = System.currentTimeMillis(),
      databaseVersion = SextantDatabase.DATABASE_VERSION,
      categories = database.categoryDao().listAllForBackup().map { it.toBackup() },
      paymentMethods = database.paymentMethodDao().listAllForBackup().map { it.toBackup() },
      transactions = database.transactionDao().listAllForBackup().map { it.toBackup() },
      budgets = database.budgetDao().listAll().map { it.toBackup() },
      recurringExpenses = database.recurringExpenseDao().listAll().map { it.toBackup() },
      // 自动记账的设置项带着本机的解析偏好，属于敏感配置，不进备份；
      // 校验那一侧同样会拒绝导入带 automation_ 前缀的设置项。
      settings = database.appSettingDao().listBackupSettings().map { BackupSetting(it.key, it.value) },
    )
    return BackupCodec.encode(document)
  }

  /**
   * 用备份覆盖当前账本。
   *
   * 整个「清空 + 写回」是一个事务：中途任何一条数据触发约束冲突都会整体回滚，用户看到
   * 「恢复失败，原有数据未被修改」，而不是一个半新半旧的账本。
   */
  suspend fun restore(content: String): BackupSummary {
    val document = BackupCodec.decodeAndValidate(content)
    try {
      database.withTransaction {
        val categories = database.categoryDao()
        val paymentMethods = database.paymentMethodDao()
        val transactions = database.transactionDao()
        val budgets = database.budgetDao()
        val recurringExpenses = database.recurringExpenseDao()
        val settings = database.appSettingDao()

        budgets.deleteAll()
        recurringExpenses.deleteAll()
        database.automationEventDao().deleteAll()
        transactions.deleteAll()
        settings.deleteAll()
        paymentMethods.deleteAll()
        categories.deleteAll()

        document.categories.forEach { categories.insert(it.toEntity()) }
        document.paymentMethods.forEach { paymentMethods.insert(it.toEntity()) }
        document.transactions.forEach { transactions.insert(it.toEntity()) }
        document.budgets.forEach { budgets.insert(it.toEntity()) }
        document.recurringExpenses.forEach { recurringExpenses.insert(it.toEntity()) }
        document.settings.forEach { settings.upsert(AppSettingEntity(it.key, it.value)) }
      }
    } catch (error: Exception) {
      throw BackupException("恢复失败，原有数据未被修改", error)
    }

    return document.summary()
  }
}

/**
 * 备份与实体之间的映射。
 *
 * 备份格式刻意独立于实体：实体跟着表结构走，备份跟着「用户导出的那份文件」走。
 * 两者绑死的话，任何一次加列都会变成「老备份读不进来」的格式变更。
 */
private fun CategoryEntity.toBackup() = BackupCategory(
  id = id,
  name = name,
  type = type.storageValue(),
  color = color,
  includeInSpending = includeInSpending,
  parentCategoryId = parentCategoryId,
  isBuiltIn = isBuiltIn,
  isActive = isActive,
  createdAt = createdAt,
)

private fun BackupCategory.toEntity() = CategoryEntity(
  id = id,
  name = name,
  type = type.toTransactionType(),
  color = color,
  includeInSpending = includeInSpending,
  parentCategoryId = parentCategoryId,
  isBuiltIn = isBuiltIn,
  isActive = isActive,
  createdAt = createdAt,
)

private fun PaymentMethodEntity.toBackup() = BackupPaymentMethod(
  id = id,
  name = name,
  color = color,
  isBuiltIn = isBuiltIn,
  isActive = isActive,
  createdAt = createdAt,
)

private fun BackupPaymentMethod.toEntity() = PaymentMethodEntity(
  id = id,
  name = name,
  color = color,
  isBuiltIn = isBuiltIn,
  isActive = isActive,
  createdAt = createdAt,
)

private fun TransactionEntity.toBackup() = BackupTransaction(
  id = id,
  categoryId = categoryId,
  paymentMethodId = paymentMethodId,
  amountCents = amountCents,
  type = type.storageValue(),
  description = description,
  transactionDate = transactionDate,
  createdAt = createdAt,
  updatedAt = updatedAt,
  entryOrigin = entryOrigin.storageValue(),
  automationKind = automationKind?.storageValue(),
  isUserEdited = isUserEdited,
)

/**
 * 备份里的流水不带 `sourceEventId`。
 *
 * 它是「这条流水由哪次通知生成」的内部关联，而事件本身不进备份；原样写回一个悬空的事件
 * id 只会让恢复后的流水看起来像自动记账生成的，却再也撤不掉。所以恢复时一律置空，
 * 来源信息保留在 `entryOrigin` / `automationKind` 上。
 */
private fun BackupTransaction.toEntity() = TransactionEntity(
  id = id,
  categoryId = categoryId,
  paymentMethodId = paymentMethodId,
  amountCents = amountCents,
  type = type.toTransactionType(),
  description = description,
  transactionDate = transactionDate,
  createdAt = createdAt,
  updatedAt = updatedAt,
  entryOrigin = entryOrigin.toEntryOrigin(),
  automationKind = automationKind?.toAutomationKind(),
  sourceEventId = null,
  isUserEdited = isUserEdited,
)

private fun BudgetEntity.toBackup() = BackupBudget(
  id = id,
  categoryId = categoryId,
  amountCents = amountCents,
  period = period,
  startDate = startDate,
  alertThreshold = alertThreshold,
  isActive = isActive,
)

private fun BackupBudget.toEntity() = BudgetEntity(
  id = id,
  categoryId = categoryId,
  amountCents = amountCents,
  period = period,
  startDate = startDate,
  alertThreshold = alertThreshold,
  isActive = isActive,
)

private fun RecurringExpenseEntity.toBackup() = BackupRecurringExpense(
  id = id,
  name = name,
  amountCents = amountCents,
  cycle = cycle.storageValue,
  customDays = customDays,
  anchorDate = anchorDate,
  categoryId = categoryId,
  paymentMethodId = paymentMethodId,
  endedAt = endedAt,
  isActive = isActive,
  note = note,
  createdAt = createdAt,
  updatedAt = updatedAt,
)

/**
 * 备份里的周期支出。
 *
 * 时间戳与 `customDays` 原样搬运，由 [BackupCodec.validate] 检查「自定义周期必须有天数、
 * 其它周期必须没有」这条不变式；这里不做归一化，否则一个有问题的备份会被悄悄修好，
 * 而用户以为恢复的就是导出的那份。
 */
private fun BackupRecurringExpense.toEntity() = RecurringExpenseEntity(
  id = id,
  name = name,
  amountCents = amountCents,
  cycle = RecurringCycle.fromStorage(cycle) ?: RecurringCycle.Monthly,
  customDays = customDays,
  anchorDate = anchorDate,
  categoryId = categoryId,
  paymentMethodId = paymentMethodId,
  endedAt = endedAt,
  isActive = isActive,
  note = note,
  createdAt = createdAt,
  updatedAt = updatedAt,
)

private fun String.toTransactionType(): TransactionType = when (this) {
  "income" -> TransactionType.Income
  "transfer" -> TransactionType.Transfer
  else -> TransactionType.Expense
}

private fun EntryOrigin.storageValue(): String = SextantTypeConverters().entryOriginToStorage(this)
private fun String.toEntryOrigin(): EntryOrigin = SextantTypeConverters().entryOriginFromStorage(this)
private fun AutomationKind.storageValue(): String = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
private fun String.toAutomationKind(): AutomationKind? = AutomationKind.entries.firstOrNull {
  it.name.equals(this, ignoreCase = true)
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
    requireUnique(document.recurringExpenses.map { it.id }, "周期支出 ID")
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
      requireBackup(item.entryOrigin in setOf("manual", "notification", "accessibility")) { "交易来源类型无效" }
      requireBackup(item.automationKind == null || item.automationKind in AUTOMATION_KINDS) { "自动交易类型无效" }
    }
    document.budgets.forEach { item ->
      requireBackup(item.id.isNotBlank() && item.categoryId in categoryIds) { "预算分类引用无效" }
      requireBackup(item.amountCents >= 0 && item.period.isNotBlank()) { "预算信息无效" }
      requireBackup(item.alertThreshold.isFinite() && item.alertThreshold >= 0) { "预算提醒阈值无效" }
    }
    document.settings.forEach { item ->
      requireBackup(item.key.isNotBlank()) { "设置项名称不能为空" }
      requireBackup(!item.key.startsWith("automation_")) { "备份不能包含自动记账敏感设置" }
    }
    document.recurringExpenses.forEach { item ->
      requireBackup(item.id.isNotBlank() && item.name.isNotBlank()) { "周期支出信息不完整" }
      requireBackup(item.amountCents > 0) { "周期支出金额无效" }
      requireBackup(item.anchorDate > 0) { "周期支出缺少首次扣费日" }
      val cycle = RecurringCycle.fromStorage(item.cycle)
      requireBackup(cycle != null) { "周期支出周期无效" }
      // 「每月 + 45 天」这种自相矛盾的行不能在库和备份之间来回传播。
      requireBackup((cycle == RecurringCycle.CustomDays) == (item.customDays != null)) { "周期支出间隔天数与周期不匹配" }
      requireBackup(item.customDays == null || item.customDays > 0) { "周期支出间隔天数无效" }
      requireBackup(item.categoryId == null || item.categoryId in categoryIds) { "周期支出分类引用无效" }
      requireBackup(item.paymentMethodId == null || item.paymentMethodId in paymentMethodIds) { "周期支出来源引用无效" }
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
  val recurringExpenses: List<BackupRecurringExpense> = emptyList(),
  val settings: List<BackupSetting> = emptyList(),
) {
  fun summary() = BackupSummary(transactions.size, categories.size, paymentMethods.size, budgets.size, recurringExpenses.size)
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
  val entryOrigin: String = "manual",
  val automationKind: String? = null,
  val isUserEdited: Boolean = false,
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

/**
 * 备份里的周期支出。
 *
 * 时间戳与 `customDays` 原样搬运，由 [BackupCodec.validate] 检查「自定义周期必须有天数、
 * 其它周期必须没有」这条不变式；这里不做归一化，否则一个有问题的备份会被悄悄修好，
 * 而用户以为恢复的就是导出的那份。
 */
@Serializable
internal data class BackupRecurringExpense(
  val id: String,
  val name: String,
  val amountCents: Long,
  val cycle: String,
  val customDays: Int? = null,
  val anchorDate: Long,
  val categoryId: String? = null,
  val paymentMethodId: String? = null,
  val endedAt: Long? = null,
  val isActive: Boolean,
  val note: String = "",
  val createdAt: Long,
  val updatedAt: Long,
)

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

private const val BACKUP_FORMAT = "sextant-backup"
private const val BACKUP_VERSION = 3
private val TRANSACTION_TYPES = setOf("expense", "income", "transfer")
private val AUTOMATION_KINDS = setOf("purchase", "refund", "receipt", "transfer_in", "transfer_out", "recharge", "repayment", "red_packet", "withdrawal", "other")
