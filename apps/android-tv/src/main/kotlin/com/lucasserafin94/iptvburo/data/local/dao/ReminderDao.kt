package com.lucasserafin94.iptvburo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lucasserafin94.iptvburo.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    /**
     * Every reminder for a profile, newest first.
     *
     * A Flow so the button, the home row and the reminders page all repaint from one write. Marking
     * a title on the details screen has to be visible on the home screen behind it without anything
     * re-fetching.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE profile_id = :profileId
        ORDER BY created_at_epoch_millis DESC
        """,
    )
    fun observeForProfile(profileId: String): Flow<List<ReminderEntity>>

    /**
     * The same list, read once.
     *
     * The notification worker runs without a screen, so it cannot collect a Flow: it wakes, asks
     * what is outstanding, decides, and exits.
     */
    @Query("SELECT * FROM reminders WHERE profile_id = :profileId")
    suspend fun forProfile(profileId: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE profile_id = :profileId AND content_key = :contentKey")
    suspend fun remove(profileId: String, contentKey: String)

    /**
     * Whether a title is already marked, for the button's state.
     *
     * Counted rather than fetched: the screen only needs to know which way the button points.
     */
    @Query(
        """
        SELECT COUNT(*) > 0 FROM reminders
        WHERE profile_id = :profileId AND content_key = :contentKey
        """,
    )
    suspend fun isMarked(profileId: String, contentKey: String): Boolean

    /**
     * Reminders across every profile, for the scheduled notification.
     *
     * The worker has no active profile — nobody has opened the app — so it cannot ask "what is
     * outstanding for the person watching". It asks for everything and the caller groups by
     * profile, which is also what stops one household member's reminders being announced under
     * another's name.
     */
    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>
}
