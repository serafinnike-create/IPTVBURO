package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerNeighboursTest {
    private fun channel(id: String, type: CatalogContentType = CatalogContentType.LIVE) =
        ChannelUi(id = id, name = id, categoryName = null, logoUrl = null, contentType = type)

    private fun episode(season: Int, number: Int?) =
        EpisodeUi(id = "s${season}e$number", title = "S${season}E$number", seasonNumber = season, episodeNumber = number)

    @Test
    fun `channel buttons walk the browsed list and wrap at the ends`() {
        val channels = listOf(channel("a"), channel("film", CatalogContentType.MOVIE), channel("b"), channel("c"))

        assertEquals("b", neighbourLiveChannel(channels, "a", 1)?.id)
        assertEquals("c", neighbourLiveChannel(channels, "a", -1)?.id)
        assertEquals("a", neighbourLiveChannel(channels, "c", 1)?.id)
    }

    @Test
    fun `no neighbour when the channel is alone or not in the list`() {
        assertNull(neighbourLiveChannel(listOf(channel("a")), "a", 1))
        assertNull(neighbourLiveChannel(listOf(channel("a"), channel("b")), "z", 1))
    }

    @Test
    fun `next episode follows numbering, not list order, across seasons`() {
        val episodes = listOf(episode(2, 1), episode(1, 2), episode(1, 1), episode(1, null))

        assertEquals("s1e2", nextEpisodeAfter(episodes, 1, 1)?.id)
        assertEquals("s2e1", nextEpisodeAfter(episodes, 1, 2)?.id)
        assertNull(nextEpisodeAfter(episodes, 2, 1))
        assertNull(nextEpisodeAfter(episodes, null, 1))
        assertNull(nextEpisodeAfter(episodes, 3, 1))
    }
}
