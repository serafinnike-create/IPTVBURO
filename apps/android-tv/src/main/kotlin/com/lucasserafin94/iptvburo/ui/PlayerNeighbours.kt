package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType

/**
 * The live channel [step] places away from [currentId] among [channels], wrapping at either end.
 *
 * The list is whatever the viewer was browsing when they opened the channel — a category, the
 * guide — so changing channel from inside the player walks the same order they saw, the way a
 * remote's channel buttons do. Null when the channel is not in that list or is the only one.
 */
internal fun neighbourLiveChannel(
    channels: List<ChannelUi>,
    currentId: String,
    step: Int,
): ChannelUi? {
    val live = channels.filter { it.contentType == CatalogContentType.LIVE }
    if (live.size < 2) return null
    val index = live.indexOfFirst { it.id == currentId }
    if (index < 0) return null
    return live[(index + step).mod(live.size)]
}

/**
 * The episode that follows season [season], episode [episode], or null at the end of the series.
 *
 * Ordered by season and then episode number rather than by list position, because providers do
 * not reliably send episodes in order. Episodes without a number cannot be placed in a sequence,
 * so neither can the one after them.
 */
internal fun nextEpisodeAfter(
    episodes: List<EpisodeUi>,
    season: Int?,
    episode: Int?,
): EpisodeUi? {
    if (season == null || episode == null) return null
    val ordered =
        episodes
            .filter { it.episodeNumber != null }
            .sortedWith(compareBy<EpisodeUi>({ it.seasonNumber }, { it.episodeNumber }))
    val index = ordered.indexOfFirst { it.seasonNumber == season && it.episodeNumber == episode }
    if (index < 0) return null
    return ordered.getOrNull(index + 1)
}
