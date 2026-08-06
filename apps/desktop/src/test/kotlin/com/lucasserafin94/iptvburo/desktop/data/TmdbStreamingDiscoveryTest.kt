package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.BestOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.ExternalContentLauncher
import com.lucasserafin94.iptvburo.domain.model.LaunchDecision
import com.lucasserafin94.iptvburo.domain.model.OfferReason
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.metadata.TmdbWatchProvider
import com.lucasserafin94.iptvburo.metadata.TmdbWatchProviders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TmdbStreamingDiscoveryTest {
    private fun provider(
        id: Int,
        name: String,
    ) = TmdbWatchProvider(providerId = id, name = name)

    @Test
    fun `each bucket maps onto its offer type`() {
        val listing =
            TmdbWatchProviders(
                region = "BR",
                subscription = listOf(provider(8, "Netflix")),
                withAds = listOf(provider(7, "Pluto TV")),
                rent = listOf(provider(2, "Apple TV")),
                buy = listOf(provider(3, "Google Play Filmes")),
            )

        val offers = TmdbStreamingDiscovery.offersFrom(listing, "Duna")

        assertEquals(OfferType.SUBSCRIPTION, offers.first { it.provider.displayName == "Netflix" }.type)
        assertEquals(OfferType.FREE_WITH_ADS, offers.first { it.provider.displayName == "Pluto TV" }.type)
        assertEquals(OfferType.RENT, offers.first { it.provider.displayName == "Apple TV" }.type)
        assertEquals(OfferType.BUY, offers.first { it.provider.displayName == "Google Play Filmes" }.type)
    }

    /**
     * The constraint that shapes the feature: TMDb has no prices, so no offer may carry one. A
     * fabricated price is a fact the user could act on and find wrong.
     */
    @Test
    fun `no offer ever carries a price`() {
        val listing =
            TmdbWatchProviders(
                region = "BR",
                rent = listOf(provider(2, "Apple TV")),
                buy = listOf(provider(3, "Google Play Filmes")),
            )

        val offers = TmdbStreamingDiscovery.offersFrom(listing, "Duna")

        assertTrue(offers.isNotEmpty())
        offers.forEach { offer -> assertNull(offer.price, "${offer.provider.displayName} gained a price") }
    }

    /** With no prices, nothing may be labelled the cheapest — the ranking must not claim one. */
    @Test
    fun `no rental is announced as the cheapest`() {
        val listing =
            TmdbWatchProviders(
                region = "BR",
                rent = listOf(provider(2, "Apple TV"), provider(3, "Google Play Filmes")),
            )

        val ranked = BestOfferPolicy.rank(TmdbStreamingDiscovery.offersFrom(listing, "Duna"))

        assertTrue(ranked.all.none { it.reason == OfferReason.CHEAPEST_RENTAL })
        assertTrue(ranked.all.all { it.reason == OfferReason.RENTAL })
    }

    @Test
    fun `a known service gets a search link rather than the TMDb page`() {
        val listing =
            TmdbWatchProviders(
                region = "BR",
                subscription = listOf(provider(8, "Netflix")),
                tmdbWatchPageUrl = "https://www.themoviedb.org/movie/1/watch?locale=BR",
            )

        val target = TmdbStreamingDiscovery.offersFrom(listing, "Duna").single().launchTarget

        assertNotNull(target)
        assertTrue(target.webUrl!!.startsWith("https://www.netflix.com/"), "got ${target.webUrl}")
    }

    @Test
    fun `an unknown service falls back to the TMDb page`() {
        val listing =
            TmdbWatchProviders(
                region = "BR",
                subscription = listOf(provider(999, "Some Regional Service")),
                tmdbWatchPageUrl = "https://www.themoviedb.org/movie/1/watch?locale=BR",
            )

        val target = TmdbStreamingDiscovery.offersFrom(listing, "Duna").single().launchTarget

        assertNotNull(target)
        assertEquals("https://www.themoviedb.org/movie/1/watch?locale=BR", target.webUrl)
    }

    @Test
    fun `an unknown service with no fallback still produces a listable offer`() {
        val listing = TmdbWatchProviders(region = "BR", subscription = listOf(provider(999, "Some Regional Service")))

        val offer = TmdbStreamingDiscovery.offersFrom(listing, "Duna").single()

        // The row still tells the user the service has it, even with nowhere to send them.
        assertEquals(OfferType.SUBSCRIPTION, offer.type)
        assertNull(offer.launchTarget)
    }

    @Test
    fun `every generated destination survives the launcher`() {
        val listing =
            TmdbWatchProviders(
                region = "BR",
                subscription = listOf(provider(8, "Netflix"), provider(9, "Disney+")),
                rent = listOf(provider(2, "Apple TV")),
                tmdbWatchPageUrl = "https://www.themoviedb.org/movie/1/watch?locale=BR",
            )

        TmdbStreamingDiscovery
            .offersFrom(listing, "Coração & Alma")
            .mapNotNull { it.launchTarget }
            .forEach { target ->
                assertIs<LaunchDecision.OpenWeb>(
                    ExternalContentLauncher.decide(target),
                    "a generated destination was refused",
                )
            }
    }

    @Test
    fun `the same service in two buckets produces two distinct offers`() {
        // Apple TV commonly appears under both rent and buy; both are real choices for the user.
        val listing =
            TmdbWatchProviders(
                region = "BR",
                rent = listOf(provider(2, "Apple TV")),
                buy = listOf(provider(2, "Apple TV")),
            )

        val offers = TmdbStreamingDiscovery.offersFrom(listing, "Duna")

        assertEquals(2, offers.size)
        assertEquals(setOf(OfferType.RENT, OfferType.BUY), offers.map { it.type }.toSet())
    }

    @Test
    fun `a service listed twice in one bucket appears once`() {
        val listing =
            TmdbWatchProviders(region = "BR", subscription = listOf(provider(8, "Netflix"), provider(8, "Netflix")))

        assertEquals(1, TmdbStreamingDiscovery.offersFrom(listing, "Duna").size)
    }

    @Test
    fun `an empty listing produces no offers rather than failing`() {
        assertTrue(TmdbStreamingDiscovery.offersFrom(TmdbWatchProviders(region = "BR"), "Duna").isEmpty())
    }

    /**
     * The slugs must line up with the deep-link table, or every service silently falls back to the
     * TMDb page and the direct links never fire.
     */
    @Test
    fun `service names slug onto the deep-link table's keys`() {
        assertEquals("netflix", TmdbStreamingDiscovery.slugFor("Netflix"))
        assertEquals("prime-video", TmdbStreamingDiscovery.slugFor("Prime Video"))
        assertEquals("disney-plus", TmdbStreamingDiscovery.slugFor("Disney+"))
        assertEquals("apple-tv", TmdbStreamingDiscovery.slugFor("Apple TV"))
        assertEquals("google-play", TmdbStreamingDiscovery.slugFor("Google Play"))
    }

    @Test
    fun `slugs are stable against punctuation and casing`() {
        assertEquals("apple-tv", TmdbStreamingDiscovery.slugFor("  APPLE TV  "))
        assertEquals("paramount-plus", TmdbStreamingDiscovery.slugFor("Paramount+"))
    }
}
