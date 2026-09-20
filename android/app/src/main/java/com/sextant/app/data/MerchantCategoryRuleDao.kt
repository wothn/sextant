package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface MerchantCategoryRuleDao {
  @Query("SELECT * FROM merchant_category_rules ORDER BY merchantName")
  suspend fun listAll(): List<MerchantCategoryRuleEntity>

  @Query("SELECT * FROM merchant_category_rules WHERE merchantKey = :key")
  suspend fun find(key: String): MerchantCategoryRuleEntity?

  @Upsert
  suspend fun upsert(rule: MerchantCategoryRuleEntity)

  @Query("DELETE FROM merchant_category_rules WHERE merchantKey = :key")
  suspend fun delete(key: String)

  @Query("DELETE FROM merchant_category_rules")
  suspend fun deleteAll()
}
