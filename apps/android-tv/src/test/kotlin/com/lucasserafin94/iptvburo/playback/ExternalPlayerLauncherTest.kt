package com.lucasserafin94.iptvburo.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlayerLauncherTest {
    @Test
    fun `only network addresses are handed to another player`() {
        assertTrue(ExternalPlayerLauncher.isPlayableAddress("http://example.invalid/live/1.ts"))
        assertTrue(ExternalPlayerLauncher.isPlayableAddress("https://example.invalid/movie/1.mp4"))
        assertFalse(ExternalPlayerLauncher.isPlayableAddress("file:///sdcard/Movies/a.mp4"))
        assertFalse(ExternalPlayerLauncher.isPlayableAddress("content://downloads/1"))
        assertFalse(ExternalPlayerLauncher.isPlayableAddress(""))
    }

    @Test
    fun `HLS and DASH are named, everything else goes as video`() {
        assertEquals("application/x-mpegURL", ExternalPlayerLauncher.mimeTypeFor("https://x.invalid/a/index.m3u8?token=1"))
        assertEquals("application/dash+xml", ExternalPlayerLauncher.mimeTypeFor("https://x.invalid/a/manifest.MPD"))
        assertEquals("video/*", ExternalPlayerLauncher.mimeTypeFor("http://x.invalid/live/user/pass/123.ts"))
        assertEquals("video/*", ExternalPlayerLauncher.mimeTypeFor("http://x.invalid/live/user/pass/123"))
    }

    @Test
    fun `request headers travel as the name and value pairs players read`() {
        assertArrayEquals(
            arrayOf("User-Agent", "BuroTest/1.0", "Referer", "https://x.invalid/"),
            ExternalPlayerLauncher.headerExtra(linkedMapOf("User-Agent" to "BuroTest/1.0", "Referer" to "https://x.invalid/")),
        )
        assertNull(ExternalPlayerLauncher.headerExtra(emptyMap()))
        assertNull(ExternalPlayerLauncher.headerExtra(mapOf("" to "x", "X-Empty" to "")))
    }
}
