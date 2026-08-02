package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["source_id"]),
        Index(value = ["source_id", "name"]),
        Index(value = ["source_id", "content_type"]),
        Index(
            value = ["source_id", "content_type", "provider_category_id"],
            unique = true,
        ),
    ],
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val name: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "content_type", defaultValue = "'UNKNOWN'")
    val contentType: String = "UNKNOWN",
    @ColumnInfo(name = "provider_category_id")
    val providerCategoryId: String? = null,
)
