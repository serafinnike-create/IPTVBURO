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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.layout.layout
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import kotlinx.coroutines.delay

/** How long the pointer must rest before the controls fade in full screen. */
private const val CONTROLS_IDLE_MILLIS = 3_000L

/** The speeds the button cycles through, in order. */
private val PLAYBACK_RATES = listOf(1.0, 1.25, 1.5, 2.0, 0.75)

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
    // A single loop that watches the clock, rather than an effect restarted by each wake. Keying an
    // effect on the counter meant a wake arriving *while it was already running* was swallowed:
    // Compose only restarts on a changed key, and the canvas fires many moves in a row. That is why
    // the controls never came back in full screen no matter how much the pointer moved.
    var lastWakeAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(wakeCounter) { lastWakeAt = System.currentTimeMillis() }
    LaunchedEffect(isFullScreen) {
        while (true) {
            val idleFor = System.currentTimeMillis() - lastWakeAt
            val shouldShow = !isFullScreen || idleFor < CONTROLS_IDLE_MILLIS
            if (shouldShow != controlsVisible) {
                controlsVisible = shouldShow
                videoPanel?.let { panel -> controller.setPointerVisible(panel, shouldShow) }
            }
            delay(200)
        }
    }

    val closePlayerRef = {
        if (state.durationMillis > 0.0) {
            onCheckpoint(state.positionMillis.toLong(), state.durationMillis.toLong())
        }
        onClose()
    }

    // A global AWT listener, because focus is the whole problem here: the embedded video surface,
    // the SwingPanel and Compose all take turns owning it, and whichever holds it swallows the key.
    // This sees every key in the process before any component does, so Escape always works no
    // matter what has focus - the difference between a player you can leave and one that forces
    // the user to kill the app.
    DisposableEffect(isFullScreen) {
        val dispatcher =
            java.awt.KeyEventDispatcher { event ->
                if (event.id != java.awt.event.KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
                wakeCounter++
                when (event.keyCode) {
                    java.awt.event.KeyEvent.VK_ESCAPE -> {
                        if (isFullScreen) onToggleFullScreen() else closePlayerRef()
                        true
                    }
                    java.awt.event.KeyEvent.VK_F11 -> {
                        onToggleFullScreen()
                        true
                    }
                    else -> false
                }
            }
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(dispatcher)
        onDispose {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(dispatcher)
        }
    }

    // Likewise for the pointer: a move anywhere in the window wakes the controls, so hovering the
    // strip where the bar belongs brings it back even when the video surface is eating the events.
    DisposableEffect(Unit) {
        val awtListener =
            java.awt.event.AWTEventListener { wakeCounter++ }
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
            awtListener,
            java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK,
        )
        onDispose { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(awtListener) }
    }

    // Nothing requested focus, so key events never reached the handler and F11 did nothing at all.
    val playerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playerFocus.requestFocus() } }

    val closePlayer = closePlayerRef
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
                    controller
                        .createComponent(
                            request = request,
                            onPointerActivity = { wakeCounter++ },
                            // The same three keys the Compose handler owns, wired to the canvas so
                            // they keep working once it holds the focus - which is exactly when the
                            // user needs Escape and has no visible control to reach for.
                            onKey = { keyCode ->
                                wakeCounter++
                                when (keyCode) {
                                    java.awt.event.KeyEvent.VK_F11 -> {
                                        onToggleFullScreen()
                                        true
                                    }
                                    java.awt.event.KeyEvent.VK_ESCAPE -> {
                                        if (isFullScreen) onToggleFullScreen() else closePlayer()
                                        true
                                    }
                                    java.awt.event.KeyEvent.VK_SPACE -> {
                                        controller.togglePlayback()
                                        true
                                    }
                                    // Everything else still counts as activity, so any key brings
                                    // the controls back even when it does nothing else.
                                    else -> false
                                }
                            },
                        ).also { panel -> videoPanel = panel }
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
            // A translucent strip over the picture rather than a bar beneath it. At 480x300 every
            // row of pixels the chrome takes is a row the film loses, and a solid slab across the
            // bottom was a fifth of the window.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xB30A0C0F)),
                            ),
                        ).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(
                    onClick = controller::togglePlayback,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = if (state.playing) "❚❚" else "▶",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onToggleCompact,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = "Voltar ao app",
                        color = BuroColors.Primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        } else if (!isFullScreen || controlsVisible) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC0A0C0F), Color(0xE60A0C0F)),
                    ),
                ).padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            // Where the thumb is being dragged to, before it is committed. Seeking on every pixel
            // of the drag flooded VLC with commands and made the picture stutter its way across the
            // film; the seek now happens once, when the thumb is released.
            var scrubTo by remember { mutableStateOf<Float?>(null) }
            val playedFraction =
                if (state.durationMillis > 0.0) {
                    (state.positionMillis / state.durationMillis).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }

            Box(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = scrubTo ?: playedFraction,
                    onValueChange = { scrubTo = it },
                    onValueChangeFinished = {
                        scrubTo?.let { fraction -> controller.seekToFraction(fraction.toDouble()) }
                        scrubTo = null
                    },
                    enabled = state.ready && state.durationMillis > 0.0,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The time under the thumb while dragging. A frame preview is not possible here -
                // VLC's control interface cannot render an arbitrary frame without interrupting
                // playback - but the timestamp answers the same question: where will this land?
                scrubTo?.let { fraction ->
                    val targetMillis = state.durationMillis * fraction
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .padding(bottom = 34.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .scrubberOffset(fraction)
                                    .background(
                                        color = Color(0xF00A0C0F),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    ).padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formatPlaybackTime(targetMillis),
                                    color = BuroColors.Primary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                // How far this jumps, so a small nudge is distinguishable from
                                // landing twenty minutes away.
                                val deltaSeconds = ((targetMillis - state.positionMillis) / 1_000.0).toInt()
                                Text(
                                    text =
                                        if (deltaSeconds >= 0) "+${deltaSeconds}s" else "${deltaSeconds}s",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton("-10 s", state.ready) { controller.seekBy(-10_000.0) }
                TransportButton(
                    label = if (state.playing) "Pausar" else "Reproduzir",
                    enabled = state.ready,
                    emphasised = true,
                    onClick = controller::togglePlayback,
                )
                TransportButton("+30 s", state.ready) { controller.seekBy(30_000.0) }
                Text(
                    "${formatPlaybackTime(state.positionMillis)} / ${formatPlaybackTime(state.durationMillis)}",
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                // Stepped through a fixed list rather than matched against overlapping ranges: VLC
                // reports the rate back as a float, so 1.2499999 fell through every branch and the
                // button appeared to do nothing. The label is formatted, not the raw double.
                TransportButton(
                    label = "%.2fx".format(state.playbackRate).removeSuffix("0").removeSuffix("0").removeSuffix("."),
                    enabled = state.ready,
                ) {
                    val current = PLAYBACK_RATES.minByOrNull { rate -> kotlin.math.abs(rate - state.playbackRate) }
                    val nextIndex = (PLAYBACK_RATES.indexOf(current) + 1) % PLAYBACK_RATES.size
                    controller.setPlaybackRate(PLAYBACK_RATES[nextIndex])
                }
                // Audio and subtitle pickers, only when the title actually carries a choice: a menu
                // with one entry is a control that cannot do anything.
                if (state.audioTracks.size > 1) {
                    TrackPicker(
                        label = "Áudio",
                        tracks = state.audioTracks,
                        activeId = state.activeAudioTrackId,
                        onSelect = controller::selectAudioTrack,
                    )
                }
                if (state.subtitleTracks.isNotEmpty()) {
                    TrackPicker(
                        label = "Legendas",
                        tracks = state.subtitleTracks,
                        activeId = state.activeSubtitleTrackId,
                        onSelect = controller::selectSubtitleTrack,
                    )
                }
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

/**
 * A transport control that sits over the picture rather than competing with it.
 *
 * Outlined and translucent by default; only the play/pause button carries the accent, because it is
 * the one the eye should find without looking. Solid gold on all three made the bar shout louder
 * than the film behind it.
 */
@Composable
private fun TransportButton(
    label: String,
    enabled: Boolean,
    emphasised: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = androidx.compose.foundation.shape.CircleShape,
        colors =
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor =
                    if (emphasised) BuroColors.Primary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                contentColor = if (emphasised) BuroColors.Primary else Color.White.copy(alpha = 0.85f),
            ),
    ) {
        Text(label, maxLines = 1)
    }
}

/**
 * Picks one track from the ones the title carries.
 *
 * Labelled with the active track rather than a fixed word, so the bar answers "what am I hearing?"
 * without being opened.
 */
@Composable
private fun TrackPicker(
    label: String,
    tracks: List<MediaTrack>,
    activeId: Int?,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = tracks.firstOrNull { it.id == activeId }

    Box {
        TransportButton(
            label = active?.label?.take(14) ?: label,
            enabled = true,
        ) { expanded = true }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BuroColors.SurfaceRaised),
        ) {
            tracks.forEach { track ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            text = track.label,
                            color = if (track.id == activeId) BuroColors.Primary else BuroColors.Text,
                        )
                    },
                    onClick = {
                        onSelect(track.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Positions the scrub label under the thumb, clamped so it never runs off either edge.
 *
 * Laid out rather than offset by a fixed amount, because the label's own width is not known until
 * the timestamp inside it is measured.
 */
private fun Modifier.scrubberOffset(fraction: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            val travel = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
            val centred = (constraints.maxWidth * fraction - placeable.width / 2f).toInt()
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(centred.coerceIn(0, travel), 0)
            }
        },
    )

private fun formatPlaybackTime(valueMillis: Double): String {
    val totalSeconds = (valueMillis.coerceAtLeast(0.0) / 1_000.0).toLong()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
