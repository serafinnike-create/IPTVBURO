package com.lucasserafin94.iptvburo.desktop.app

import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The guide's filter box: every word typed must appear in the channel name, ignoring case and accents.
 *
 * A real subscription sends thousands of channels in one alphabetical run, so the filter is how a
 * viewer reaches a station at all; one that missed "Nações" for "nacoes" would look like a channel
 * the provider does not carry.
 */
class GuideFilterTest {
    private val channels =
        listOf("SporTV 2 HD", "SporTV 2 SD", "ESPN FHD", "Rede Nações", "A&E HD").map(::channel)

    @Test
    fun `an empty filter keeps every channel in order`() {
        assertEquals(channels, filterGuideChannels(channels, "   "))
    }

    @Test
    fun `every word must match, in any order`() {
        assertEquals(listOf("SporTV 2 HD"), names(filterGuideChannels(channels, "hd sport")))
    }

    @Test
    fun `case and accents are ignored on both sides`() {
        assertEquals(listOf("Rede Nações"), names(filterGuideChannels(channels, "NACOES")))
        assertEquals(listOf("Rede Nações"), names(filterGuideChannels(channels, "nações")))
    }

    @Test
    fun `nothing matching gives an empty list`() {
        assertEquals(emptyList(), filterGuideChannels(channels, "bbc"))
    }

    private fun names(items: List<XtreamCatalogItem>) = items.map { it.name }

    private fun channel(name: String) =
        XtreamCatalogItem(
            providerId = name,
            name = name,
            contentType = XtreamContentType.LIVE,
            categoryIds = emptyList(),
            containerExtension = null,
            artworkUrl = null,
            year = null,
            rating = null,
            addedAtEpochSeconds = null,
        )
}
