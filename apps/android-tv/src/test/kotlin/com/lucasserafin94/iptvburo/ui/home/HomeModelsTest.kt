package com.lucasserafin94.iptvburo.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeModelsTest {
    @Test
    fun `stale focus id falls back to hero`() {
        val section = section()

        assertEquals(
            section.hero.id,
            section.resolveInitialFocusId("source-that-no-longer-exists"),
        )
    }

    @Test
    fun `valid rail focus id is preserved`() {
        val section = section()

        assertEquals(
            "demo:rail:item",
            section.resolveInitialFocusId("demo:rail:item"),
        )
    }

    @Test
    fun `source item id round trips without losing separators`() {
        val sourceId = "provider:account:playlist"
        val itemId = DemoHomeCatalog.sourceItemId(sourceId)

        assertEquals(sourceId, DemoHomeCatalog.sourceIdFromItemId(itemId))
        assertNull(DemoHomeCatalog.sourceIdFromItemId("demo:not-a-source"))
    }

    private fun section(): HomeSection {
        val hero = item(
            id = "demo:hero",
            cardFormat = HomeCardFormat.LANDSCAPE,
        )
        val railItem = item(
            id = "demo:rail:item",
            cardFormat = HomeCardFormat.POSTER,
        )
        return HomeSection(
            id = "demo:section",
            hero = hero,
            rails = listOf(
                HomeRail(
                    id = "demo:rail",
                    title = "Rail",
                    kind = HomeRailKind.EDITORIAL,
                    cardFormat = HomeCardFormat.POSTER,
                    items = listOf(railItem),
                    isDemonstration = true,
                ),
            ),
        )
    }

    private fun item(
        id: String,
        cardFormat: HomeCardFormat,
    ): HomeItem =
        HomeItem(
            id = id,
            title = "Title",
            subtitle = "Subtitle",
            synopsis = "Synopsis",
            metadata = "Metadata",
            badge = "Demo",
            kind = HomeItemKind.DEMO_STORY,
            cardFormat = cardFormat,
            palette = HomeArtworkPalette.AURORA,
            isDemonstration = true,
        )
}
