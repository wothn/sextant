package com.sextant.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 实体：数据库表结构的唯一声明处。
 *
 * 这些类刻意和 [Models.kt] 里的领域模型分开：
 *
 * - 领域模型只带业务字段，权限、是否内置、创建时间这类「库里的账」不往 UI 传；
 * - 实体则要一字不差地描述表结构，包括那些 UI 永远不看、但迁移和唯一索引依赖的列
 *   （`eventKey`、`payloadHash`、`priority`…）。
 *
 * 两类混在一起时，很容易为了让 UI 方便而改实体——那等于悄悄改表结构。分开之后，
 * `columnInfo` 与 `Index` 声明的就是磁盘上的真相。
 */

@Entity(tableName = "categories")
internal data class CategoryEntity(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "name") val name: String,
  @ColumnInfo(name = "type") val type: TransactionType,
  @ColumnInfo(name = "color") val color: Long,
  @ColumnInfo(name = "includeInSpending", defaultValue = "1") val includeInSpending: Boolean,
  @ColumnInfo(name = "parentCategoryId") val parentCategoryId: String?,
  @ColumnInfo(name = "isBuiltIn", defaultValue = "0") val isBuiltIn: Boolean,
  @ColumnInfo(name = "isActive", defaultValue = "1") val isActive: Boolean,
  @ColumnInfo(name = "createdAt") val createdAt: Long,
)

@Entity(
  tableName = "transactions",
  indices = [
    Index(value = ["transactionDate"], name = "idx_transactions_date"),
    Index(value = ["type", "transactionDate"], name = "idx_transactions_type_date"),
    Index(value = ["categoryId", "transactionDate"], name = "idx_transactions_category_date"),
    Index(value = ["paymentMethodId", "transactionDate"], name = "idx_transactions_payment_date"),
    // 部分唯一索引：自动记账的同一笔通知只能落一条流水，手工流水（sourceEventId 为 null）不受限。
    Index(value = ["sourceEventId"], name = "idx_transactions_source_event", unique = true),
  ],
)
internal data class TransactionEntity(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "categoryId") val categoryId: String?,
  @ColumnInfo(name = "paymentMethodId") val paymentMethodId: String?,
  @ColumnInfo(name = "amountCents") val amountCents: Long,
  @ColumnInfo(name = "type") val type: TransactionType,
  @ColumnInfo(name = "description", defaultValue = "''") val description: String,
  @ColumnInfo(name = "transactionDate") val transactionDate: Long,
  @ColumnInfo(name = "createdAt") val createdAt: Long,
  @ColumnInfo(name = "updatedAt") val updatedAt: Long,
  @ColumnInfo(name = "entryOrigin", defaultValue = "'manual'") val entryOrigin: EntryOrigin,
  @ColumnInfo(name = "automationKind") val automationKind: AutomationKind?,
  @ColumnInfo(name = "sourceEventId") val sourceEventId: String?,
  @ColumnInfo(name = "isUserEdited", defaultValue = "0") val isUserEdited: Boolean,
)

@Entity(tableName = "payment_methods")
internal data class PaymentMethodEntity(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "name") val name: String,
  @ColumnInfo(name = "color") val color: Long,
  @ColumnInfo(name = "isBuiltIn", defaultValue = "0") val isBuiltIn: Boolean,
  @ColumnInfo(name = "isActive", defaultValue = "1") val isActive: Boolean,
  @ColumnInfo(name = "createdAt") val createdAt: Long,
)

@Entity(tableName = "budgets")
internal data class BudgetEntity(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "categoryId") val categoryId: String,
  @ColumnInfo(name = "amountCents") val amountCents: Long,
  @ColumnInfo(name = "period") val period: String,
  @ColumnInfo(name = "startDate") val startDate: Long,
  @ColumnInfo(name = "alertThreshold", defaultValue = "0.8") val alertThreshold: Double,
  @ColumnInfo(name = "isActive", defaultValue = "1") val isActive: Boolean,
)

@Entity(tableName = "app_settings")
internal data class AppSettingEntity(
  @PrimaryKey @ColumnInfo(name = "key") val key: String,
  @ColumnInfo(name = "value") val value: String,
)

/**
 * 周期支出（房租、会员、宽带这类固定节奏的金额）。
 *
 * 它不参与流水统计，只是「计划」：真实扣款仍然记成普通流水。因此这里没有外键，
 * 也不触发任何账本重算——`categoryId` / `paymentMethodId` 是弱引用，指向已被归档的
 * 分类或来源时仍然可用，只是在展示上退化成「未分类」。
 */
@Entity(
  tableName = "recurring_expenses",
  indices = [Index(value = ["isActive", "anchorDate"], name = "idx_recurring_expenses_active")],
)
internal data class RecurringExpenseEntity(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "name") val name: String,
  @ColumnInfo(name = "amountCents") val amountCents: Long,
  @ColumnInfo(name = "cycle") val cycle: RecurringCycle,
  @ColumnInfo(name = "customDays") val customDays: Int?,
  @ColumnInfo(name = "anchorDate") val anchorDate: Long,
  @ColumnInfo(name = "categoryId") val categoryId: String?,
  @ColumnInfo(name = "paymentMethodId") val paymentMethodId: String?,
  @ColumnInfo(name = "endedAt") val endedAt: Long?,
  @ColumnInfo(name = "isActive", defaultValue = "1") val isActive: Boolean,
  @ColumnInfo(name = "note", defaultValue = "''") val note: String,
  @ColumnInfo(name = "createdAt") val createdAt: Long,
  @ColumnInfo(name = "updatedAt") val updatedAt: Long,
)

/**
 * 自动记账的原始事件（加密后的通知载荷 + 处理状态）。
 *
 * `eventKey` 上的唯一约束是幂等的根据：同一 package/通知 key/时间戳 只会入一行，
 * 重复投递的通知被 `INSERT OR IGNORE` 挡掉。`providerReference` 用部分唯一索引，
 * 因为它在解析成功之前一直是 null，普通唯一索引会把这些行全都判成冲突。
 */
@Entity(
  tableName = "automation_events",
  indices = [
    Index(value = ["eventKey"], name = "index_automation_events_eventKey", unique = true),
    Index(
      value = ["sourcePackage", "providerReference"],
      name = "idx_automation_provider_reference",
      unique = true,
    ),
    Index(value = ["status", "createdAt"], name = "idx_automation_status_created"),
  ],
)
internal data class AutomationEventEntity(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "eventKey") val eventKey: String,
  @ColumnInfo(name = "sourcePackage") val sourcePackage: String,
  @ColumnInfo(name = "notificationKey") val notificationKey: String,
  @ColumnInfo(name = "postedAt") val postedAt: Long,
  @ColumnInfo(name = "payloadHash") val payloadHash: String,
  @ColumnInfo(name = "encryptedPayload") val encryptedPayload: ByteArray?,
  @ColumnInfo(name = "payloadIv") val payloadIv: ByteArray?,
  @ColumnInfo(name = "status") val status: AutomationEventStatus,
  @ColumnInfo(name = "parser") val parser: String?,
  @ColumnInfo(name = "providerReference") val providerReference: String?,
  @ColumnInfo(name = "failureCode") val failureCode: String?,
  @ColumnInfo(name = "parsedSummary") val parsedSummary: String?,
  @ColumnInfo(name = "transactionId") val transactionId: String?,
  @ColumnInfo(name = "createdAt") val createdAt: Long,
  @ColumnInfo(name = "processedAt") val processedAt: Long?,
) {
  // ByteArray 的 equals 是引用比较，data class 自动生成的版本会让两条真正相同的行
  // 判成不同；这里按内容比较，避免以后拿它做去重或断言时出现反直觉的结果。
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AutomationEventEntity) return false
    return id == other.id &&
      eventKey == other.eventKey &&
      sourcePackage == other.sourcePackage &&
      notificationKey == other.notificationKey &&
      postedAt == other.postedAt &&
      payloadHash == other.payloadHash &&
      encryptedPayload.contentEquals(other.encryptedPayload) &&
      payloadIv.contentEquals(other.payloadIv) &&
      status == other.status &&
      parser == other.parser &&
      providerReference == other.providerReference &&
      failureCode == other.failureCode &&
      parsedSummary == other.parsedSummary &&
      transactionId == other.transactionId &&
      createdAt == other.createdAt &&
      processedAt == other.processedAt
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + eventKey.hashCode()
    result = 31 * result + sourcePackage.hashCode()
    result = 31 * result + notificationKey.hashCode()
    result = 31 * result + postedAt.hashCode()
    result = 31 * result + payloadHash.hashCode()
    result = 31 * result + (encryptedPayload?.contentHashCode() ?: 0)
    result = 31 * result + (payloadIv?.contentHashCode() ?: 0)
    result = 31 * result + status.hashCode()
    result = 31 * result + (parser?.hashCode() ?: 0)
    result = 31 * result + (providerReference?.hashCode() ?: 0)
    result = 31 * result + (failureCode?.hashCode() ?: 0)
    result = 31 * result + (parsedSummary?.hashCode() ?: 0)
    result = 31 * result + (transactionId?.hashCode() ?: 0)
    result = 31 * result + createdAt.hashCode()
    result = 31 * result + (processedAt?.hashCode() ?: 0)
    return result
  }
}

/** 来源列表带一笔流水数：用于「已被流水使用的来源不能删除」这条不变式。 */
internal data class PaymentMethodWithUsage(
  @Embedded val method: PaymentMethodEntity,
  @ColumnInfo(name = "transactionCount") val transactionCount: Long,
)

/** 预算进度查询的一行：预算额度加它所属分类的展示信息。 */
internal data class BudgetWithCategory(
  @ColumnInfo(name = "categoryId") val categoryId: String,
  @ColumnInfo(name = "amountCents") val amountCents: Long,
  @ColumnInfo(name = "name") val categoryName: String,
  @ColumnInfo(name = "color") val categoryColor: Long,
)
