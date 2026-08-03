package com.lucasserafin94.iptvburo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * An offline copy is filed under what the content *is*, never under a provider id or a stream URL.
 *
 * That is what lets a stored file still be recognised after the user replaces their playlist, and
 * it is what keeps signed URLs and credentials out of the filesystem.
 */
class DownloadKeyTest {
    @Test
    fun `quality and language decoration does not change the key`() {
        assertEquals(
            movieDownloadKey("[4K] The Feature (2021) DUAL"),
            movieDownloadKey("The Feature (2021) HEVC DUBLADO"),
        )
    }

    @Test
    fun `two different films do not collide`() {
        assertNotEquals(
            movieDownloadKey("The Feature (2021)"),
            movieDownloadKey("Another Feature (2021)"),
        )
    }

    @Test
    fun `episodes of one series are keyed by season and episode`() {
        val series = "Synthetic Series"
        val first = episodeDownloadKey(series, episodeUi(seasonNumber = 1, episodeNumber = 2))
        val second = episodeDownloadKey(series, episodeUi(seasonNumber = 1, episodeNumber = 3))

        assertNotEquals(first, second)
        assertEquals(
            first,
            episodeDownloadKey(series, episodeUi(seasonNumber = 1, episodeNumber = 2)),
        )
    }

    @Test
    fun `a film and a series of the same name are kept apart`() {
        assertNotEquals(
            movieDownloadKey("Shared Name"),
            episodeDownloadKey("Shared Name", episodeUi(seasonNumber = 1, episodeNumber = 1)),
        )
    }

    private fun episodeUi(
        seasonNumber: Int,
        episodeNumber: Int?,
    ): EpisodeUi =
        EpisodeUi(
            id = "episode-$seasonNumber-$episodeNumber",
            title = "Synthetic episode",
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
}
