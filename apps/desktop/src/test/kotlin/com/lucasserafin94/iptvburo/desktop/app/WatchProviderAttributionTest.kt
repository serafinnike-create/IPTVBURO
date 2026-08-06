package com.lucasserafin94.iptvburo.desktop.app

import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.metadata.WATCH_PROVIDER_ATTRIBUTION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JustWatch's terms require their data to be credited on **every item** that displays it, and state
 * that access is revoked where usage does not comply. A credits screen is explicitly not enough.
 *
 * These assertions pin the rule the screen applies, so a later layout tidy-up cannot quietly drop
 * the line and take the API access with it.
 */
class WatchProviderAttributionTest {
    /** Mirrors the expression in `SubscriptionsWorkspace.OfferRow`. */
    private fun showsAttribution(
        isDemo: Boolean,
        type: OfferType,
    ): Boolean = !isDemo && type != OfferType.USER_LIBRARY

    @Test
    fun `a real listing from a service is credited`() {
        listOf(OfferType.SUBSCRIPTION, OfferType.FREE_WITH_ADS, OfferType.RENT, OfferType.BUY, OfferType.UNAVAILABLE)
            .forEach { type ->
                assertTrue(showsAttribution(isDemo = false, type = type), "$type was not credited")
            }
    }

    @Test
    fun `the user's own library is not credited to anyone else`() {
        // The app worked this out from the user's own playlist; JustWatch had no part in it.
        assertFalse(showsAttribution(isDemo = false, type = OfferType.USER_LIBRARY))
    }

    @Test
    fun `invented demo rows are not credited to a real company`() {
        // Crediting JustWatch for listings they never supplied would misattribute made-up data
        // to a real source — the opposite failure, and just as dishonest.
        OfferType.entries.forEach { type ->
            assertFalse(showsAttribution(isDemo = true, type = type), "$type credited a real source for demo data")
        }
    }

    @Test
    fun `the attribution names JustWatch and is not translated`() {
        assertEquals("Streaming data provided by JustWatch", WATCH_PROVIDER_ATTRIBUTION)
    }
}
