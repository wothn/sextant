package com.sextant.app.data

import androidx.room.TypeConverter

/**
 * 枚举与 TEXT 列之间的转换。
 *
 * 存进去的字符串必须和迁移前 `SQLiteOpenHelper` 时代完全一致，否则老库读出来会全部
 * 退化成默认值——那种错误不会报错，只会把收入显示成支出。所以每种枚举都在这里显式
 * 写死取值，而不是靠 `name.lowercase()` 之类「看起来等价」的推导。
 *
 * 读回时对未知取值一律给出兜底而不是抛异常：库里出现将来版本写的新值、或者被外部工具
 * 改坏的行时，用户应该看到一行「退化的」数据，而不是整个页面崩掉。
 */
internal class SextantTypeConverters {
  @TypeConverter
  fun transactionTypeToStorage(value: TransactionType): String = when (value) {
    TransactionType.Expense -> "expense"
    TransactionType.Income -> "income"
    TransactionType.Transfer -> "transfer"
  }

  @TypeConverter
  fun transactionTypeFromStorage(value: String?): TransactionType = when (value) {
    "income" -> TransactionType.Income
    "transfer" -> TransactionType.Transfer
    else -> TransactionType.Expense
  }

  @TypeConverter
  fun entryOriginToStorage(value: EntryOrigin): String = when (value) {
    EntryOrigin.Manual -> "manual"
    EntryOrigin.Notification -> "notification"
  }

  @TypeConverter
  fun entryOriginFromStorage(value: String?): EntryOrigin =
    if (value == "notification") EntryOrigin.Notification else EntryOrigin.Manual

  @TypeConverter
  fun automationKindToStorage(value: AutomationKind): String = value.snakeCase()

  @TypeConverter
  fun automationKindFromStorage(value: String?): AutomationKind? = value?.toAutomationKind()

  @TypeConverter
  fun automationEventStatusToStorage(value: AutomationEventStatus): String = value.snakeCase()

  @TypeConverter
  fun automationEventStatusFromStorage(value: String?): AutomationEventStatus =
    AutomationEventStatus.entries.firstOrNull { it.snakeCase() == value } ?: AutomationEventStatus.Failed

  @TypeConverter
  fun recurringCycleToStorage(value: RecurringCycle): String = value.storageValue

  /**
   * 库里出现不认识的周期时退化成「每月」而不是丢弃整行，和 [transactionTypeFromStorage]
   * 的处理一致；[RecurringCycle] 本身不允许 null，所以这里也不能返回 null。
   */
  @TypeConverter
  fun recurringCycleFromStorage(value: String?): RecurringCycle =
    RecurringCycle.fromStorage(value) ?: RecurringCycle.Monthly
}

/** `RedPacket` → `red_packet`，和迁移前的 `dbValue` 保持逐字一致。 */
private fun Enum<*>.snakeCase(): String = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

private fun String.toAutomationKind(): AutomationKind? = AutomationKind.entries.firstOrNull {
  it.name.equals(this, ignoreCase = true)
}
