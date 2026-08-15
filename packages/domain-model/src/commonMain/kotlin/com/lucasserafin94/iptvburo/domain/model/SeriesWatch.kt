package com.lucasserafin94.iptvburo.domain.model

/**
 * What a followed series looked like the last time the app counted it.
 *
 * Kept per series and per profile so "there is a new episode" can be answered without asking the
 * provider what changed — providers do not say. The only way to know is to remember what was there
 * before and compare.
 */
data class SeriesWatermark(
    /** The series' provider-independent identity, so a replaced playlist does not orphan the mark. */
    val identityKey: String,
    /** The highest season seen. */
    val latestSeason: Int,
    /** The highest episode seen *within* [latestSeason]. */
    val latestEpisode: Int,
    /** How many episodes the series had in total, across every season. */
    val episodeCount: Int,
)

/** What changed about a series since it was last counted. */
sealed interface SeriesChange {
    /** Nothing new. The common answer, and the one that must post no notice at all. */
    data object None : SeriesChange

    /** A new episode of a season already being followed. */
    data class NewEpisode(
        val season: Int,
        val episode: Int,
    ) : SeriesChange

    /**
     * A whole new season.
     *
     * Reported instead of the episodes inside it, not as well: a season arriving with ten episodes
     * is one piece of news, and ten notices for it would be the fastest way to make somebody turn
     * the bell off.
     */
    data class NewSeason(
        val season: Int,
    ) : SeriesChange
}

/**
 * Decides what is new about a series the viewer follows.
 *
 * ## Why this only runs for favourites
 *
 * Announcing every series a provider adds an episode to would be a stream of noise about programmes
 * nobody is watching. A favourite is the viewer saying "I am following this", which is exactly the
 * permission this needs — and the reason the caller, not this policy, decides which series to ask
 * about.
 *
 * ## Why a watermark rather than a date
 *
 * Providers do not publish when an episode was added, and the dates they do publish are the
 * broadcast dates — a series added to a playlist today can carry episodes dated years ago. Counting
 * what is there and comparing it with what was there last time is the only signal that survives
 * that, and it has the useful property of being right the first time a series is followed: there is
 * nothing to compare against, so nothing is announced.
 */
object SeriesWatchPolicy {
    /**
     * Compares a series against its watermark.
     *
     * [previous] is null the first time a series is followed, and that must produce [SeriesChange.None]
     * — a viewer who has just marked a series as a favourite does not want to be told that every
     * episode of it is new.
     */
    fun changeSince(
        previous: SeriesWatermark?,
        seasons: List<Int>,
        episodesInLatestSeason: List<Int>,
        totalEpisodes: Int,
    ): SeriesChange {
        val latestSeason = seasons.maxOrNull() ?: return SeriesChange.None
        val latestEpisode = episodesInLatestSeason.maxOrNull() ?: 0

        // Nothing to compare against. The first sighting establishes the mark and says nothing.
        if (previous == null) return SeriesChange.None

        if (latestSeason > previous.latestSeason) return SeriesChange.NewSeason(latestSeason)

        // Within the same season, and further along than last time.
        if (latestSeason == previous.latestSeason && latestEpisode > previous.latestEpisode) {
            return SeriesChange.NewEpisode(latestSeason, latestEpisode)
        }

        // The count grew without the highest number moving. That happens when a provider backfills
        // an earlier season, and it is still worth one notice — something the viewer did not have
        // is now there — but it is reported against the season that actually leads, because there
        // is no way to tell which episode arrived.
        if (totalEpisodes > previous.episodeCount) {
            return SeriesChange.NewEpisode(latestSeason, latestEpisode)
        }

        return SeriesChange.None
    }

    /** The mark to store after a comparison, whatever the answer was. */
    fun watermarkFor(
        identityKey: String,
        seasons: List<Int>,
        episodesInLatestSeason: List<Int>,
        totalEpisodes: Int,
    ): SeriesWatermark =
        SeriesWatermark(
            identityKey = identityKey,
            latestSeason = seasons.maxOrNull() ?: 0,
            latestEpisode = episodesInLatestSeason.maxOrNull() ?: 0,
            episodeCount = totalEpisodes,
        )
}
