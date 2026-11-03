package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.xtream.XtreamEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextEpisodeTest {
    private fun episode(season: Int, number: Int?) =
        XtreamEpisode(
            providerId = "s${season}e$number",
            title = "S${season}E$number",
            seasonNumber = season,
            episodeNumber = number,
            containerExtension = "mkv",
            artworkUrl = null,
        )

    @Test
    fun `the next episode follows the numbering across seasons, not the list order`() {
        val episodes = listOf(episode(2, 1), episode(1, 2), episode(1, 1), episode(1, null))
        assertEquals("s1e2", nextEpisodeIn(episodes, episode(1, 1))?.providerId)
        assertEquals("s2e1", nextEpisodeIn(episodes, episode(1, 2))?.providerId)
        assertNull(nextEpisodeIn(episodes, episode(2, 1)))
        assertNull(nextEpisodeIn(episodes, episode(1, null)))
    }
}
