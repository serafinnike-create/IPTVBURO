package com.lucasserafin94.iptvburo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lucasserafin94.iptvburo.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT * FROM categories
        WHERE source_id = :sourceId
          AND (:contentType IS NULL OR content_type = :contentType)
        ORDER BY sort_order, name COLLATE NOCASE
        """,
    )
    fun observeForSource(
        sourceId: String,
        contentType: String?,
    ): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT category_id AS categoryId, COUNT(*) AS itemCount
        FROM channels
        WHERE source_id = :sourceId
          AND (:contentType IS NULL OR content_type = :contentType)
        GROUP BY category_id
        """,
    )
    fun observeItemCounts(
        sourceId: String,
        contentType: String?,
    ): Flow<List<CategoryItemCount>>

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Upsert
    fun upsertAllBlocking(categories: List<CategoryEntity>)

    @Upsert
    fun upsertBlocking(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE source_id = :sourceId")
    suspend fun deleteForSource(sourceId: String)
}

data class CategoryItemCount(
    val categoryId: String?,
    val itemCount: Int,
)
