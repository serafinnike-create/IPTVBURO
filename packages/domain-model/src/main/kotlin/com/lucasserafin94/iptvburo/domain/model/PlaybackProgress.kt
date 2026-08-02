package com.lucasserafin94.iptvburo.domain.model

enum class PlaybackContentType {
    MOVIE,
    EPISODE,
}

data class PlaybackProgressIdentity(
    val profileId: String,
    val sourceId: String,
    val contentId: String,
    val contentType: PlaybackContentType,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) {
    init {
        require(profileId.isNotBlank())
        require(sourceId.isNotBlank())
        require(contentId.isNotBlank())
    }
}

data class PlaybackProgress(
    val identity: PlaybackProgressIdentity,
    val positionMs: Long,
    val durationMs: Long,
    val progressPercent: Double,
    val lastWatchedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long,
    val revision: Long,
)

sealed interface ResumeDecision {
    data object StartFromBeginning : ResumeDecision

    data class ResumeFrom(val positionMs: Long, val progressPercent: Double) : ResumeDecision

    data object WatchAgain : ResumeDecision
}

object PlaybackProgressPolicy {
    const val MINIMUM_POSITION_MS = 30_000L
    const val MINIMUM_PERCENT = 0.02
    const val COMPLETED_PERCENT = 0.90
    const val COMPLETED_REMAINING_MS = 5 * 60_000L
    const val REMAINING_RULE_MINIMUM_DURATION_MS = 10 * 60_000L
    const val REMAINING_RULE_MINIMUM_PERCENT = 0.50

    fun sanitizePosition(positionMs: Long, durationMs: Long): Long =
        if (durationMs <= 0L) 0L else positionMs.coerceIn(0L, durationMs)

    fun percent(positionMs: Long, durationMs: Long): Double =
        if (durationMs <= 0L) 0.0 else sanitizePosition(positionMs, durationMs).toDouble() / durationMs

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val safePosition = sanitizePosition(positionMs, durationMs)
        val remaining = durationMs - safePosition
        val watchedPercent = percent(safePosition, durationMs)
        return watchedPercent >= COMPLETED_PERCENT ||
            (durationMs >= REMAINING_RULE_MINIMUM_DURATION_MS &&
                watchedPercent >= REMAINING_RULE_MINIMUM_PERCENT &&
                remaining <= COMPLETED_REMAINING_MS)
    }

    fun isEligible(positionMs: Long, durationMs: Long, seekable: Boolean): Boolean =
        seekable && durationMs > 0L && positionMs >= MINIMUM_POSITION_MS &&
            percent(positionMs, durationMs) >= MINIMUM_PERCENT && !isCompleted(positionMs, durationMs)

    fun resumeDecision(progress: PlaybackProgress?): ResumeDecision =
        when {
            progress == null -> ResumeDecision.StartFromBeginning
            progress.completedAtEpochMillis != null -> ResumeDecision.WatchAgain
            isEligible(progress.positionMs, progress.durationMs, seekable = true) ->
                ResumeDecision.ResumeFrom(progress.positionMs, progress.progressPercent)
            else -> ResumeDecision.StartFromBeginning
        }
}

interface PlaybackProgressRepository {
    fun find(identity: PlaybackProgressIdentity): PlaybackProgress?

    fun save(progress: PlaybackProgress)

    fun remove(identity: PlaybackProgressIdentity)

    fun continueWatching(profileId: String, limit: Int = 30): List<PlaybackProgress>
}

class GetResumeDecisionUseCase(private val repository: PlaybackProgressRepository) {
    operator fun invoke(identity: PlaybackProgressIdentity): ResumeDecision =
        PlaybackProgressPolicy.resumeDecision(repository.find(identity))
}

class SavePlaybackCheckpointUseCase(
    private val repository: PlaybackProgressRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(
        identity: PlaybackProgressIdentity,
        positionMs: Long,
        durationMs: Long,
        seekable: Boolean,
    ): PlaybackProgress? {
        if (!seekable || durationMs <= 0L) return null
        val now = clock()
        val safePosition = PlaybackProgressPolicy.sanitizePosition(positionMs, durationMs)
        val existing = repository.find(identity)
        if (existing?.completedAtEpochMillis != null && safePosition < existing.positionMs) return existing
        val completed = PlaybackProgressPolicy.isCompleted(safePosition, durationMs)
        if (!completed && safePosition < PlaybackProgressPolicy.MINIMUM_POSITION_MS) return existing
        val saved = PlaybackProgress(
            identity = identity,
            positionMs = safePosition,
            durationMs = durationMs,
            progressPercent = PlaybackProgressPolicy.percent(safePosition, durationMs),
            lastWatchedAtEpochMillis = now,
            completedAtEpochMillis = if (completed) existing?.completedAtEpochMillis ?: now else null,
            updatedAtEpochMillis = now,
            revision = (existing?.revision ?: 0L) + 1L,
        )
        repository.save(saved)
        return saved
    }
}

class MarkPlaybackCompletedUseCase(
    private val repository: PlaybackProgressRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(identity: PlaybackProgressIdentity, durationMs: Long): PlaybackProgress? {
        if (durationMs <= 0L) return null
        val now = clock()
        val existing = repository.find(identity)
        val completed = PlaybackProgress(
            identity = identity,
            positionMs = durationMs,
            durationMs = durationMs,
            progressPercent = 1.0,
            lastWatchedAtEpochMillis = now,
            completedAtEpochMillis = existing?.completedAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            revision = (existing?.revision ?: 0L) + 1L,
        )
        repository.save(completed)
        return completed
    }
}

class ClearPlaybackProgressUseCase(private val repository: PlaybackProgressRepository) {
    operator fun invoke(identity: PlaybackProgressIdentity) = repository.remove(identity)
}

class ObserveContinueWatchingUseCase(private val repository: PlaybackProgressRepository) {
    operator fun invoke(profileId: String, limit: Int = 30): List<PlaybackProgress> =
        repository.continueWatching(profileId, limit)
            .filter { PlaybackProgressPolicy.isEligible(it.positionMs, it.durationMs, seekable = true) }
            .sortedByDescending(PlaybackProgress::lastWatchedAtEpochMillis)
            .take(limit.coerceAtLeast(0))
}
