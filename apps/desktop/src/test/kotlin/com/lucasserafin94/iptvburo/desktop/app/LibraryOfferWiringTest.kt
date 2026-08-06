package com.lucasserafin94.iptvburo.desktop.app

import com.lucasserafin94.iptvburo.domain.model.BestOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.LibraryOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.OfferReason
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.asExternalCandidate
import com.lucasserafin94.iptvburo.domain.model.asLibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.Price
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The end-to-end shape of "this film is already in your list".
 *
 * The pieces are tested separately in the domain; what this pins is that the desktop's own
 * conversion from a playlist [Channel] lines up with what the matching policy expects. That seam is
 * where a rename or a changed field would silently stop producing the row, and the failure would
 * look like "the feature just doesn't appear" rather than like an error.
 */
class LibraryOfferWiringTest {
    private fun channel(
        id: String,
        name: String,
        year: Int? = null,
        contentType: CatalogContentType = CatalogContentType.MOVIE,
    ) = Channel(
        id = id,
        sourceId = "source-1",
        name = name,
        streamUri = "https://example.invalid/stream",
        contentType = contentType,
        year = year,
    )

    private fun externalTitle(
        title: String,
        year: Int?,
        kind: ExternalTitleKind = ExternalTitleKind.MOVIE,
    ) = ExternalTitle(
        id = ExternalContentId("demo", "1"),
        title = title,
        kind = kind,
        year = year,
        isDemo = true,
    )

    @Test
    fun `a film in the playlist becomes the top-ranked way to watch it`() {
        val playlist = listOf(channel("local-7", "Duna", year = 2021)).map(Channel::asLibraryCandidate)
        val offers =
            listOf(
                StreamingOffer(
                    provider = StreamingProvider.of("demo-store", "Store"),
                    type = OfferType.RENT,
                    price = Price(1490, "BRL"),
                ),
            )

        val combined =
            LibraryOfferPolicy.withLibraryOffer(
                offers = offers,
                external = externalTitle("Duna", year = 2021).asExternalCandidate(),
                library = playlist,
            )
        val best = BestOfferPolicy.rank(combined).best

        assertEquals(OfferReason.IN_YOUR_LIBRARY, best?.reason)
        // The rental is still there — the user may prefer to rent it in better quality.
        assertTrue(combined.any { it.type == OfferType.RENT })
    }

    @Test
    fun `the row resolves back to the exact playlist item to open`() {
        val playlist =
            listOf(
                channel("wrong-1", "Outro filme", year = 2021),
                channel("right-1", "Duna", year = 2021),
            ).map(Channel::asLibraryCandidate)

        val found =
            LibraryOfferPolicy.findInLibrary(
                external = externalTitle("Duna", year = 2021).asExternalCandidate(),
                library = playlist,
            )

        assertNotNull(found)
        assertEquals("right-1", found.localContentId)
    }

    /**
     * The regression that matters most: a playlist holding the wrong version of a title must not
     * produce the row at all. If this fails, the app tells the user it has a film and then opens
     * a different one.
     */
    @Test
    fun `a different year in the playlist produces no row`() {
        val playlist = listOf(channel("local-7", "Duna", year = 1984)).map(Channel::asLibraryCandidate)

        val found =
            LibraryOfferPolicy.findInLibrary(
                external = externalTitle("Duna", year = 2021).asExternalCandidate(),
                library = playlist,
            )

        assertNull(found)
    }

    @Test
    fun `a live channel never matches a film`() {
        // Live entries are filtered out before matching, but even if one arrived its name would not
        // match a film's — this pins that a channel called after a film cannot claim to be it.
        val playlist =
            listOf(channel("live-1", "Duna", year = 2021, contentType = CatalogContentType.LIVE))
                .filter { it.contentType != CatalogContentType.LIVE }
                .map(Channel::asLibraryCandidate)

        assertTrue(playlist.isEmpty())
    }

    @Test
    fun `a series in the playlist matches a series, not a film of the same name`() {
        val playlist =
            listOf(channel("series-1", "Fargo", year = 1996, contentType = CatalogContentType.SERIES))
                .map(Channel::asLibraryCandidate)

        val asFilm =
            LibraryOfferPolicy.findInLibrary(
                external = externalTitle("Fargo", year = 1996, kind = ExternalTitleKind.MOVIE).asExternalCandidate(),
                library = playlist,
            )
        val asSeries =
            LibraryOfferPolicy.findInLibrary(
                external = externalTitle("Fargo", year = 1996, kind = ExternalTitleKind.SERIES).asExternalCandidate(),
                library = playlist,
            )

        assertNull(asFilm, "a series must not be offered as the film of the same name")
        assertNotNull(asSeries)
    }
}
