package com.sextant.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** 一条命中「支付应用 + 资金方向 + 事件类型」的候选规则，业务侧再按 pattern 决定是否真的匹配。 */
internal data class ClassificationRuleCandidate(
  @ColumnInfo(name = "categoryId") val categoryId: String,
  @ColumnInfo(name = "pattern") val pattern: String,
  @ColumnInfo(name = "matchMode") val matchMode: String,
)

@Dao
internal interface ClassificationRuleDao {
  /**
   * 候选规则：同一个支付应用、同一个资金方向、同一类事件，且指向仍然启用的分类。
   *
   * 连接分类表既过滤了归档分类的规则，也顺带校验了「规则的分类必须与资金方向同型」。
   * 排序固定为优先级从高到低，保证多条规则命中时结果稳定——所以匹配循环必须
   * 按返回顺序取第一条命中的，不能自己再排。
   */
  @Query(
    """
    SELECT r.categoryId AS categoryId, r.pattern AS pattern, r.matchMode AS matchMode
    FROM classification_rules r
    JOIN categories c ON c.id = r.categoryId AND c.isActive = 1
    WHERE r.sourcePackage = :sourcePackage AND r.direction = :direction
      AND r.automationKind = :automationKind AND c.type = :direction
    ORDER BY r.priority DESC, r.updatedAt DESC
    """,
  )
  suspend fun listCandidates(
    sourcePackage: String,
    direction: TransactionType,
    automationKind: AutomationKind,
  ): List<ClassificationRuleCandidate>

  @Query("SELECT * FROM classification_rules ORDER BY priority DESC, createdAt ASC")
  suspend fun listAll(): List<ClassificationRuleEntity>

  @Insert
  suspend fun insert(rule: ClassificationRuleEntity)

  @Query("DELETE FROM classification_rules")
  suspend fun deleteAll()
}
