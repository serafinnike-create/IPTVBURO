package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "avatar_key")
    val avatarKey: String,
    @ColumnInfo(name = "profile_type")
    val profileType: String,
    @ColumnInfo(name = "language_tag")
    val languageTag: String,
    @ColumnInfo(name = "audio_language_tag")
    val audioLanguageTag: String,
    @ColumnInfo(name = "subtitle_language_tag")
    val subtitleLanguageTag: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    /**
     * A photo the user chose, stored as a content URI, or null for a drawn avatar.
     *
     * The URI rather than a copy of the image: Android's photo picker grants persistable read
     * access to exactly the item chosen, which is narrower than holding a duplicate of somebody's
     * photograph inside the app's own storage.
     */
    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,
    /**
     * The playlist this profile signs in to, or null to use whichever source is available.
     *
     * Null is the state every existing profile starts in and stays valid indefinitely: a household
     * with one playlist has nothing to choose between, and forcing a choice on them would be a
     * setting that only ever has one answer.
     *
     * Deliberately not a foreign key. A source is deleted and re-imported with a fresh id every
     * time a playlist is replaced, and a cascade would silently reset the profile's choice; a
     * dangling id resolves to "no preference", which is the same as never having chosen.
     */
    @ColumnInfo(name = "source_id")
    val sourceId: String? = null,
)
