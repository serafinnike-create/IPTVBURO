package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {
    @Test
    fun `error disables playback and ignores stale player callbacks`() {
        val failed = reducePlaybackUiState(
            current = PlaybackUiState(
                isPlaying = true,
                isSeekable = true,
            ),
            event = PlaybackUiEvent.Error(PlaybackFailure.UNSUPPORTED_MEDIA),
        )

        val afterStaleReady = reducePlaybackUiState(
            current = failed,
            event = PlaybackUiEvent.StateChanged(
                phase = PlaybackPhase.READY,
                isPlaying = true,
                isSeekable = true,
            ),
        )
        val afterStaleFrame = reducePlaybackUiState(
            current = afterStaleReady,
            event = PlaybackUiEvent.FirstFrame(isSeekable = true),
        )

        assertTrue(afterStaleFrame.hasError)
        assertFalse(afterStaleFrame.isLoading)
        assertFalse(afterStaleFrame.isPlaying)
        assertFalse(afterStaleFrame.isSeekable)
        assertEquals(PlaybackFailure.UNSUPPORTED_MEDIA, afterStaleFrame.failure)
    }

    @Test
    fun `retry defers play focus until player is ready`() {
        val retrying = reducePlaybackUiState(
            current = PlaybackUiState(hasError = true),
            event = PlaybackUiEvent.Retry,
        )
        val buffering = reducePlaybackUiState(
            current = retrying,
            event = PlaybackUiEvent.StateChanged(
                phase = PlaybackPhase.BUFFERING,
                isPlaying = false,
                isSeekable = false,
            ),
        )
        val ready = reducePlaybackUiState(
            current = buffering,
            event = PlaybackUiEvent.StateChanged(
                phase = PlaybackPhase.READY,
                isPlaying = false,
                isSeekable = true,
            ),
        )

        assertFalse(retrying.hasError)
        assertEquals(null, retrying.failure)
        assertTrue(retrying.focusPlayWhenReady)
        assertTrue(buffering.focusPlayWhenReady)
        assertFalse(ready.focusPlayWhenReady)
        assertFalse(ready.isLoading)
    }

    @Test
    fun `playback error families become safe user-facing reasons`() {
        assertEquals(PlaybackFailure.CONNECTION, playbackFailureFromErrorCode(2_002))
        assertEquals(PlaybackFailure.UNSUPPORTED_MEDIA, playbackFailureFromErrorCode(4_003))
        assertEquals(PlaybackFailure.UNKNOWN, playbackFailureFromErrorCode(1_001))
    }

    @Test
    fun `ended state remains visible until playback starts again`() {
        val ended = reducePlaybackUiState(
            current = PlaybackUiState(isSeekable = true),
            event = PlaybackUiEvent.StateChanged(
                phase = PlaybackPhase.ENDED,
                isPlaying = false,
                isSeekable = true,
            ),
        )
        val duplicatePauseCallback = reducePlaybackUiState(
            current = ended,
            event = PlaybackUiEvent.PlayingChanged(
                isPlaying = false,
                isSeekable = true,
            ),
        )
        val restarted = reducePlaybackUiState(
            current = duplicatePauseCallback,
            event = PlaybackUiEvent.PlayingChanged(
                isPlaying = true,
                isSeekable = true,
            ),
        )

        assertTrue(duplicatePauseCallback.hasEnded)
        assertFalse(restarted.hasEnded)
    }

    @Test
    fun `play after ended restarts from the default position`() {
        assertEquals(
            PlaybackToggleAction.RESTART_FROM_DEFAULT,
            playbackToggleAction(
                isPlaying = false,
                hasEnded = true,
            ),
        )
    }

    @Test
    fun `stop pauses and resumes exactly once when playback was active`() {
        val stopped = onPlaybackStopped(
            current = PlaybackLifecycleState(),
            wasPlaying = true,
        )
        val started = onPlaybackStarted(stopped.state)
        val resumedAgain = onPlaybackStarted(started.state)

        assertTrue(stopped.pause)
        assertTrue(stopped.state.resumeAfterStop)
        assertTrue(started.play)
        assertFalse(started.state.resumeAfterStop)
        assertFalse(resumedAgain.play)
    }

    @Test
    fun `paused playback stays paused across stop and start`() {
        val stopped = onPlaybackStopped(
            current = PlaybackLifecycleState(),
            wasPlaying = false,
        )
        val started = onPlaybackStarted(stopped.state)

        assertTrue(stopped.pause)
        assertFalse(started.play)
    }
}
