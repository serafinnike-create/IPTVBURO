package com.lucasserafin94.iptvburo.playback

import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.GetResumeDecisionUseCase
import com.lucasserafin94.iptvburo.domain.model.MarkPlaybackCompletedUseCase
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import com.lucasserafin94.iptvburo.domain.model.SavePlaybackCheckpointUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class AndroidPlaybackProgressCoordinator @Inject constructor(
    repository: RoomPlaybackProgressRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val getResumeDecision = GetResumeDecisionUseCase(repository)
    private val saveCheckpoint = SavePlaybackCheckpointUseCase(repository)
    private val markCompleted = MarkPlaybackCompletedUseCase(repository)

    suspend fun resumeDecision(identity: PlaybackProgressIdentity): ResumeDecision =
        withContext(ioDispatcher) { getResumeDecision(identity) }

    suspend fun checkpoint(identity: PlaybackProgressIdentity, positionMs: Long, durationMs: Long) =
        withContext(ioDispatcher) {
            saveCheckpoint(identity, positionMs, durationMs, seekable = durationMs > 0L)
            Unit
        }

    suspend fun ended(identity: PlaybackProgressIdentity, durationMs: Long) =
        withContext(ioDispatcher) {
            markCompleted(identity, durationMs)
            Unit
        }

    fun checkpointAsync(identity: PlaybackProgressIdentity?, positionMs: Long, durationMs: Long) {
        identity ?: return
        scope.launch { checkpoint(identity, positionMs, durationMs) }
    }

    fun endedAsync(identity: PlaybackProgressIdentity?, durationMs: Long) {
        identity ?: return
        scope.launch { ended(identity, durationMs) }
    }
}
