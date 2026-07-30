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
        ORDER BY sort_order, name COLLATE NOCASE
        """,
    )
    fun observeForSource(sourceId: String): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Upsert
    fun upsertBlocking(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE source_id = :sourceId")
    suspend fun deleteForSource(sourceId: String)
}
