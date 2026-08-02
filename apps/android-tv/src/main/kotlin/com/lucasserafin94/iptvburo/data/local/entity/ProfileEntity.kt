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
)
