package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface AppSettingDao {
  @Query("SELECT value FROM app_settings WHERE key = :key")
  suspend fun findValue(key: String): String?

  @Query("SELECT * FROM app_settings WHERE key LIKE 'automation_%'")
  suspend fun listAutomationSettings(): List<AppSettingEntity>

  @Query("SELECT * FROM app_settings WHERE key NOT LIKE 'automation_%' ORDER BY key ASC")
  suspend fun listBackupSettings(): List<AppSettingEntity>

  @Query("SELECT * FROM app_settings ORDER BY key ASC")
  suspend fun listAll(): List<AppSettingEntity>

  /**
   * 写入或覆盖一项设置。
   *
   * 用 upsert 而不是 `INSERT OR REPLACE`：后者语义上是「删掉再插一行」，
   * 一旦将来给设置表加上触发器或外键，它会以删除的方式触发。upsert 只做更新。
   */
  @Upsert
  suspend fun upsert(setting: AppSettingEntity)

  @Query("DELETE FROM app_settings")
  suspend fun deleteAll()
}
