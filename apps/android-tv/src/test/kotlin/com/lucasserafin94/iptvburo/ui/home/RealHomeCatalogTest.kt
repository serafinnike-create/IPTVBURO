package com.lucasserafin94.iptvburo.ui.home

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.ui.ChannelUi
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealHomeCatalogTest {
    @Test
    fun `separates release years from recently added classics`() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val section =
            RealHomeCatalog.section(
                sources = listOf(HomeSourceSummary("source", "Fonte", 6)),
                catalogItems =
                    listOf(
                        item("release-hero", year, "Lançamento $year", 8.1),
                        item("release-row", year, "Lançamento $year", 7.8),
                        item("previous", year - 1, "Lançamento ${year - 1}", 7.3),
                        item("classic", year - 25, "Adicionado recentemente", 8.4),
                        item("recent", year - 2, "Adicionado recentemente", 6.9),
                        item("series", year - 3, "Série", 8.8, CatalogContentType.SERIES),
                    ),
            )

        assertEquals(year.toString(), section.hero.metadata.substringBefore("  •  "))
        assertTrue(section.rails.any { it.title == "Lançamentos $year" })
        assertTrue(section.rails.any { it.title == "Lançamentos ${year - 1}" })
        assertEquals(
            listOf("classic"),
            section.rails.single { it.title == "Clássicos que chegaram agora" }.items.map(HomeItem::id),
        )
        assertEquals(
            listOf("recent"),
            section.rails.single { it.title == "Adicionados recentemente" }.items.map(HomeItem::id),
        )
    }

    private fun item(
        id: String,
        year: Int,
        label: String,
        rating: Double,
        type: CatalogContentType = CatalogContentType.MOVIE,
    ) =
        ChannelUi(
            id = id,
            name = id,
            categoryName = label,
            logoUrl = "https://images.example/$id.jpg",
            contentType = type,
            year = year,
            rating = rating,
        )
}
