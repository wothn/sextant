package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface PaymentMethodDao {
  /**
   * 来源列表：启用的排在前面，同组内按创建顺序。
   *
   * 流水数用 LEFT JOIN 聚合出来，是为了「已被流水使用的来源不能删除」这条不变式——
   * 计数在这里一次取完，而不是每条来源再查一次。
   */
  @Query(
    """
    SELECT p.*, COUNT(t.id) AS transactionCount
    FROM payment_methods p
    LEFT JOIN transactions t ON t.paymentMethodId = p.id
    GROUP BY p.id
    ORDER BY p.isActive DESC, p.createdAt ASC
    """,
  )
  suspend fun listWithUsage(): List<PaymentMethodWithUsage>

  @Query("SELECT * FROM payment_methods WHERE id = :id")
  suspend fun findById(id: String): PaymentMethodEntity?

  @Query("SELECT id FROM payment_methods WHERE name = :name COLLATE NOCASE LIMIT 1")
  suspend fun findIdByName(name: String): String?

  @Query("SELECT id FROM payment_methods WHERE name = :name COLLATE NOCASE AND id != :excludingId LIMIT 1")
  suspend fun findIdByNameExcluding(name: String, excludingId: String): String?

  /** 自动记账按支付应用找默认来源：优先启用中的，其次最早的。 */
  @Query("SELECT id FROM payment_methods WHERE name = :name ORDER BY isActive DESC, createdAt ASC LIMIT 1")
  suspend fun findIdForAutomation(name: String): String?

  @Query("SELECT COUNT(*) FROM payment_methods")
  suspend fun count(): Long

  @Insert
  suspend fun insert(method: PaymentMethodEntity)

  @Query("UPDATE payment_methods SET name = :name, color = :color WHERE id = :id")
  suspend fun update(id: String, name: String, color: Long)

  @Query("UPDATE payment_methods SET isActive = :isActive WHERE id = :id")
  suspend fun setActive(id: String, isActive: Boolean)

  @Query("DELETE FROM payment_methods WHERE id = :id")
  suspend fun deleteById(id: String)

  /** 导出备份用：包含已停用的来源，按创建顺序。 */
  @Query("SELECT * FROM payment_methods ORDER BY createdAt ASC")
  suspend fun listAllForBackup(): List<PaymentMethodEntity>

  @Query("DELETE FROM payment_methods")
  suspend fun deleteAll()
}
