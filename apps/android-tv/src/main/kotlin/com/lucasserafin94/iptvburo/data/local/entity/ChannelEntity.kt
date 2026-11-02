package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["source_id"]),
        Index(value = ["category_id"]),
        Index(value = ["source_id", "tvg_id"]),
        Index(value = ["source_id", "name"]),
        // (source_id, content_type, category_id, sort_order) used to sit here too. It is a prefix of
        // the index below, so it served no query the longer one cannot, and cost one more B-tree
        // write on every row of a hundred-thousand-row import.
        Index(value = ["source_id", "content_type", "category_id", "sort_order", "id"]),
        // The home's "released this year" and "recently added" rows. Without these each one read
        // every row of the source and sorted them all to keep two dozen.
        Index(value = ["source_id", "year"]),
        // Newest first, then playlist order: matching the row's ORDER BY column for column and
        // direction for direction is what lets SQLite read the first two dozen straight off the
        // index and stop, instead of sorting the source. A plain ascending index was ignored.
        Index(
            value = ["source_id", "added_at_epoch_seconds", "sort_order", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            value = ["source_id", "content_type", "provider_item_id"],
            unique = true,
        ),
    ],
)
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String?,
    @ColumnInfo(name = "tvg_id")
    val tvgId: String?,
    @ColumnInfo(name = "tvg_name")
    val tvgName: String?,
    val name: String,
    @ColumnInfo(name = "logo_url")
    val logoUrl: String?,
    @ColumnInfo(name = "stream_url")
    val streamUrl: String,
    @ColumnInfo(name = "user_agent")
    val userAgent: String?,
    val referer: String?,
    val origin: String?,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "content_type", defaultValue = "'UNKNOWN'")
    val contentType: String = "UNKNOWN",
    @ColumnInfo(name = "provider_item_id")
    val providerItemId: String? = null,
    @ColumnInfo(name = "container_extension")
    val containerExtension: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    @ColumnInfo(name = "added_at_epoch_seconds")
    val addedAtEpochSeconds: Long? = null,
    /** Days of recordings the provider keeps for a live channel; null when it keeps none. */
    @ColumnInfo(name = "catch_up_days")
    val catchUpDays: Int? = null,
) {
    /**
     * Stream and artwork URLs may contain provider credentials for legacy M3U sources.
     * Keep them, and all playback header values, out of diagnostics by construction.
     */
    override fun toString(): String =
        "ChannelEntity(" +
            "id=$id, sourceId=$sourceId, categoryId=$categoryId, tvgId=$tvgId, " +
            "tvgName=$tvgName, name=$name, " +
            "logoUrl=${if (logoUrl == null) "null" else "<redacted>"}, " +
            "streamUrl=${if (streamUrl.isBlank()) "<unresolved>" else "<redacted>"}, " +
            "userAgent=${if (userAgent == null) "null" else "<redacted>"}, " +
            "referer=${if (referer == null) "null" else "<redacted>"}, " +
            "origin=${if (origin == null) "null" else "<redacted>"}, " +
            "sortOrder=$sortOrder, contentType=$contentType, " +
            "providerItemId=$providerItemId, containerExtension=$containerExtension, " +
            "year=$year, rating=$rating, addedAtEpochSeconds=$addedAtEpochSeconds" +
            ")"
}
