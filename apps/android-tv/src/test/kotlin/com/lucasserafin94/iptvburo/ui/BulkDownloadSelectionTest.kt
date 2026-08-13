package com.lucasserafin94.iptvburo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What "Baixar temporada" actually queues.
 *
 * The rule exists because `startDownload` refuses a download that is *running* but says nothing
 * about one already stored: without it, a bulk button pressed on a season the viewer already has
 * would re-fetch every file, spending their data and the provider's bandwidth to produce bytes that
 * are already on disk.
 *
 * Written against state rather than a ViewModel, which is why the production rule is a function of
 * [AppUiState]: this is a decision about a list, and proving it should not need a download manager
 * or a filesystem.
 */
class BulkDownloadSelectionTest {
    @Test
    fun `every episode is queued when nothing is stored yet`() {
        val state = seriesState()

        assertEquals(
            listOf("s1e1", "s1e2", "s2e1"),
            episodesWorthDownloading(state).map(EpisodeUi::id),
        )
    }

    @Test
    fun `an episode already on disk is skipped`() {
        val state = seriesState(completed = setOf("s1e2"))

        assertEquals(
            listOf("s1e1", "s2e1"),
            episodesWorthDownloading(state).map(EpisodeUi::id),
        )
    }

    @Test
    fun `a season filter narrows on top of the same rule`() {
        val state = seriesState(completed = setOf("s1e1"))

        assertEquals(
            listOf("s1e2"),
            episodesWorthDownloading(state) { it.seasonNumber == 1 }.map(EpisodeUi::id),
        )
    }

    @Test
    fun `a fully stored season queues nothing`() {
        val state = seriesState(completed = setOf("s1e1", "s1e2"))

        assertTrue(episodesWorthDownloading(state) { it.seasonNumber == 1 }.isEmpty())
    }

    @Test
    fun `a download in progress is still queued rather than treated as stored`() {
        // Only Completed means "the bytes are here". A running transfer is refused further down by
        // startDownload, which is the right place for it — treating it as stored here would let a
        // cancelled download disappear from the bulk button with no file to show for it.
        val state =
            seriesState().let { base ->
                base.copy(
                    downloads =
                        base.downloads +
                            (
                                episodeDownloadKey(SERIES_TITLE, base.seriesDetails!!.episodes[0]) to
                                    DownloadStateUi.Running(fraction = 0.4f)
                            ),
                )
            }

        assertEquals(3, episodesWorthDownloading(state).size)
    }

    @Test
    fun `episodes are queued in playing order whatever order they arrived in`() {
        // The queue order is the point: most of these wait rather than run, so a viewer who starts
        // watching before the last file lands should get the beginning first.
        val shuffled =
            seriesState(
                episodes =
                    listOf(
                        episode("s2e1", season = 2, number = 1),
                        episode("s1e2", season = 1, number = 2),
                        episode("s1e1", season = 1, number = 1),
                    ),
            )

        assertEquals(
            listOf("s1e1", "s1e2", "s2e1"),
            episodesWorthDownloading(shuffled).map(EpisodeUi::id),
        )
    }

    @Test
    fun `no open series means nothing to queue`() {
        assertTrue(episodesWorthDownloading(AppUiState()).isEmpty())
    }

    private fun seriesState(
        episodes: List<EpisodeUi> = defaultEpisodes,
        completed: Set<String> = emptySet(),
    ): AppUiState {
        val details =
            SeriesDetailsUi(
                title = SERIES_TITLE,
                plot = null,
                episodes = episodes,
            )
        return AppUiState(
            seriesDetails = details,
            downloads =
                episodes
                    .filter { it.id in completed }
                    .associate { episode ->
                        episodeDownloadKey(SERIES_TITLE, episode) to DownloadStateUi.Completed
                    },
        )
    }

    private companion object {
        const val SERIES_TITLE = "Synthetic Series"

        val defaultEpisodes =
            listOf(
                episode("s1e1", season = 1, number = 1),
                episode("s1e2", season = 1, number = 2),
                episode("s2e1", season = 2, number = 1),
            )

        fun episode(
            id: String,
            season: Int,
            number: Int?,
        ): EpisodeUi =
            EpisodeUi(
                id = id,
                title = "Synthetic episode",
                seasonNumber = season,
                episodeNumber = number,
            )
    }
}
