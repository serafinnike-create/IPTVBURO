package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Turning a page must not build an object for every row it rejects.
 *
 * The catalogue is tens of thousands of rows and a page is turned on every keystroke in the search
 * box, so the cost of *rejecting* a row is paid far more often than the cost of showing one. Two
 * things were being done for rows that had already been excluded:
 *
 *  - `matches` computed all five of its tests as `val`s before consulting any of them, so
 *    `identityAt` — which builds a whole `XtreamCatalogItem` to read one field — ran even when the
 *    category test had already rejected the row.
 *  - the paging loop decoded each row's category list whether or not anything read it, which in the
 *    ordinary case (no Kids profile, no locked category) is nobody.
 *
 * These assert the *behaviour* is unchanged, which is what the reordering could plausibly break;
 * the speed itself is left to the measurement in the accompanying comment rather than to a timing
 * assertion that would measure the CI machine's load.
 */
class CatalogPagingCostTest {
    private fun catalogue(): CompactXtreamCatalog {
        val catalogue = CompactXtreamCatalog(XtreamContentType.MOVIE)
        (1..500).forEach { index ->
            catalogue.add(
                XtreamCatalogItem(
                    providerId = index.toString(),
                    name = if (index % 5 == 0) "Duna $index" else "Filme sintetico $index",
                    contentType = XtreamContentType.MOVIE,
                    categoryIds = listOf(if (index % 2 == 0) "c1" else "c2"),
                    containerExtension = "mp4",
                    artworkUrl = "https://images.invalid/$index.jpg",
                    year = 2020 + (index % 6),
                    rating = (index % 10).toDouble(),
                    addedAtEpochSeconds = index.toLong(),
                ),
            )
        }
        return catalogue
    }

    /** A category filter still selects exactly the rows in that category. */
    @Test
    fun `the category filter is unchanged`() {
        val catalogue = catalogue()

        val matched = (0 until catalogue.size).count { index -> catalogue.matches(index, "c1", "") }

        assertEquals(250, matched, "Half the fixture is in c1.")
    }

    /** The query still matches on the name, case-insensitively. */
    @Test
    fun `the search filter is unchanged`() {
        val catalogue = catalogue()

        val matched = (0 until catalogue.size).count { index -> catalogue.matches(index, null, "duna") }

        assertEquals(100, matched, "Every fifth title is a Duna.")
    }

    /** The year and rating filters still exclude what they excluded. */
    @Test
    fun `the year and rating filters are unchanged`() {
        val catalogue = catalogue()

        val byYear =
            (0 until catalogue.size).count { index ->
                catalogue.matches(index, null, "", releaseYear = 2021)
            }
        val byRating =
            (0 until catalogue.size).count { index ->
                catalogue.matches(index, null, "", minimumRating = 8.0)
            }

        assertTrue(byYear > 0, "The fixture carries titles from 2021.")
        assertTrue(byRating > 0, "The fixture carries well-rated titles.")
        // 8 and 9 of every ten.
        assertEquals(100, byRating, "Exactly the rows rated 8.0 and 9.0 should pass.")
    }

    /**
     * The filters still compose.
     *
     * Reordering a chain of `&&` into early returns is behaviour-preserving only if every test still
     * runs when the ones before it pass — this is what would catch a `return true` written where a
     * `return false` belonged.
     */
    @Test
    fun `filters still combine`() {
        val catalogue = catalogue()

        val both =
            (0 until catalogue.size).count { index -> catalogue.matches(index, "c1", "duna") }
        val plain =
            (0 until catalogue.size).count { index -> catalogue.matches(index, null, "duna") }

        assertTrue(both < plain, "Adding a category must narrow the result, not widen it.")
        assertTrue(both > 0, "Some Dunas are in c1.")
    }

    /** No filter at all still matches everything. */
    @Test
    fun `an unfiltered match takes every row`() {
        val catalogue = catalogue()

        val matched = (0 until catalogue.size).count { index -> catalogue.matches(index, null, "") }

        assertEquals(catalogue.size, matched)
    }
}
