package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface RecurringExpenseDao {
  /**
   * 全部周期支出，启用的排在前面，同组内按首次扣费日。
   *
   * 「下一次扣费」是按当前时间推算出来的（见 `RecurringSchedule.kt`），不落库，
   * 所以这里不排序也排不了；页面按推算结果再排一次。
   */
  @Query("SELECT * FROM recurring_expenses ORDER BY isActive DESC, anchorDate ASC, createdAt ASC")
  suspend fun listAll(): List<RecurringExpenseEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM recurring_expenses WHERE id = :id)")
  suspend fun exists(id: String): Boolean

  @Insert
  suspend fun insert(expense: RecurringExpenseEntity)

  @Query(
    """
    UPDATE recurring_expenses
    SET name = :name, amountCents = :amountCents, cycle = :cycle, customDays = :customDays,
      anchorDate = :anchorDate, categoryId = :categoryId, paymentMethodId = :paymentMethodId,
      endedAt = :endedAt, isActive = :isActive, note = :note, updatedAt = :updatedAt
    WHERE id = :id
    """,
  )
  suspend fun update(
    id: String,
    name: String,
    amountCents: Long,
    cycle: RecurringCycle,
    customDays: Int?,
    anchorDate: Long,
    categoryId: String?,
    paymentMethodId: String?,
    endedAt: Long?,
    isActive: Boolean,
    note: String,
    updatedAt: Long,
  )

  @Query("UPDATE recurring_expenses SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
  suspend fun setActive(id: String, isActive: Boolean, updatedAt: Long)

  @Query("DELETE FROM recurring_expenses WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM recurring_expenses")
  suspend fun deleteAll()
}
