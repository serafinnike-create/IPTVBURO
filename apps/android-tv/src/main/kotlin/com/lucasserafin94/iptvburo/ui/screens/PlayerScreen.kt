package com.lucasserafin94.iptvburo.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaSession
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.playback.PictureInPictureRequest
import com.lucasserafin94.iptvburo.playback.PlaybackSessionFactory
import com.lucasserafin94.iptvburo.playback.AndroidPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import com.lucasserafin94.iptvburo.domain.model.SubtitlePresentation
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.EpisodeUi
import com.lucasserafin94.iptvburo.ui.LiveProgramUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * How the picture is fitted to the surface.
 *
 * Cycled by a button rather than chosen from a menu: there are four, the effect is obvious the
 * moment it applies, and a menu for four options is more taps than trying the next one.
 */
@OptIn(markerClass = [UnstableApi::class])
enum class VideoScaleMode(
    val resizeMode: Int,
    /** Named on the button, so translated rather than a hard-coded string. */
    val labelResource: Int,
) {
    /** The title's own shape, letterboxed. Nothing is ever cropped. */
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, R.string.player_scale_original),

    /** Fills the screen, cropping the edges of anything that does not match. */
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, R.string.player_scale_fill),

    /** Stretched to the surface, ignoring the source shape. */
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, R.string.player_scale_stretch),

    /** Fixed to the width, which is how a 4:3 source is usually wanted on a wide screen. */
    FIXED_WIDTH(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, R.string.player_scale_wide),
    ;

    fun next(): VideoScaleMode = entries[(ordinal + 1) % entries.size]
}

@Composable
fun PlayerScreen(
    channel: ChannelUi,
    nowPlaying: LiveProgramUi?,
    nextPlaying: LiveProgramUi?,
    isEpgLoading: Boolean,
    playbackSessionFactory: PlaybackSessionFactory,
    playbackProgressCoordinator: AndroidPlaybackProgressCoordinator,
    activeProfileId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    /** Null for live television, which is not a title anyone returns to from a favourites list. */
    onToggleFavorite: (() -> Unit)? = null,
    /** How subtitles are drawn, from settings. */
    subtitles: SubtitlePresentation = SubtitlePresentation(),
    /** The channel's schedule, for the guide. Empty for anything that is not live television. */
    liveSchedule: List<LiveProgramUi> = emptyList(),
    /** Changing channel from inside the player; null when there is no neighbour to go to. */
    onPreviousChannel: (() -> Unit)? = null,
    onNextChannel: (() -> Unit)? = null,
    /** The episode after this one, or null for anything that is not a series episode or the last one. */
    nextEpisode: EpisodeUi? = null,
    onPlayNextEpisode: () -> Unit = {},
    /** Replays a past programme of this live channel from its provider's archive. */
    onReplayProgramme: (LiveProgramUi) -> Unit = {},
) {
    val progressIdentity = remember(channel.id, activeProfileId) {
        playbackProgressIdentity(activeProfileId, channel)
    }
    val player = remember(channel.id, activeProfileId) {
        playbackSessionFactory.create(channel, autoPlay = progressIdentity == null)
    }
    var resumeDecision by remember(player) { mutableStateOf<ResumeDecision?>(null) }
    var resumeChoiceResolved by remember(player) { mutableStateOf(progressIdentity == null) }
    var playbackState by remember(channel.id) {
        mutableStateOf(PlaybackUiState(isLoading = true))
    }
    var lifecycleState by remember(player) {
        mutableStateOf(PlaybackLifecycleState())
    }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var controlsLocked by remember(player) { mutableStateOf(false) }
    var playbackSpeed by remember(player) { mutableFloatStateOf(1f) }
    var videoBounds by remember(player) { mutableStateOf<Rect?>(null) }
    var scaleMode by remember(player) { mutableStateOf(VideoScaleMode.FIT) }
    var showSchedule by remember(player) { mutableStateOf(false) }
    // How many times in a row the stream has been reopened on its own, and the wait before the
    // next attempt — null when no reconnect is pending. See [reconnectDelayMillis].
    var reconnectAttempt by remember(player) { mutableIntStateOf(0) }
    var reconnectAfterMillis by remember(player) { mutableStateOf<Long?>(null) }
    var gestureFeedback by remember(player) { mutableStateOf<GestureFeedback?>(null) }
    var gestureFeedbackSerial by remember(player) { mutableIntStateOf(0) }
    var levelDragging by remember(player) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context.findActivity()
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val isTelevision =
        androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
            Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var playerVolume by remember(player, audioManager) {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum.toFloat(),
        )
    }
    val isFullscreen = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isInPictureInPicture =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity?.isInPictureInPictureMode == true
    var screenBrightness by remember(activity) {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.65f,
        )
    }

    LaunchedEffect(player) {
        // Matched to the slider's starting position rather than forced to 1f, so the control and
        // what is actually audible agree from the first frame.
        player.volume = playerVolume.coerceIn(0f, 1f)
    }

    // The clock behind the scrubber. Polled rather than observed because ExoPlayer reports position
    // on demand — there is no callback that fires as it advances.
    //
    // Paused while the user is dragging: writing the player's position back into state mid-drag
    // would fight the thumb and make the bar jump under the finger.
    var scrubbingTo by remember(player) { mutableStateOf<Float?>(null) }

    // The tracks the stream turned out to carry, refreshed as the player discovers them: an HLS
    // stream reports its alternates only once the manifest is parsed, so reading this once at
    // start-up would find nothing on exactly the streams that have the most to offer.
    var tracks by remember(player) { mutableStateOf(androidx.media3.common.Tracks.EMPTY) }
    var showTrackSheet by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(newTracks: androidx.media3.common.Tracks) {
                    tracks = newTracks
                }
            }
        player.addListener(listener)
        tracks = player.currentTracks
        onDispose { player.removeListener(listener) }
    }
    val audioTracks = remember(tracks) { tracks.selectableTracks(androidx.media3.common.C.TRACK_TYPE_AUDIO) }
    val subtitleTracks = remember(tracks) { tracks.selectableTracks(androidx.media3.common.C.TRACK_TYPE_TEXT) }

    /**
     * The last position seen while the film was genuinely playing.
     *
     * ExoPlayer reports `currentPosition == duration` once it has been stopped or has run to the
     * end, and every checkpoint outside the polling loop reads the position at a moment when that
     * may already be true. Saving it recorded a film as fully watched the instant the user left it,
     * so nothing ever appeared under Continue watching. This is the value those checkpoints use.
     */
    var lastPlayingPositionMs by remember(player) { mutableLongStateOf(0L) }

    // A single 500ms ticker feeds the UI position readout, the "last known playing position"
    // sample, and the periodic checkpoint save — three independent reasons to poll the same
    // player clock, previously three separate loops each doing their own main-thread reads.
    LaunchedEffect(player, scrubbingTo == null, progressIdentity, resumeChoiceResolved) {
        if (scrubbingTo != null) return@LaunchedEffect
        var wasSamplingPlayback = false
        var ticksSinceLastPlayingSample = 0
        var ticksSinceLastCheckpoint = 0
        while (true) {
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            playbackState =
                playbackState.copy(
                    positionMillis = player.currentPosition.coerceAtLeast(0L),
                    durationMillis = duration,
                )
            if (progressIdentity != null && resumeChoiceResolved && player.isPlaying && duration > 0L) {
                ticksSinceLastPlayingSample += 1
                // Sampled right away the instant playback starts, not only after the first full
                // interval: waiting would let an early exit (e.g. backgrounding within that first
                // second) checkpoint against the stale value left over from before playback began.
                if (!wasSamplingPlayback || ticksSinceLastPlayingSample >= PLAYING_SAMPLE_TICKS) {
                    ticksSinceLastPlayingSample = 0
                    lastPlayingPositionMs = player.currentPosition
                }
                wasSamplingPlayback = true
                ticksSinceLastCheckpoint += 1
                if (ticksSinceLastCheckpoint >= CHECKPOINT_TICKS) {
                    ticksSinceLastCheckpoint = 0
                    playbackProgressCoordinator.checkpointAsync(progressIdentity, player.currentPosition, duration)
                }
            } else {
                wasSamplingPlayback = false
                ticksSinceLastPlayingSample = 0
                ticksSinceLastCheckpoint = 0
            }
            delay(500)
        }
    }

    LaunchedEffect(isInPictureInPicture) {
        if (isInPictureInPicture) controlsVisible = false
    }

    // The title's own shape, for the picture-in-picture window. Without it the system opened a
    // window of its default shape and letterboxed the film inside it.
    var videoAspect by remember(player) { mutableStateOf<Rational?>(null) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    videoAspect = pictureInPictureAspect(videoSize)
                }
            }
        player.addListener(listener)
        videoAspect = pictureInPictureAspect(player.videoSize)
        onDispose { player.removeListener(listener) }
    }

    if (!isTelevision && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Armed only while something is actually playing: leaving the app over a paused film or an
        // error message should not float that frozen frame over the home screen.
        val wantsPictureInPicture = playbackState.isPlaying && resumeChoiceResolved
        LaunchedEffect(activity, videoBounds, videoAspect, wantsPictureInPicture) {
            val params =
                PictureInPictureParams.Builder().apply {
                    videoBounds?.let(::setSourceRectHint)
                    videoAspect?.let(::setAspectRatio)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(wantsPictureInPicture)
                    }
                }.build()
            PictureInPictureRequest.pending = params.takeIf { wantsPictureInPicture }
            runCatching { activity?.setPictureInPictureParams(params) }
        }
        DisposableEffect(activity) {
            onDispose {
                PictureInPictureRequest.pending = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        activity?.setPictureInPictureParams(
                            PictureInPictureParams.Builder()
                                .setAutoEnterEnabled(false)
                                .build(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Hides the status and navigation bars while a title is open, and puts them back on the way
     * out.
     *
     * Without this the clock and battery sat over the picture for the whole film — the player
     * filled the window but the window was never the whole screen. They come back on a swipe, so
     * nothing becomes unreachable.
     */
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            // Only on a phone. A television hides them for the whole app, and restoring them here
            // would leave the bars up over every other screen after the first title played.
            if (!isTelevision) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                // Hand the orientation back to the device. The fullscreen button pins it to
                // landscape, and leaving it pinned meant the rest of the app stayed sideways after
                // the film ended, with no way to turn it back from inside the app.
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(controlsVisible, controlsLocked, playbackState.isPlaying) {
        if (controlsVisible && !controlsLocked && playbackState.isPlaying) {
            delay(4_500)
            controlsVisible = false
        }
    }

    LaunchedEffect(player, progressIdentity) {
        if (progressIdentity == null) return@LaunchedEffect
        val decision = playbackProgressCoordinator.resumeDecision(progressIdentity)
        resumeDecision = decision
        if (decision !is ResumeDecision.ResumeFrom) {
            resumeChoiceResolved = true
            player.play()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = reducePlaybackUiState(
                    current = playbackState,
                    event = PlaybackUiEvent.StateChanged(
                        phase = state.toPlaybackPhase(),
                        isPlaying = player.isPlaying,
                        isSeekable = player.isCurrentMediaItemSeekable,
                    ),
                )
                if (state == Player.STATE_ENDED && player.duration > 0L) {
                    playbackProgressCoordinator.endedAsync(progressIdentity, player.duration)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    reconnectAttempt = 0
                    reconnectAfterMillis = null
                }
                playbackState = reducePlaybackUiState(
                    current = playbackState,
                    event = PlaybackUiEvent.PlayingChanged(
                        isPlaying = isPlaying,
                        isSeekable = player.isCurrentMediaItemSeekable,
                    ),
                )
                if (!isPlaying && resumeChoiceResolved && player.duration > 0L) {
                    playbackProgressCoordinator.checkpointAsync(progressIdentity, player.currentPosition, player.duration)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // The code and its name, never the URL — a stream address carries the account's
                // credentials. Without this line a failure left nothing behind at all, and "the
                // connection failed" was as much as anybody could find out about a channel that
                // would not open.
                // The HTTP status too when there is one: 403 (the provider refusing this account or
                // this stream) and 404 (a dead channel still listed) mean very different things to
                // whoever is trying to fix it, and both arrive as the same error code.
                val status =
                    (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                        ?.responseCode
                android.util.Log.w(
                    "IptvBuroPlayer",
                    "Playback failed: ${error.errorCodeName} (${error.errorCode})" +
                        (status?.let { " HTTP $it" } ?: ""),
                )
                val delayMillis = reconnectDelayMillis(error.errorCode, status, reconnectAttempt)
                if (delayMillis != null) {
                    reconnectAttempt += 1
                    reconnectAfterMillis = delayMillis
                    return
                }
                reconnectAfterMillis = null
                playbackState = reducePlaybackUiState(
                    current = playbackState,
                    event =
                        PlaybackUiEvent.Error(
                            playbackFailureFromErrorCode(error.errorCode, status),
                        ),
                )
            }

            override fun onRenderedFirstFrame() {
                // Pictures again, so the next drop gets the full set of attempts.
                reconnectAttempt = 0
                reconnectAfterMillis = null
                playbackState = reducePlaybackUiState(
                    current = playbackState,
                    event = PlaybackUiEvent.FirstFrame(
                        isSeekable = player.isCurrentMediaItemSeekable,
                    ),
                )
            }
        }
        player.addListener(listener)

        onDispose {
            if (player.duration > 0L) {
                playbackProgressCoordinator.checkpointAsync(
                    progressIdentity,
                    lastPlayingPositionMs,
                    player.duration,
                )
            }
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }

    // A media session for whatever is playing. It is what headset and Bluetooth buttons, the TV
    // remote's media keys, the assistant ("pause") and the picture-in-picture window's own
    // controls talk to; without one, none of them reached the player at all.
    //
    // Declared after the effect that releases the player, so that on the way out it is disposed
    // first: a session must let go of its player before the player is released.
    DisposableEffect(player) {
        val session =
            runCatching {
                MediaSession.Builder(context, player)
                    .setId("iptvburo-player-${System.identityHashCode(player)}")
                    .build()
            }.onFailure { error ->
                android.util.Log.w("IptvBuroPlayer", "Media session unavailable: ${error.javaClass.simpleName}")
            }.getOrNull()
        onDispose { session?.release() }
    }

    // Reopens the stream after a recoverable failure. A live channel goes back to the live edge —
    // reopening where it failed would land outside the window the provider still holds — while a
    // film keeps its place, which prepare() preserves on its own.
    LaunchedEffect(player, reconnectAfterMillis, reconnectAttempt) {
        val wait = reconnectAfterMillis ?: return@LaunchedEffect
        delay(wait)
        if (channel.contentType == CatalogContentType.LIVE || player.duration <= 0L) {
            player.seekToDefaultPosition()
        }
        player.prepare()
        player.play()
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (player.duration > 0L) {
                        playbackProgressCoordinator.checkpointAsync(
                            progressIdentity,
                            lastPlayingPositionMs,
                            player.duration,
                        )
                    }
                    val transition = onPlaybackStopped(
                        current = lifecycleState,
                        wasPlaying = player.isPlaying,
                    )
                    lifecycleState = transition.state
                    if (transition.pause) player.pause()
                }

                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME,
                -> {
                    val transition = onPlaybackStarted(lifecycleState)
                    lifecycleState = transition.state
                    if (transition.play) player.play()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // The next episode, offered over the closing credits and played when this one ends. Cancelling
    // it holds for the rest of this episode only, so the offer comes back on the next one.
    var upNextDismissed by remember(player) { mutableStateOf(false) }
    val remainingMillis = (playbackState.durationMillis - playbackState.positionMillis).coerceAtLeast(0L)
    val showUpNext =
        nextEpisode != null && !upNextDismissed && resumeChoiceResolved && !playbackState.hasError &&
            playbackState.durationMillis > 0L && (remainingMillis <= UP_NEXT_LEAD_MS || playbackState.hasEnded)
    LaunchedEffect(playbackState.hasEnded, nextEpisode, upNextDismissed) {
        if (playbackState.hasEnded && nextEpisode != null && !upNextDismissed) onPlayNextEpisode()
    }

    val closePlayer = {
        if (player.duration > 0L) {
            playbackProgressCoordinator.checkpointAsync(
                progressIdentity,
                lastPlayingPositionMs,
                player.duration,
            )
        }
        onBack()
    }
    BackHandler(onBack = closePlayer)

    val setVolume: (Float) -> Unit = { value ->
        playerVolume = value
        // The player's own gain, not only the device stream. Driving the stream alone meant the
        // slider did nothing whenever the phone was already at maximum volume — which is where
        // people leave it — because there was no headroom left to move into.
        player.volume = value.coerceIn(0f, 1f)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (value.coerceIn(0f, 1f) * maximum).roundToInt(),
            0,
        )
    }
    val setBrightness: (Float) -> Unit = { value ->
        screenBrightness = value
        activity?.window?.let { window ->
            // `apply { screenBrightness = value }` looked right and did nothing: inside that block
            // the name resolved to the composable's own `screenBrightness` state, not to
            // LayoutParams.screenBrightness, so the window was reassigned its existing attributes
            // unchanged. Naming the receiver is what makes the assignment reach the window.
            window.attributes =
                window.attributes.also { params ->
                    params.screenBrightness = value.coerceIn(0.01f, 1f)
                }
        }
    }
    val showGestureFeedback: (GestureFeedback) -> Unit = { feedback ->
        gestureFeedback = feedback
        gestureFeedbackSerial += 1
    }
    LaunchedEffect(gestureFeedbackSerial, levelDragging) {
        if (gestureFeedback == null || levelDragging) return@LaunchedEffect
        delay(GESTURE_FEEDBACK_MS)
        gestureFeedback = null
    }

    // Touch gestures belong to phones and tablets. A television has no touch screen, and the
    // remote's own keys already seek, so only the plain tap is kept there.
    val gesturesEnabled = !isTelevision && !controlsLocked && !isInPictureInPicture && resumeChoiceResolved

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                // The remote's channel keys, which a television viewer reaches for first.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ChannelUp -> onNextChannel?.let { it(); true } ?: false
                    Key.ChannelDown -> onPreviousChannel?.let { it(); true } ?: false
                    else -> false
                }
            }
            .playerGestures(
                enabled = gesturesEnabled,
                key = player,
                onTap = { if (!controlsLocked) controlsVisible = !controlsVisible },
                onDoubleTap = { zone ->
                    when (zone) {
                        SeekZone.MIDDLE -> if (player.isPlaying) player.pause() else player.play()
                        SeekZone.BACK, SeekZone.FORWARD -> if (playbackState.isSeekable) {
                            val previous = (gestureFeedback as? GestureFeedback.Seek)?.seconds
                            val seconds = accumulatedSeekSeconds(previous, zone)
                            val step = if (zone == SeekZone.BACK) -DOUBLE_TAP_SEEK_SECONDS else DOUBLE_TAP_SEEK_SECONDS
                            val target = (player.currentPosition + step * 1_000L).coerceAtLeast(0L)
                            player.seekTo(if (player.duration > 0L) target.coerceAtMost(player.duration) else target)
                            showGestureFeedback(GestureFeedback.Seek(seconds))
                        }
                    }
                },
                onLevelDragStart = { side ->
                    levelDragging = true
                    controlsVisible = false
                    showGestureFeedback(
                        GestureFeedback.Level(side, if (side == LevelSide.VOLUME) playerVolume else screenBrightness),
                    )
                },
                onLevelDrag = { side, fraction ->
                    val updated =
                        adjustedLevel(if (side == LevelSide.VOLUME) playerVolume else screenBrightness, fraction)
                    if (side == LevelSide.VOLUME) setVolume(updated) else setBrightness(updated)
                    gestureFeedback = GestureFeedback.Level(side, updated)
                },
                onLevelDragEnd = {
                    levelDragging = false
                    gestureFeedbackSerial += 1
                },
                onPinch = { zoomIn ->
                    scaleMode = if (zoomIn) VideoScaleMode.ZOOM else VideoScaleMode.FIT
                    showGestureFeedback(GestureFeedback.Scale(scaleMode))
                },
            ),
    ) {
        VideoSurface(
            player = player,
            subtitles = subtitles,
            scaleMode = scaleMode,
            modifier = Modifier
                // The whole surface, letting the player letterbox to the title's own shape. A
                // fixed 16:9 box was tried and cropped anything wider — a 2.35:1 film lost its
                // sides. Giving the controls room is done by making the controls smaller, not by
                // cutting the picture.
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    videoBounds = Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt(),
                    )
                },
        )

        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.68f),
                            0.3f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
            )
        }

        gestureFeedback?.let { feedback ->
            GestureFeedbackBadge(
                feedback = feedback,
                modifier = Modifier.align(
                    when {
                        feedback is GestureFeedback.Seek && feedback.seconds < 0 -> Alignment.CenterStart
                        feedback is GestureFeedback.Seek -> Alignment.CenterEnd
                        else -> Alignment.Center
                    },
                ).padding(horizontal = 48.dp),
            )
        }

        // The padding applies to the controls, never to the video: insetting the picture is what
        // left a black band where the status bar used to be. The surface behind is full-bleed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            if (controlsVisible && !controlsLocked) {
                PlayerTopBar(
                    channelName = channel.name,
                    nowPlaying = nowPlaying,
                    nextPlaying = nextPlaying,
                    isEpgLoading = isEpgLoading,
                    onBack = closePlayer,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }

            if (controlsVisible && !controlsLocked && !playbackState.hasError) {
                PlayerControls(
                    state = playbackState,
                    volume = playerVolume,
                    brightness = screenBrightness,
                    speed = playbackSpeed,
                    isFullscreen = isFullscreen,
                    showMobileControls = !isTelevision,
                    canUsePictureInPicture = !isTelevision && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                    scrubbingTo = scrubbingTo,
                    onScrubbing = { value -> scrubbingTo = value },
                    onScrubFinished = {
                        scrubbingTo?.let { target ->
                            player.seekTo(target.toLong().coerceAtLeast(0L))
                            if (player.duration > 0L) {
                                playbackProgressCoordinator.checkpointAsync(
                                    progressIdentity,
                                    player.currentPosition,
                                    player.duration,
                                )
                            }
                        }
                        // Released only after the seek, so the poll does not briefly redraw the
                        // bar at the old position before the player has moved.
                        scrubbingTo = null
                    },
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    hasSelectableTracks = audioTracks.size > 1 || subtitleTracks.isNotEmpty(),
                    onOpenTracks = { showTrackSheet = true },
                    onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                    onSeekBack = {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        if (player.duration > 0L) {
                            playbackProgressCoordinator.checkpointAsync(progressIdentity, player.currentPosition, player.duration)
                        }
                    },
                    onSeekForward = {
                        val target = player.currentPosition + 30_000L
                        player.seekTo(
                            if (player.duration > 0) target.coerceAtMost(player.duration) else target,
                        )
                        if (player.duration > 0L) {
                            playbackProgressCoordinator.checkpointAsync(progressIdentity, player.currentPosition, player.duration)
                        }
                    },
                    onVolumeChanged = setVolume,
                    onBrightnessChanged = setBrightness,
                    onCycleSpeed = {
                        playbackSpeed = nextPlaybackSpeed(playbackSpeed)
                        player.setPlaybackSpeed(playbackSpeed)
                    },
                    scaleMode = scaleMode,
                    onCycleScaleMode = { scaleMode = scaleMode.next() },
                    isLive = channel.contentType == CatalogContentType.LIVE,
                    onOpenSchedule = { showSchedule = true },
                    onPreviousChannel = onPreviousChannel,
                    onNextChannel = onNextChannel,
                    onNextEpisode = if (nextEpisode != null) onPlayNextEpisode else null,
                    onPictureInPicture = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val builder = PictureInPictureParams.Builder()
                            videoBounds?.let(builder::setSourceRectHint)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                builder.setAutoEnterEnabled(true)
                            }
                            controlsVisible = false
                            activity?.enterPictureInPictureMode(builder.build())
                        }
                    },
                    onToggleFullscreen = {
                        activity?.requestedOrientation =
                            if (isFullscreen) {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                    },
                    onLock = {
                        controlsLocked = true
                        controlsVisible = false
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (controlsLocked) {
                ControlButton(
                    onClick = {
                        controlsLocked = false
                        controlsVisible = true
                    },
                    icon = {
                        Icon(
                            Icons.Rounded.LockOpen,
                            contentDescription = stringResource(R.string.player_unlock_controls),
                            tint = BuroTextPrimary,
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
                )
            }

            if (showUpNext && nextEpisode != null) {
                UpNextCard(
                    episode = nextEpisode,
                    secondsLeft = (remainingMillis / 1_000L).toInt(),
                    onPlayNow = onPlayNextEpisode,
                    onCancel = { upNextDismissed = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = if (controlsVisible) 150.dp else 24.dp),
                )
            }

            if (showSchedule) {
                LiveScheduleSheet(
                    channelName = channel.name,
                    schedule = liveSchedule,
                    isLoading = isEpgLoading,
                    onDismiss = { showSchedule = false },
                    catchUpDays = channel.catchUpDays,
                    onReplay = { programme ->
                        showSchedule = false
                        onReplayProgramme(programme)
                    },
                )
            }

            if (showTrackSheet) {
                TrackSelectorSheet(
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                    subtitlesDisabled =
                        player.trackSelectionParameters.disabledTrackTypes
                            .contains(androidx.media3.common.C.TRACK_TYPE_TEXT),
                    onSelectAudio = { track ->
                        player.selectTrack(tracks, androidx.media3.common.C.TRACK_TYPE_AUDIO, track)
                        showTrackSheet = false
                    },
                    onSelectSubtitle = { track ->
                        player.selectTrack(tracks, androidx.media3.common.C.TRACK_TYPE_TEXT, track)
                        showTrackSheet = false
                    },
                    onDisableSubtitles = {
                        player.disableSubtitles()
                        showTrackSheet = false
                    },
                    onDismiss = { showTrackSheet = false },
                )
            }

            when {
                !resumeChoiceResolved && resumeDecision is ResumeDecision.ResumeFrom -> {
                    val decision = resumeDecision as ResumeDecision.ResumeFrom
                    ResumeChoice(
                        positionMs = decision.positionMs,
                        onContinue = {
                            player.seekTo(decision.positionMs)
                            resumeChoiceResolved = true
                            player.play()
                        },
                        onStartOver = {
                            player.seekTo(0L)
                            resumeChoiceResolved = true
                            player.play()
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                !resumeChoiceResolved -> Text(
                    text = stringResource(R.string.player_loading),
                    color = BuroTextPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )

                playbackState.hasError -> PlaybackError(
                    failure = playbackState.failure ?: PlaybackFailure.UNKNOWN,
                    onRetry = {
                        playbackState = reducePlaybackUiState(
                            current = playbackState,
                            event = PlaybackUiEvent.Retry,
                        )
                        reconnectAttempt = 0
                        if (channel.contentType == CatalogContentType.LIVE || player.duration <= 0L) {
                            player.seekToDefaultPosition()
                        }
                        player.prepare()
                        player.play()
                    },
                    modifier = Modifier.align(Alignment.Center),
                )

                reconnectAfterMillis != null -> Text(
                    text = stringResource(R.string.player_reconnecting, reconnectAttempt, RECONNECT_ATTEMPTS),
                    color = BuroTextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BuroCanvas.copy(alpha = 0.86f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )

                playbackState.isLoading -> Text(
                    text = stringResource(R.string.player_loading),
                    color = BuroTextPrimary,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BuroCanvas.copy(alpha = 0.86f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }

        }
    }
}

@Composable
private fun ResumeChoice(
    positionMs: Long,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).background(BuroCanvas.copy(alpha = 0.96f)).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.player_resume_question), color = BuroTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        FocusSurface(
            onClick = onContinue,
            modifier = Modifier.width(260.dp).height(54.dp),
            backgroundColor = BuroTextPrimary,
            focusedBackgroundColor = BuroTextPrimary,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.player_resume_from, formatResumeTime(positionMs)),
                color = BuroCanvas,
                fontWeight = FontWeight.Bold,
            )
        }
        FocusSurface(
            onClick = onStartOver,
            modifier = Modifier.width(260.dp).height(54.dp),
            backgroundColor = BuroSurface,
            focusedBackgroundColor = BuroSurface,
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.player_start_over), color = BuroTextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The next episode, offered over the end of this one.
 *
 * Without it a series ended on a black frame and the viewer had to leave the player, find the
 * season again and pick the next number by hand. The count runs down the time this episode has
 * left; when it reaches zero the next one starts on its own, unless it was cancelled.
 */
@Composable
private fun UpNextCard(
    episode: EpisodeUi,
    secondsLeft: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BuroCanvas.copy(alpha = 0.94f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.player_next_episode),
            color = BuroTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            episode.title,
            color = BuroTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.player_next_episode_in, secondsLeft.coerceAtLeast(0)),
            color = BuroTextSecondary,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusSurface(
                onClick = onPlayNow,
                modifier = Modifier.weight(1f).height(48.dp),
                backgroundColor = BuroTextPrimary,
                focusedBackgroundColor = BuroTextPrimary,
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.player_play_now), color = BuroCanvas, fontWeight = FontWeight.Bold)
            }
            FocusSurface(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(48.dp),
                backgroundColor = BuroSurface,
                focusedBackgroundColor = BuroSurface,
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.common_cancel), color = BuroTextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

internal fun playbackProgressIdentity(profileId: String?, channel: ChannelUi): PlaybackProgressIdentity? {
    val safeProfileId = profileId?.takeIf(String::isNotBlank) ?: return null
    val contentType = when (channel.contentType) {
        CatalogContentType.MOVIE -> PlaybackContentType.MOVIE
        CatalogContentType.EPISODE -> PlaybackContentType.EPISODE
        else -> return null
    }
    return PlaybackProgressIdentity(
        profileId = safeProfileId,
        sourceId = channel.sourceId,
        contentId = channel.providerItemId ?: channel.id,
        contentType = contentType,
        seriesId = channel.seriesId,
        seasonNumber = channel.seasonNumber,
        episodeNumber = channel.episodeNumber,
    )
}

private fun formatResumeTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainder = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder) else "%02d:%02d".format(minutes, remainder)
}

@Composable
@OptIn(markerClass = [UnstableApi::class])
private fun VideoSurface(
    player: ExoPlayer,
    subtitles: SubtitlePresentation,
    /** How the picture is fitted to the surface. See [VideoScaleMode]. */
    scaleMode: VideoScaleMode,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                resizeMode = scaleMode.resizeMode
                keepScreenOn = true
                this.player = player
                applySubtitleStyle(subtitles)
            }
        },
        update = { view ->
            view.player = player
            view.useController = false
            view.resizeMode = scaleMode.resizeMode
            // Applied on update too, so changing the setting while a title is open takes effect
            // on this one rather than only on the next.
            view.applySubtitleStyle(subtitles)
        },
        // No fillMaxSize here: the caller decides the shape, because a phone in portrait wants a
        // 16:9 band at the top and everything else wants the whole surface.
        modifier = modifier,
    )
}

/**
 * Draws the viewer's chosen subtitle appearance.
 *
 * `setApplyEmbeddedStyles(false)` is what makes the choice stick: without it a stream carrying its
 * own styling overrides everything set here, and the setting appears to do nothing on exactly the
 * titles that have subtitles worth styling.
 */
@OptIn(markerClass = [UnstableApi::class])
private fun PlayerView.applySubtitleStyle(presentation: SubtitlePresentation) {
    val view = subtitleView ?: return
    view.setApplyEmbeddedStyles(false)
    view.setStyle(
        CaptionStyleCompat(
            // The chosen colour at full opacity; the enum carries 24-bit RGB.
            0xFF000000.toInt() or presentation.colour.rgb,
            if (presentation.background) BACKGROUND_SCRIM else android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            // An outline as well as the box: it is what keeps text legible when the box is off.
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            null,
        ),
    )
    view.setFractionalTextSize(
        SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * presentation.size.scale,
    )
}

/** Dark enough to read over a bright scene, translucent enough not to block it. */
private const val BACKGROUND_SCRIM = 0xB3000000.toInt()

/** 500ms ticks between "last known playing position" samples: a 1-second cadence. */
private const val PLAYING_SAMPLE_TICKS = 2

/** 500ms ticks between checkpoint saves: a 12-second cadence, unchanged from the old loop. */
private const val CHECKPOINT_TICKS = 24

/** How long a gesture's badge stays over the picture once the gesture is over. */
private const val GESTURE_FEEDBACK_MS = 700L

/** How long before the end the next episode is offered — roughly where the credits begin. */
private const val UP_NEXT_LEAD_MS = 20_000L

/**
 * The picture-in-picture shape for [videoSize], or null before the stream has reported one.
 *
 * Clamped to the range Android accepts — roughly 1:2.39 to 2.39:1 — because asking for anything
 * outside it throws, and some streams report a degenerate size while they start.
 */
private fun pictureInPictureAspect(videoSize: VideoSize): Rational? {
    if (videoSize.width <= 0 || videoSize.height <= 0) return null
    val width = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt().coerceAtLeast(1)
    val ratio = width.toFloat() / videoSize.height
    return when {
        ratio > MAX_PIP_RATIO -> Rational(239, 100)
        ratio < 1f / MAX_PIP_RATIO -> Rational(100, 239)
        else -> Rational(width, videoSize.height)
    }
}

private const val MAX_PIP_RATIO = 2.38f

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun PlayerTopBar(
    channelName: String,
    nowPlaying: LiveProgramUi?,
    nextPlaying: LiveProgramUi?,
    isEpgLoading: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            onClick = onBack,
            icon = {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.player_back),
                    tint = BuroTextPrimary,
                )
            },
        )
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "IPTV BURO",
                color = BuroAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = channelName,
                color = BuroTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                isEpgLoading -> Text(stringResource(R.string.player_guide_loading), color = BuroTextSecondary, fontSize = 13.sp)
                nowPlaying != null -> {
                    Text(stringResource(R.string.player_now, nowPlaying.title), color = BuroTextPrimary.copy(alpha = 0.84f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    nextPlaying?.let { next ->
                        Text(stringResource(R.string.player_next, next.title), color = BuroTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlaybackUiState,
    volume: Float,
    brightness: Float,
    speed: Float,
    isFullscreen: Boolean,
    showMobileControls: Boolean,
    canUsePictureInPicture: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    scaleMode: VideoScaleMode,
    onCycleScaleMode: () -> Unit,
    /** True for live television, which is the only thing with a schedule to show. */
    isLive: Boolean,
    onOpenSchedule: () -> Unit,
    onPreviousChannel: (() -> Unit)?,
    onNextChannel: (() -> Unit)?,
    onNextEpisode: (() -> Unit)?,
    onPictureInPicture: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onLock: () -> Unit,
    /** The thumb's position while a drag is in progress, or null when the player owns the bar. */
    scrubbingTo: Float?,
    onScrubbing: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    isFavorite: Boolean,
    /** Null where favouriting makes no sense — live television the user cannot revisit. */
    onToggleFavorite: (() -> Unit)?,
    /** False when the stream carries a single audio track and no subtitles. */
    hasSelectableTracks: Boolean,
    onOpenTracks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // FlowRow, not Row. Seven controls do not fit across a phone, and in a plain Row the last
        // of them — subtitles and favourite — were laid out past the right edge with no gesture
        // that could reach them. Wrapping keeps every control on screen at any width.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            onPreviousChannel?.let { previous ->
                ControlButton(
                    onClick = previous,
                    icon = {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.player_previous_channel),
                            tint = BuroTextPrimary,
                        )
                    },
                )
            }

            if (state.isSeekable) {
                ControlButton(
                    onClick = onSeekBack,
                    icon = {
                        Icon(
                            Icons.Rounded.FastRewind,
                            contentDescription = stringResource(R.string.player_seek_back_10),
                            tint = BuroTextPrimary,
                        )
                    },
                )
            }

            ControlButton(
                onClick = onPlayPause,
                emphasized = true,
                requestFocus = !state.focusPlayWhenReady,
                icon = {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                        tint = BuroCanvas,
                        modifier = Modifier.size(34.dp),
                    )
                },
            )

            if (state.isSeekable) {
                ControlButton(
                    onClick = onSeekForward,
                    icon = {
                        Icon(
                            Icons.Rounded.FastForward,
                            contentDescription = stringResource(R.string.player_seek_forward_30),
                            tint = BuroTextPrimary,
                        )
                    },
                )
            }

            (onNextChannel ?: onNextEpisode)?.let { next ->
                ControlButton(
                    onClick = next,
                    icon = {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription =
                                stringResource(
                                    if (onNextChannel != null) R.string.player_next_channel else R.string.player_next_episode,
                                ),
                            tint = BuroTextPrimary,
                        )
                    },
                )
            }

            ControlButton(
                onClick = onCycleSpeed,
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Speed, contentDescription = stringResource(R.string.player_speed), tint = BuroTextPrimary, modifier = Modifier.size(22.dp))
                        Text("${speed}x", color = BuroTextPrimary, fontSize = 10.sp)
                    }
                },
            )
            // The schedule, for live television only. A film has no "what is on later", and a
            // button that opens an empty sheet on every VOD title would be noise.
            if (isLive) {
                ControlButton(
                    onClick = onOpenSchedule,
                    icon = {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = stringResource(R.string.live_schedule_title),
                            tint = BuroTextPrimary,
                        )
                    },
                )
            }

            // The picture's shape. Next to speed because both are "how this is playing" rather
            // than "what is playing", and the current mode is named on the button so the effect
            // is legible before it is pressed.
            ControlButton(
                onClick = onCycleScaleMode,
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.AspectRatio,
                            contentDescription = stringResource(R.string.player_aspect_ratio),
                            tint = BuroTextPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(stringResource(scaleMode.labelResource), color = BuroTextPrimary, fontSize = 9.sp)
                    }
                },
            )
            if (canUsePictureInPicture) {
                ControlButton(
                    onClick = onPictureInPicture,
                    icon = { Icon(Icons.Rounded.PictureInPicture, contentDescription = stringResource(R.string.player_picture_in_picture), tint = BuroTextPrimary) },
                )
            }
            ControlButton(
                onClick = onToggleFullscreen,
                icon = {
                    Icon(
                        if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = stringResource(if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_enter_fullscreen),
                        tint = BuroTextPrimary,
                    )
                },
            )
            ControlButton(
                onClick = onLock,
                icon = { Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.player_lock_controls), tint = BuroTextPrimary) },
            )
            // Only where there is a genuine choice. A stream with one audio track and no subtitles
            // would open an empty sheet, which reads as the feature being broken.
            if (hasSelectableTracks) {
                ControlButton(
                    onClick = onOpenTracks,
                    icon = {
                        Icon(
                            Icons.Rounded.ClosedCaption,
                            contentDescription = stringResource(R.string.player_tracks),
                            tint = BuroTextPrimary,
                        )
                    },
                )
            }
            // Favouriting from the player: the moment somebody decides they like something is
            // while they are watching it, not after they have left and found the details page.
            onToggleFavorite?.let { toggleFavorite ->
                ControlButton(
                    onClick = toggleFavorite,
                    icon = {
                        Icon(
                            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription =
                                stringResource(
                                    if (isFavorite) {
                                        R.string.details_favorite_added
                                    } else {
                                        R.string.details_favorite_add
                                    },
                                ),
                            tint = if (isFavorite) Color(0xFFE46C7A) else BuroTextPrimary,
                        )
                    },
                )
            }
        }


        // The scrubber. Only where there is something to scrub: a live stream has no duration, and
        // a bar pinned at 100% would describe it wrongly.
        if (state.isSeekable && state.durationMillis > 0L) {
            Spacer(Modifier.height(6.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Slider(
                    value = scrubbingTo ?: state.positionMillis.toFloat(),
                    onValueChange = onScrubbing,
                    onValueChangeFinished = onScrubFinished,
                    valueRange = 0f..state.durationMillis.toFloat(),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = BuroAccent,
                            activeTrackColor = BuroAccent,
                            inactiveTrackColor = BuroTextPrimary.copy(alpha = 0.22f),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // While dragging this reads the thumb, not the player: the number under the
                    // finger is the one the user is choosing, which is what makes scrubbing usable.
                    Text(
                        text = formatPlaybackClock((scrubbingTo ?: state.positionMillis.toFloat()).toLong()),
                        color = BuroTextPrimary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                    Text(
                        text = formatPlaybackClock(state.durationMillis),
                        color = BuroTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (showMobileControls) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Both on one line, as steppers rather than sliders. Two full-width sliders cost
                // two rows of the screen; minus and plus give the same control in a fraction of
                // the space, and are easier to hit than a thumb while the film is playing.
                // FlowRow rather than a plain Row: the two steppers fit side by side on a wide
                // screen but not on a narrow one, where the volume "+" was drawn half off the
                // right edge. Wrapping puts the second one on its own line instead of cutting it.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LevelStepper(
                        icon = Icons.Rounded.BrightnessHigh,
                        contentDescription = stringResource(R.string.player_brightness),
                        value = brightness,
                        onValueChange = { value -> onBrightnessChanged(value.coerceIn(0.05f, 1f)) },
                    )
                    LevelStepper(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.player_volume),
                        value = volume,
                        onValueChange = { value -> onVolumeChanged(value.coerceIn(0f, 1f)) },
                    )
                }
            }
        }

        if (!state.isSeekable && !state.isLoading && !state.hasError) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.player_seek_unavailable),
                color = BuroTextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * A level with minus and plus, for brightness and volume.
 *
 * A stepper rather than a slider: two full-width sliders took two rows of a phone screen, and a
 * thumb is hard to catch while a film is playing. The percentage is shown so the current level is
 * readable at a glance rather than inferred from a track.
 */
@Composable
private fun LevelStepper(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = BuroTextPrimary.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp),
        )
        StepperButton(label = "−", onClick = { onValueChange(value - LEVEL_STEP) })
        Text(
            text = "${(value * 100).roundToInt()}%",
            color = BuroTextPrimary.copy(alpha = 0.85f),
            fontSize = 13.sp,
            // Fixed width so the row does not shift as the number changes width. Wide enough for
            // "100%" and no wider, because two of these have to share one line.
            modifier = Modifier.width(38.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton(label = "+", onClick = { onValueChange(value + LEVEL_STEP) })
    }
}

@Composable
private fun StepperButton(
    label: String,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(38.dp),
    ) {
        Text(
            text = label,
            color = BuroTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** One press moves a tenth, so the full range is ten presses rather than a hundred. */
private const val LEVEL_STEP = 0.1f

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    emphasized: Boolean = false,
    requestFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) focusRequester.requestFocus()
    }

    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .size(if (emphasized) 68.dp else 54.dp)
            .focusRequester(focusRequester),
        backgroundColor = if (emphasized) BuroTextPrimary.copy(alpha = 0.94f) else BuroCanvas.copy(alpha = 0.58f),
        focusedBackgroundColor = if (emphasized) BuroTextPrimary else BuroSurface.copy(alpha = 0.88f),
        selectedBackgroundColor = if (emphasized) BuroTextPrimary else BuroSurface.copy(alpha = 0.88f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

private fun nextPlaybackSpeed(current: Float): Float =
    when {
        current < 0.75f -> 1f
        current < 1.1f -> 1.25f
        current < 1.4f -> 1.5f
        current < 1.75f -> 2f
        else -> 0.5f
    }

@Composable
private fun PlaybackError(
    failure: PlaybackFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        retryFocusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BuroCanvas.copy(alpha = 0.94f))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.player_error),
            color = BuroDanger,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                stringResource(
                    when (failure) {
                        PlaybackFailure.CONNECTION -> R.string.player_error_connection
                        PlaybackFailure.STREAM_GONE -> R.string.player_error_stream_gone
                        PlaybackFailure.UNSUPPORTED_MEDIA -> R.string.player_error_unsupported
                        PlaybackFailure.UNKNOWN -> R.string.player_error_unknown
                    },
                ),
            color = BuroTextSecondary,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(320.dp),
        )
        Spacer(Modifier.height(16.dp))
        FocusSurface(
            onClick = onRetry,
            modifier = Modifier
                .width(180.dp)
                .height(52.dp)
                .focusRequester(retryFocusRequester),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.player_retry),
                    color = BuroTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun Int.toPlaybackPhase(): PlaybackPhase =
    when (this) {
        Player.STATE_IDLE -> PlaybackPhase.IDLE
        Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
        Player.STATE_READY -> PlaybackPhase.READY
        Player.STATE_ENDED -> PlaybackPhase.ENDED
        else -> PlaybackPhase.IDLE
    }

/**
 * Milliseconds as a clock the viewer reads at a glance.
 *
 * Hours appear only when the item has them: "1:42:07" for a film, "04:31" for an episode. A fixed
 * "00:04:31" would be padding nobody needs on the majority of content.
 */
internal fun formatPlaybackClock(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
