package com.lucasserafin94.iptvburo.desktop.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopPlayerOverlay(
    request: DesktopPlaybackRequest,
    onCheckpoint: (Long, Long) -> Unit,
    onEnded: (Long) -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    isCompact: Boolean = false,
    onToggleCompact: () -> Unit = {},
    onClose: () -> Unit,
) {
    val controller = remember(request) { VlcDesktopPlayer() }
    var state by remember(request) { mutableStateOf(DesktopPlaybackSnapshot()) }

    DisposableEffect(controller) {
        onDispose {
            val latest = controller.snapshot()
            if (latest.durationMillis > 0.0) onCheckpoint(latest.positionMillis.toLong(), latest.durationMillis.toLong())
            controller.dispose()
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            state = controller.snapshot()
            delay(250)
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            delay(12_000)
            val latest = controller.snapshot()
            if (latest.playing && latest.durationMillis > 0.0) {
                onCheckpoint(latest.positionMillis.toLong(), latest.durationMillis.toLong())
            }
        }
    }
    LaunchedEffect(state.playing, state.ready) {
        if (state.ready && !state.playing && !state.ended && state.durationMillis > 0.0) {
            onCheckpoint(state.positionMillis.toLong(), state.durationMillis.toLong())
        }
    }
    LaunchedEffect(state.ended) {
        if (state.ended && state.durationMillis > 0.0) onEnded(state.durationMillis.toLong())
    }

    // Controls reveal on pointer movement and hide again after a pause, the behaviour every video
    // player uses. Only meaningful in full screen; windowed mode keeps them pinned.
    var controlsVisible by remember { mutableStateOf(true) }
    var videoPanel by remember { mutableStateOf<javax.swing.JPanel?>(null) }
    var wakeCounter by remember { mutableStateOf(0) }
    LaunchedEffect(wakeCounter, isFullScreen) {
        if (!isFullScreen) {
            controlsVisible = true
            return@LaunchedEffect
        }
        controlsVisible = true
        // Hidden regardless of playback state. Keeping them up while paused meant a title that had
        // not started yet - or was buffering - sat behind a permanent bar across the bottom of an
        // otherwise full-screen picture. Any movement or keypress brings them straight back.
        delay(3_000)
        controlsVisible = false
        videoPanel?.let { panel -> controller.setPointerVisible(panel, false) }
    }

    // Nothing requested focus, so key events never reached the handler and F11 did nothing at all.
    val playerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playerFocus.requestFocus() } }

    val closePlayer = {
        if (state.durationMillis > 0.0) onCheckpoint(state.positionMillis.toLong(), state.durationMillis.toLong())
        onClose()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPointerEvent(PointerEventType.Move) { wakeCounter++ }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Any key wakes the controls, not only the ones handled below: reaching for the
                // keyboard is the user asking to see where they are.
                wakeCounter++
                when (event.key) {
                    Key.F11 -> {
                        onToggleFullScreen()
                        true
                    }
                    // Escape leaves full screen first and only closes the player when already
                    // windowed. Closing outright meant the one obvious "get me out of here" key
                    // ended playback, and with the chrome hidden there was nothing else to press.
                    Key.Escape -> {
                        if (isFullScreen) onToggleFullScreen() else closePlayer()
                        true
                    }
                    Key.Spacebar -> {
                        if (state.ready) controller.togglePlayback()
                        state.ready
                    }
                    else -> false
                }
            }
            .focusRequester(playerFocus)
            .focusable(),
    ) {
        // Hidden in full screen so the picture is not letterboxed by a 64 dp bar. It is not the only
        // way out: in full screen the same actions appear as a floating bar over the video whenever
        // the controls are awake, because relying on F11 alone stranded the user when the embedded
        // video surface held the keyboard focus and the key never arrived.
        // Hidden in the compact window too. At 480x300 a 64dp bar plus the transport controls left
        // the video no height at all: the picture-in-picture window showed buttons and nothing else.
        if (!isFullScreen && !isCompact) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = closePlayer,
                ) { Text("Voltar") }
                Spacer(Modifier.width(16.dp))
                Text(request.title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${state.engineName} • HEVC/H.264", color = BuroColors.Primary)
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onToggleCompact) {
                    Text(if (isCompact) "Voltar ao app" else "Janela pequena")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onToggleFullScreen) {
                    Text("⛶  Tela cheia (F11)")
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            SwingPanel(
                // Movement over the video surface reaches Compose only through this callback: the
                // AWT canvas consumes it, so without this the controls never knew the user was
                // still there and never hid either.
                factory = {
                    controller.createComponent(request) { wakeCounter++ }
                        .also { panel -> videoPanel = panel }
                },
                update = { panel ->
                    controller.setPointerVisible(panel, controlsVisible || !isFullScreen)
                },
                modifier = Modifier.fillMaxSize(),
            )
            state.errorMessage?.let { message ->
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(message, color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { controller.retry(request) }) { Text("Tentar novamente") }
                }
            }
            if (state.loading && state.errorMessage == null) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = BuroColors.Primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Preparando vídeo…", color = Color.White)
                }
            }
        }
        // In full screen the transport bar is an overlay that hides itself, so the video keeps the
        // whole surface. Outside full screen it stays pinned, which is the expected windowed
        // behaviour and avoids controls that flicker while the user is aiming at them. The compact
        // window skips it entirely: there is no room for a seek bar, a clock and five buttons in
        // 300dp of height, and taking that room is what left no picture at all.
        if (isCompact) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xE6121418))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = controller::togglePlayback) {
                    Text(
                        text = if (state.playing) "❚❚" else "▶",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleCompact) {
                    Text(
                        text = "Voltar ao app",
                        color = BuroColors.Primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        } else if (!isFullScreen || controlsVisible) {
        Column(Modifier.fillMaxWidth().background(Color(0xE6121418)).padding(horizontal = 24.dp, vertical = 14.dp)) {
            Slider(
                value = if (state.durationMillis > 0.0) (state.positionMillis / state.durationMillis).toFloat().coerceIn(0f, 1f) else 0f,
                onValueChange = { controller.seekToFraction(it.toDouble()) },
                enabled = state.ready && state.durationMillis > 0.0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { controller.seekBy(-10_000.0) }, enabled = state.ready) { Text("-10 s") }
                Button(onClick = controller::togglePlayback, enabled = state.ready) { Text(if (state.playing) "Pausar" else "Reproduzir") }
                Button(onClick = { controller.seekBy(30_000.0) }, enabled = state.ready) { Text("+30 s") }
                Text(
                    "${formatPlaybackTime(state.positionMillis)} / ${formatPlaybackTime(state.durationMillis)}",
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        val next = when (state.playbackRate) {
                            in 0.0..1.0 -> 1.25
                            in 1.01..1.25 -> 1.5
                            in 1.26..1.5 -> 2.0
                            else -> 1.0
                        }
                        controller.setPlaybackRate(next)
                    },
                    enabled = state.ready,
                ) { Text("${state.playbackRate}x") }
                Text("Volume ${(state.volume * 100).toInt()}%", color = Color.White)
                Slider(
                    value = state.volume.toFloat(),
                    onValueChange = { controller.setVolume(it.toDouble()) },
                    enabled = state.ready,
                    modifier = Modifier.width(180.dp),
                )
                // The way back, on screen rather than only on a key. In full screen the top bar is
                // hidden, and the embedded video surface can hold the keyboard focus so F11 and
                // Escape never arrive - which left closing the whole app as the only way out.
                if (isFullScreen) {
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onToggleFullScreen) { Text("⛶  Sair da tela cheia") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = closePlayer) { Text("Voltar ao app") }
                }
            }
        }
        }
    }
}

private fun formatPlaybackTime(valueMillis: Double): String {
    val totalSeconds = (valueMillis.coerceAtLeast(0.0) / 1_000.0).toLong()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
