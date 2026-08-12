package com.lucasserafin94.iptvburo.ui.screens

internal enum class PlaybackPhase {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}

internal enum class PlaybackFailure {
    CONNECTION,

    /**
     * The provider answered, and said this stream is not there.
     *
     * Separate from [CONNECTION] because the advice is opposite: a 404 or a 403 is a channel the
     * playlist still lists but the provider has removed or will not serve, and telling somebody to
     * check their network sends them to look at the one thing that is working.
     */
    STREAM_GONE,
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
    /**
     * Where playback is and how long the item runs, in milliseconds.
     *
     * Both are zero for a live stream, which has no duration to report and no position within one.
     * The controls use that to decide whether a scrubber makes sense at all: a bar that fills to
     * 100% and stays there would misdescribe live television.
     */
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
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

/**
 * What to tell the viewer, from the player's error code and the HTTP status behind it.
 *
 * [httpStatus] is null unless the failure came from an HTTP response. It is what separates "your
 * network is down" from "this channel is gone", which look identical in the error code alone.
 */
internal fun playbackFailureFromErrorCode(
    errorCode: Int,
    httpStatus: Int? = null,
): PlaybackFailure =
    when {
        // The provider replied and refused. 404 is a dead channel still in the playlist; 401 and
        // 403 are the account not being allowed this stream. Neither is fixed by checking wifi.
        httpStatus in setOf(401, 403, 404, 410) -> PlaybackFailure.STREAM_GONE
        errorCode / ERROR_CODE_FAMILY_SIZE == IO_ERROR_FAMILY -> PlaybackFailure.CONNECTION
        errorCode / ERROR_CODE_FAMILY_SIZE == DECODER_ERROR_FAMILY -> PlaybackFailure.UNSUPPORTED_MEDIA
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
