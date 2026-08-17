package com.lucasserafin94.iptvburo.data.repository

import com.lucasserafin94.iptvburo.data.local.dao.FavoriteDao
import com.lucasserafin94.iptvburo.data.local.dao.ProfileDao
import com.lucasserafin94.iptvburo.data.local.dao.SeriesWatchDao
import com.lucasserafin94.iptvburo.data.local.entity.SeriesWatchEntity
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.data.reminders.SeriesSeriesNotice
import com.lucasserafin94.iptvburo.domain.model.SeriesChange
import com.lucasserafin94.iptvburo.domain.model.SeriesWatchPolicy
import com.lucasserafin94.iptvburo.domain.model.SeriesWatermark
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Notices when a favourited series gains an episode or a season.
 *
 * ## Why this exists
 *
 * Somebody following a weekly series has no way to know the new episode landed: the app shows it in
 * the catalogue, but only if they go and look. This is the "your series has a new episode" notice,
 * and it fires **only for series in favourites** — that is the signal that somebody is following
 * it, and counting every series in a playlist of thousands would be both useless and slow.
 *
 * ## How it decides
 *
 * By counting. A playlist carries no air dates worth trusting, but it does say how many episodes a
 * series has, so the app remembers yesterday's count and compares. [SeriesWatchPolicy] holds the
 * rules about what that difference means — and, more importantly, what it does *not* mean.
 *
 * ## The first count is always silent
 *
 * A series being counted for the first time stores its size and says nothing. Without that rule,
 * favouriting a finished series would immediately announce its whole back catalogue as new.
 */
@Singleton
class SeriesWatchRepository
    @Inject
    constructor(
        private val seriesWatchDao: SeriesWatchDao,
        private val favoriteDao: FavoriteDao,
        private val profileDao: ProfileDao,
        private val catalogRepository: CatalogRepository,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /**
         * Counts every favourited series for one profile and reports what changed.
         *
         * Returns only the changes worth announcing; an unchanged series produces nothing. The
         * stored count is updated either way, so a series that shrank or was re-imported is measured
         * from its new size next time rather than announcing the difference forever.
         */
        suspend fun check(
            profileId: String,
            now: Long = System.currentTimeMillis(),
        ): List<SeriesSeriesNotice> =
            withContext(ioDispatcher) {
                val favouriteIds = runCatching { favoriteDao.observeIds(profileId).first() }
                    .getOrDefault(emptyList())
                if (favouriteIds.isEmpty()) return@withContext emptyList()

                favouriteIds.mapNotNull { channelId ->
                    val channel =
                        runCatching { catalogRepository.getChannel(channelId) }.getOrNull()
                            ?: return@mapNotNull null
                    // Films have nothing to follow: they do not gain episodes.
                    if (channel.contentType != CatalogContentType.SERIES) return@mapNotNull null
                    val providerSeriesId = channel.providerItemId ?: return@mapNotNull null

                    val details =
                        runCatching {
                            catalogRepository.loadSeriesDetails(channel.sourceId, providerSeriesId)
                        }.getOrNull() ?: return@mapNotNull null
                    // An empty episode list is a failed or partial load, not a series that lost
                    // everything. Storing zero would make the next successful count look like a
                    // flood of new episodes.
                    if (details.episodes.isEmpty()) return@mapNotNull null

                    val seasons = details.episodes.map { episode -> episode.seasonNumber }.distinct()
                    val latestSeason = seasons.maxOrNull() ?: 1
                    // Episode numbers within the leading season, which is what tells "one more
                    // episode of what I am watching" apart from "a whole new season".
                    val episodesInLatestSeason =
                        details.episodes
                            .filter { episode -> episode.seasonNumber == latestSeason }
                            .mapNotNull { episode -> episode.episodeNumber }

                    val stored = seriesWatchDao.find(profileId, channelId)
                    val previous =
                        stored?.let { entity ->
                            SeriesWatermark(
                                identityKey = entity.channelId,
                                latestSeason = entity.latestSeason,
                                latestEpisode = entity.seasonCount,
                                episodeCount = entity.episodeCount,
                            )
                        }
                    val change =
                        SeriesWatchPolicy.changeSince(
                            previous = previous,
                            seasons = seasons,
                            episodesInLatestSeason = episodesInLatestSeason,
                            totalEpisodes = details.episodes.size,
                        )

                    // Written whatever the verdict, including None: this is the baseline the next
                    // check measures against, and skipping it for a shrinking series would have the
                    // app re-announcing the same difference every day.
                    //
                    // `seasonCount` carries the highest episode number of the leading season rather
                    // than a count of seasons — the column predates the policy that now defines what
                    // is compared, and reusing it avoids a second migration for one integer.
                    seriesWatchDao.upsert(
                        SeriesWatchEntity(
                            profileId = profileId,
                            channelId = channelId,
                            title = channel.name,
                            episodeCount = details.episodes.size,
                            seasonCount = episodesInLatestSeason.maxOrNull() ?: 0,
                            latestSeason = latestSeason,
                            checkedAtEpochMillis = now,
                        ),
                    )
                    change
                        .takeUnless { it is SeriesChange.None }
                        ?.let { real -> SeriesSeriesNotice(title = channel.name, change = real) }
                }
            }

        /**
         * Checks every profile, for the worker that runs with nobody signed in.
         *
         * Kept per profile rather than flattened: two people in a household follow different series,
         * and one person's new episode must never be announced as the other's.
         */
        suspend fun checkAllProfiles(
            now: Long = System.currentTimeMillis(),
        ): Map<String, List<SeriesSeriesNotice>> =
            withContext(ioDispatcher) {
                val profiles = runCatching { profileDao.observeAll().first() }.getOrDefault(emptyList())
                profiles
                    .associate { profile -> profile.id to check(profile.id, now) }
                    .filterValues { changes -> changes.isNotEmpty() }
            }

        /** Forgets a series, which is what unfavouriting one means. */
        suspend fun forget(profileId: String, channelId: String) {
            withContext(ioDispatcher) {
                runCatching { seriesWatchDao.remove(profileId, channelId) }
            }
        }
    }
