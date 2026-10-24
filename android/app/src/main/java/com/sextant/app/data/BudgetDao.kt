package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface BudgetDao {
  @Query("SELECT * FROM budgets WHERE id = :id")
  suspend fun findById(id: String): BudgetEntity?

  /**
   * 某个分类在某个周期上的预算。周期用 (period, startDate) 定位，取「月度 + 本月首日」。
   *
   * 这里只返回 id：调用方拿到之后决定是更新还是新建，判断留在仓储里，
   * 数据访问层不掺业务分支。
   */
  @Query("SELECT id FROM budgets WHERE categoryId = :categoryId AND period = 'month' AND startDate = :startDate")
  suspend fun findIdForCategoryMonth(categoryId: String, startDate: Long): String?

  @Insert
  suspend fun insert(budget: BudgetEntity)

  @Query("UPDATE budgets SET amountCents = :amountCents, isActive = 1 WHERE id = :id")
  suspend fun updateAmount(id: String, amountCents: Long)

  /** 清除分类预算：只停用当月那一行，历史月份保留。 */
  @Query(
    "UPDATE budgets SET isActive = 0 WHERE categoryId = :categoryId AND period = 'month' AND startDate = :startDate",
  )
  suspend fun deactivateForCategoryMonth(categoryId: String, startDate: Long)

  /**
   * 本月生效的分类预算，连带分类的展示信息。
   *
   * 内连接分类表同时起到了「过滤掉已归档分类的预算」的作用：归档分类的预算不该
   * 再出现在页面上。
   */
  @Query(
    """
    SELECT b.categoryId AS categoryId, b.amountCents AS amountCents,
      c.name AS name, c.color AS color
    FROM budgets b
    JOIN categories c ON c.id = b.categoryId
    WHERE b.isActive = 1 AND b.period = 'month' AND b.startDate = :startDate
    """,
  )
  suspend fun listActiveForMonth(startDate: Long): List<BudgetWithCategory>

  @Query("DELETE FROM budgets")
  suspend fun deleteAll()

  /** 导出备份用：按周期起始日正序。 */
  @Query("SELECT * FROM budgets ORDER BY startDate ASC")
  suspend fun listAll(): List<BudgetEntity>
}
