package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface TransactionDao {
  /**
   * 账本明细。时间范围是唯一必填条件，其余都是「传空即不过滤」的可选筛选项。
   *
   * 分类筛选会把子分类的流水并进来（`categoryId IN (SELECT …)`），这样用户在概览里
   * 点「餐饮」时能看到「正餐 / 奶茶」的明细，而不是只看到直接记在「餐饮」上的那几笔。
   *
   * 金额上下限用 `< 0` 当「未设置」：真正的金额永远不小于 0，而这样写可以避免
   * `(:min IS NULL OR …)` 那种写法在 SQLite 里退化成全表扫描。
   *
   * 收支方向同样用空串当「全部」，而参数类型必须是存进库里的字面量字符串，不能是
   * [TransactionType]：传枚举时 Room 会走 TypeConverter 绑成 `"expense"`，
   * `:type = ''` 便永远不成立，`OR` 的后半段于是把「不筛选方向」静默变成「只看支出」——
   * 收入流水既进不了明细，也进不了按方向拆分的汇总。
   */
  @Query(
    """
    SELECT * FROM transactions
    WHERE transactionDate >= :rangeStart AND transactionDate < :rangeEnd
      AND (:query = '' OR description LIKE '%' || :query || '%')
      AND (:categoryId = '' OR categoryId = :categoryId OR categoryId IN (
        SELECT id FROM categories WHERE parentCategoryId = :categoryId
      ))
      AND (:paymentMethodId = '' OR paymentMethodId = :paymentMethodId)
      AND (:type = '' OR type = :type)
      AND (:minAmountCents < 0 OR amountCents >= :minAmountCents)
      AND (:maxAmountCents < 0 OR amountCents <= :maxAmountCents)
    ORDER BY transactionDate DESC
    """,
  )
  suspend fun listFiltered(
    rangeStart: Long,
    rangeEnd: Long,
    query: String,
    categoryId: String,
    paymentMethodId: String,
    type: String,
    minAmountCents: Long,
    maxAmountCents: Long,
  ): List<TransactionEntity>

  @Query("SELECT * FROM transactions WHERE id = :id")
  suspend fun findById(id: String): TransactionEntity?

  @Query("SELECT COUNT(*) FROM transactions")
  suspend fun count(): Long

  /** 自动记账的幂等闸门：同一条通知事件只允许对应一笔流水。 */
  @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE sourceEventId = :eventId)")
  suspend fun existsForSourceEvent(eventId: String): Boolean

  @Insert
  suspend fun insert(transaction: TransactionEntity)

  @Query(
    """
    UPDATE transactions SET type = :type, amountCents = :amountCents, categoryId = :categoryId,
      paymentMethodId = :paymentMethodId, description = :description, transactionDate = :transactionDate,
      updatedAt = :updatedAt,
      isUserEdited = CASE WHEN entryOrigin = 'notification' THEN 1 ELSE isUserEdited END
    WHERE id = :id
    """,
  )
  suspend fun update(
    id: String,
    type: TransactionType,
    amountCents: Long,
    categoryId: String?,
    paymentMethodId: String?,
    description: String,
    transactionDate: Long,
    updatedAt: Long,
  )

  @Query("DELETE FROM transactions WHERE id = :id")
  suspend fun deleteById(id: String)

  /** 导出备份用：按时间正序，恢复后重建的顺序与导出时一致。 */
  @Query("SELECT * FROM transactions ORDER BY transactionDate ASC, createdAt ASC")
  suspend fun listAllForBackup(): List<TransactionEntity>

  @Query("DELETE FROM transactions")
  suspend fun deleteAll()
}
