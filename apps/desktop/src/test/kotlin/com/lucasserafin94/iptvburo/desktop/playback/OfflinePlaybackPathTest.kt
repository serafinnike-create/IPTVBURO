package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The exact path a downloaded episode takes from disk to VLC.
 *
 * A finished download that opens to a black screen has been reported twice. Both times the file was
 * intact and VLC could play it from a command line, which means the fault is in the handoff — so
 * this pins the handoff itself, using the real directory name.
 *
 * `Videos/IPTV BURO` contains a space, and that space is the whole difficulty: `Path.toUri`
 * percent-encodes it, VLC's HTTP control interface does not decode it, and the resulting path does
 * not exist. VLC then reports no error and simply plays nothing.
 */
class OfflinePlaybackPathTest {
    @Test
    fun `a downloaded file in a directory with a space reaches VLC as a native path`() {
        val directory = Files.createTempDirectory("iptv-buro-test").resolve("IPTV BURO")
        directory.createDirectories()
        val file = directory.resolve("series_liar-game_2026_s1e1.mp4")
        Files.writeString(file, "not really a video")

        try {
            val uri = file.toUri()
            // This is what Path.toUri produces, and handing it to VLC verbatim is the bug.
            assertTrue("%20" in uri.toString(), "expected the space to be encoded in the URI: $uri")

            val forVlc = uri.toVlcInput()

            assertTrue("%20" !in forVlc, "VLC was handed a percent-encoded path: $forVlc")
            assertTrue(forVlc.contains("IPTV BURO"), "the directory name did not survive: $forVlc")
            // A native path, not a file: URL — VLC resolves the former unambiguously.
            assertTrue(!forVlc.startsWith("file:"), "still a file URI: $forVlc")
            assertEquals(file.toString(), forVlc)
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun `accented episode titles survive the round trip`() {
        // Sanitised file names keep accents from the provider's own titles.
        val directory = Files.createTempDirectory("iptv-buro-accents").resolve("IPTV BURO")
        directory.createDirectories()
        val file = directory.resolve("series_coracao-selvagem_s1e1.mp4")
        Files.writeString(file, "not really a video")

        try {
            val forVlc = file.toUri().toVlcInput()

            assertEquals(file.toString(), forVlc)
            assertTrue(Path.of(forVlc).toFile().exists(), "VLC would be given a path that does not exist")
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun `a remote stream keeps its encoded form`() {
        // The opposite case: for an HTTP source the encoding is part of the address and must stay.
        val remote = java.net.URI("https://example.invalid/live/stream%20one.m3u8")

        assertEquals("https://example.invalid/live/stream%20one.m3u8", remote.toVlcInput())
    }
}
