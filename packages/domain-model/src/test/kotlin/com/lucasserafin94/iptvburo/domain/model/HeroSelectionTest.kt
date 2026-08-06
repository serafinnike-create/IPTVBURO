package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeroSelectionTest {
    private fun candidate(
        id: String,
        rating: Double? = null,
        year: Int? = null,
        hasArtwork: Boolean = true,
    ) = HeroCandidate(id = id, title = "Title $id", year = year, rating = rating, hasArtwork = hasArtwork)

    @Test
    fun `the rotation holds five distinct titles`() {
        val pool = (1..20).map { index -> candidate("c$index", rating = 7.0) }

        val rotation = HeroSelection.rotationFor(pool, dayOfEpoch = 20_000)

        assertEquals(5, rotation.size)
        assertEquals(5, rotation.map(HeroCandidate::id).toSet().size, "the same title appeared twice")
    }

    @Test
    fun `a better rated title beats a worse one`() {
        val pool = listOf(candidate("bad", rating = 3.0), candidate("good", rating = 9.0))

        val rotation = HeroSelection.rotationFor(pool, dayOfEpoch = 0, count = 1)

        assertEquals("good", rotation.single().id)
    }

    @Test
    fun `between equal ratings the newer title wins`() {
        val pool = listOf(candidate("old", rating = 8.0, year = 1995), candidate("new", rating = 8.0, year = 2025))

        assertEquals("new", HeroSelection.rotationFor(pool, dayOfEpoch = 0, count = 1).single().id)
    }

    /** Quality still outranks recency: a great old film beats a mediocre new one. */
    @Test
    fun `a much better old title beats a weak new one`() {
        val pool = listOf(candidate("classic", rating = 9.0, year = 1972), candidate("weak", rating = 5.0, year = 2026))

        assertEquals("classic", HeroSelection.rotationFor(pool, dayOfEpoch = 0, count = 1).single().id)
    }

    @Test
    fun `a title with no artwork is never chosen`() {
        val pool =
            listOf(
                candidate("no-art", rating = 10.0, hasArtwork = false),
                candidate("has-art", rating = 4.0, hasArtwork = true),
            )

        // The banner is mostly image; the highest rated title in the world renders as an empty
        // rectangle without one.
        assertEquals("has-art", HeroSelection.rotationFor(pool, dayOfEpoch = 0, count = 1).single().id)
    }

    @Test
    fun `an unrated title is treated as unremarkable, not bad`() {
        val pool = listOf(candidate("unrated"), candidate("poor", rating = 2.0), candidate("great", rating = 9.5))

        val order = HeroSelection.rotationFor(pool, dayOfEpoch = 0, count = 3).map(HeroCandidate::id)

        assertEquals("great", order.first())
        assertTrue(order.indexOf("unrated") < order.indexOf("poor"), "unrated ranked below a poorly rated title")
    }

    @Test
    fun `the same day always gives the same banner`() {
        val pool = (1..30).map { index -> candidate("c$index", rating = 5.0 + index % 5) }

        assertEquals(
            HeroSelection.rotationFor(pool, dayOfEpoch = 19_800),
            HeroSelection.rotationFor(pool, dayOfEpoch = 19_800),
            "the banner reshuffled between calls on the same day",
        )
    }

    @Test
    fun `a different day gives a different banner`() {
        val pool = (1..30).map { index -> candidate("c$index", rating = 7.0) }

        val today = HeroSelection.rotationFor(pool, dayOfEpoch = 19_800).map(HeroCandidate::id)
        val tomorrow = HeroSelection.rotationFor(pool, dayOfEpoch = 19_801).map(HeroCandidate::id)

        assertTrue(today != tomorrow, "the banner did not change overnight")
    }

    @Test
    fun `input order does not change the result`() {
        val pool = (1..25).map { index -> candidate("c$index", rating = 6.0 + index % 3) }

        // A refetch returning the same titles shuffled must not reshuffle the banner.
        assertEquals(
            HeroSelection.rotationFor(pool, dayOfEpoch = 100),
            HeroSelection.rotationFor(pool.shuffled(), dayOfEpoch = 100),
        )
    }

    @Test
    fun `a pool smaller than the rotation yields what there is`() {
        val pool = listOf(candidate("only", rating = 8.0), candidate("second", rating = 7.0))

        assertEquals(2, HeroSelection.rotationFor(pool, dayOfEpoch = 5).size)
    }

    @Test
    fun `an empty pool yields nothing rather than failing`() {
        assertTrue(HeroSelection.rotationFor(emptyList(), dayOfEpoch = 5).isEmpty())
        assertTrue(HeroSelection.rotationFor(listOf(candidate("a", hasArtwork = false)), dayOfEpoch = 5).isEmpty())
    }

    @Test
    fun `a negative day is handled like any other`() {
        // Dates before the epoch are not expected, but floorMod must not produce a negative index.
        val pool = (1..10).map { index -> candidate("c$index", rating = 7.0) }

        assertEquals(5, HeroSelection.rotationFor(pool, dayOfEpoch = -3).size)
    }
}
