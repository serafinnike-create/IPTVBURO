package com.lucasserafin94.iptvburo.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = playbackState.copy(
                    isLoading = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE,
                    isPlaying = player.isPlaying,
                    isSeekable = player.isCurrentMediaItemSeekable,
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackState = playbackState.copy(
                    isPlaying = isPlaying,
                    isSeekable = player.isCurrentMediaItemSeekable,
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackState = playbackState.copy(
                    isLoading = false,
                    hasError = true,
                    isPlaying = false,
                )
            }

            override fun onRenderedFirstFrame() {
                playbackState = playbackState.copy(
                    isLoading = false,
                    hasError = false,
                    isSeekable = player.isCurrentMediaItemSeekable,
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

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VideoSurface(player)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.68f),
                        0.3f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )

        PlayerTopBar(
            channelName = channel.name,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )

        when {
            playbackState.hasError -> PlaybackError(
                onRetry = {
                    playbackState = PlaybackUiState(isLoading = true)
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

        PlayerControls(
            state = playbackState,
            onPlayPause = {
                if (player.isPlaying) player.pause() else player.play()
            },
            onSeekBack = player::seekBack,
            onSeekForward = player::seekForward,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
@OptIn(markerClass = [UnstableApi::class])
private fun VideoSurface(player: ExoPlayer) {
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
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PlayerTopBar(
    channelName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 34.dp, vertical = 28.dp),
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
        Column {
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
            )
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlaybackUiState,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp, vertical = 28.dp),
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
                requestFocus = true,
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
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) focusRequester.requestFocus()
    }

    FocusSurface(
        onClick = onClick,
        modifier = Modifier
            .size(if (emphasized) 68.dp else 54.dp)
            .focusRequester(focusRequester),
        backgroundColor = if (emphasized) Teal else Surface.copy(alpha = 0.9f),
        focusedBackgroundColor = if (emphasized) Teal else Surface,
        selectedBackgroundColor = if (emphasized) Teal else Surface,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun PlaybackError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Spacer(Modifier.height(16.dp))
        FocusSurface(
            onClick = onRetry,
            modifier = Modifier
                .width(180.dp)
                .height(52.dp),
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

private data class PlaybackUiState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isSeekable: Boolean = false,
    val hasError: Boolean = false,
)
