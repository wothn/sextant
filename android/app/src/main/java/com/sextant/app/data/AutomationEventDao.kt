package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface AutomationEventDao {
  /**
   * 记下一条待处理的支付通知。
   *
   * `eventKey` 上有唯一索引，[OnConflictStrategy.IGNORE] 让重复投递的同一条通知
   * 直接落到 -1，也就是「这次不插入」。幂等性由约束保证，而不是先查再插——
   * 后者在监听服务被系统重复唤醒时会有竞态。
   */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIgnoringDuplicates(event: AutomationEventEntity): Long

  @Query("SELECT * FROM automation_events WHERE id = :id")
  suspend fun findById(id: String): AutomationEventEntity?

  @Query(
    """
    SELECT id, sourcePackage, postedAt, status, parser, failureCode, parsedSummary, transactionId
    FROM automation_events ORDER BY createdAt DESC LIMIT :limit
    """,
  )
  suspend fun listRecentSummaries(limit: Int): List<AutomationEventSummary>

  @Query(
    """
    SELECT EXISTS(
      SELECT 1 FROM automation_events
      WHERE sourcePackage = :sourcePackage AND providerReference = :reference AND id != :eventId
    )
    """,
  )
  suspend fun providerReferenceExists(sourcePackage: String, reference: String, eventId: String): Boolean

  /**
   * 更新事件的处理状态。
   *
   * 每个「可空字段」都配一个 `setXxx` 开关，而不是靠「传 null 就是不更新」：
   * `failureCode` 和 `transactionId` 都需要被显式清空（重试、撤销都要求它们回到 null），
   * 用 null 兼职表达「不改」会让这两个语义撞在一起。
   */
  @Query(
    """
    UPDATE automation_events
    SET status = :status, processedAt = :processedAt,
      parser = COALESCE(:parser, parser),
      failureCode = CASE WHEN :setFailureCode THEN :failureCode ELSE failureCode END,
      parsedSummary = COALESCE(:parsedSummary, parsedSummary),
      transactionId = CASE WHEN :setTransactionId THEN :transactionId ELSE transactionId END,
      providerReference = COALESCE(:providerReference, providerReference),
      encryptedPayload = CASE WHEN :clearPayload THEN NULL ELSE encryptedPayload END,
      payloadIv = CASE WHEN :clearPayload THEN NULL ELSE payloadIv END
    WHERE id = :id
    """,
  )
  suspend fun updateStatus(
    id: String,
    status: AutomationEventStatus,
    processedAt: Long,
    parser: String?,
    failureCode: String?,
    setFailureCode: Boolean,
    parsedSummary: String?,
    transactionId: String?,
    setTransactionId: Boolean,
    providerReference: String?,
    clearPayload: Boolean,
  )

  /**
   * 把失败事件退回待处理，供「重试」使用。
   *
   * 条件里带 `encryptedPayload IS NOT NULL`：原始载荷已经被清理掉的事件重试没有意义，
   * 让它返回 0 行比排一个注定失败的任务更好。返回是否真的改动了行，调用方据此提示用户。
   */
  @Query(
    """
    UPDATE automation_events
    SET status = :capturedStatus, failureCode = NULL, processedAt = NULL
    WHERE id = :id AND status = :failedStatus AND encryptedPayload IS NOT NULL
    """,
  )
  suspend fun markCapturedForRetry(
    id: String,
    capturedStatus: AutomationEventStatus,
    failedStatus: AutomationEventStatus,
  ): Int

  /** 撤销自动入账时把事件退回未处理状态，并解除它与流水的关联。 */
  @Query("UPDATE automation_events SET status = :status, transactionId = NULL, processedAt = :processedAt WHERE id = :id")
  suspend fun markUndone(id: String, status: AutomationEventStatus, processedAt: Long)

  /** 保留期到了就丢掉原始载荷，只留下处理结果，避免加密后的通知长期堆在库里。 */
  @Query("UPDATE automation_events SET encryptedPayload = NULL, payloadIv = NULL WHERE createdAt < :before")
  suspend fun clearPayloadsOlderThan(before: Long)

  @Query("DELETE FROM automation_events WHERE createdAt < :before AND status != :keepStatus")
  suspend fun deleteOlderThan(before: Long, keepStatus: AutomationEventStatus)

  @Query("DELETE FROM automation_events")
  suspend fun deleteAll()
}
