package com.lucasserafin94.iptvburo.data.mapper

import com.lucasserafin94.iptvburo.data.local.entity.CategoryEntity
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.security.ChannelFieldCipher
import com.lucasserafin94.iptvburo.playlist.ParsedChannel
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class PlaylistEntityMapper @Inject constructor(
    private val channelFieldCipher: ChannelFieldCipher,
) {
    fun newSession(sourceId: String): PlaylistMappingSession =
        PlaylistMappingSession(sourceId)

    fun map(
        sourceId: String,
        channels: List<ParsedChannel>,
    ): MappedPlaylist {
        val session = newSession(sourceId)
        val categories = mutableListOf<CategoryEntity>()
        val channelEntities =
            buildList {
                for (channel in channels) {
                    session.mapChannel(channel)?.let { mapped ->
                        mapped.newCategory?.let(categories::add)
                        add(mapped.channel)
                    }
                }
            }

        return MappedPlaylist(
            categories = categories,
            channels = channelEntities,
            skippedChannelCount = session.skippedChannelCount,
        )
    }

    inner class PlaylistMappingSession internal constructor(
        private val sourceId: String,
    ) {
        private val categoryByNormalizedName = linkedMapOf<String, CategoryEntity>()
        private var nextSortOrder = 0

        var skippedChannelCount: Int = 0
            private set

        val categoryCount: Int
            get() = categoryByNormalizedName.size

        fun mapChannel(channel: ParsedChannel): MappedChannel? {
            val sortOrder = nextSortOrder
            nextSortOrder += 1

            if (!channel.hasSupportedStreamUri()) {
                skippedChannelCount += 1
                return null
            }

            var newCategory: CategoryEntity? = null
            val categoryId = channel.groupTitle
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { categoryName ->
                    val normalizedName = categoryName.lowercase(Locale.ROOT)
                    categoryByNormalizedName[normalizedName]?.id
                        ?: CategoryEntity(
                            id = stableId("$sourceId:category:$normalizedName"),
                            sourceId = sourceId,
                            name = categoryName,
                            sortOrder = categoryByNormalizedName.size,
                        ).also { category ->
                            categoryByNormalizedName[normalizedName] = category
                            newCategory = category
                        }.id
                }

            return MappedChannel(
                newCategory = newCategory,
                channel =
                    channel.toEntity(
                        sourceId = sourceId,
                        categoryId = categoryId,
                        sortOrder = sortOrder,
                    ),
            )
        }

        private fun ParsedChannel.toEntity(
            sourceId: String,
            categoryId: String?,
            sortOrder: Int,
        ): ChannelEntity {
            val normalizedTvgId = tvgId?.trim()?.takeIf(String::isNotEmpty)
            val normalizedStreamUri = streamUri.trim()
            val identity = normalizedTvgId ?: normalizedStreamUri
            val headerLookup = requestHeaders.entries.associate { (name, value) ->
                name.lowercase(Locale.ROOT) to value
            }

            return ChannelEntity(
                id = stableId("$sourceId:channel:$identity:$sortOrder"),
                sourceId = sourceId,
                categoryId = categoryId,
                tvgId = normalizedTvgId,
                tvgName = tvgName?.trim()?.takeIf(String::isNotEmpty),
                name = name.trim().ifEmpty {
                    tvgName?.trim().orEmpty().ifEmpty { "Untitled channel" }
                },
                logoUrl = logoUri?.trim()?.takeIf(String::isNotEmpty),
                streamUrl = channelFieldCipher.encrypt(normalizedStreamUri).orEmpty(),
                userAgent = channelFieldCipher.encrypt(headerLookup["user-agent"]?.takeIf(String::isNotBlank)),
                referer = channelFieldCipher.encrypt(headerLookup["referer"]?.takeIf(String::isNotBlank)),
                origin = channelFieldCipher.encrypt(headerLookup["origin"]?.takeIf(String::isNotBlank)),
                sortOrder = sortOrder,
            )
        }

        private fun ParsedChannel.hasSupportedStreamUri(): Boolean =
            runCatching {
                val uri = URI(streamUri.trim())
                uri.scheme?.lowercase(Locale.ROOT) in SUPPORTED_STREAM_SCHEMES &&
                    !uri.host.isNullOrBlank()
            }.getOrDefault(false)
    }

    private fun stableId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()

    private companion object {
        val SUPPORTED_STREAM_SCHEMES = setOf("http", "https")
    }
}

data class MappedChannel(
    val newCategory: CategoryEntity?,
    val channel: ChannelEntity,
) {
    override fun toString(): String =
        "MappedChannel(" +
            "newCategory=${if (newCategory == null) "null" else newCategory.id}, " +
            "channelId=${channel.id})"
}

data class MappedPlaylist(
    val categories: List<CategoryEntity>,
    val channels: List<ChannelEntity>,
    val skippedChannelCount: Int,
) {
    override fun toString(): String =
        "MappedPlaylist(" +
            "categoryCount=${categories.size}, channelCount=${channels.size}, " +
            "skippedChannelCount=$skippedChannelCount)"
}
