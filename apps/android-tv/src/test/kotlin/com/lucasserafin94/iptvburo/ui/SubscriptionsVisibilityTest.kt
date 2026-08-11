package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.ExternalLaunchTarget
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.LibraryOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.asExternalCandidate
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryCapability
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider
import com.lucasserafin94.iptvburo.ui.navigation.availableRibbonSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether Assinaturas may be seen at all, and what its rows must say.
 *
 * These are the two ways this feature can do real harm: showing a destination that opens onto
 * nothing, and showing someone else's data without the credit their terms require. Both are
 * asserted here rather than left to inspection of a running screen.
 */
class SubscriptionsVisibilityTest {
    @Test
    fun `subscriptions stays hidden until a metadata key is configured`() {
        val sections = availableRibbonSections(offlineSupported = false, subscriptionsVisible = false)

        assertFalse(
            "Without a TMDB key there is no catalogue to browse, so the entry must be absent " +
                "rather than opening onto an empty screen.",
            AppSection.SUBSCRIPTIONS in sections,
        )
    }

    @Test
    fun `subscriptions appears once a key makes the catalogue real`() {
        val sections = availableRibbonSections(offlineSupported = false, subscriptionsVisible = true)

        assertTrue(AppSection.SUBSCRIPTIONS in sections)
    }

    @Test
    fun `downloads stays hidden independently of subscriptions`() {
        val sections = availableRibbonSections(offlineSupported = false, subscriptionsVisible = true)

        assertFalse(
            "Offline support is a separate capability; turning subscriptions on must not " +
                "smuggle in a Downloads destination with no vault behind it.",
            AppSection.DOWNLOADS in sections,
        )
    }

    @Test
    fun `capability follows the key rather than being set directly`() {
        assertEquals(
            StreamingDiscoveryCapability.UNAVAILABLE,
            StreamingDiscoveryCapability.of(hasRealProvider = false),
        )
        assertEquals(
            StreamingDiscoveryCapability.AVAILABLE,
            StreamingDiscoveryCapability.of(hasRealProvider = true),
        )
    }

    @Test
    fun `every catalogue row carries the JustWatch credit`() {
        val offer =
            StreamingOffer(
                provider = StreamingProvider.of("netflix", "Netflix"),
                type = OfferType.SUBSCRIPTION,
                launchTarget = ExternalLaunchTarget(webUrl = "https://www.netflix.com/search?q=Test"),
            ).toUi()

        assertTrue(
            "JustWatch requires the source credited on every item showing their data. A row " +
                "that drops it puts the app's access at risk.",
            offer.requiresAttribution,
        )
    }

    @Test
    fun `the user's own library is not credited to JustWatch`() {
        // The reserved provider, not one built here: the domain requires a USER_LIBRARY offer to
        // come from it, so no catalogue row can ever impersonate the user's own list.
        val offer =
            StreamingOffer(
                provider = LibraryOfferPolicy.USER_LIBRARY_PROVIDER,
                type = OfferType.USER_LIBRARY,
            ).toUi()

        assertFalse(
            "The user's own playlist was matched by this app, not reported by JustWatch.",
            offer.requiresAttribution,
        )
        assertTrue(offer.isUserLibrary)
    }

    @Test
    fun `a confident title match claims the user's own copy`() {
        val library =
            listOf(
                Channel(
                    id = "local-1",
                    sourceId = "source",
                    name = "A Última Casa",
                    streamUri = "https://example.test/a",
                    contentType = CatalogContentType.MOVIE,
                    year = 2026,
                ).toLibraryCandidate(),
            )
        val external =
            ExternalTitle(
                id = ExternalContentId("tmdb", "1"),
                title = "A Última Casa",
                kind = ExternalTitleKind.MOVIE,
                year = 2026,
            )

        val found = LibraryOfferPolicy.findInLibrary(external.asExternalCandidate(), library)

        assertEquals("local-1", found?.localContentId)
        assertEquals(OfferType.USER_LIBRARY, found?.offer?.type)
    }

    @Test
    fun `an unrelated title never claims the user owns it`() {
        // The damaging failure: a false "you have this" sends the user to a title that is not
        // there, and afterwards they have no reason to trust the rows that are correct.
        val library =
            listOf(
                Channel(
                    id = "local-1",
                    sourceId = "source",
                    name = "Coração Acelerado",
                    streamUri = "https://example.test/b",
                    contentType = CatalogContentType.MOVIE,
                    year = 2026,
                ).toLibraryCandidate(),
            )
        val external =
            ExternalTitle(
                id = ExternalContentId("tmdb", "2"),
                title = "A Última Casa",
                kind = ExternalTitleKind.MOVIE,
                year = 2026,
            )

        assertEquals(null, LibraryOfferPolicy.findInLibrary(external.asExternalCandidate(), library))
    }

    @Test
    fun `the year is read from the title when the provider left the field empty`() {
        // Playlists routinely write "Filme (2019)" and leave the year column blank. Without this
        // the matcher loses its strongest signal for telling two same-named films apart.
        val candidate =
            Channel(
                id = "local-1",
                sourceId = "source",
                name = "A Última Casa (2026)",
                streamUri = "https://example.test/a",
                contentType = CatalogContentType.MOVIE,
            ).toLibraryCandidate()

        assertEquals(2026, candidate.year)
    }

    @Test
    fun `no offer is ever given a price`() {
        // TMDb returns no prices in any bucket, so a rental row must carry the service name and
        // nothing more. A number here would be invented and the user could act on it.
        val rental =
            StreamingOffer(
                provider = StreamingProvider.of("apple-tv", "Apple TV"),
                type = OfferType.RENT,
                launchTarget = ExternalLaunchTarget(webUrl = "https://tv.apple.com/search?term=Test"),
            )

        assertEquals(null, rental.price)
    }
}
