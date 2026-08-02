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

    private fun item(
        index: Int,
        contentType: XtreamContentType = XtreamContentType.MOVIE,
        categoryIds: List<String>,
    ): XtreamCatalogItem =
        XtreamCatalogItem(
            providerId = index.toString(),
            name = "Movie $index",
            contentType = contentType,
            categoryIds = categoryIds,
            containerExtension = "mp4",
            artworkUrl = "https://images.invalid/$index.jpg",
            year = 2026,
            rating = 4.5,
            addedAtEpochSeconds = index.toLong(),
        )
}
