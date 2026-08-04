package com.lucasserafin94.iptvburo.desktop.playback

import java.net.URI
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the app hands VLC for a source.
 *
 * A downloaded file lives under "Videos/IPTV BURO", whose space Path.toUri percent-encodes. Passing
 * that encoded form through VLC's HTTP control interface made it open a path that does not exist,
 * so an offline title reported no error and never started.
 */
class VlcInputTest {
    @Test
    fun `a local file becomes a native path, not a percent-encoded uri`() {
        val file = Path.of("C:", "Users", "someone", "Videos", "IPTV BURO", "movie_x_2026.mp4")

        val input = file.toUri().toVlcInput()

        assertTrue(!input.contains("%20"), "the space must not stay encoded: $input")
        assertTrue(!input.startsWith("file:"), "VLC gets the path itself: $input")
        assertTrue(input.contains("IPTV BURO"), "the real directory name must survive: $input")
    }

    @Test
    fun `a remote stream keeps its uri`() {
        val remote = URI("http://example.test/live/1234.ts?token=abc")

        assertEquals(remote.toASCIIString(), remote.toVlcInput())
    }

    /** Encoding is part of a remote address and must not be undone. */
    @Test
    fun `a remote uri keeps its encoding`() {
        val remote = URI("http://example.test/movie/A%20Title.mp4")

        assertTrue(remote.toVlcInput().contains("%20"))
    }

    @Test
    fun `an accented file name survives`() {
        val file = Path.of("C:", "Users", "someone", "Videos", "IPTV BURO", "coração.mkv")

        val input = file.toUri().toVlcInput()

        assertTrue(input.endsWith("coração.mkv"), "was $input")
    }
}
