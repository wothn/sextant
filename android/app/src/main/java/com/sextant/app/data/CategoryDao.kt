package com.sextant.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface CategoryDao {
  /**
   * 启用的分类，排序与迁移前一致：先支出后收入，父分类排在自己的子分类前面。
   *
   * `parentCategoryId IS NOT NULL ASC` 是 SQLite 的写法，null 排在最前，
   * 所以根分类先出、子分类随后，UI 才能按「父 → 子」成组渲染。
   */
  @Query(
    """
    SELECT * FROM categories
    WHERE isActive = 1
    ORDER BY type ASC, parentCategoryId IS NOT NULL ASC, createdAt ASC
    """,
  )
  suspend fun listActive(): List<CategoryEntity>

  @Query(
    """
    SELECT id FROM categories
    WHERE name = :name AND type = :type AND parentCategoryId IS NULL
    LIMIT 1
    """,
  )
  suspend fun findRootId(name: String, type: TransactionType): String?

  @Query(
    """
    SELECT id FROM categories
    WHERE name = :name AND type = :type AND parentCategoryId = :parentCategoryId
    LIMIT 1
    """,
  )
  suspend fun findChildId(name: String, type: TransactionType, parentCategoryId: String): String?

  @Insert
  suspend fun insert(category: CategoryEntity)

  @Query(
    """
    UPDATE categories
    SET name = :name, type = :type, color = :color,
      includeInSpending = :includeInSpending, parentCategoryId = :parentCategoryId
    WHERE id = :id
    """,
  )
  suspend fun update(
    id: String,
    name: String,
    type: TransactionType,
    color: Long,
    includeInSpending: Boolean,
    parentCategoryId: String?,
  )

  /** 停用分类会连带停用它的直接子分类，否则子分类会变成孤儿挂在列表里。 */
  @Query("UPDATE categories SET isActive = 0 WHERE id = :id OR parentCategoryId = :id")
  suspend fun archive(id: String)

  /** 撤销停用：与 [archive] 同范围恢复，保证父分类回来时子分类也回来。 */
  @Query("UPDATE categories SET isActive = 1 WHERE id = :id OR parentCategoryId = :id")
  suspend fun unarchive(id: String)

  @Query("SELECT COUNT(*) FROM categories")
  suspend fun count(): Long

  /** 导出备份用：包含已归档的分类，按创建顺序。 */
  @Query("SELECT * FROM categories ORDER BY createdAt ASC")
  suspend fun listAllForBackup(): List<CategoryEntity>

  @Query("DELETE FROM categories")
  suspend fun deleteAll()
}
