package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * What counts as news about a series somebody follows.
 *
 * The failure that matters is announcing something that is not new. A bell that cries wolf is one
 * people switch off, and then the episode they actually cared about goes unmentioned too.
 */
class SeriesWatchPolicyTest {
    /**
     * The first sighting says nothing.
     *
     * Somebody who has just marked a series as a favourite must not be told that all forty of its
     * episodes are new — which is what any policy without this would do.
     */
    @Test
    fun `a series seen for the first time announces nothing`() {
        val change =
            SeriesWatchPolicy.changeSince(
                previous = null,
                seasons = listOf(1, 2, 3),
                episodesInLatestSeason = listOf(1, 2, 3, 4, 5),
                totalEpisodes = 40,
            )

        assertEquals(SeriesChange.None, change)
    }

    @Test
    fun `an unchanged series announces nothing`() {
        val mark = SeriesWatermark("series:x", latestSeason = 2, latestEpisode = 8, episodeCount = 20)

        val change =
            SeriesWatchPolicy.changeSince(
                previous = mark,
                seasons = listOf(1, 2),
                episodesInLatestSeason = listOf(1, 2, 3, 4, 5, 6, 7, 8),
                totalEpisodes = 20,
            )

        assertEquals(SeriesChange.None, change)
    }

    /** The Friday case: one more episode of the season already being followed. */
    @Test
    fun `one more episode of the current season is a new episode`() {
        val mark = SeriesWatermark("series:x", latestSeason = 2, latestEpisode = 8, episodeCount = 20)

        val change =
            SeriesWatchPolicy.changeSince(
                previous = mark,
                seasons = listOf(1, 2),
                episodesInLatestSeason = (1..9).toList(),
                totalEpisodes = 21,
            )

        assertEquals(SeriesChange.NewEpisode(season = 2, episode = 9), change)
    }

    /**
     * A whole season is one piece of news, not ten.
     *
     * A season arriving with its episodes would otherwise produce a notice per episode, which is
     * the fastest way to make somebody turn the bell off entirely.
     */
    @Test
    fun `a new season is announced as a season rather than as its episodes`() {
        val mark = SeriesWatermark("series:x", latestSeason = 2, latestEpisode = 10, episodeCount = 20)

        val change =
            SeriesWatchPolicy.changeSince(
                previous = mark,
                seasons = listOf(1, 2, 3),
                episodesInLatestSeason = (1..10).toList(),
                totalEpisodes = 30,
            )

        assertIs<SeriesChange.NewSeason>(change)
        assertEquals(3, change.season)
    }

    /**
     * A backfilled earlier season still counts as something new.
     *
     * The highest numbers do not move when a provider adds episodes to season one, but the viewer
     * genuinely has something they did not have. Reported against the leading season, because
     * nothing in the numbers says which episode arrived.
     */
    @Test
    fun `episodes added to an earlier season are still news`() {
        val mark = SeriesWatermark("series:x", latestSeason = 3, latestEpisode = 4, episodeCount = 20)

        val change =
            SeriesWatchPolicy.changeSince(
                previous = mark,
                seasons = listOf(1, 2, 3),
                episodesInLatestSeason = listOf(1, 2, 3, 4),
                totalEpisodes = 24,
            )

        assertIs<SeriesChange.NewEpisode>(change)
    }

    /**
     * A shrinking catalogue announces nothing.
     *
     * Providers drop titles, and a series losing episodes must not be reported as news — least of
     * all as *new* episodes, which is what a naive comparison of counts would do.
     */
    @Test
    fun `a series that lost episodes announces nothing`() {
        val mark = SeriesWatermark("series:x", latestSeason = 3, latestEpisode = 10, episodeCount = 30)

        val change =
            SeriesWatchPolicy.changeSince(
                previous = mark,
                seasons = listOf(1, 2, 3),
                episodesInLatestSeason = listOf(1, 2, 3),
                totalEpisodes = 23,
            )

        assertEquals(SeriesChange.None, change)
    }

    /** An empty series is not news either, however it got that way. */
    @Test
    fun `a series with no episodes announces nothing`() {
        val change =
            SeriesWatchPolicy.changeSince(
                previous = SeriesWatermark("series:x", 1, 1, 1),
                seasons = emptyList(),
                episodesInLatestSeason = emptyList(),
                totalEpisodes = 0,
            )

        assertEquals(SeriesChange.None, change)
    }

    /** The mark records what was actually counted, so the next comparison has something to use. */
    @Test
    fun `the watermark records the leading season and episode`() {
        val mark =
            SeriesWatchPolicy.watermarkFor(
                identityKey = "series:x",
                seasons = listOf(1, 2, 3),
                episodesInLatestSeason = listOf(1, 2, 3, 4),
                totalEpisodes = 34,
            )

        assertEquals(3, mark.latestSeason)
        assertEquals(4, mark.latestEpisode)
        assertEquals(34, mark.episodeCount)
    }
}
