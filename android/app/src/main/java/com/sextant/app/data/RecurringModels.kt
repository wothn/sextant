package com.sextant.app.data

/**
 * 周期支出的重复方式。
 *
 * [storageValue] 显式写死，不复用 `AutomationKind` 那套 camelCase → snake_case 的转换：
 * 那套转换在回读时用 `enum.name.equals(value, ignoreCase = true)` 比较，多词枚举
 * （`RedPacket` vs `red_packet`）会对不上。这里按 [ThemePreference] 的写法，标识独立于枚举名。
 */
enum class RecurringCycle(val storageValue: String) {
  Weekly("weekly"),
  Monthly("monthly"),
  Quarterly("quarterly"),
  Semiannual("semiannual"),
  Yearly("yearly"),
  CustomDays("custom_days"),
  ;

  companion object {
    fun fromStorage(value: String?): RecurringCycle? = entries.firstOrNull { it.storageValue == value }
  }
}

/**
 * 周期支出相对「今天」的状态。
 *
 * [Ending] 与 [Ended] 的区别在于「已取消」和「已失效」：会员取消后通常还有一段有效期，
 * 这段时间内它依然会扣最后一笔，所以 [Ending] 仍计入汇总，[Ended] 不再计入。
 */
enum class RecurringState {
  /** 正常重复。 */
  Active,

  /** 已设置停止日期，但在该日期之前仍然有效。 */
  Ending,

  /** 已过停止日期。 */
  Ended,

  /** 手动暂停，不参与推算与汇总。 */
  Paused,
}

/**
 * 一笔周期性固定支出：房租、会员、宽带、保险这类按固定节奏发生的金额。
 *
 * 它**不是流水**。真实扣款仍然由用户记成普通流水，本记录只描述「计划」：什么时候扣、扣多少。
 * 这样预算、分析页和收支口径都不会被凭空生成的记录污染，[RecurringSchedule.kt] 里推算出来的
 * 日期也只是预期值。
 *
 * 时间是本地时区当天零点的毫秒时间戳，和账本其余部分一致（见 `LedgerDateRanges.kt`）。
 */
data class RecurringExpense(
  val id: String,
  val name: String,
  val amountCents: Long,
  val cycle: RecurringCycle,
  /** 仅 [RecurringCycle.CustomDays] 有值，其余必须为 null。 */
  val customDays: Int?,
  /** 首次扣费日。所有后续扣费日都由它推算，见 [occurrence]。 */
  val anchorDate: Long,
  val categoryId: String?,
  val paymentMethodId: String?,
  /** 停止日期，语义是「此日起不再扣费」。有值即表示这笔支出已经不再继续。 */
  val endedAt: Long?,
  val isActive: Boolean,
  val note: String,
  val createdAt: Long,
  val updatedAt: Long,
)

/**
 * 待写入的周期支出。
 *
 * 单独一个类型是为了给 [SextantRepository.saveRecurringExpense] 用：那边有十来个字段，
 * 光靠位置参数调用（其中还挨着好几个 `Long?` / `String?`）迟早会把「分类」和「来源」传反，
 * 而这两种错误都不会报错，只会安静地存错。id 与时间戳由仓储负责，不在这里。
 */
data class RecurringExpenseDraft(
  val name: String,
  val amountCents: Long,
  val cycle: RecurringCycle,
  /** 仅 [RecurringCycle.CustomDays] 需要；其它周期传进来的值会被仓储归一化成 null。 */
  val customDays: Int?,
  val anchorDate: Long,
  val categoryId: String?,
  val paymentMethodId: String?,
  /** 停止扣费的日期；语义见 [RecurringExpense.endedAt]。null 表示一直继续。 */
  val endedAt: Long? = null,
  val isActive: Boolean,
  val note: String,
)
