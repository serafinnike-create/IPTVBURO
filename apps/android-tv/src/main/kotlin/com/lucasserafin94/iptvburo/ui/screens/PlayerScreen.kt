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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.lucasserafin94.iptvburo.playback.AndroidPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import com.lucasserafin94.iptvburo.ui.ChannelUi
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
        player.volume = 1f
    }

    LaunchedEffect(isInPictureInPicture) {
        if (isInPictureInPicture) controlsVisible = false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        LaunchedEffect(activity, videoBounds) {
            val sourceRect = videoBounds ?: return@LaunchedEffect
            activity?.setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(true)
                    .setSourceRectHint(sourceRect)
                    .build(),
            )
        }
        DisposableEffect(activity) {
            onDispose {
                activity?.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(false)
                        .build(),
                )
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

    LaunchedEffect(player, progressIdentity, resumeChoiceResolved) {
        if (progressIdentity == null || !resumeChoiceResolved) return@LaunchedEffect
        while (true) {
            delay(12_000)
            if (player.isPlaying && player.duration > 0L) {
                playbackProgressCoordinator.checkpointAsync(progressIdentity, player.currentPosition, player.duration)
            }
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
            if (player.duration > 0L) {
                playbackProgressCoordinator.checkpointAsync(progressIdentity, player.currentPosition, player.duration)
            }
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (player.duration > 0L) {
                        playbackProgressCoordinator.checkpointAsync(progressIdentity, player.currentPosition, player.duration)
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

    val closePlayer = {
        if (player.duration > 0L) {
            playbackProgressCoordinator.checkpointAsync(progressIdentity, player.currentPosition, player.duration)
        }
        onBack()
    }
    BackHandler(onBack = closePlayer)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(controlsLocked) {
                detectTapGestures {
                    if (!controlsLocked) controlsVisible = !controlsVisible
                }
            },
    ) {
        VideoSurface(
            player = player,
            modifier = Modifier.onGloballyPositioned { coordinates ->
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
                    onVolumeChanged = { value ->
                        playerVolume = value
                        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (value.coerceIn(0f, 1f) * maximum).roundToInt(),
                            0,
                        )
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
                            Icons.Default.LockOpen,
                            contentDescription = stringResource(R.string.player_unlock_controls),
                            tint = BuroTextPrimary,
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
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
                        player.prepare()
                        player.play()
                    },
                    modifier = Modifier.align(Alignment.Center),
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
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                this.player = player
            }
        },
        update = {
            it.player = player
            it.useController = false
        },
        modifier = modifier.fillMaxSize(),
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
                    Icons.AutoMirrored.Filled.ArrowBack,
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
    onPictureInPicture: () -> Unit,
    onToggleFullscreen: () -> Unit,
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
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
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
                            Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.player_seek_forward_30),
                            tint = BuroTextPrimary,
                        )
                    },
                )
            }

            ControlButton(
                onClick = onCycleSpeed,
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.player_speed), tint = BuroTextPrimary, modifier = Modifier.size(22.dp))
                        Text("${speed}x", color = BuroTextPrimary, fontSize = 10.sp)
                    }
                },
            )
            if (canUsePictureInPicture) {
                ControlButton(
                    onClick = onPictureInPicture,
                    icon = { Icon(Icons.Default.PictureInPicture, contentDescription = stringResource(R.string.player_picture_in_picture), tint = BuroTextPrimary) },
                )
            }
            ControlButton(
                onClick = onToggleFullscreen,
                icon = {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = stringResource(if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_enter_fullscreen),
                        tint = BuroTextPrimary,
                    )
                },
            )
            ControlButton(
                onClick = onLock,
                icon = { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.player_lock_controls), tint = BuroTextPrimary) },
            )
        }


        if (showMobileControls) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.BrightnessHigh, contentDescription = stringResource(R.string.player_brightness), tint = BuroTextPrimary)
                Slider(value = brightness, onValueChange = onBrightnessChanged, valueRange = 0.05f..1f, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.player_volume), tint = BuroTextPrimary)
                Slider(value = volume, onValueChange = onVolumeChanged, valueRange = 0f..1f, modifier = Modifier.weight(1f))
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
