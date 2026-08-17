package com.lucasserafin94.iptvburo.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The "Lançamentos" shelf has to change from one day to the next.
 *
 * Reported as the releases being frozen: four days running, the same eighteen films, every card
 * reading ★5.0. They were not stale data — the shelf sorted the year's releases by rating and took
 * the first eighteen, with nothing in the selection tied to the date, so the best-rated titles were
 * the honest answer every single day. The ★5.0 on every card was the tell.
 *
 * Every other shelf on the Home already rotates on a date-derived seed. These tests pin the same
 * behaviour here, and pin the constraint that makes the shelf worth having: what rotates in must
 * still come from the well-rated part of the year, not from the unrated tail.
 */
class ReleaseShelfRotationTest {
    /**
     * The selection rule, transcribed from `SessionXtreamRepository.releasesForYear`.
     *
     * Duplicated rather than driven through the repository, which wants a live session, a catalogue
     * and a provider. What is under test is the arithmetic, and it is the arithmetic that was wrong.
     */
    private fun shelf(
        ranked: List<String>,
        limit: Int,
        rotation: Int,
    ): List<String> {
        if (rotation == 0 || ranked.size <= limit) return ranked.take(limit)
        val pool = ranked.take((limit * 4).coerceAtMost(ranked.size))
        val offset = Math.floorMod(rotation, pool.size)
        return List(limit.coerceAtMost(pool.size)) { index -> pool[(offset + index) % pool.size] }
    }

    /** A year's releases, best first, as the repository hands them over. */
    private val ranked = (1..120).map { position -> "film-$position" }

    private fun seedFor(dayOfYear: Int, year: Int = 2026) = dayOfYear * 11 + year

    @Test
    fun `consecutive days do not show the same shelf`() {
        val today = shelf(ranked, limit = 18, rotation = seedFor(229))
        val tomorrow = shelf(ranked, limit = 18, rotation = seedFor(230))

        assertNotEquals(today, tomorrow, "The shelf did not move overnight.")
    }

    /**
     * The reported symptom, as a test: four days must not be one shelf.
     *
     * Four because that is how long the user watched it stay still before saying so.
     */
    @Test
    fun `four days running produce four different shelves`() {
        val days = (229..232).map { day -> shelf(ranked, limit = 18, rotation = seedFor(day)) }

        assertEquals(4, days.distinct().size, "Some of the four days repeated a shelf.")
    }

    /** The same day always gives the same shelf, or the Home would reshuffle under the user. */
    @Test
    fun `the same day is stable`() {
        assertEquals(
            shelf(ranked, limit = 18, rotation = seedFor(229)),
            shelf(ranked, limit = 18, rotation = seedFor(229)),
        )
    }

    /**
     * Rotation must not become a licence to show anything.
     *
     * The shelf's value is that its contents are worth watching. The pool is bounded to four
     * shelves' worth of the best entries, so nothing from position 73 onward can appear here even
     * though the year holds 120 releases.
     */
    @Test
    fun `everything shown comes from the well-rated pool`() {
        val poolCeiling = 18 * 4
        val allowed = ranked.take(poolCeiling).toSet()

        (1..365).forEach { day ->
            val shown = shelf(ranked, limit = 18, rotation = seedFor(day))
            val strays = shown.filterNot(allowed::contains)
            assertTrue(strays.isEmpty(), "Day $day reached outside the pool: $strays")
        }
    }

    /**
     * The shelf is always full.
     *
     * Taking a window from the offset without wrapping returns a short list on most days, and a Home
     * row whose length changes reads as a loading fault rather than as a different selection.
     */
    @Test
    fun `the shelf is never short`() {
        (1..365).forEach { day ->
            val shown = shelf(ranked, limit = 18, rotation = seedFor(day))
            assertEquals(18, shown.size, "Day $day produced ${shown.size} cards.")
        }
    }

    /** And never shows the same film twice within one day, which wrapping could cause. */
    @Test
    fun `no day repeats a film within its own shelf`() {
        (1..365).forEach { day ->
            val shown = shelf(ranked, limit = 18, rotation = seedFor(day))
            assertEquals(shown.size, shown.distinct().size, "Day $day repeated a film: $shown")
        }
    }

    /**
     * A short year still works.
     *
     * Early in January, or on a small playlist, the year may hold fewer releases than the shelf
     * wants. Rotating a pool smaller than the limit must not crash or produce duplicates — it simply
     * shows what there is.
     */
    @Test
    fun `a year with fewer releases than the shelf holds is returned whole`() {
        val sparse = (1..5).map { "film-$it" }

        val shown = shelf(sparse, limit = 18, rotation = seedFor(229))

        assertEquals(sparse, shown, "A short year should be shown as-is.")
    }

    /** Films and series must not advance in lockstep, or the two rows look like one. */
    @Test
    fun `films and series rotate independently`() {
        val filmSeed = 229 * 11 + 2026
        val seriesSeed = 229 * 19 + 2026

        assertNotEquals(
            shelf(ranked, limit = 18, rotation = filmSeed),
            shelf(ranked, limit = 18, rotation = seriesSeed),
            "The films and series shelves moved together.",
        )
    }

    /** Zero keeps the old, stable "best of the year" ordering for callers that want it. */
    @Test
    fun `rotation zero is the unrotated ranking`() {
        assertEquals(ranked.take(18), shelf(ranked, limit = 18, rotation = 0))
    }
}
