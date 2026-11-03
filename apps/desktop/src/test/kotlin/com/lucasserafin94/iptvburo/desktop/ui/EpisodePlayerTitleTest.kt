package com.lucasserafin94.iptvburo.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** The player's title for an episode names the series, whatever the provider called the episode. */
class EpisodePlayerTitleTest {
    @Test
    fun `a bare code becomes the series and the episode`() {
        assertEquals("Carangas x Carrões · T1E2", episodePlayerTitle("Carangas x Carrões", 1, 2, "S01E02"))
        assertEquals("Carangas x Carrões · T1E2", episodePlayerTitle("Carangas x Carrões", 1, 2, "  "))
    }

    @Test
    fun `a real episode name follows the code`() {
        assertEquals("Dark · T2E3 · Ghosts", episodePlayerTitle("Dark", 2, 3, "Ghosts"))
    }

    @Test
    fun `a name that already carries the series is kept as it is`() {
        assertEquals("Dark - S02E03 - Ghosts", episodePlayerTitle("Dark", 2, 3, "Dark - S02E03 - Ghosts"))
    }

    @Test
    fun `provider decoration on the series name is dropped`() {
        assertEquals("Dark · T1E1", episodePlayerTitle("Dark 4K [HDR]", 1, 1, "S01E01"))
    }
}
