package com.lucasserafin94.iptvburo.data.mapper

import com.lucasserafin94.iptvburo.data.local.entity.CategoryEntity
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.SourceEntity
import com.lucasserafin94.iptvburo.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogEntityMappersTest {
    @Test
    fun `maps a persisted source to the domain model`() {
        val entity =
            SourceEntity(
                id = "source-1",
                displayName = "Open channels",
                type = SourceType.LOCAL_M3U.name,
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 200L,
                channelCount = 2,
            )

        val source = entity.toDomain()

        assertEquals("source-1", source.id)
        assertEquals("Open channels", source.name)
        assertEquals(SourceType.LOCAL_M3U, source.type)
        assertEquals(100L, source.createdAtEpochMillis)
        assertEquals(200L, source.updatedAtEpochMillis)
        assertEquals(2, source.channelCount)
    }

    @Test
    fun `uses the local source type when persisted data is unknown`() {
        val entity =
            SourceEntity(
                id = "source-1",
                displayName = "Legacy source",
                type = "REMOVED_TYPE",
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L,
                channelCount = 0,
            )

        assertEquals(SourceType.LOCAL_M3U, entity.toDomain().type)
    }

    @Test
    fun `maps categories without losing their source namespace`() {
        val category =
            CategoryEntity(
                id = "category-1",
                sourceId = "source-1",
                name = "News",
                sortOrder = 4,
            ).toDomain()

        assertEquals("category-1", category.id)
        assertEquals("source-1", category.sourceId)
        assertEquals("News", category.name)
        assertEquals(4, category.sortOrder)
    }

    @Test
    fun `reconstructs only supported playback headers`() {
        val channel =
            ChannelEntity(
                id = "channel-1",
                sourceId = "source-1",
                categoryId = null,
                tvgId = null,
                tvgName = null,
                name = "Open stream",
                logoUrl = null,
                streamUrl = "https://media.example/open.m3u8",
                userAgent = "IPTV BURO",
                referer = "https://portal.example/",
                origin = null,
                sortOrder = 0,
            ).toDomain()

        assertEquals(
            mapOf(
                "User-Agent" to "IPTV BURO",
                "Referer" to "https://portal.example/",
            ),
            channel.requestHeaders,
        )
        assertNull(channel.categoryId)
        assertNull(channel.tvgId)
    }
}
