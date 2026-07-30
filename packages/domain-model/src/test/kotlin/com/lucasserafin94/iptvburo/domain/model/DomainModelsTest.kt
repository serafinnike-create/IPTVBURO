package com.lucasserafin94.iptvburo.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {
    @Test
    fun `seek availability follows the declared capability`() {
        assertTrue(
            PlaybackCapabilities(seekCapability = SeekCapability.PRECISE).canSeek,
        )
        assertTrue(
            PlaybackCapabilities(seekCapability = SeekCapability.APPROXIMATE).canSeek,
        )
        assertTrue(
            PlaybackCapabilities(
                isLive = true,
                seekCapability = SeekCapability.LIVE_WINDOW,
                liveWindowDurationMillis = 30_000,
            ).canSeek,
        )
        assertFalse(
            PlaybackCapabilities(seekCapability = SeekCapability.NOT_SEEKABLE).canSeek,
        )
        assertFalse(
            PlaybackCapabilities(seekCapability = SeekCapability.UNKNOWN).canSeek,
        )
    }

    @Test
    fun `negative durations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackCapabilities(durationMillis = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackCapabilities(liveWindowDurationMillis = -1)
        }
    }

    @Test
    fun `channel string representation redacts playback secrets`() {
        val channel =
            Channel(
                id = "channel-1",
                sourceId = "source-1",
                name = "Test channel",
                streamUri = "https://media.example/stream.m3u8?token=secret-token",
                logoUri = "https://media.example/logo.png?signature=secret-signature",
                requestHeaders =
                    mapOf(
                        "Authorization" to "Bearer secret-token",
                        "Cookie" to "session=secret-cookie",
                    ),
            )

        val representation = channel.toString()

        assertFalse(representation.contains("secret-token"))
        assertFalse(representation.contains("secret-signature"))
        assertFalse(representation.contains("secret-cookie"))
        assertTrue(representation.contains("Authorization"))
        assertTrue(representation.contains("streamUri=<redacted>"))
    }

    @Test
    fun `playlist header string representation redacts urls and header values`() {
        val header =
            PlaylistHeader(
                name = "Legal fixture",
                epgUrls = listOf("https://example.invalid/epg.xml?token=epg-secret"),
                attributes =
                    mapOf(
                        "url-tvg" to "https://example.invalid/epg.xml?token=epg-secret",
                    ),
                requestHeaders = mapOf("Authorization" to "Bearer header-secret"),
            )

        val representation = header.toString()

        assertFalse(representation.contains("epg-secret"))
        assertFalse(representation.contains("header-secret"))
        assertTrue(representation.contains("epgUrlCount=1"))
        assertTrue(representation.contains("Authorization"))
    }
}
