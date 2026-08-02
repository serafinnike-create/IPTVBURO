package com.lucasserafin94.iptvburo.ui.screens

internal enum class PlaybackPhase {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}

internal enum class PlaybackFailure {
    CONNECTION,
    UNSUPPORTED_MEDIA,
    UNKNOWN,
}

internal data class PlaybackUiState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isSeekable: Boolean = false,
    val hasError: Boolean = false,
    val failure: PlaybackFailure? = null,
    val hasEnded: Boolean = false,
    val focusPlayWhenReady: Boolean = false,
)

internal sealed interface PlaybackUiEvent {
    data class StateChanged(
        val phase: PlaybackPhase,
        val isPlaying: Boolean,
        val isSeekable: Boolean,
    ) : PlaybackUiEvent

    data class PlayingChanged(
        val isPlaying: Boolean,
        val isSeekable: Boolean,
    ) : PlaybackUiEvent

    data class FirstFrame(
        val isSeekable: Boolean,
    ) : PlaybackUiEvent

    data class Error(
        val failure: PlaybackFailure,
    ) : PlaybackUiEvent

    data object Retry : PlaybackUiEvent
}

internal fun reducePlaybackUiState(
    current: PlaybackUiState,
    event: PlaybackUiEvent,
): PlaybackUiState =
    when (event) {
        is PlaybackUiEvent.StateChanged -> {
            if (current.hasError) {
                current.copy(
                    isLoading = false,
                    isPlaying = false,
                    isSeekable = false,
                    hasEnded = false,
                )
            } else {
                current.copy(
                    isLoading =
                        event.phase == PlaybackPhase.IDLE ||
                            event.phase == PlaybackPhase.BUFFERING,
                    isPlaying = event.isPlaying,
                    isSeekable = event.isSeekable,
                    hasEnded = event.phase == PlaybackPhase.ENDED,
                    focusPlayWhenReady =
                        current.focusPlayWhenReady &&
                            event.phase != PlaybackPhase.READY,
                )
            }
        }

        is PlaybackUiEvent.PlayingChanged -> {
            if (current.hasError) {
                current.copy(
                    isPlaying = false,
                    isSeekable = false,
                )
            } else {
                current.copy(
                    isPlaying = event.isPlaying,
                    isSeekable = event.isSeekable,
                    hasEnded = if (event.isPlaying) false else current.hasEnded,
                )
            }
        }

        is PlaybackUiEvent.FirstFrame -> {
            if (current.hasError) {
                current
            } else {
                current.copy(
                    isLoading = false,
                    isSeekable = event.isSeekable,
                )
            }
        }

        is PlaybackUiEvent.Error ->
            current.copy(
                isLoading = false,
                isPlaying = false,
                isSeekable = false,
                hasError = true,
                failure = event.failure,
                hasEnded = false,
                focusPlayWhenReady = false,
            )

        PlaybackUiEvent.Retry ->
            current.copy(
                isLoading = true,
                isPlaying = false,
                isSeekable = false,
                hasError = false,
                failure = null,
                hasEnded = false,
                focusPlayWhenReady = true,
            )
    }

internal fun playbackFailureFromErrorCode(errorCode: Int): PlaybackFailure =
    when (errorCode / ERROR_CODE_FAMILY_SIZE) {
        IO_ERROR_FAMILY -> PlaybackFailure.CONNECTION
        DECODER_ERROR_FAMILY -> PlaybackFailure.UNSUPPORTED_MEDIA
        else -> PlaybackFailure.UNKNOWN
    }

internal data class PlaybackLifecycleState(
    val resumeAfterStop: Boolean = false,
)

internal data class PlaybackLifecycleTransition(
    val state: PlaybackLifecycleState,
    val pause: Boolean = false,
    val play: Boolean = false,
)

internal enum class PlaybackToggleAction {
    PLAY,
    PAUSE,
    RESTART_FROM_DEFAULT,
}

internal fun playbackToggleAction(
    isPlaying: Boolean,
    hasEnded: Boolean,
): PlaybackToggleAction =
    when {
        hasEnded -> PlaybackToggleAction.RESTART_FROM_DEFAULT
        isPlaying -> PlaybackToggleAction.PAUSE
        else -> PlaybackToggleAction.PLAY
    }

internal fun onPlaybackStopped(
    current: PlaybackLifecycleState,
    wasPlaying: Boolean,
): PlaybackLifecycleTransition =
    PlaybackLifecycleTransition(
        state = current.copy(
            resumeAfterStop = current.resumeAfterStop || wasPlaying,
        ),
        pause = true,
    )

internal fun onPlaybackStarted(
    current: PlaybackLifecycleState,
): PlaybackLifecycleTransition =
    PlaybackLifecycleTransition(
        state = current.copy(resumeAfterStop = false),
        play = current.resumeAfterStop,
    )

private const val ERROR_CODE_FAMILY_SIZE = 1_000
private const val IO_ERROR_FAMILY = 2
private const val DECODER_ERROR_FAMILY = 4
