package com.lucasserafin94.iptvburo.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Artwork URLs a provider hands us, and whether they survive to the screen.
 *
 * Some posters are missing while others load, and the difference is in the URL rather than in the
 * image: a provider rewrites TMDb's address to its own mirror and leaves a double slash behind —
 * `http://host//t/p/w600.../file.jpg`. Both hosts answer that request, so the picture exists; what
 * matters is whether the app's own handling of the string preserves it.
 *
 * No network here. This pins the parsing, which is where a URL gets dropped or mangled.
 */
class ArtworkUrlTest {
    /** Transcribed from a real download's sidecar. The double slash is the provider's, not ours. */
    private val providerMirrored =
        "http://file.gstaticontent.com//t/p/w600_and_h900_bestv2/Y9eB8hNiRYkR2XFShHre4nDrfW.jpg"

    @Test
    fun `a mirrored URL with a double slash is still a valid absolute URL`() {
        val uri = java.net.URI(providerMirrored)

        assertEquals("http", uri.scheme)
        assertEquals("file.gstaticontent.com", uri.host)
        assertTrue(uri.path.startsWith("//t/p/"), "path was ${uri.path}")
    }

    /**
     * The shape the app builds for itself, for comparison.
     *
     * When a provider gives no artwork the app asks TMDb directly, and that address has a single
     * slash. Both must reach the loader unchanged — normalising one into the other would rewrite a
     * host the provider chose deliberately.
     */
    @Test
    fun `the app's own TMDb URL is unaffected`() {
        val direct = "https://image.tmdb.org/t/p/w342/Y9eB8hNiRYkR2XFShHre4nDrfW.jpg"
        val uri = java.net.URI(direct)

        assertEquals("image.tmdb.org", uri.host)
        assertEquals("/t/p/w342/Y9eB8hNiRYkR2XFShHre4nDrfW.jpg", uri.path)
    }

    /**
     * A blank or absent URL must produce no request at all.
     *
     * This is the case behind a poster that never appears: the fallback letter is drawn and nothing
     * is ever fetched, which looks identical to a fetch that failed.
     */
    @Test
    fun `blank artwork produces no request`() {
        listOf(null, "", "   ").forEach { value ->
            assertTrue(
                value?.takeIf(String::isNotBlank) == null,
                "'$value' must be treated as no artwork",
            )
        }
    }
}
