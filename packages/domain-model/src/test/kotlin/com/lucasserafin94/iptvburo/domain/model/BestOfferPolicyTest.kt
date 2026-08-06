package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ranking rules for the "Assinaturas" area.
 *
 * Written against the cases a naive implementation gets wrong: treating every subscription alike
 * regardless of whether the user pays for it, dropping the more expensive rental once a cheaper one
 * is found, modelling the user's own library as a zero price, and throwing on an empty offer list.
 *
 * Providers here are invented and every URL is `example.invalid`.
 */
class BestOfferPolicyTest {
    private val userLibrary = StreamingProvider.of(USER_LIBRARY_PROVIDER_ID, "Your list")
    private val subscribed = StreamingProvider.of("provider-a", "Provider A")
    private val notSubscribed = StreamingProvider.of("provider-b", "Provider B")
    private val freeService = StreamingProvider.of("provider-c", "Provider C")
    private val cheapStore = StreamingProvider.of("store-cheap", "Cheap Store")
    private val pricyStore = StreamingProvider.of("store-pricy", "Pricy Store")

    private val prefersA = UserStreamingPreference(subscribedProviderIds = setOf("provider-a"))

    private fun subscription(provider: StreamingProvider) = StreamingOffer(provider = provider, type = OfferType.SUBSCRIPTION)

    private fun rental(
        provider: StreamingProvider,
        amountMinor: Long,
        currency: String = "EUR",
    ) = StreamingOffer(provider = provider, type = OfferType.RENT, price = Price(amountMinor, currency))

    // ---------------------------------------------------------------------------------------------
    // Subscription the user has vs one they do not
    // ---------------------------------------------------------------------------------------------

    /**
     * The case the whole [UserStreamingPreference] exists for. Both offers are `SUBSCRIPTION`; only
     * the stated preference separates them, and an implementation that ranks by [OfferType] alone
     * gets this wrong.
     */
    @Test
    fun `a subscription the user has not selected ranks below one they have`() {
        val ranking = BestOfferPolicy.rank(listOf(subscription(notSubscribed), subscription(subscribed)), prefersA)

        assertEquals(
            listOf(subscribed.id, notSubscribed.id),
            ranking.all.map { it.provider.id },
        )
        assertEquals(OfferReason.INCLUDED_IN_YOUR_SUBSCRIPTION, ranking.best?.reason)
        assertEquals(OfferReason.REQUIRES_NEW_SUBSCRIPTION, ranking.all.last().reason)
    }

    /** A service the user does not pay for costs recurring money, so it ranks below a one-off buy. */
    @Test
    fun `an unsubscribed service ranks below rent and buy`() {
        val buy = StreamingOffer(provider = pricyStore, type = OfferType.BUY, price = Price(1299, "EUR"))
        val ranking =
            BestOfferPolicy.rank(
                listOf(subscription(notSubscribed), buy, rental(cheapStore, 399)),
                UserStreamingPreference.NONE,
            )

        assertEquals(
            listOf(OfferReason.CHEAPEST_RENTAL, OfferReason.PURCHASE, OfferReason.REQUIRES_NEW_SUBSCRIPTION),
            ranking.all.map(RankedOffer::reason),
        )
    }

    @Test
    fun `subscription matching ignores case in the stated preference`() {
        val preference = UserStreamingPreference(subscribedProviderIds = setOf("PROVIDER-A"))

        assertEquals(
            OfferReason.INCLUDED_IN_YOUR_SUBSCRIPTION,
            BestOfferPolicy.best(listOf(subscription(subscribed)), preference)?.reason,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Rentals
    // ---------------------------------------------------------------------------------------------

    /** The cheaper rental wins, and — the part a naive filter drops — the dearer one is still listed. */
    @Test
    fun `the cheapest rental wins but the more expensive one is still listed`() {
        val cheap = rental(cheapStore, 399)
        val pricy = rental(pricyStore, 699)

        val ranking = BestOfferPolicy.rank(listOf(pricy, cheap), UserStreamingPreference.NONE)

        assertEquals(2, ranking.all.size, "no valid offer may be hidden")
        assertEquals(OfferReason.CHEAPEST_RENTAL, ranking.all[0].reason)
        assertEquals(cheapStore.id, ranking.all[0].provider.id)
        assertEquals(OfferReason.RENTAL, ranking.all[1].reason)
        assertEquals(pricyStore.id, ranking.all[1].provider.id)
    }

    /** Two providers at the same price: exactly one may be labelled cheapest. */
    @Test
    fun `only one rental is labelled cheapest when prices tie`() {
        val ranking =
            BestOfferPolicy.rank(
                listOf(rental(cheapStore, 499), rental(pricyStore, 499)),
                UserStreamingPreference.NONE,
            )

        assertEquals(1, ranking.all.count { it.reason == OfferReason.CHEAPEST_RENTAL })
        assertEquals(2, ranking.all.size)
    }

    /**
     * No exchange rates exist in the app, so a foreign-currency quote is never declared cheaper.
     * The preferred currency decides which rentals compete.
     */
    @Test
    fun `rentals in another currency are shown but not compared`() {
        val euro = rental(pricyStore, 999, "EUR")
        val other = rental(cheapStore, 100, "BRL")

        val ranking =
            BestOfferPolicy.rank(
                listOf(euro, other),
                UserStreamingPreference(preferredCurrency = "EUR"),
            )

        assertEquals(2, ranking.all.size)
        val cheapest = ranking.all.single { it.reason == OfferReason.CHEAPEST_RENTAL }
        assertEquals("EUR", cheapest.offer.price?.currencyCode)
    }

    /** An unpriced rental cannot be claimed to be the cheapest, but it is still offered. */
    @Test
    fun `a rental with no price is listed but never called cheapest`() {
        val unpriced = StreamingOffer(provider = pricyStore, type = OfferType.RENT)

        val ranking = BestOfferPolicy.rank(listOf(unpriced, rental(cheapStore, 499)), UserStreamingPreference.NONE)

        assertEquals(2, ranking.all.size)
        assertEquals(cheapStore.id, ranking.all.single { it.reason == OfferReason.CHEAPEST_RENTAL }.provider.id)
        assertEquals(OfferReason.RENTAL, ranking.all.single { it.provider.id == pricyStore.id }.reason)
    }

    // ---------------------------------------------------------------------------------------------
    // The user's own library
    // ---------------------------------------------------------------------------------------------

    /**
     * The library outranks a subscription the user already pays for and a free-with-ads offer, and
     * carries no price at all — it is an origin, not a discount.
     */
    @Test
    fun `the user library outranks everything and carries no price`() {
        val library = StreamingOffer(provider = userLibrary, type = OfferType.USER_LIBRARY)
        val ranking =
            BestOfferPolicy.rank(
                listOf(
                    rental(cheapStore, 199),
                    StreamingOffer(provider = freeService, type = OfferType.FREE_WITH_ADS),
                    subscription(subscribed),
                    library,
                ),
                prefersA,
            )

        val best = assertNotNull(ranking.best)
        assertEquals(OfferReason.IN_YOUR_LIBRARY, best.reason)
        assertEquals(userLibrary.id, best.provider.id)
        assertNull(best.offer.price, "the user's own library is an origin, not a free offer")
        assertTrue(!best.offer.isPaidPerTitle)
    }

    /**
     * The invariant that stops the library being modelled as a zero price. A "free" badge on the
     * user's own list would claim the product gives content away.
     */
    @Test
    fun `a user library offer cannot be constructed with a price`() {
        val failure =
            runCatching {
                StreamingOffer(provider = userLibrary, type = OfferType.USER_LIBRARY, price = Price(0, "EUR"))
            }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "a zero price on the user's library must be rejected")
    }

    @Test
    fun `a subscription offer cannot carry a price`() {
        assertTrue(
            runCatching {
                StreamingOffer(provider = subscribed, type = OfferType.SUBSCRIPTION, price = Price(499, "EUR"))
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `the reserved library provider cannot back some other offer type`() {
        assertTrue(
            runCatching {
                StreamingOffer(provider = userLibrary, type = OfferType.FREE_WITH_ADS)
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Empty and degenerate input
    // ---------------------------------------------------------------------------------------------

    /** An empty list is an ordinary answer from a catalogue, so it yields a value, not a throw. */
    @Test
    fun `an empty offer list yields a defined result rather than an exception`() {
        val ranking = BestOfferPolicy.rank(emptyList(), prefersA)

        assertTrue(ranking.isEmpty)
        assertNull(ranking.best)
        assertEquals(emptyList(), ranking.alternatives)
        assertNull(BestOfferPolicy.best(emptyList()))
    }

    @Test
    fun `a title known only to be unavailable still ranks`() {
        val ranking = BestOfferPolicy.rank(listOf(StreamingOffer(provider = notSubscribed, type = OfferType.UNAVAILABLE)))

        assertEquals(OfferReason.NOT_AVAILABLE, ranking.best?.reason)
        assertTrue(ranking.alternatives.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // Full order and stability
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the full order runs library subscription free rent buy then unsubscribed`() {
        val offers =
            listOf(
                StreamingOffer(provider = notSubscribed, type = OfferType.SUBSCRIPTION),
                StreamingOffer(provider = pricyStore, type = OfferType.BUY, price = Price(1499, "EUR")),
                rental(cheapStore, 499),
                StreamingOffer(provider = freeService, type = OfferType.FREE_WITH_ADS),
                subscription(subscribed),
                StreamingOffer(provider = userLibrary, type = OfferType.USER_LIBRARY),
            )

        val ranking = BestOfferPolicy.rank(offers.shuffled(), prefersA)

        assertEquals(
            listOf(
                OfferReason.IN_YOUR_LIBRARY,
                OfferReason.INCLUDED_IN_YOUR_SUBSCRIPTION,
                OfferReason.FREE_WITH_ADS,
                OfferReason.CHEAPEST_RENTAL,
                OfferReason.PURCHASE,
                OfferReason.REQUIRES_NEW_SUBSCRIPTION,
            ),
            ranking.all.map(RankedOffer::reason),
        )
        assertEquals(offers.size, ranking.all.size, "ranking must never drop an offer")
    }

    /** Ranking must not depend on the order the adapter happened to emit. */
    @Test
    fun `ranking is stable regardless of input order`() {
        val offers =
            listOf(
                subscription(subscribed),
                rental(cheapStore, 499),
                rental(pricyStore, 899),
                StreamingOffer(provider = notSubscribed, type = OfferType.SUBSCRIPTION),
            )

        val forwards = BestOfferPolicy.rank(offers, prefersA).all.map { it.provider.id }
        val backwards = BestOfferPolicy.rank(offers.reversed(), prefersA).all.map { it.provider.id }

        assertEquals(forwards, backwards)
    }

    @Test
    fun `every input offer appears exactly once in the ranking`() {
        val offers =
            listOf(
                rental(cheapStore, 399),
                rental(pricyStore, 399),
                StreamingOffer(provider = freeService, type = OfferType.FREE_WITH_ADS),
                subscription(notSubscribed),
            )

        val ranking = BestOfferPolicy.rank(offers, UserStreamingPreference.NONE)

        assertEquals(offers.size, ranking.all.size)
        assertEquals(offers.toSet(), ranking.all.map(RankedOffer::offer).toSet())
    }
}
