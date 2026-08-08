package com.lucasserafin94.iptvburo.desktop.playback

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a file path reaches VLC through its HTTP control interface.
 *
 * This is the bug behind a downloaded film that opens to a black screen. The engine starts, the
 * surface is valid — the log shows `handle=1114606 surface=1536x604` — and VLC then reports
 * `state=stopped length=0`, meaning it opened nothing at all.
 *
 * The cause is `URLEncoder`, which implements **HTML form encoding**, not URL encoding. Its one
 * famous difference is the space: a form encodes it as `+`, a URL as `%20`. VLC decodes the query
 * as a URL, so `IPTV BURO` arrives as `IPTV+BURO` — a directory that does not exist. VLC reports no
 * error for a path it cannot open; it simply plays nothing.
 *
 * The app's own download directory is `Videos/IPTV BURO`. Every offline title went through this.
 */
class VlcQueryEncodingTest {
    private val realPath = """C:\Users\seraf\Videos\IPTV BURO\series_liar-game_2026_s1e1.mp4"""

    /** The old behaviour, kept so the difference is stated rather than remembered. */
    @Test
    fun `form encoding turns a space into a plus`() {
        val formEncoded = URLEncoder.encode(realPath, StandardCharsets.UTF_8)

        assertTrue(
            "IPTV+BURO" in formEncoded,
            "URLEncoder is form encoding: it produces a '+' that VLC reads literally. Was $formEncoded",
        )
    }

    /** What VLC needs: a space as %20, so the path it opens is the path that exists. */
    @Test
    fun `query encoding preserves the space as percent-twenty`() {
        val encoded = encodeQueryValue(realPath)

        assertTrue("IPTV%20BURO" in encoded, "was $encoded")
        assertFalse("+" in encoded, "a literal plus would become part of the path VLC opens: $encoded")
    }

    /** A round trip, because the only thing that matters is what VLC decodes back. */
    @Test
    fun `the decoded value is the original path`() {
        val decoded = java.net.URLDecoder.decode(encodeQueryValue(realPath), StandardCharsets.UTF_8)

        assertEquals(realPath, decoded)
    }

    /**
     * A remote stream survives too.
     *
     * Provider URLs carry query parameters of their own, and their `&` and `=` must stay encoded or
     * VLC would read them as further commands.
     */
    @Test
    fun `a URL with its own query is encoded whole`() {
        val stream = "https://example.invalid/live/1.m3u8?token=abc&format=ts"

        val encoded = encodeQueryValue(stream)

        assertFalse("&format" in encoded, "the stream's own & must not split the command: $encoded")
        assertEquals(stream, java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8))
    }

    /** Accented titles reach VLC intact; the sanitised file names still carry them. */
    @Test
    fun `an accented path round-trips`() {
        val accented = """C:\Users\seraf\Videos\IPTV BURO\filme_coracao-selvagem.mp4"""

        assertEquals(accented, java.net.URLDecoder.decode(encodeQueryValue(accented), StandardCharsets.UTF_8))
    }
}
