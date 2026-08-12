package com.lucasserafin94.iptvburo.playback

import com.lucasserafin94.iptvburo.data.local.dao.PlaybackProgressDao
import com.lucasserafin94.iptvburo.data.local.entity.PlaybackProgressEntity
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPlaybackProgressRepository @Inject constructor(
    private val dao: PlaybackProgressDao,
) : PlaybackProgressRepository {
    override fun find(identity: PlaybackProgressIdentity): PlaybackProgress? =
        dao.find(identity.profileId, identity.sourceId, identity.contentId, identity.contentType.name)?.toDomain()

    override fun save(progress: PlaybackProgress) {
        val current = find(progress.identity)
        if (current != null && current.revision > progress.revision) return
        if (current?.completedAtEpochMillis != null && progress.completedAtEpochMillis == null) return
        dao.upsert(progress.toEntity())
    }

    override fun remove(identity: PlaybackProgressIdentity) =
        dao.remove(identity.profileId, identity.sourceId, identity.contentId, identity.contentType.name)

    override fun continueWatching(profileId: String, limit: Int): List<PlaybackProgress> =
        dao.continueWatching(profileId, limit).map { it.toDomain() }

    override fun history(profileId: String, limit: Int): List<PlaybackProgress> =
        dao.history(profileId, limit).map { it.toDomain() }

    private fun PlaybackProgress.toEntity() = PlaybackProgressEntity(
        profileId = identity.profileId,
        sourceId = identity.sourceId,
        contentId = identity.contentId,
        contentType = identity.contentType.name,
        seriesId = identity.seriesId,
        seasonNumber = identity.seasonNumber,
        episodeNumber = identity.episodeNumber,
        positionMs = positionMs,
        durationMs = durationMs,
        progressPercent = progressPercent,
        lastWatchedAtEpochMillis = lastWatchedAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        revision = revision,
    )

    private fun PlaybackProgressEntity.toDomain() = PlaybackProgress(
        identity = PlaybackProgressIdentity(
            profileId = profileId,
            sourceId = sourceId,
            contentId = contentId,
            contentType = PlaybackContentType.valueOf(contentType),
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        ),
        positionMs = positionMs,
        durationMs = durationMs,
        progressPercent = progressPercent,
        lastWatchedAtEpochMillis = lastWatchedAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        revision = revision,
    )
}
