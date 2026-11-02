package com.lucasserafin94.iptvburo.data.tvhome

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.ContinueWatchingUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextEntriesTest {
    private fun item(name: String, type: CatalogContentType, logo: String? = null, progress: Float = 0.4f) =
        ContinueWatchingUi(
            channel = ChannelUi(id = name, name = name, categoryName = null, logoUrl = logo, contentType = type, year = 2024),
            progress = progress,
        )

    @Test
    fun `only films become launcher cards, each opening through the app's own link`() {
        val entries =
            watchNextEntries(
                listOf(item("Dune", CatalogContentType.MOVIE), item("News", CatalogContentType.LIVE), item("S1E1", CatalogContentType.EPISODE)),
            )
        assertEquals(listOf("Dune"), entries.map { it.title })
        assertTrue(entries.single().appUri.startsWith("iptvburo://title?"))
    }

    @Test
    fun `a provider-hosted poster never reaches the launcher`() {
        val entry = watchNextEntries(listOf(item("Dune", CatalogContentType.MOVIE, logo = "http://panel.test/u/p/poster.jpg"))).single()
        assertNull(entry.posterUrl)
    }

    @Test
    fun `progress is clamped and the row is capped`() {
        val many = (1..20).map { item("Film $it", CatalogContentType.MOVIE, progress = 1.5f) }
        val entries = watchNextEntries(many, limit = 10)
        assertEquals(10, entries.size)
        assertEquals(1f, entries.first().progress)
    }
}
