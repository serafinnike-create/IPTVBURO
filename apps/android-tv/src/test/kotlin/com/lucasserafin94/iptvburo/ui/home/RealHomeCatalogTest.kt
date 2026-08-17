package com.lucasserafin94.iptvburo.ui.home

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.ContinueWatchingUi
import com.lucasserafin94.iptvburo.ui.ProviderShelfUi
import com.lucasserafin94.iptvburo.ui.SubscriptionTitleUi
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

    /**
     * The release rails follow the calendar rather than a year written into the app.
     *
     * Every other test here builds its fixtures from `Calendar.getInstance()`, so all of them would
     * still pass if the year were hard-coded — they would simply be comparing the same constant on
     * both sides. This one asks the question directly: a catalogue holding *next* year's films must
     * not produce a rail for them today, and the rail that does appear must be named for the year
     * the machine is actually in. When the clock rolls over to January, the same code names the new
     * year without anybody editing a string.
     */
    @Test
    fun `release rails are named for the current year, not a fixed one`() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val section =
            RealHomeCatalog.section(
                sources = listOf(HomeSourceSummary("source", "Fonte", 4)),
                catalogItems =
                    listOf(
                        item("this-year-hero", year, "Lançamento $year", 8.2),
                        item("this-year-row", year, "Lançamento $year", 7.9),
                        item("last-year", year - 1, "Lançamento ${year - 1}", 7.4),
                        // Dated a year ahead: nothing may name a year that has not arrived.
                        item("next-year", year + 1, "Lançamento ${year + 1}", 7.1),
                    ),
            )

        val railTitles = section.rails.map(HomeRail::title)
        assertTrue("Lançamentos $year", railTitles.contains("Lançamentos $year"))
        assertTrue("Lançamentos ${year - 1}", railTitles.contains("Lançamentos ${year - 1}"))
        assertTrue(
            "A rail must never be named for a year that has not started: $railTitles",
            railTitles.none { title -> title == "Lançamentos ${year + 1}" },
        )
    }

    @Test
    fun `the banner offers a rotation rather than one fixed title`() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val section =
            RealHomeCatalog.section(
                sources = listOf(HomeSourceSummary("source", "Fonte", 4)),
                catalogItems =
                    (1..6).map { index ->
                        item("item-$index", year, "Lançamento $year", 8.0)
                    },
            )

        assertTrue(
            "A banner holding one image was the complaint; several candidates must rotate.",
            section.heroRotation.size > 1,
        )
        assertEquals(
            "The first slot is what the screen opens on, so it must be the hero itself.",
            section.hero.id,
            section.heroRotation.first().id,
        )
        assertEquals(
            "A title must not appear twice in one rotation.",
            section.heroRotation.map(HomeItem::id).distinct().size,
            section.heroRotation.size,
        )
    }

    @Test
    fun `a catalogue with a single title still produces a usable banner`() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val section =
            RealHomeCatalog.section(
                sources = listOf(HomeSourceSummary("source", "Fonte", 1)),
                catalogItems = listOf(item("only", year, "Lançamento $year", 7.5)),
            )

        // No rotation to run, and that must render as a still banner rather than as no banner.
        assertEquals(listOf("only"), section.heroRotation.map(HomeItem::id))
        assertEquals("only", section.hero.id)
    }

    @Test
    fun `a service listing the same title twice does not crash the home screen`() {
        // Found on a device: TMDb returns a title twice when a service lists it under two entries,
        // HomeRail rejects duplicate ids, and the whole home screen died with an
        // IllegalArgumentException — the app closed rather than dropping one card.
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val duplicated =
            SubscriptionTitleUi(
                externalNamespace = "tmdb",
                externalId = "1",
                title = "Mesmo Filme",
                year = year,
                posterUrl = null,
                isSeries = false,
                isDemo = false,
            )

        val section =
            RealHomeCatalog.section(
                sources = listOf(HomeSourceSummary("source", "Fonte", 1)),
                catalogItems = listOf(item("local", year, "Filme", 7.0)),
                streamingShelves =
                    listOf(
                        ProviderShelfUi(providerId = "netflix", providerName = "Netflix", titles = listOf(duplicated, duplicated)),
                        // A repeated provider would break the section for the same reason.
                        ProviderShelfUi(providerId = "netflix", providerName = "Netflix", titles = listOf(duplicated)),
                    ),
            )

        val streamingRails = section.rails.filter { it.kind == HomeRailKind.STREAMING_SERVICE }
        assertEquals(1, streamingRails.size)
        assertEquals(1, streamingRails.single().items.size)
    }

    @Test
    fun `continue watching is a real first rail with profile progress`() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val continued = item("continued", year - 2, "Filme", 7.4)

        val section =
            RealHomeCatalog.section(
                sources = listOf(HomeSourceSummary("source", "Fonte", 2)),
                catalogItems = listOf(item("hero", year, "Lançamento $year", 8.0), continued),
                continueWatching = listOf(ContinueWatchingUi(continued, progress = 0.42f)),
            )

        val rail = section.rails.first()
        assertEquals(HomeRailKind.CONTINUE_WATCHING, rail.kind)
        assertEquals("continued", rail.items.single().id)
        assertEquals(0.42f, rail.items.single().progress)
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
