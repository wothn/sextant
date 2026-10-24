package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AutomationDiagnosticDao {
  @Insert suspend fun insert(entry: AutomationDiagnosticEntity)

  @Query("SELECT * FROM automation_diagnostics ORDER BY id ASC")
  suspend fun listAll(): List<AutomationDiagnosticEntity>

  @Query("SELECT COUNT(*) FROM automation_diagnostics")
  fun observeCount(): Flow<Int>

  @Query("SELECT * FROM automation_diagnostics ORDER BY id DESC LIMIT 8")
  fun observeRecent(): Flow<List<AutomationDiagnosticEntity>>

  @Query("DELETE FROM automation_diagnostics WHERE createdAt < :before OR id NOT IN (SELECT id FROM automation_diagnostics ORDER BY id DESC LIMIT :limit)")
  suspend fun prune(before: Long, limit: Int)

  @Query("DELETE FROM automation_diagnostics")
  suspend fun deleteAll()
}
