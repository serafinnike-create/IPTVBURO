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

    /**
     * As many titles as were asked for, and no title twice.
     *
     * The count itself is not the point and has changed — five was a handful somebody recognised by
     * the second day, so the banner now cycles twenty. What must hold is that the rotation fills up
     * and that nothing repeats inside it.
     */
    @Test
    fun `the rotation holds the titles it was asked for, all distinct`() {
        val pool = (1..40).map { index -> candidate("c$index", rating = 7.0) }

        val rotation = HeroSelection.rotationFor(pool, dayOfEpoch = 20_000, count = 12)

        assertEquals(12, rotation.size)
        assertEquals(12, rotation.map(HeroCandidate::id).toSet().size, "the same title appeared twice")
    }

    /** And the default fills from a pool that can supply it. */
    @Test
    fun `the default rotation is filled`() {
        val pool = (1..40).map { index -> candidate("c$index", rating = 7.0) }

        val rotation = HeroSelection.rotationFor(pool, dayOfEpoch = 20_000)

        assertEquals(
            rotation.size,
            rotation.map(HeroCandidate::id).toSet().size,
            "the same title appeared twice",
        )
        assertTrue(rotation.size > 5, "a rotacao devia mostrar mais do que um punhado")
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
    fun `an unrated title is treated as unremarkable - not bad`() {
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

        // Bounded by the pool, which is the point: a rotation cannot invent titles.
        assertEquals(10, HeroSelection.rotationFor(pool, dayOfEpoch = -3).size)
    }

    private fun mixCandidate(
        id: String,
        year: Int,
        isSeries: Boolean = false,
        categories: List<String> = emptyList(),
        addedAt: Long? = null,
    ) = HeroCandidate(
        id = id,
        title = "Title $id",
        year = year,
        rating = 7.0,
        categoryIds = categories,
        isSeries = isSeries,
        addedAtEpochSeconds = addedAt,
    )

    /**
     * Three of today's arrivals, thirty other current releases, and one of every other kind.
     *
     * [now] is null for a provider that does not date its entries, which many do not.
     */
    private fun mixPool(thisYear: Int, now: Long? = null) =
        (1..3).map { index -> mixCandidate("hoje$index", thisYear, addedAt = now?.minus(3_600L)) } +
            (1..30).map { index -> mixCandidate("new$index", thisYear) } +
            listOf(
                mixCandidate("serie", thisYear, isSeries = true),
                mixCandidate("velho1", thisYear - 30),
                mixCandidate("velho2", thisYear - 25),
                mixCandidate("meio1", thisYear - 8),
                mixCandidate("meio2", thisYear - 9),
                mixCandidate("anime", thisYear - 5, categories = listOf("Animes | Lancamentos")),
            )

    /**
     * The banner leads with what arrived today, then one of each thing it would otherwise miss.
     *
     * Ranked purely by score it fills with whatever the catalogue has most of, and somebody
     * scrolling past twenty titles from the same year learns nothing about what else is there. But
     * the point of the banner is what is new, so the other kinds get one slot each and no more.
     */
    @Test
    fun `the rotation leads with today and carries one of each other kind`() {
        val thisYear = 2026
        val now = 1_800_000_000L

        val mixed = HeroSelection.mixed(mixPool(thisYear, now), thisYear, now).take(6)
        val ids = mixed.map { it.id }

        assertEquals(
            listOf("hoje1", "hoje2", "hoje3"),
            ids.take(3),
            "os lancamentos do dia nao vem a frente: $ids",
        )
        assertTrue("anime" in ids, "sem anime nenhum: $ids")
        assertTrue("serie" in ids, "sem serie nenhuma: $ids")
        assertTrue("velho1" in ids, "sem filme antigo nenhum: $ids")
    }

    /** And it carries both a film and a series, because the app is for both. */
    @Test
    fun `the front of the rotation carries a film and a series`() {
        val thisYear = 2026

        val front = HeroSelection.mixed(mixPool(thisYear), thisYear).take(4)

        assertTrue(front.any { !it.isSeries }, "o banner nao mostra nenhum filme")
        assertTrue(front.any { it.isSeries }, "o banner nao mostra nenhuma serie")
    }

    /**
     * Everything after the four reserved slots is a current release.
     *
     * The banner is about what is new. The anime, the series and the old film are there so it is
     * not *only* that — not so it becomes a survey of the catalogue.
     */
    @Test
    fun `the rest of the rotation is current releases`() {
        val thisYear = 2026
        val now = 1_800_000_000L

        val rest = HeroSelection.mixed(mixPool(thisYear, now), thisYear, now).drop(6).take(8)

        assertTrue(
            rest.all { it.year != null && it.year!! >= thisYear - HeroSelection.NEW_RELEASE_YEARS },
            "o resto do banner nao sao lancamentos: ${rest.map { "${it.id}(${it.year})" }}",
        )
    }

    /** A provider that does not date its entries still fills the banner. */
    @Test
    fun `a catalogue with no dates still fills the banner`() {
        val pool = mixPool(2026, now = null)

        val mixed = HeroSelection.mixed(pool, 2026, nowEpochSeconds = 1_800_000_000L)

        assertEquals(pool.size, mixed.size)
    }

    /** Nothing is lost and nothing is duplicated: the mix reorders, it does not filter. */
    @Test
    fun `the mix keeps every title exactly once`() {
        val pool = mixPool(2026)

        val mixed = HeroSelection.mixed(pool, 2026)

        assertEquals(pool.size, mixed.size, "a mistura perdeu ou repetiu titulos")
        assertEquals(pool.map { it.id }.toSet(), mixed.map { it.id }.toSet())
    }

    /**
     * A catalogue with none of a kind simply carries more of the others.
     *
     * Slots are an aim, not a requirement: a small provider with no old titles must still fill the
     * banner rather than showing fewer.
     */
    @Test
    fun `a pool missing a kind still fills the banner`() {
        val onlyNew = (1..10).map { index -> mixCandidate("new$index", 2026) }

        val mixed = HeroSelection.mixed(onlyNew, 2026)

        assertEquals(10, mixed.size)
    }

    /** A title with no year is treated as middle-aged, not as ancient. */
    @Test
    fun `an unknown year does not count as old`() {
        val pool =
            (1..8).map { index -> mixCandidate("new$index", 2026) } +
                HeroCandidate(id = "sem-ano", title = "Sem ano", rating = 7.0)

        val mixed = HeroSelection.mixed(pool, 2026)

        assertEquals(pool.size, mixed.size)
    }
}
