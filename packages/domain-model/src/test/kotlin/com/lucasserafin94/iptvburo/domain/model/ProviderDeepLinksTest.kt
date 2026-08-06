package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderDeepLinksTest {
    @Test
    fun `a known service gets a search for the title`() {
        val url = ProviderDeepLinks.searchUrlFor("netflix", "Duna")

        assertEquals("https://www.netflix.com/search?q=Duna", url)
    }

    @Test
    fun `spaces are encoded rather than left raw`() {
        val url = ProviderDeepLinks.searchUrlFor("netflix", "Duna Parte Dois")

        assertNotNull(url)
        assertTrue("%20" in url, "spaces must be percent-encoded: $url")
        assertTrue(" " !in url, "a raw space would break the URL: $url")
    }

    /**
     * Portuguese titles carry accents, and a mis-encoded one searches for the wrong string — the
     * user sees "no results" on a service that does have the film.
     */
    @Test
    fun `accented titles survive encoding`() {
        val url = ProviderDeepLinks.searchUrlFor("netflix", "Coração")

        assertNotNull(url)
        // ç is two bytes in UTF-8, so it must become two percent-escapes.
        assertTrue("%C3%A7" in url, "expected UTF-8 percent-encoding, got: $url")
    }

    @Test
    fun `ampersands and question marks cannot break out of the query`() {
        val url = ProviderDeepLinks.searchUrlFor("netflix", "Isto & Aquilo?")

        assertNotNull(url)
        assertTrue("%26" in url, "a raw & would start a new query parameter: $url")
        // The only ? in the URL is the one that opens the query string.
        assertEquals(1, url.count { it == '?' })
    }

    @Test
    fun `an unknown service has no search pattern`() {
        assertNull(ProviderDeepLinks.searchUrlFor("some-service-we-do-not-know", "Duna"))
    }

    @Test
    fun `a blank title produces no destination`() {
        assertNull(ProviderDeepLinks.searchUrlFor("netflix", "   "))
    }

    @Test
    fun `the service id is matched regardless of casing`() {
        assertEquals(
            ProviderDeepLinks.searchUrlFor("netflix", "Duna"),
            ProviderDeepLinks.searchUrlFor("  NETFLIX ", "Duna"),
        )
    }

    @Test
    fun `an unknown service falls back to the catalogue's own page`() {
        val target =
            ProviderDeepLinks.bestTargetFor(
                providerId = "unknown-service",
                title = "Duna",
                catalogueFallbackUrl = "https://www.themoviedb.org/movie/1/watch?locale=BR",
            )

        assertNotNull(target)
        assertEquals("https://www.themoviedb.org/movie/1/watch?locale=BR", target.webUrl)
    }

    @Test
    fun `with nothing known at all there is no target rather than a broken one`() {
        assertNull(ProviderDeepLinks.bestTargetFor("unknown-service", "Duna", catalogueFallbackUrl = null))
    }

    @Test
    fun `a known service prefers its own search over the catalogue page`() {
        val target =
            ProviderDeepLinks.bestTargetFor(
                providerId = "netflix",
                title = "Duna",
                catalogueFallbackUrl = "https://www.themoviedb.org/movie/1/watch",
            )

        assertNotNull(target)
        assertTrue(target.webUrl!!.startsWith("https://www.netflix.com/"))
    }

    /**
     * Every destination this produces is handed to the launcher, which refuses streams, credentials
     * and non-https schemes. A pattern that could not survive that check would render a dead button.
     */
    @Test
    fun `every generated destination passes the launcher's safety checks`() {
        val services = listOf("netflix", "prime-video", "disney-plus", "apple-tv", "google-play", "hbo-max", "globoplay", "paramount-plus")

        services.forEach { service ->
            listOf("Duna", "Duna Parte Dois", "Coração", "Isto & Aquilo?").forEach { title ->
                val target = ProviderDeepLinks.bestTargetFor(service, title)
                assertNotNull(target, "$service produced no target")
                val decision = ExternalContentLauncher.decide(target)
                assertIs<LaunchDecision.OpenWeb>(decision, "$service / $title was refused: $decision")
            }
        }
    }

    @Test
    fun `every service with a search pattern also has a homepage to fall back to`() {
        listOf("netflix", "prime-video", "disney-plus", "apple-tv", "google-play", "hbo-max", "globoplay", "paramount-plus")
            .forEach { service ->
                assertNotNull(ProviderDeepLinks.homepageFor(service), "$service has no homepage fallback")
            }
    }

    @Test
    fun `no destination is a stream or carries a credential`() {
        // The patterns are written by hand, so this guards against a paste that included a token.
        listOf("netflix", "prime-video", "disney-plus", "apple-tv", "google-play")
            .mapNotNull { service -> ProviderDeepLinks.searchUrlFor(service, "Duna") }
            .forEach { url ->
                assertTrue(url.startsWith("https://"), "not https: $url")
                assertTrue(".m3u8" !in url && ".mpd" !in url, "looks like a stream: $url")
                assertTrue("token=" !in url && "api_key=" !in url, "carries a credential: $url")
            }
    }
}
