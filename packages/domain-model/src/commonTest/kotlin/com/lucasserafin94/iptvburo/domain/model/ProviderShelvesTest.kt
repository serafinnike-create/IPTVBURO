package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The "browse by service" grouping — GDD 9.
 *
 * The rules under test are product rules, not rendering details: a title on three services belongs
 * on three shelves, the user's own list is not a service, and an offer the catalogue marked
 * unavailable must not be turned into a claim that the service carries it.
 */
class ProviderShelvesTest {
    private val netflix = StreamingProvider.of("netflix", "Netflix")
    private val prime = StreamingProvider.of("prime-video", "Prime Video")
    private val appleTv = StreamingProvider.of("apple-tv", "Apple TV")
    private val yourList = StreamingProvider.of(USER_LIBRARY_PROVIDER_ID, "Your list")

    private fun title(
        id: String,
        name: String,
        year: Int? = 2024,
        isDemo: Boolean = false,
        offers: List<StreamingOffer> = emptyList(),
    ) = ExternalTitleDetails(
        title =
            ExternalTitle(
                id = ExternalContentId("test", id),
                title = name,
                kind = ExternalTitleKind.MOVIE,
                year = year,
                isDemo = isDemo,
            ),
        offers = offers,
    )

    private fun subscription(provider: StreamingProvider) = StreamingOffer(provider = provider, type = OfferType.SUBSCRIPTION)

    // -------------------------------------------------------------------------------------------
    // The central rule: one title, many shelves
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a title carried by three services appears on three shelves`() {
        val shared =
            title(
                "1",
                "Shared",
                offers = listOf(subscription(netflix), subscription(prime), subscription(appleTv)),
            )

        val shelves = streamingShelves(listOf(shared))

        assertEquals(3, shelves.size)
        assertEquals(
            setOf("netflix", "prime-video", "apple-tv"),
            shelves.map { it.provider.id }.toSet(),
        )
        shelves.forEach { shelf ->
            assertEquals(listOf("Shared"), shelf.titles.map { it.title.title })
        }
    }

    @Test
    fun `each provider gets its own shelf holding only what it carries`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "Only Netflix", offers = listOf(subscription(netflix))),
                    title("2", "Only Prime", offers = listOf(subscription(prime))),
                ),
            )

        assertEquals(2, shelves.size)
        val byId = shelves.associateBy { it.provider.id }
        assertEquals(listOf("Only Netflix"), byId.getValue("netflix").titles.map { it.title.title })
        assertEquals(listOf("Only Prime"), byId.getValue("prime-video").titles.map { it.title.title })
    }

    @Test
    fun `a title with no offers lands on no shelf at all`() {
        assertTrue(streamingShelves(listOf(title("1", "Orphan"))).isEmpty())
    }

    @Test
    fun `an empty catalogue produces no shelves rather than an empty shelf`() {
        assertTrue(streamingShelves(emptyList()).isEmpty())
    }

    // -------------------------------------------------------------------------------------------
    // What must not become a shelf
    // -------------------------------------------------------------------------------------------

    /** The user's list already has its own screen; a rail of it beside Netflix duplicates it. */
    @Test
    fun `the user's own library is not a service and gets no shelf by default`() {
        val shelves =
            streamingShelves(
                listOf(
                    title(
                        "1",
                        "Mine",
                        offers =
                            listOf(
                                StreamingOffer(provider = yourList, type = OfferType.USER_LIBRARY),
                                subscription(netflix),
                            ),
                    ),
                ),
            )

        assertEquals(listOf("netflix"), shelves.map { it.provider.id })
    }

    @Test
    fun `the user's library can be included when a caller asks for it`() {
        val shelves =
            streamingShelves(
                listOf(
                    title(
                        "1",
                        "Mine",
                        offers = listOf(StreamingOffer(provider = yourList, type = OfferType.USER_LIBRARY)),
                    ),
                ),
                includeUserLibrary = true,
            )

        assertEquals(listOf(USER_LIBRARY_PROVIDER_ID), shelves.map { it.provider.id })
    }

    /**
     * The rule with the most at stake: UNAVAILABLE means the catalogue said "not watchable here".
     * Putting the title on that service's shelf would assert the opposite.
     */
    @Test
    fun `an unavailable offer does not put a title on that service's shelf`() {
        val shelves =
            streamingShelves(
                listOf(
                    title(
                        "1",
                        "Not here",
                        offers = listOf(StreamingOffer(provider = netflix, type = OfferType.UNAVAILABLE)),
                    ),
                ),
            )

        assertTrue(shelves.isEmpty())
    }

    @Test
    fun `unavailable offers can be included deliberately`() {
        val shelves =
            streamingShelves(
                listOf(
                    title(
                        "1",
                        "Not here",
                        offers = listOf(StreamingOffer(provider = netflix, type = OfferType.UNAVAILABLE)),
                    ),
                ),
                includeUnavailable = true,
            )

        assertEquals(listOf("netflix"), shelves.map { it.provider.id })
    }

    @Test
    fun `a service keeps the titles it does carry when another of its offers is unavailable`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "Available", offers = listOf(subscription(netflix))),
                    title(
                        "2",
                        "Gone",
                        offers = listOf(StreamingOffer(provider = netflix, type = OfferType.UNAVAILABLE)),
                    ),
                ),
            )

        assertEquals(1, shelves.size)
        assertEquals(listOf("Available"), shelves.single().titles.map { it.title.title })
    }

    // -------------------------------------------------------------------------------------------
    // Identity and deduplication
    // -------------------------------------------------------------------------------------------

    @Test
    fun `two offers from one service put the title on its shelf once`() {
        val shelves =
            streamingShelves(
                listOf(
                    title(
                        "1",
                        "Both ways",
                        offers =
                            listOf(
                                StreamingOffer(provider = appleTv, type = OfferType.RENT, price = Price(990, "BRL")),
                                StreamingOffer(provider = appleTv, type = OfferType.BUY, price = Price(3990, "BRL")),
                            ),
                    ),
                ),
            )

        assertEquals(1, shelves.size)
        assertEquals(1, shelves.single().size)
    }

    @Test
    fun `casing in a provider id never splits one service into two shelves`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "A", offers = listOf(subscription(StreamingProvider.of("Netflix", "Netflix")))),
                    title("2", "B", offers = listOf(subscription(StreamingProvider.of("  netflix ", "Netflix")))),
                ),
            )

        assertEquals(1, shelves.size)
        assertEquals(2, shelves.single().size)
    }

    // -------------------------------------------------------------------------------------------
    // Ordering: the same response must always render the same way
    // -------------------------------------------------------------------------------------------

    @Test
    fun `shelves are ordered by size - largest first`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "A", offers = listOf(subscription(netflix), subscription(prime))),
                    title("2", "B", offers = listOf(subscription(netflix))),
                    title("3", "C", offers = listOf(subscription(netflix))),
                ),
            )

        assertEquals(listOf("netflix", "prime-video"), shelves.map { it.provider.id })
    }

    @Test
    fun `shelves of equal size fall back to the provider name`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "A", offers = listOf(subscription(prime))),
                    title("2", "B", offers = listOf(subscription(appleTv))),
                    title("3", "C", offers = listOf(subscription(netflix))),
                ),
            )

        assertEquals(listOf("Apple TV", "Netflix", "Prime Video"), shelves.map { it.provider.displayName })
    }

    @Test
    fun `titles within a shelf are ordered by name regardless of catalogue order`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "Zebra", offers = listOf(subscription(netflix))),
                    title("2", "alpha", offers = listOf(subscription(netflix))),
                    title("3", "Mango", offers = listOf(subscription(netflix))),
                ),
            )

        assertEquals(listOf("alpha", "Mango", "Zebra"), shelves.single().titles.map { it.title.title })
    }

    /** Two different films sharing a name still need a defined order between them. */
    @Test
    fun `identically named titles are ordered by their stable id`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("b", "Remake", offers = listOf(subscription(netflix))),
                    title("a", "Remake", offers = listOf(subscription(netflix))),
                ),
            )

        assertEquals(listOf("test:a", "test:b"), shelves.single().titles.map { it.title.id.key })
    }

    @Test
    fun `the same catalogue in a different order produces identical shelves`() {
        val catalogue =
            listOf(
                title("1", "A", offers = listOf(subscription(netflix), subscription(prime))),
                title("2", "B", offers = listOf(subscription(prime))),
                title("3", "C", offers = listOf(subscription(netflix))),
            )

        assertEquals(
            streamingShelves(catalogue).map { it.provider.id to it.titles.map { t -> t.title.id.key } },
            streamingShelves(catalogue.reversed()).map { it.provider.id to it.titles.map { t -> t.title.id.key } },
        )
    }

    // -------------------------------------------------------------------------------------------
    // Bounds and invariants
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a shelf is capped so one huge service cannot produce an unrenderable rail`() {
        val many = (1..50).map { index -> title("$index", "Title $index", offers = listOf(subscription(netflix))) }

        assertEquals(3, streamingShelves(many, maxTitlesPerShelf = 3).single().size)
        assertEquals(DEFAULT_SHELF_SIZE, streamingShelves(many).single().size)
    }

    @Test
    fun `a shelf size of zero is refused rather than silently emptying every rail`() {
        assertTrue(
            runCatching { streamingShelves(emptyList(), maxTitlesPerShelf = 0) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `an empty shelf cannot be constructed`() {
        assertTrue(
            runCatching { ProviderShelf(provider = netflix, titles = emptyList()) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `no shelf returned is ever empty`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "A", offers = listOf(subscription(netflix))),
                    title("2", "B"),
                ),
            )

        assertTrue(shelves.all { it.titles.isNotEmpty() })
    }

    // -------------------------------------------------------------------------------------------
    // The demo marking, which must survive grouping
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a shelf built from fixture titles reports itself as demo`() {
        val shelves =
            streamingShelves(
                listOf(title("1", "Made up", isDemo = true, offers = listOf(subscription(netflix)))),
            )

        assertTrue(shelves.single().isDemo)
    }

    @Test
    fun `a shelf mixing real and fixture titles is not reported as wholly demo`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "Made up", isDemo = true, offers = listOf(subscription(netflix))),
                    title("2", "Real", isDemo = false, offers = listOf(subscription(netflix))),
                ),
            )

        assertFalse(shelves.single().isDemo)
    }

    @Test
    fun `grouping preserves the demo flag on every title it moves`() {
        val shelves =
            streamingShelves(
                listOf(
                    title("1", "Made up", isDemo = true, offers = listOf(subscription(netflix), subscription(prime))),
                ),
            )

        assertTrue(shelves.flatMap { it.titles }.all { it.title.isDemo })
    }

    // -------------------------------------------------------------------------------------------
    // Single-provider lookup
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a single provider's shelf can be looked up by id`() {
        val catalogue =
            listOf(
                title("1", "A", offers = listOf(subscription(netflix), subscription(prime))),
                title("2", "B", offers = listOf(subscription(prime))),
            )

        val shelf = streamingShelfFor(catalogue, "NETFLIX")

        assertNotNull(shelf)
        assertEquals("netflix", shelf.provider.id)
        assertEquals(listOf("A"), shelf.titles.map { it.title.title })
    }

    @Test
    fun `looking up a service that carries nothing returns null`() {
        assertNull(
            streamingShelfFor(
                listOf(title("1", "A", offers = listOf(subscription(netflix)))),
                "disney-plus",
            ),
        )
    }

    @Test
    fun `the user's library can be looked up by name even though it is not a shelf by default`() {
        val catalogue =
            listOf(
                title(
                    "1",
                    "Mine",
                    offers = listOf(StreamingOffer(provider = yourList, type = OfferType.USER_LIBRARY)),
                ),
            )

        assertNotNull(streamingShelfFor(catalogue, USER_LIBRARY_PROVIDER_ID))
        assertTrue(streamingShelves(catalogue).isEmpty())
    }
}
