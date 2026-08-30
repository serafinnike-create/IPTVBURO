package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the Descobrir deck offers, in what order, and what a swipe teaches it.
 *
 * The deck decides what somebody sees before they see it, so the rules worth pinning are the ones
 * that would be invisible when wrong: a title they already dismissed coming back, a deck that only
 * mirrors what they already like, or an order that reshuffles between two builds.
 */
class DiscoveryDeckTest {
    private fun candidate(
        id: String,
        genres: List<String> = emptyList(),
        rating: Double? = null,
        title: String = id,
    ) = DiscoveryCandidate(id = id, title = title, genres = genres, rating = rating)

    @Test
    fun `titles matching what the viewer keeps and watches come first`() {
        val deck =
            DiscoveryDeck.build(
                candidates =
                    listOf(
                        candidate("doc", listOf("Documentário"), rating = 9.5),
                        candidate("terror", listOf("Terror"), rating = 6.0),
                        candidate("comedia", listOf("Comédia"), rating = 7.0),
                    ),
                taste = TasteProfile(favouriteGenres = listOf("Terror"), watchedGenres = listOf("Terror")),
            )

        // The horror film leads despite the lowest rating of the three: rating is a tiebreaker, and
        // a well-reviewed documentary must not outrank the genre somebody actually watches.
        assertEquals("terror", deck.first().id)
    }

    @Test
    fun `a genre watched often outweighs one watched once`() {
        val taste = TasteProfile(watchedGenres = listOf("Ação", "Ação", "Ação", "Ação", "Romance"))

        assertTrue(
            DiscoveryDeck.score(candidate("a", listOf("Ação")), taste) >
                DiscoveryDeck.score(candidate("r", listOf("Romance")), taste),
            "four views must outweigh one",
        )
    }

    @Test
    fun `keeping something counts for more than merely watching it`() {
        assertTrue(
            DiscoveryDeck.score(candidate("a", listOf("Ação")), TasteProfile(favouriteGenres = listOf("Ação"))) >
                DiscoveryDeck.score(candidate("a", listOf("Ação")), TasteProfile(watchedGenres = listOf("Ação"))),
            "choosing to keep a title is a stronger signal than starting one",
        )
    }

    @Test
    fun `the same genre written differently is one taste`() {
        // Providers write these inconsistently, and treating "Ação" and "acao" as two tastes would
        // split the very signal the deck runs on.
        val taste = TasteProfile(favouriteGenres = listOf("Ação"))
        assertTrue(DiscoveryDeck.score(candidate("x", listOf("acao")), taste) > 0.0)
        assertTrue(DiscoveryDeck.score(candidate("y", listOf("  AÇÃO  ")), taste) > 0.0)
    }

    @Test
    fun `nothing already seen is ever offered again`() {
        // The rule people notice immediately when it breaks: a card they dismissed coming back
        // reads as the app ignoring them.
        val deck =
            DiscoveryDeck.build(
                candidates = listOf(candidate("a", listOf("Ação")), candidate("b", listOf("Ação"))),
                taste = TasteProfile(favouriteGenres = listOf("Ação"), seenIds = setOf("a")),
            )

        assertEquals(listOf("b"), deck.map { it.id })
    }

    @Test
    fun `a deck carries something outside the viewer's usual taste`() {
        // A deck that only confirms what somebody already likes stops being discovery. Twenty
        // action films and five of everything else: a purely ranked deck would be all action.
        val candidates =
            (1..20).map { candidate("acao-$it", listOf("Ação"), rating = 8.0) } +
                (1..5).map { candidate("outro-$it", listOf("Documentário"), rating = 5.0) }

        val deck = DiscoveryDeck.build(candidates, TasteProfile(favouriteGenres = listOf("Ação")))

        assertTrue(
            deck.any { card -> card.genres.none { it == "Ação" } },
            "the deck must include at least one departure from the usual taste",
        )
    }

    @Test
    fun `a deck is never longer than one sitting`() {
        val candidates = (1..200).map { candidate("id-$it", listOf("Ação"), rating = 7.0) }

        assertEquals(
            DiscoveryDeck.DECK_SIZE,
            DiscoveryDeck.build(candidates, TasteProfile(favouriteGenres = listOf("Ação"))).size,
        )
    }

    @Test
    fun `building the same deck twice gives the same order`() {
        // Otherwise the cards reshuffle under the finger of somebody who left the screen and came
        // back, which reads as the app losing their place.
        val candidates = (1..40).map { candidate("id-$it", listOf("Ação"), rating = it / 5.0) }
        val taste = TasteProfile(favouriteGenres = listOf("Ação"))

        assertEquals(
            DiscoveryDeck.build(candidates, taste).map { it.id },
            DiscoveryDeck.build(candidates, taste).map { it.id },
        )
    }

    @Test
    fun `a profile with no history is offered what the catalogue does best`() {
        // A first run has told us nothing. Guessing would be worse than offering what most people
        // like, and it must not come back empty for the person most likely to try the feature.
        val deck =
            DiscoveryDeck.build(
                candidates =
                    listOf(
                        candidate("fraco", rating = 3.0),
                        candidate("otimo", rating = 9.0),
                        candidate("medio", rating = 6.0),
                    ),
                taste = TasteProfile(),
            )

        assertEquals("otimo", deck.first().id)
        assertFalse(deck.isEmpty())
    }

    @Test
    fun `a catalogue with nothing left to offer produces an empty deck`() {
        // A real state with its own screen, not a failure: somebody who swiped through everything
        // has earned "you have seen it all" rather than a spinner.
        assertTrue(
            DiscoveryDeck.build(
                candidates = listOf(candidate("a")),
                taste = TasteProfile(seenIds = setOf("a")),
            ).isEmpty(),
        )
    }

    @Test
    fun `a duplicated catalogue row is offered once`() {
        assertEquals(
            1,
            DiscoveryDeck.build(
                candidates = listOf(candidate("a", rating = 8.0), candidate("a", rating = 8.0)),
                taste = TasteProfile(),
            ).size,
        )
    }

    // --- what this sitting's swipes teach the deck -------------------------------------------

    @Test
    fun `a genre just kept from is pushed up the next deck`() {
        // The responsiveness that makes swiping worth doing: keeping two comedies has to visibly
        // change what comes next, or the feature feels deaf.
        val session =
            SessionTaste()
                .after(listOf("Comédia"), DiscoveryVerdict.KEPT)
                .after(listOf("Comédia"), DiscoveryVerdict.KEPT)

        val deck =
            DiscoveryDeck.build(
                candidates = listOf(candidate("terror", listOf("Terror")), candidate("comedia", listOf("Comédia"))),
                taste = TasteProfile(),
                session = session,
            )

        assertEquals("comedia", deck.first().id)
    }

    @Test
    fun `a skip weighs less than a keep`() {
        // People skip for reasons that have nothing to do with genre — already seen it, poor
        // poster, not in the mood — so one skip must not bury a whole genre.
        val kept = SessionTaste().after(listOf("Drama"), DiscoveryVerdict.KEPT)
        val skipped = SessionTaste().after(listOf("Drama"), DiscoveryVerdict.SKIPPED)

        assertTrue(kept.leaningFor(listOf("Drama")) > 0.0, "keeping did not raise the genre")
        assertTrue(skipped.leaningFor(listOf("Drama")) < 0.0, "skipping did not lower the genre")
    }

    @Test
    fun `a long run of one answer is clamped`() {
        // Otherwise a viewer who keeps eight horror films in a row gets a deck of nothing but
        // horror, which is a dead end rather than discovery.
        var session = SessionTaste()
        repeat(30) { session = session.after(listOf("Terror"), DiscoveryVerdict.KEPT) }

        assertTrue(session.leaningFor(listOf("Terror")) <= 1.0)
        // And a genre nobody has said anything about is not dragged along by it.
        assertEquals(0.0, session.leaningFor(listOf("Nunca visto")))
    }

    @Test
    fun `swipes alone are enough to rank a deck`() {
        // A brand-new profile that has watched nothing still teaches the deck by swiping, which is
        // the only signal available on a first sitting.
        val session = SessionTaste().after(listOf("Terror"), DiscoveryVerdict.KEPT)

        val deck =
            DiscoveryDeck.build(
                candidates = listOf(candidate("outro", listOf("Comédia"), rating = 9.0), candidate("terror", listOf("Terror"), rating = 5.0)),
                taste = TasteProfile(),
                session = session,
            )

        assertEquals("terror", deck.first().id)
    }

    @Test
    fun `a title with no genres is still offered`() {
        // Most playlists carry patchy categories, and dropping those rows would empty the deck on
        // exactly the catalogues that need it most.
        val deck = DiscoveryDeck.build(listOf(candidate("sem-genero", rating = 7.0)), TasteProfile())

        assertEquals(1, deck.size)
    }

    /**
     * A card with a trailer moves on by itself; one without waits to be judged.
     *
     * The card shows a trailer beside the poster now, and once it has played its while the deck
     * advances. A card showing only a still poster has nothing to finish, so advancing it would be
     * sliding the screen away from somebody still reading it.
     */
    @Test
    fun `only a card with a trailer advances on its own`() {
        assertTrue(DiscoveryDeck.advancesOnItsOwn("abc123XYZ_-"))
        assertFalse(DiscoveryDeck.advancesOnItsOwn(null), "um cartao sem trailer passou sozinho")
        assertFalse(DiscoveryDeck.advancesOnItsOwn("   "), "um id vazio contou como trailer")
    }

    /** The card that ran out its trailer leaves the deck, and only that card. */
    @Test
    fun `passing over removes just that card`() {
        val deck = listOf(candidate("um"), candidate("dois"), candidate("tres"))

        val rest = DiscoveryDeck.afterPassingOver(deck, "dois")

        assertEquals(listOf("um", "tres"), rest.map { it.id })
    }

    /**
     * Passing over is not a verdict, and the taste profile must not learn from it.
     *
     * Wired to a decision, every card whose trailer simply finished would be filed as a film this
     * viewer rejected — a profile built from choices nobody made. The rule is that passing over
     * touches the deck and nothing else.
     */
    @Test
    fun `passing over teaches the session nothing`() {
        val untouched = SessionTaste()
        val terror = listOf("Terror")

        // A real verdict does move the leaning — asserted here so the check below is known to be
        // capable of failing, rather than passing because nothing could ever change it.
        val judged = untouched.after(terror, DiscoveryVerdict.SKIPPED)
        assertTrue(
            judged.leaningFor(terror) != untouched.leaningFor(terror),
            "um veredicto real nao mexeu no perfil, entao este teste nao prova nada",
        )

        DiscoveryDeck.afterPassingOver(listOf(candidate("um", terror)), "um")

        assertEquals(
            0.0,
            untouched.leaningFor(terror),
            "passar a frente ensinou alguma coisa ao perfil de gosto",
        )
    }

    /** A card nobody judged is not dropped from a deck it was never in. */
    @Test
    fun `passing over an unknown card changes nothing`() {
        val deck = listOf(candidate("um"), candidate("dois"))

        assertEquals(deck, DiscoveryDeck.afterPassingOver(deck, "nao-existe"))
    }
}
