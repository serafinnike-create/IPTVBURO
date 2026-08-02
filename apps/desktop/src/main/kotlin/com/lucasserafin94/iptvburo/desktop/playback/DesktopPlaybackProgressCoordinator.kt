package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.domain.model.GetResumeDecisionUseCase
import com.lucasserafin94.iptvburo.domain.model.MarkPlaybackCompletedUseCase
import com.lucasserafin94.iptvburo.domain.model.ObserveContinueWatchingUseCase
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import com.lucasserafin94.iptvburo.domain.model.SavePlaybackCheckpointUseCase

class DesktopPlaybackProgressCoordinator(
    repository: DesktopPlaybackProgressStore = DesktopPlaybackProgressStore(),
) {
    private val getResumeDecision = GetResumeDecisionUseCase(repository)
    private val saveCheckpoint = SavePlaybackCheckpointUseCase(repository)
    private val markCompleted = MarkPlaybackCompletedUseCase(repository)
    private val observeContinueWatching = ObserveContinueWatchingUseCase(repository)

    fun resumeDecision(identity: PlaybackProgressIdentity?): ResumeDecision =
        identity?.let(getResumeDecision::invoke) ?: ResumeDecision.StartFromBeginning

    fun checkpoint(identity: PlaybackProgressIdentity?, positionMs: Long, durationMs: Long) {
        identity ?: return
        saveCheckpoint(identity, positionMs, durationMs, seekable = durationMs > 0L)
    }

    fun ended(identity: PlaybackProgressIdentity?, durationMs: Long) {
        identity ?: return
        markCompleted(identity, durationMs)
    }

    fun continueWatching(profileId: String, limit: Int = 30): List<PlaybackProgress> =
        observeContinueWatching(profileId, limit)
}
