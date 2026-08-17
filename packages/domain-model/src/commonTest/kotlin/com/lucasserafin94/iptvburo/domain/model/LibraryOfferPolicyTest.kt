package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryOfferPolicyTest {
    private fun external(
        title: String,
        year: Int? = null,
        kind: MatchKind = MatchKind.MOVIE,
        externalIds: Map<String, String> = emptyMap(),
    ) = ExternalCandidate(
        externalContentId = "tmdb:1",
        title = title,
        year = year,
        kind = kind,
        externalIds = externalIds,
    )

    private fun local(
        id: String = "local-1",
        title: String,
        year: Int? = null,
        kind: MatchKind = MatchKind.MOVIE,
        externalIds: Map<String, String> = emptyMap(),
    ) = LibraryCandidate(
        localContentId = id,
        title = title,
        year = year,
        kind = kind,
        externalIds = externalIds,
    )

    @Test
    fun `a confident match becomes a playable library offer`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", year = 2021),
                library = listOf(local(title = "Duna", year = 2021)),
            )

        assertNotNull(found)
        assertEquals(OfferType.USER_LIBRARY, found.offer.type)
        assertEquals("local-1", found.localContentId)
    }

    @Test
    fun `the library offer carries no price and no external destination`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", year = 2021),
                library = listOf(local(title = "Duna", year = 2021)),
            )

        assertNotNull(found)
        // The user's own list is an origin, not a free offer. A price here — even zero — would read
        // as the app giving content away.
        assertNull(found.offer.price)
        // It opens inside the app, so there is nothing for the external launcher to receive.
        assertNull(found.offer.launchTarget)
    }

    /**
     * The failure this policy exists to prevent: claiming the user has a film they do not.
     *
     * The catalogue holds the 1984 film, the service is describing the 2021 one. Anything other
     * than null here sends the user to the wrong film and makes every other row untrustworthy.
     */
    @Test
    fun `a remake in the library is never offered as the same film`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", year = 2021),
                library = listOf(local(title = "Duna", year = 1984)),
            )

        assertNull(found)
    }

    @Test
    fun `a title-only match is too weak to claim`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna"),
                library = listOf(local(title = "Duna")),
            )

        assertNull(found)
    }

    @Test
    fun `an empty library offers nothing rather than failing`() {
        assertNull(LibraryOfferPolicy.findInLibrary(external("Duna", year = 2021), emptyList()))
    }

    @Test
    fun `a film is never matched to a series of the same name`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Fargo", year = 1996, kind = MatchKind.MOVIE),
                library = listOf(local(title = "Fargo", year = 1996, kind = MatchKind.SERIES)),
            )

        assertNull(found)
    }

    @Test
    fun `the library offer is added to the other ways of watching`() {
        val netflix = StreamingProvider.of("demo-a", "Service A")
        val offers = listOf(StreamingOffer(provider = netflix, type = OfferType.SUBSCRIPTION))

        val combined =
            LibraryOfferPolicy.withLibraryOffer(
                offers = offers,
                external = external("Duna", year = 2021),
                library = listOf(local(title = "Duna", year = 2021)),
            )

        assertEquals(2, combined.size)
        assertTrue(combined.any { it.type == OfferType.USER_LIBRARY })
        // Nothing else is dropped: the user can still choose to watch it elsewhere.
        assertTrue(combined.any { it.type == OfferType.SUBSCRIPTION })
    }

    @Test
    fun `the other ways of watching are untouched when the user has no copy`() {
        val offers = listOf(StreamingOffer(provider = StreamingProvider.of("demo-a", "Service A"), type = OfferType.SUBSCRIPTION))

        val combined =
            LibraryOfferPolicy.withLibraryOffer(
                offers = offers,
                external = external("Duna", year = 2021),
                library = listOf(local(title = "Outro filme", year = 2021)),
            )

        assertEquals(offers, combined)
    }

    @Test
    fun `the ranked result puts the user's own copy first`() {
        val offers =
            listOf(
                StreamingOffer(
                    provider = StreamingProvider.of("demo-store", "Store"),
                    type = OfferType.RENT,
                    price = Price(499, "BRL"),
                ),
                StreamingOffer(provider = StreamingProvider.of("demo-a", "Service A"), type = OfferType.SUBSCRIPTION),
            )

        val combined =
            LibraryOfferPolicy.withLibraryOffer(
                offers = offers,
                external = external("Duna", year = 2021),
                library = listOf(local(title = "Duna", year = 2021)),
            )
        val best = BestOfferPolicy.rank(combined).best

        assertEquals(OfferReason.IN_YOUR_LIBRARY, best?.reason)
    }

    @Test
    fun `a catalogue claiming to be the user's library does not produce two rows`() {
        val offers = listOf(StreamingOffer(provider = LibraryOfferPolicy.USER_LIBRARY_PROVIDER, type = OfferType.USER_LIBRARY))

        val combined =
            LibraryOfferPolicy.withLibraryOffer(
                offers = offers,
                external = external("Duna", year = 2021),
                library = listOf(local(title = "Duna", year = 2021)),
            )

        assertEquals(1, combined.count { it.type == OfferType.USER_LIBRARY })
    }

    @Test
    fun `a channel becomes a candidate carrying its name and year`() {
        val channel =
            Channel(
                id = "channel-1",
                sourceId = "source-1",
                name = "Duna",
                streamUri = "https://example.invalid/stream",
                contentType = CatalogContentType.MOVIE,
                year = 2021,
            )

        val candidate = channel.asLibraryCandidate()

        assertEquals("channel-1", candidate.localContentId)
        assertEquals("Duna", candidate.title)
        assertEquals(2021, candidate.year)
        assertEquals(MatchKind.MOVIE, candidate.kind)
    }

    @Test
    fun `a series channel becomes a series candidate`() {
        val channel =
            Channel(
                id = "series-1",
                sourceId = "source-1",
                name = "Fargo",
                streamUri = "https://example.invalid/stream",
                contentType = CatalogContentType.SERIES,
            )

        assertEquals(MatchKind.SERIES, channel.asLibraryCandidate().kind)
    }
}
