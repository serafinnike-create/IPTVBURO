package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactXtreamCatalogTest {
    @Test
    fun `retains rendered fields artwork and category membership compactly`() {
        val catalog = CompactXtreamCatalog(XtreamContentType.MOVIE)
        catalog.add(item(index = 7, categoryIds = listOf("new", "four-k")))

        assertTrue(catalog.matches(0, "four-k", "movie"))
        assertFalse(catalog.matches(0, "other", "movie"))
        assertEquals(
            item(index = 7, categoryIds = listOf("new", "four-k")).copy(
                addedAtEpochSeconds = null,
            ),
            catalog.itemAt(0),
        )
    }

    @Test
    fun `indexes five hundred thousand session rows`() {
        val catalog = CompactXtreamCatalog(XtreamContentType.LIVE)
        repeat(500_000) { index ->
            catalog.add(
                item(
                    index = index,
                    contentType = XtreamContentType.LIVE,
                    categoryIds = listOf("category-${index % 20}"),
                ),
            )
        }

        assertEquals(500_000, catalog.size)
        assertTrue(catalog.matches(499_999, "category-19", "499999"))
        assertEquals("499999", catalog.itemAt(499_999).providerId)
    }

    @Test
    fun `a minimum rating keeps only titles that reach it`() {
        val catalog = CompactXtreamCatalog(XtreamContentType.MOVIE)
        catalog.add(item(1, categoryIds = listOf("a"), rating = 4.5))
        catalog.add(item(2, categoryIds = listOf("a"), rating = 2.0))
        catalog.add(item(3, categoryIds = listOf("a"), rating = 4.0))

        val matching =
            (0 until catalog.size).filter { index ->
                catalog.matches(index, null, "", null, minimumRating = 4.0, allowedIdentities = null)
            }

        assertEquals(listOf(0, 2), matching)
    }

    /**
     * Providers leave the rating out often. Treating a missing rating as passing would fill a
     * "4 stars and up" filter with titles that were never scored.
     */
    @Test
    fun `an unrated title is excluded once a minimum is set`() {
        val catalog = CompactXtreamCatalog(XtreamContentType.MOVIE)
        catalog.add(item(1, categoryIds = listOf("a"), rating = null))

        assertEquals(
            false,
            catalog.matches(0, null, "", null, minimumRating = 1.0, allowedIdentities = null),
        )
        assertEquals(
            true,
            catalog.matches(0, null, "", null, minimumRating = null, allowedIdentities = null),
            "with no minimum it must still be listed",
        )
    }

    private fun item(
        index: Int,
        contentType: XtreamContentType = XtreamContentType.MOVIE,
        categoryIds: List<String>,
        rating: Double? = 4.5,
    ): XtreamCatalogItem =
        XtreamCatalogItem(
            providerId = index.toString(),
            name = "Movie $index",
            contentType = contentType,
            categoryIds = categoryIds,
            containerExtension = "mp4",
            artworkUrl = "https://images.invalid/$index.jpg",
            year = 2026,
            rating = rating,
            addedAtEpochSeconds = index.toLong(),
        )
}
