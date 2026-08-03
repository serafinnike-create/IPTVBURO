package com.lucasserafin94.iptvburo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lucasserafin94.iptvburo.data.local.entity.LibraryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryEntryDao {
    @Query("SELECT content_key FROM library_entries WHERE profile_id = :profileId")
    fun observeKeys(profileId: String): Flow<List<String>>

    @Query("SELECT content_key FROM library_entries WHERE profile_id = :profileId")
    suspend fun keys(profileId: String): List<String>

    @Query(
        "SELECT * FROM library_entries WHERE profile_id = :profileId " +
            "ORDER BY added_at_epoch_millis DESC LIMIT :limit",
    )
    suspend fun entries(
        profileId: String,
        limit: Int,
    ): List<LibraryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entry: LibraryEntryEntity)

    @Query("DELETE FROM library_entries WHERE profile_id = :profileId AND content_key = :contentKey")
    suspend fun remove(
        profileId: String,
        contentKey: String,
    )

    @Query("DELETE FROM library_entries WHERE profile_id = :profileId")
    suspend fun clear(profileId: String)
}
