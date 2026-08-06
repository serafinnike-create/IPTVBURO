package com.lucasserafin94.iptvburo.desktop.app

import com.lucasserafin94.iptvburo.desktop.data.DemoStreamingDiscoveryProvider
import com.lucasserafin94.iptvburo.domain.model.BestOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.ExternalContentLauncher
import com.lucasserafin94.iptvburo.domain.model.LaunchDecision
import com.lucasserafin94.iptvburo.domain.model.OfferReason
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryCapability
import com.lucasserafin94.iptvburo.domain.model.USER_LIBRARY_PROVIDER_ID
import com.lucasserafin94.iptvburo.domain.model.streamingShelves
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the rule that the Assinaturas area is only ever as visible as its backing is real.
 *
 * This exists because a screen can compile, pass its own tests and still never reach the user — the
 * failure is always in the wiring, never in the screen. These assertions pin the wiring itself.
 */
class SubscriptionsVisibilityTest {
    @Test
    fun `with nothing at all behind it the area stays out of navigation`() {
        // Removing the demo catalogue must take the entry with it, rather than leaving a screen
        // that opens onto nothing.
        val capability = StreamingDiscoveryCapability.of(hasRealProvider = false, hasFixtureProvider = false)

        assertFalse(capability.isVisible)
    }

    @Test
    fun `connecting a provider is the only thing that reveals the area`() {
        assertFalse(StreamingDiscoveryCapability.of(hasRealProvider = false).isVisible)
        assertTrue(StreamingDiscoveryCapability.of(hasRealProvider = true).isVisible)
    }

    /**
     * The state the app actually ships in right now: the demo catalogue is wired up, so the entry
     * is in the sidebar and every row is badged. If this ever fails the section has silently
     * vanished from the UI again.
     */
    @Test
    fun `the demo catalogue makes the section reachable and badged`() {
        val provider = DemoStreamingDiscoveryProvider()
        val capability =
            StreamingDiscoveryCapability.of(hasRealProvider = false, hasFixtureProvider = true)

        assertTrue(capability.isVisible, "the Assinaturas entry must be in the sidebar")
        assertTrue(capability.requiresDemoLabel, "demo listings must be badged")
        assertTrue(DemoStreamingDiscoveryProvider.DEMO_CATALOGUE.isNotEmpty(), "the section must have something to list")
        assertTrue(
            DemoStreamingDiscoveryProvider.DEMO_CATALOGUE.all { details -> details.title.isDemo },
            "every demo title must carry isDemo, so an escaped one is still labelled",
        )
        assertNotNull(provider)
    }

    /**
     * The invented listings name real companies, so the ranking must be visibly doing its job:
     * the user's own list first, and no price attached to it.
     */
    @Test
    fun `the demo ranking puts the user's own list first and free of charge`() {
        val withLibrary =
            DemoStreamingDiscoveryProvider.DEMO_CATALOGUE
                .first { details -> details.offers.any { it.type == OfferType.USER_LIBRARY } }

        val best = BestOfferPolicy.rank(withLibrary.offers).best

        assertEquals(OfferReason.IN_YOUR_LIBRARY, best?.reason)
        assertNull(best?.offer?.price, "the user's own list is an origin, never a zero price")
    }

    /** Every demo destination must survive the launcher's checks, or the buttons do nothing. */
    @Test
    fun `every demo launch target is safe to open`() {
        DemoStreamingDiscoveryProvider.DEMO_CATALOGUE
            .flatMap { details -> details.offers }
            .mapNotNull { offer -> offer.launchTarget }
            .forEach { target ->
                val decision = ExternalContentLauncher.decide(target)
                assertTrue(
                    decision is LaunchDecision.OpenWeb || decision is LaunchDecision.OpenApp,
                    "a demo target was refused by the launcher: $decision",
                )
            }
    }

    @Test
    fun `a demo-backed area is visible but never unlabelled`() {
        val capability = StreamingDiscoveryCapability.of(hasRealProvider = false, hasFixtureProvider = true)

        assertTrue(capability.isVisible)
        // If this ever passes without the badge being rendered, invented listings reach the user
        // looking like real availability answers.
        assertTrue(capability.requiresDemoLabel)
    }

    // ---------------------------------------------------------------------------------------------
    // The shelves the screen opens on
    // ---------------------------------------------------------------------------------------------

    /**
     * The screen's default view is the shelves, so an empty grouping means an empty screen. This
     * pins the fixture to the thing the UI actually renders rather than to the flat list it used to.
     */
    @Test
    fun `the demo catalogue produces shelves for the screen to open on`() {
        val shelves = streamingShelves(DemoStreamingDiscoveryProvider.DEMO_CATALOGUE)

        assertTrue(shelves.isNotEmpty(), "the shelves view must have something to show on arrival")
        assertTrue(shelves.all { it.titles.isNotEmpty() }, "no shelf may be a heading over blank space")
    }

    /** The named services the user is expected to recognise must each get their own rail. */
    @Test
    fun `the demo services each get a shelf`() {
        val ids = streamingShelves(DemoStreamingDiscoveryProvider.DEMO_CATALOGUE).map { it.provider.id }

        listOf("netflix", "prime-video", "disney-plus", "apple-tv", "google-play").forEach { id ->
            assertTrue(id in ids, "$id has no shelf")
        }
    }

    /**
     * The whole point of the view: a title on several services is on several shelves. The demo
     * catalogue has one, so this also guards the fixture against being flattened later.
     */
    @Test
    fun `a demo title carried by several services appears on each of their shelves`() {
        val shelves = streamingShelves(DemoStreamingDiscoveryProvider.DEMO_CATALOGUE)

        val shelvesPerTitle =
            shelves
                .flatMap { shelf -> shelf.titles.map { it.title.id.key } }
                .groupingBy { it }
                .eachCount()

        assertTrue(
            shelvesPerTitle.values.any { it > 1 },
            "no demo title reaches more than one shelf, so the grouping is untested by the fixture",
        )
    }

    /** Grouping must not strip the marking that makes the invented listings honest. */
    @Test
    fun `every title on every demo shelf is still marked as demo`() {
        val shelves = streamingShelves(DemoStreamingDiscoveryProvider.DEMO_CATALOGUE)

        assertTrue(shelves.all { it.isDemo }, "a demo shelf reported itself as real")
        assertTrue(
            shelves.flatMap { it.titles }.all { it.title.isDemo },
            "a title lost its demo marking when it was grouped onto a shelf",
        )
    }

    /**
     * The demo catalogue marks one title as unavailable on a service. That must not become a claim
     * that the service carries it.
     */
    @Test
    fun `an unavailable demo listing does not put its title on that service's shelf`() {
        val unavailable =
            DemoStreamingDiscoveryProvider.DEMO_CATALOGUE
                .first { details -> details.offers.all { it.type == OfferType.UNAVAILABLE } }

        val carried =
            streamingShelves(DemoStreamingDiscoveryProvider.DEMO_CATALOGUE)
                .flatMap { shelf -> shelf.titles.map { it.title.id.key } }

        assertFalse(unavailable.title.id.key in carried)
    }

    /** The user's own list has its own screen; it must not appear as a service among the others. */
    @Test
    fun `the user's own list is not one of the demo shelves`() {
        val ids = streamingShelves(DemoStreamingDiscoveryProvider.DEMO_CATALOGUE).map { it.provider.id }

        assertFalse(USER_LIBRARY_PROVIDER_ID in ids)
    }
}
