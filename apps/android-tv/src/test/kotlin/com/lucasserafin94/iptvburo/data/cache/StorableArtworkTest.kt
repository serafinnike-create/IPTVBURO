package com.lucasserafin94.iptvburo.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a reminder is allowed to remember about a poster.
 *
 * A reminder outlives the playlist it was made from, so this rule is what stops a deleted source
 * leaving the subscriber's credentials behind on disk. It was previously an allowlist of TMDb hosts,
 * which kept no credentials and no posters either: an ordinary Xtream playlist serves its artwork
 * from plain static addresses, so every reminder was stored blank. These assertions pin the
 * replacement — reject the credential, not the host — so that neither half can regress quietly.
 */
class StorableArtworkTest {
    @Test
    fun `keeps a provider poster that carries no credential`() {
        // The shape a real playlist serves: a static path, no query, no userinfo. This is the case
        // the old host allowlist rejected, and rejecting it bought nothing.
        assertTrue(isStorableArtwork("http://24horas.cc/images/017bcee0f41381fdd4db67aa0b3df7bf.jpg"))
        assertTrue(isStorableArtwork("http://cb.visualplay.online/static/logos/canais/amc.png"))
        assertTrue(isStorableArtwork("https://provider.example/posters/1234.jpg"))
    }

    @Test
    fun `keeps the artwork shape this user's own catalogue serves`() {
        // Taken verbatim from a real playlist on a real device, double slash and all: the importer
        // builds `host//t/p/...` and the poster is a plain static path with no credential anywhere
        // in it. The old host allowlist rejected exactly this, which is why every reminder on that
        // device was stored blank and the home rail drew a coloured rectangle instead of a poster.
        assertTrue(
            isStorableArtwork(
                "http://file.gstaticontent.com//t/p/w600_and_h900_bestv2/gUgfk88uIvUMCMLQVybKX2N58pj.jpg",
            ),
        )
    }

    @Test
    fun `keeps public metadata artwork and the app's own files`() {
        assertTrue(isStorableArtwork("https://image.tmdb.org/t/p/w342/abc.jpg"))
        assertTrue(isStorableArtwork("file:///data/user/0/app/files/profile-photos/a.jpg"))
    }

    @Test
    fun `refuses userinfo, which is a credential in the plainest form`() {
        assertFalse(isStorableArtwork("https://subscriber:hunter2@provider.example/posters/1.jpg"))
        assertFalse(isStorableArtwork("http://user@provider.example/posters/1.jpg"))
    }

    @Test
    fun `refuses any query string, where a signed or token-bearing URL puts one`() {
        assertFalse(isStorableArtwork("https://provider.example/poster.jpg?token=abc123"))
        assertFalse(isStorableArtwork("https://provider.example/poster.jpg?username=me&password=x"))
        // Refused even when the query looks harmless: this cannot tell a cache-buster from a
        // credential, and the safe answer for an ambiguous case is to keep no poster.
        assertFalse(isStorableArtwork("https://provider.example/poster.jpg?v=2"))
    }

    @Test
    fun `refuses the provider's authenticated paths, whatever the casing`() {
        // Xtream builds these as /<kind>/<username>/<password>/<id>, which is the shape that made
        // storing provider artwork a concern in the first place.
        assertFalse(isStorableArtwork("http://provider.example/movie/subscriber/hunter2/42.jpg"))
        assertFalse(isStorableArtwork("http://provider.example/series/subscriber/hunter2/42.jpg"))
        assertFalse(isStorableArtwork("http://provider.example/live/subscriber/hunter2/42.png"))
        // A provider answering with different casing must not walk past the check.
        assertFalse(isStorableArtwork("http://provider.example/Movie/subscriber/hunter2/42.jpg"))
    }

    @Test
    fun `refuses what is not a usable address at all`() {
        assertFalse(isStorableArtwork(""))
        assertFalse(isStorableArtwork("   "))
        assertFalse(isStorableArtwork("not a url"))
        // Neither http nor a local file: a data URI would embed the whole image in the row.
        assertFalse(isStorableArtwork("data:image/png;base64,iVBORw0KGgo="))
        assertFalse(isStorableArtwork("https://provider.example/" + "a".repeat(3_000) + ".jpg"))
    }
}
