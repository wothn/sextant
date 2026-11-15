package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** 空分类键代表总预算；同月例外优先于持续生效的版本。零额度代表停用。 */
@Dao
internal interface BudgetRuleDao {
  @Upsert
  suspend fun upsert(rule: BudgetRuleEntity)

  @Query("DELETE FROM budget_rules WHERE categoryId = :categoryId AND startMonth = :month AND onlyThisMonth = 1")
  suspend fun deleteOverride(categoryId: String, month: Long)

  @Query("SELECT * FROM budget_rules ORDER BY startMonth, categoryId, onlyThisMonth")
  suspend fun listAll(): List<BudgetRuleEntity>

  @Query("DELETE FROM budget_rules")
  suspend fun deleteAll()
}

internal fun List<BudgetRuleEntity>.effectiveBudgets(month: Long): Map<String, Long> =
  filter { it.startMonth <= month && (!it.onlyThisMonth || it.startMonth == month) }
    .groupBy { it.categoryId }
    .mapValues { (_, rules) ->
      rules.maxWith(compareBy<BudgetRuleEntity> { it.onlyThisMonth }.thenBy { it.startMonth }).amountCents
    }
    .filterValues { it > 0 }

enum class BudgetScope(val label: String) {
  FromThisMonth("本月及以后"),
  ThisMonth("仅本月"),
}
