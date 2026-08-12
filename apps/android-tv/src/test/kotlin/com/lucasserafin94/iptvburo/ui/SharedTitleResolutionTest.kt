package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolving a shared link against *this* device's catalogue.
 *
 * The sender's catalogue row id is meaningless here — it names a row in their database — so the
 * link carries a [ContentIdentity] and the local row is found by recomputing the same identity over
 * local names. These tests are about that recomputation surviving the way real providers write
 * titles, and about it refusing to open the wrong film.
 */
class SharedTitleResolutionTest {
    private fun movie(
        name: String,
        year: Int? = null,
        id: String = "local-1",
    ) = Channel(
        id = id,
        sourceId = "source-1",
        name = name,
        streamUri = "http://example.invalid/stream",
        contentType = CatalogContentType.MOVIE,
        year = year,
    )

    /** The whole point: two providers decorate one film differently and it still resolves. */
    @Test
    fun `a link made from one provider's name matches another provider's copy`() {
        val senderIdentity =
            ContentIdentity.of(ContentKind.MOVIE, "[4K] Duna: Parte Dois (2024) DUAL", 2024)

        assertTrue(movie("Duna Parte Dois 1080p LEG", year = 2024).matches(senderIdentity))
    }

    @Test
    fun `a year written into the name counts when the provider left the field empty`() {
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Duna", 2021)

        assertTrue(movie("Duna (2021) DUBLADO", year = null).matches(identity))
    }

    /**
     * A playlist that states no year at all still matches, because most Xtream lists do not carry
     * one and refusing them would fail on exactly the catalogues this feature is for.
     */
    @Test
    fun `a row with no year at all matches a link that carries one`() {
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Duna", 2021)

        assertTrue(movie("Duna 4K", year = null).matches(identity))
    }

    /** The remake case. Opening the wrong film is worse than opening nothing. */
    @Test
    fun `a different year never matches`() {
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Duna", 2021)

        assertFalse(movie("Duna", year = 1984).matches(identity))
        assertFalse(movie("Duna (1984)", year = null).matches(identity))
    }

    @Test
    fun `a film never matches a series of the same name`() {
        val seriesIdentity = ContentIdentity.of(ContentKind.SERIES, "Fargo", 2014)

        assertFalse(movie("Fargo", year = 2014).matches(seriesIdentity))
    }

    @Test
    fun `a different film never matches`() {
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Duna", 2021)

        assertFalse(movie("Duna Drifter", year = 2021).matches(identity))
        assertFalse(movie("Luna", year = 2021).matches(identity))
    }

    /** Live channels are not shareable titles and must never be resolved as one. */
    @Test
    fun `a live channel is never a match`() {
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Globo", 2021)
        val live =
            Channel(
                id = "live-1",
                sourceId = "source-1",
                name = "Globo",
                streamUri = "http://example.invalid/live",
                contentType = CatalogContentType.LIVE,
                year = 2021,
            )

        assertFalse(live.matches(identity))
    }

    // ---------------------------------------------------------------------------------------
    // The search fragment handed to SQL before the identity comparison runs
    // ---------------------------------------------------------------------------------------

    /**
     * The fragment has to appear in the *undecorated* local name. Picking the longest word is what
     * makes that likely: a leading article matches thousands of unrelated rows and would return a
     * candidate page that does not contain the film at all.
     */
    @Test
    fun `the search fragment is the most distinctive word`() {
        assertEquals(
            "compadecida",
            ContentIdentity.of(ContentKind.MOVIE, "O Auto da Compadecida", 2000).searchFragment(),
        )
    }

    @Test
    fun `the search fragment survives a single-word title`() {
        assertEquals("duna", ContentIdentity.of(ContentKind.MOVIE, "Duna", 2021).searchFragment())
    }

    @Test
    fun `the search fragment survives a title with no year`() {
        assertEquals("duna", ContentIdentity.of(ContentKind.MOVIE, "Duna").searchFragment())
    }

    /** The fragment must actually occur in a real provider name, or the SQL page comes back empty. */
    @Test
    fun `the search fragment occurs in the decorated local name`() {
        val fragment =
            ContentIdentity.of(ContentKind.MOVIE, "Duna: Parte Dois", 2024).searchFragment()

        assertTrue(
            "fragment '$fragment' would not be found in a real row",
            "[4K] Duna: Parte Dois (2024) DUAL".contains(fragment, ignoreCase = true),
        )
    }

    // ---------------------------------------------------------------------------------------
    // End to end, over the link format itself
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a built link resolves against a differently decorated local row`() {
        val link =
            TitleShareLink.of(
                identity = ContentIdentity.of(ContentKind.MOVIE, "Duna: Parte Dois", 2024),
                title = "Duna: Parte Dois",
                year = 2024,
            )
        assertNotNull(link)

        val received = TitleShareLink.parse(link!!.webUrl())
        assertNotNull(received)

        val localRow = movie("Duna Parte Dois [L] 1080p", year = 2024)
        assertTrue(localRow.matches(received!!.identity))
        assertTrue(
            localRow.name.contains(received.identity.searchFragment(), ignoreCase = true),
        )
    }
}
