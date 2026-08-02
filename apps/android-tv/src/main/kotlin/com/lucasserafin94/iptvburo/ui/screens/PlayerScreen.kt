package com.lucasserafin94.iptvburo.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.playback.PlaybackSessionFactory
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.Danger
import com.lucasserafin94.iptvburo.ui.theme.Ink
import com.lucasserafin94.iptvburo.ui.theme.Muted
import com.lucasserafin94.iptvburo.ui.theme.Surface
import com.lucasserafin94.iptvburo.ui.theme.Teal
import com.lucasserafin94.iptvburo.ui.theme.White

@Composable
fun PlayerScreen(
    channel: ChannelUi,
    playbackSessionFactory: PlaybackSessionFactory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = remember(channel.id) {
        playbackSessionFactory.create(channel)
    }
    var playbackState by remember(channel.id) {
        mutableStateOf(PlaybackUiState(isLoading = true))
    }
    var lifecycleState by remember(player) {
        mutableStateOf(PlaybackLifecycleState())
    }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var controlsLocked by remember(player) { mutableStateOf(false) }
    var playbackSpeed by remember(player) { mutableFloatStateOf(1f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = (androidx.compose.ui.platform.LocalContext.current).findActivity()
    val isTelevision =
        androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
            Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var playerVolume by remember(player) { mutableFloatStateOf(player.volume) }
    var screenBrightness by remember(activity) {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.65f,
        )
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
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackState = reducePlaybackUiState(
                    current = playbackState,
                    event = PlaybackUiEvent.PlayingChanged(
                        isPlaying = isPlaying,
                        isSeekable = player.isCurrentMediaItemSeekable,
                    ),
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackState = reducePlaybackUiState(
                    current = playbackState,
                    event =
                        PlaybackUiEvent.Error(
                            playbackFailureFromErrorCode(error.errorCode),
                        ),
                )
            }

            override fun onRenderedFirstFrame() {
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
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
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

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VideoSurface(
            player = player,
            controllerEnabled = !controlsLocked,
            onControlsVisibilityChanged = { controlsVisible = it },
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            if (controlsVisible && !controlsLocked) {
                PlayerTopBar(
                    channelName = channel.name,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }

            if (controlsVisible && !controlsLocked && !playbackState.hasError) {
                PlayerControls(
                    state = playbackState,
                    volume = playerVolume,
                    brightness = screenBrightness,
                    speed = playbackSpeed,
                    showMobileControls = !isTelevision,
                    canUsePictureInPicture = !isTelevision && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                    onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                    onSeekBack = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) },
                    onSeekForward = {
                        val target = player.currentPosition + 30_000L
                        player.seekTo(
                            if (player.duration > 0) target.coerceAtMost(player.duration) else target,
                        )
                    },
                    onVolumeChanged = { value ->
                        playerVolume = value
                        player.volume = value
                    },
                    onBrightnessChanged = { value ->
                        screenBrightness = value
                        activity?.window?.let { window ->
                            window.attributes = window.attributes.apply {
                                screenBrightness = value
                            }
                        }
                    },
                    onCycleSpeed = {
                        playbackSpeed = nextPlaybackSpeed(playbackSpeed)
                        player.setPlaybackSpeed(playbackSpeed)
                    },
                    onPictureInPicture = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
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
                        Icon(Icons.Default.LockOpen, contentDescription = "Desbloquear controles", tint = White)
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
                )
            }

            when {
                playbackState.hasError -> PlaybackError(
                    failure = playbackState.failure ?: PlaybackFailure.UNKNOWN,
                    onRetry = {
                        playbackState = reducePlaybackUiState(
                            current = playbackState,
                            event = PlaybackUiEvent.Retry,
                        )
                        player.prepare()
                        player.play()
                    },
                    modifier = Modifier.align(Alignment.Center),
                )

                playbackState.isLoading -> Text(
                    text = stringResource(R.string.player_loading),
                    color = White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.copy(alpha = 0.86f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }

        }
    }
}

@Composable
@OptIn(markerClass = [UnstableApi::class])
private fun VideoSurface(
    player: ExoPlayer,
    controllerEnabled: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = controllerEnabled
                controllerShowTimeoutMs = 4_500
                controllerAutoShow = true
                controllerHideOnTouch = true
                setShowRewindButton(true)
                setShowFastForwardButton(true)
                setShowPreviousButton(false)
                setShowNextButton(false)
                setShowShuffleButton(false)
                setShowSubtitleButton(true)
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        onControlsVisibilityChanged(visibility == View.VISIBLE)
                    },
                )
                setFullscreenButtonClickListener { fullscreen ->
                    context.findActivity()?.requestedOrientation =
                        if (fullscreen) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                }
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                this.player = player
            }
        },
        update = {
            it.player = player
            it.useController = controllerEnabled
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun PlayerTopBar(
    channelName: String,
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
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.player_back),
                    tint = White,
                )
            },
        )
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "IPTV BURO",
                color = Teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = channelName,
                color = White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlaybackUiState,
    volume: Float,
    brightness: Float,
    speed: Float,
    showMobileControls: Boolean,
    canUsePictureInPicture: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onPictureInPicture: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isSeekable) {
                ControlButton(
                    onClick = onSeekBack,
                    icon = {
                        Icon(
                            Icons.Default.FastRewind,
                            contentDescription = "-10s",
                            tint = White,
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
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(34.dp),
                    )
                },
            )

            if (state.isSeekable) {
                ControlButton(
                    onClick = onSeekForward,
                    icon = {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = "+30s",
                            tint = White,
                        )
                    },
                )
            }

            ControlButton(
                onClick = onCycleSpeed,
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Speed, contentDescription = "Velocidade", tint = White, modifier = Modifier.size(22.dp))
                        Text("${speed}x", color = White, fontSize = 10.sp)
                    }
                },
            )
            if (canUsePictureInPicture) {
                ControlButton(
                    onClick = onPictureInPicture,
                    icon = { Icon(Icons.Default.PictureInPicture, contentDescription = "Picture-in-Picture", tint = White) },
                )
            }
            ControlButton(
                onClick = onLock,
                icon = { Icon(Icons.Default.Lock, contentDescription = "Bloquear controles", tint = White) },
            )
        }


        if (showMobileControls) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.BrightnessHigh, contentDescription = "Brilho", tint = White)
                Slider(value = brightness, onValueChange = onBrightnessChanged, valueRange = 0.05f..1f, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume", tint = White)
                Slider(value = volume, onValueChange = onVolumeChanged, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            }
        }

        if (!state.isSeekable && !state.isLoading && !state.hasError) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.player_seek_unavailable),
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

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
        backgroundColor = if (emphasized) White.copy(alpha = 0.94f) else Ink.copy(alpha = 0.58f),
        focusedBackgroundColor = if (emphasized) White else Surface.copy(alpha = 0.88f),
        selectedBackgroundColor = if (emphasized) White else Surface.copy(alpha = 0.88f),
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
            .background(Ink.copy(alpha = 0.94f))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.player_error),
            color = Danger,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                stringResource(
                    when (failure) {
                        PlaybackFailure.CONNECTION -> R.string.player_error_connection
                        PlaybackFailure.UNSUPPORTED_MEDIA -> R.string.player_error_unsupported
                        PlaybackFailure.UNKNOWN -> R.string.player_error_unknown
                    },
                ),
            color = Muted,
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
                    color = White,
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
