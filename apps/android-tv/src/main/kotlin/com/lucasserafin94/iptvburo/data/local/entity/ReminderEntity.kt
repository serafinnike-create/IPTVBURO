package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A title someone asked to be reminded about, per profile.
 *
 * Keyed on the content identity rather than on a `channels` row, unlike favourites. That is not a
 * stylistic difference — it is what makes the feature possible at all:
 *
 * - an **upcoming** title has no catalogue row yet, so there is nothing for a foreign key to point
 *   at when the reminder is created;
 * - favourites carry `ON DELETE CASCADE` to `channels`, so re-importing a playlist deletes them.
 *   A reminder that vanished when the list was refreshed would lose exactly the titles it exists
 *   to watch for.
 *
 * The cascade to `profiles` is kept: a deleted profile has no reminders, and leaving orphans would
 * mean notifying on behalf of somebody who no longer exists.
 */
@Entity(
    tableName = "reminders",
    primaryKeys = ["profile_id", "content_key"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class ReminderEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    /** `ContentIdentity.key`: what the title *is*, independent of any provider's numbering. */
    @ColumnInfo(name = "content_key")
    val contentKey: String,

    /** Shown in the reminders page and the notification. Presentation only. */
    @ColumnInfo(name = "title")
    val title: String,

    /**
     * Poster, when one is known.
     *
     * Only ever a public metadata URL or a local file. A provider-hosted artwork address commonly
     * carries the subscriber's credentials in its path, and this row outlives the playlist it came
     * from — storing one would keep a credential long after the source was removed.
     */
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String? = null,

    /**
     * ISO date the title is expected, or null when it is already available.
     *
     * Text rather than an epoch, because a release date is a *day* rather than an instant: the
     * provider says "12 September", not "12 September at 03:00 UTC", and turning that into a
     * timestamp invents a precision that shifts the day across time zones.
     */
    @ColumnInfo(name = "release_date")
    val releaseDate: String? = null,

    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)
