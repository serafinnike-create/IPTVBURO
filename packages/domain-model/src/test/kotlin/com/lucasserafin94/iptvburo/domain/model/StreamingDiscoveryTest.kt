package com.lucasserafin94.iptvburo.domain.model

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The discovery contracts and their invariants.
 *
 * Covers the model rules that keep the "Assinaturas" area honest: identifiers that cannot collide,
 * launch targets that go somewhere and never reach a log, and a fixture whose results are always
 * marked as demo data.
 */
class StreamingDiscoveryTest {
    private val fixture = FakeStreamingDiscoveryProvider()


    // ---------------------------------------------------------------------------------------------
    // Provider identity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `provider ids are normalised so casing never splits one service in two`() {
        assertEquals(
            StreamingProvider.of("Provider-A", "Provider A"),
            StreamingProvider.of("  provider-a  ", "Provider A"),
        )
    }

    @Test
    fun `a provider needs an id and a name`() {
        assertTrue(runCatching { StreamingProvider(id = " ", displayName = "X") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { StreamingProvider(id = "x", displayName = " ") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `only the reserved id counts as the user library`() {
        assertTrue(StreamingProvider.of(USER_LIBRARY_PROVIDER_ID, "Your list").isUserLibrary)
        assertFalse(StreamingProvider.of("provider-a", "Provider A").isUserLibrary)
    }

    // ---------------------------------------------------------------------------------------------
    // Offer types
    // ---------------------------------------------------------------------------------------------

    /** Exactly the six types named in the specification, no more. */
    @Test
    fun `there are exactly six offer types`() {
        assertEquals(
            listOf("USER_LIBRARY", "SUBSCRIPTION", "FREE_WITH_ADS", "RENT", "BUY", "UNAVAILABLE"),
            OfferType.entries.map(OfferType::name),
        )
    }

    @Test
    fun `only rent and buy support a price`() {
        assertEquals(
            setOf(OfferType.RENT, OfferType.BUY),
            OfferType.entries.filter(OfferType::supportsPrice).toSet(),
        )
    }

    @Test
    fun `a price cannot be negative and compares within a currency`() {
        assertTrue(runCatching { Price(-1, "EUR") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(Price(399, "EUR") < Price(499, "EUR"))
        assertEquals("EUR", Price(399, " eur ").currencyCode)
    }

    // ---------------------------------------------------------------------------------------------
    // External identifiers
    // ---------------------------------------------------------------------------------------------

    /** Namespacing is what stops two catalogues reusing the same number for different films. */
    @Test
    fun `external ids from different namespaces never collide`() {
        val a = ExternalContentId("catalogue-one", "42")
        val b = ExternalContentId("catalogue-two", "42")

        assertTrue(a != b)
        assertEquals("catalogue-one:42", a.key)
    }

    /**
     * A discovery result maps onto the app's own identity, so it can be lined up against the user's
     * library without the foreign id ever becoming the persisted key.
     */
    @Test
    fun `an external title exposes the app's own content identity`() {
        val title =
            ExternalTitle(
                id = ExternalContentId("demo", "1"),
                title = "Demo Film One",
                kind = ExternalTitleKind.MOVIE,
                year = 2021,
            )

        assertEquals(ContentIdentity.of(ContentKind.MOVIE, "Demo Film One", 2021), title.contentIdentity)
    }

    @Test
    fun `a series and a film of the same name get distinct identities`() {
        val film = ExternalTitle(ExternalContentId("demo", "1"), "Same Name", ExternalTitleKind.MOVIE, 2020)
        val series = ExternalTitle(ExternalContentId("demo", "2"), "Same Name", ExternalTitleKind.SERIES, 2020)

        assertTrue(film.contentIdentity != series.contentIdentity)
    }

    // ---------------------------------------------------------------------------------------------
    // Launch targets
    // ---------------------------------------------------------------------------------------------

    /**
     * `ExternalLaunchTarget` and its safety rules are tested alongside `ExternalContentLauncher`,
     * which owns them. What belongs here is the join: an offer carries a target, and that target
     * must be one the launcher is willing to open. A discovery fixture that produced addresses the
     * launcher refuses would be describing a hand-off that cannot actually happen.
     */
    @Test
    fun `every fixture launch target is one the launcher will open`() {
        val targets =
            FakeStreamingDiscoveryProvider.DEMO_CATALOGUE
                .flatMap(ExternalTitleDetails::offers)
                .mapNotNull(StreamingOffer::launchTarget)

        assertTrue(targets.isNotEmpty(), "the fixture must exercise hand-off")
        targets.forEach { target ->
            assertTrue(
                ExternalContentLauncher.decide(target) !is LaunchDecision.Unavailable,
                "the launcher refused a fixture target: $target",
            )
        }
    }

    /** An offer that is only a signpost still need not carry a destination. */
    @Test
    fun `an offer may have no launch target`() {
        val offer = StreamingOffer(provider = StreamingProvider.of("p", "P"), type = OfferType.UNAVAILABLE)

        assertNull(offer.launchTarget)
    }

    // ---------------------------------------------------------------------------------------------
    // Preferences and details
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the default preference claims no subscriptions`() {
        assertFalse(UserStreamingPreference.NONE.subscribesTo(StreamingProvider.of("provider-a", "Provider A")))
    }

    @Test
    fun `details reject an implausible runtime but allow an unknown one`() {
        val title = ExternalTitle(ExternalContentId("demo", "1"), "Demo", ExternalTitleKind.MOVIE)

        assertTrue(
            runCatching { ExternalTitleDetails(title = title, runtimeMinutes = 0) }.exceptionOrNull()
                is IllegalArgumentException,
        )
        assertNull(ExternalTitleDetails(title = title).runtimeMinutes)
        assertTrue(ExternalTitleDetails(title = title).offers.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // The synthetic fixture
    // ---------------------------------------------------------------------------------------------

    /** Nothing the fixture returns may look like a genuine catalogue result. */
    @Test
    fun `every fixture title is marked as demo data`() {
        assertTrue(FakeStreamingDiscoveryProvider.DEMO_CATALOGUE.isNotEmpty())
        assertTrue(FakeStreamingDiscoveryProvider.DEMO_CATALOGUE.all { it.title.isDemo })
    }

    @Test
    fun `fixture search matches case insensitively and bounds its results`() = runBlockingTest {
        assertEquals(listOf("Demo Film One"), fixture.search("film one").map(ExternalTitle::title))
        assertEquals(1, fixture.search("demo", limit = 1).size)
    }

    @Test
    fun `fixture search returns nothing for a blank or unmatched query`() = runBlockingTest {
        assertTrue(fixture.search("   ").isEmpty())
        assertTrue(fixture.search("no such title").isEmpty())
    }

    @Test
    fun `fixture details and offers return null and empty for an unknown id`() = runBlockingTest {
        val unknown = ExternalContentId("demo", "does-not-exist")

        assertNull(fixture.details(unknown))
        assertTrue(fixture.offers(unknown).isEmpty(), "nothing known is a normal answer, not an error")
    }

    @Test
    fun `fixture offers rank through the policy`() = runBlockingTest {
        val offers = fixture.offers(ExternalContentId("demo", "2"))

        val ranking = BestOfferPolicy.rank(offers, UserStreamingPreference.NONE)

        assertEquals(OfferReason.IN_YOUR_LIBRARY, ranking.best?.reason)
        assertEquals(OfferReason.FREE_WITH_ADS, ranking.alternatives.single().reason)
    }

    @Test
    fun `fixture upcoming only returns unreleased titles`() = runBlockingTest {
        val upcoming = fixture.upcoming()

        assertTrue(upcoming.isNotEmpty())
        assertTrue(upcoming.all { (it.year ?: 0) > FakeStreamingDiscoveryProvider.DEMO_CURRENT_YEAR })
    }

    /**
     * Runs [block] to completion on the calling thread.
     *
     * The fixture is pure in-memory data, so none of its suspend functions ever actually suspend and
     * this returns before it hands back. Doing it with the stdlib's own `startCoroutine` keeps
     * `domain-model` free of a `kotlinx-coroutines-test` dependency that no module here carries — the
     * module stays pure Kotlin, as the domain layer is meant to.
     *
     * If a future implementation really did suspend, the assertion below fails loudly rather than
     * letting the test pass without having checked anything.
     */
    private fun runBlockingTest(block: suspend () -> Unit) {
        var outcome: Result<Unit>? = null
        block.startCoroutine(
            Continuation(EmptyCoroutineContext) { result -> outcome = result },
        )
        val settled = assertNotNull(outcome, "the fixture must not actually suspend")
        settled.getOrThrow()
    }
}
