package com.lucasserafin94.iptvburo.data.mapper

import com.lucasserafin94.iptvburo.playlist.ParsedChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistEntityMapperTest {
    private val mapper = PlaylistEntityMapper()

    @Test
    fun `groups category names case insensitively in first-seen order`() {
        val mapped =
            mapper.map(
                sourceId = "source-1",
                channels = listOf(
                    channel(name = "One", group = "News"),
                    channel(name = "Two", group = " news "),
                    channel(name = "Three", group = "Sports"),
                ),
            )

        assertEquals(listOf("News", "Sports"), mapped.categories.map { it.name })
        assertEquals(listOf(0, 1), mapped.categories.map { it.sortOrder })
        assertEquals(
            mapped.channels[0].categoryId,
            mapped.channels[1].categoryId,
        )
    }

    @Test
    fun `skips unsupported and malformed stream URLs`() {
        val mapped =
            mapper.map(
                sourceId = "source-1",
                channels = listOf(
                    channel(name = "HTTPS", streamUri = "https://media.example/live.m3u8"),
                    channel(name = "File", streamUri = "file:///sdcard/private.m3u8"),
                    channel(name = "Malformed", streamUri = "not a URL"),
                    channel(name = "Missing host", streamUri = "http:stream"),
                ),
            )

        assertEquals(listOf("HTTPS"), mapped.channels.map { it.name })
        assertEquals(3, mapped.skippedChannelCount)
    }

    @Test
    fun `persists playback headers needed by Media3 but drops credentials`() {
        val mapped =
            mapper.map(
                sourceId = "source-1",
                channels = listOf(
                    ParsedChannel(
                        name = "Open",
                        streamUri = "https://media.example/live.m3u8",
                        requestHeaders = mapOf(
                            "User-Agent" to "IPTV BURO",
                            "Referer" to "https://portal.example/",
                            "Origin" to "https://portal.example",
                            "Authorization" to "Bearer must-not-be-persisted",
                            "Cookie" to "session=must-not-be-persisted",
                        ),
                    ),
                ),
            )

        val channel = mapped.channels.single()
        assertEquals("IPTV BURO", channel.userAgent)
        assertEquals("https://portal.example/", channel.referer)
        assertEquals("https://portal.example", channel.origin)
    }

    @Test
    fun `leaves category empty for ungrouped channels`() {
        val mapped =
            mapper.map(
                sourceId = "source-1",
                channels = listOf(channel(name = "Ungrouped", group = "  ")),
            )

        assertEquals(0, mapped.categories.size)
        assertNull(mapped.channels.single().categoryId)
    }

    @Test
    fun `mapped persistence models redact playback values from diagnostics`() {
        val secretMarker = "synthetic-private-marker"
        val mapped =
            mapper.map(
                sourceId = "source-1",
                channels = listOf(
                    ParsedChannel(
                        name = "Diagnostic safety",
                        streamUri = "https://media.example/live.m3u8?token=$secretMarker",
                        logoUri = "https://media.example/logo.png?token=$secretMarker",
                        requestHeaders =
                            mapOf(
                                "User-Agent" to secretMarker,
                                "Referer" to "https://portal.example/$secretMarker",
                                "Origin" to "https://$secretMarker.example",
                            ),
                    ),
                ),
            )

        val channel = mapped.channels.single()
        assertFalse(channel.toString().contains(secretMarker))
        assertFalse(MappedChannel(null, channel).toString().contains(secretMarker))
        assertFalse(mapped.toString().contains(secretMarker))
    }

    private fun channel(
        name: String,
        streamUri: String = "https://media.example/$name.m3u8",
        group: String? = null,
    ): ParsedChannel =
        ParsedChannel(
            name = name,
            streamUri = streamUri,
            groupTitle = group,
        )
}
