package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A favourite, keyed on content identity rather than on a row in the current playlist.
 *
 * `favorites` keys on `channel_id` and declares `onDelete = CASCADE` against `channels`. Because
 * every import mints a fresh `sourceId` (a random UUID) and therefore fresh channel rows, replacing
 * a playlist made the database **delete the user's favourites outright** — not orphan them, delete
 * them. This table survives that because it holds no foreign key into the catalogue at all.
 *
 * [contentKey] is a `ContentIdentity`: normalised title plus year, so the same film matches across
 * providers that number and decorate their catalogues differently.
 *
 * [lastKnownTitle] exists so the library can still show something meaningful for an entry whose
 * content is not present in the currently connected playlist.
 */
@Entity(
    tableName = "library_entries",
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
data class LibraryEntryEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,
    @ColumnInfo(name = "content_key")
    val contentKey: String,
    @ColumnInfo(name = "last_known_title")
    val lastKnownTitle: String,
    @ColumnInfo(name = "content_type")
    val contentType: String,
    @ColumnInfo(name = "added_at_epoch_millis")
    val addedAtEpochMillis: Long,
)
