package com.lucasserafin94.iptvburo.desktop.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.download.DISPLAY_LOCALE
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.AudioOutputMode
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
    /**
     * Whether the title playing is a favourite, or null when it cannot be one.
     *
     * Null hides the control rather than showing a heart that does nothing — a downloaded file and
     * a local playlist entry have no catalogue item to favourite.
     */
    isFavorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
    /** How subtitles are drawn. Applied when the engine starts, so it reaches the next title. */
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** Speaker layout asked of the sound card. Applied when the engine starts, like the style. */
    audioOutput: AudioOutputMode = AudioOutputMode.SYSTEM,
    /**
     * Changes the layout. The engine restarts, so playback resumes from where it was.
     *
     * Takes the position the engine has actually reached, because the caller has no way to read it
     * once this returns — see the call site.
     */
    onSelectAudioOutput: (AudioOutputMode, Long) -> Unit = { _, _ -> },
    onClose: () -> Unit,
) {
    // Keyed on the style and the audio mode as well as the request: VLC builds its text renderer
    // and its audio chain with the video chain, so a change to either only reaches the engine
    // through a fresh player.
    // Read here rather than inside `remember`: the engine's failure messages have to be in the
    // language the app is running in, and a player kept across a language change would otherwise
    // go on answering in the old one.
    val screenText = strings.shareStrings.screens
    val controller =
        remember(request, subtitleStyle, audioOutput, screenText) {
            VlcDesktopPlayer(subtitleStyle, audioOutput, text = screenText)
        }
    // Keyed on the controller, not on the request. Changing the speaker layout builds a new player
    // without changing the request, so a request-keyed snapshot survived the swap and reported the
    // dead engine's `ready` and duration for the new one — which is what left Space and the click
    // handler enabled over a picture that was not there.
    var state by remember(controller) { mutableStateOf(DesktopPlaybackSnapshot()) }

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
    DisposableEffect(isFullScreen, state.ready) {
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
                    // Space belongs here for the same reason Escape does, and it was left out.
                    //
                    // The Compose handler further down is correct and unreachable in the ordinary
                    // case: pressing space is something you do *after* clicking the picture, and
                    // clicking the picture hands focus to the embedded video surface. Two testers
                    // reported the key doing nothing, which is exactly what a focused native
                    // surface swallowing it looks like.
                    java.awt.event.KeyEvent.VK_SPACE -> {
                        if (state.ready) controller.togglePlayback()
                        state.ready
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
    var playerFocusAttached by remember { mutableStateOf(false) }
    LaunchedEffect(playerFocusAttached) {
        if (playerFocusAttached) playerFocus.requestFocus()
    }

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
            // A FocusRequester cannot be used until its modifier is attached. Requesting from a
            // plain LaunchedEffect raced the first layout and emitted "not initialized", leaving
            // F11 and Space inactive until the user clicked the player.
            .onGloballyPositioned { playerFocusAttached = true }
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
            // `key(controller)` is what makes changing the speaker layout work.
            //
            // SwingPanel calls `factory` once and then keeps that AWT component for the life of the
            // composition — it is not keyed on anything the lambda captures. Selecting a new layout
            // builds a fresh VlcDesktopPlayer (VLC fixes its audio chain at process start, so there
            // is no other way to apply it) and disposes the old one, killing the process that owned
            // the canvas on screen. Without this key, `factory` never ran again: the new player was
            // never given a video surface, so the picture went black and the clock sat at 00:00.
            //
            // Keying the subtree discards the old panel and builds the new player its own.
            key(controller) {
            SwingPanel(
                // Movement over the video surface reaches Compose only through this callback: the
                // AWT canvas consumes it, so without this the controls never knew the user was
                // still there and never hid either.
                factory = {
                    controller
                        .createComponent(
                            request = request,
                            onPointerActivity = { wakeCounter++ },
                            // Click the picture to pause, the way every other player behaves.
                            //
                            // createComponent has taken an onClick since multiview needed it, and
                            // the single player simply never passed one — so clicking the video
                            // woke the controls and did nothing else. Guarded on `ready` for the
                            // same reason the keys are: toggling before the engine reports a state
                            // is a command sent into nothing.
                            onClick = { if (state.ready) controller.togglePlayback() },
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
                                        // Guarded like the other two paths. This one toggled
                                        // unconditionally, so a space during start-up sent a
                                        // pause the engine was not yet in a state to answer.
                                        if (state.ready) controller.togglePlayback()
                                        state.ready
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
            }
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

            // Where the pointer is over the bar, whether or not a drag is happening. Only the drag
            // used to show a time, so a user hovering to find a scene saw nothing — they had to
            // commit to a drag to learn where they were pointing.
            var hoverFraction by remember { mutableStateOf<Float?>(null) }
            var barWidth by remember { mutableStateOf(0) }
            val playedFraction =
                if (state.durationMillis > 0.0) {
                    (state.positionMillis / state.durationMillis).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size -> barWidth = size.width }
                        .onPointerEvent(PointerEventType.Move) { event ->
                            val x = event.changes.firstOrNull()?.position?.x ?: return@onPointerEvent
                            hoverFraction = if (barWidth > 0) (x / barWidth).coerceIn(0f, 1f) else null
                        }
                        .onPointerEvent(PointerEventType.Exit) { hoverFraction = null },
            ) {
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
                // The drag position when dragging, otherwise wherever the pointer is. Both answer
                // "where will this land?", and a hover is how a user looks for a scene without
                // committing to a jump.
                (scrubTo ?: hoverFraction.takeIf { state.durationMillis > 0.0 })?.let { fraction ->
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
                // Scrollable, because a Row does not shrink children that carry no weight.
                //
                // Once the transport buttons, the rate, the subtitle and audio pickers, the
                // brightness readout and the volume slider are all in here, the row wants more
                // width than the window has. Without somewhere to overflow to, Compose took it out
                // of the only flexible thing present — the button labels — and broke "Sair da tela
                // cheia" one character per line, which stretched the bar to the height of the
                // screen. Scrolling keeps every control at its natural size and reachable.
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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

                // Filled and gold when the title is a favourite, outlined and grey when it is not —
                // so the heart answers "is this saved?" before it is pressed, rather than only
                // acting when it is. Absent entirely for sources that cannot be favourited.
                isFavorite?.let { favourite ->
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = onToggleFavorite) {
                        Text(
                            text = if (favourite) "♥" else "♡",
                            color = if (favourite) Color(0xFFD6A956) else Color(0xFFB8B4AC),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                // Stepped through a fixed list rather than matched against overlapping ranges: VLC
                // reports the rate back as a float, so 1.2499999 fell through every branch and the
                // button appeared to do nothing. The label is formatted, not the raw double.
                TransportButton(
                    label = "%.2fx".format(DISPLAY_LOCALE, state.playbackRate).removeSuffix("0").removeSuffix("0").removeSuffix("."),
                    enabled = state.ready,
                ) {
                    val current = PLAYBACK_RATES.minByOrNull { rate -> kotlin.math.abs(rate - state.playbackRate) }
                    val nextIndex = (PLAYBACK_RATES.indexOf(current) + 1) % PLAYBACK_RATES.size
                    controller.setPlaybackRate(PLAYBACK_RATES[nextIndex])
                }
                // Speaker layout, cycled like the rate beside it.
                //
                // The options offered depend on what the track actually carries: a 5.1 mix is not
                // offered an upmix to 5.1, because there is nothing to upmix and a control that
                // claims to add what is already there teaches people the controls mean nothing.
                //
                // Changing this restarts the engine — VLC builds the audio chain with the rest of
                // the pipeline — so the label says which layout is being asked for rather than
                // pretending the change is instant.
                TransportButton(
                    label = audioOutputLabel(audioOutput),
                    enabled = state.ready,
                ) {
                    // Two channels assumed, which is what the great majority of provider streams
                    // carry and the case the upmix exists for. Reading the real count from VLC is
                    // worth doing later; assuming stereo only ever offers one option too many,
                    // never one too few, and every option remains safe to pick.
                    val options = AudioOutputMode.optionsFor(channels = 2)
                    val next = options[(options.indexOf(audioOutput) + 1) % options.size]
                    // The live position goes with the mode, because the caller cannot get it any
                    // other way in time: the disposal checkpoint is written when the old player is
                    // torn down, which is *after* this returns, and the stored one is only updated
                    // every twelve seconds. Reading it there rewound the film by up to twelve
                    // seconds on every audio change.
                    onSelectAudioOutput(next, state.positionMillis.toLong())
                }
                // Cycles rather than opening a menu: six values, and a viewer fixing a squashed
                // picture wants to see the next one immediately, not pick from a list.
                OutlinedButton(
                    onClick = {
                        val values = PlaybackAspectRatio.entries
                        val next = values[(values.indexOf(state.aspectRatio) + 1) % values.size]
                        controller.setAspectRatio(next)
                    },
                    enabled = state.ready,
                ) {
                    Text(state.aspectRatio.label)
                }

                // Shown whenever a track exists, not only when there is a choice. A single-entry
                // menu still answers "what language is this?", and hiding the control entirely made
                // the player look as though it had no track support at all — which is how it read
                // on a film with one audio track and no subtitles.
                if (state.audioTracks.isNotEmpty()) {
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
                // Brightness before volume: a film too dark to follow is the more common complaint,
                // and a viewer reaching for it is usually mid-scene.
                // Says when it takes effect, because it cannot take effect now.
                //
                // VLC builds the adjust filter with the video chain and its control interface has
                // no command to change it afterwards — the slider moved and the picture did not,
                // which reads as a broken control rather than as a deferred setting. The subtitle
                // options carry the same limitation and say so in the same way.
                Text(
                    text = "☀ ${(state.brightness * 100).toInt()}%",
                    color = Color.White,
                )
                if (state.brightness != 1.0) {
                    // Literal, like every other label in this overlay. Introducing the strings
                    // mechanism for one line would leave the file half-translated, which is worse
                    // than consistently untranslated; the whole overlay is a separate job.
                    Text(
                        text = "vale para o próximo",
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
                Slider(
                    value = state.brightness.toFloat(),
                    onValueChange = { controller.setBrightness(it.toDouble()) },
                    valueRange = 0.5f..1.8f,
                    enabled = state.ready,
                    modifier = Modifier.width(120.dp),
                )
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
                    // Never wrapped.
                    //
                    // The controls sit in a Row that runs out of width once the volume slider and
                    // the brightness readout are in it, and Compose then breaks the label one
                    // character per line: the button became a tall vertical column of letters and
                    // pushed the whole bar to the height of the screen.
                    //
                    // maxLines alone is not enough — a single line still needs somewhere to go —
                    // so the label is short and the row is allowed to scroll instead.
                    OutlinedButton(onClick = onToggleFullScreen) {
                        Text("⛶  Sair da tela cheia", maxLines = 1, softWrap = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = closePlayer) {
                        Text("Voltar ao app", maxLines = 1, softWrap = false)
                    }
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
    return if (hours > 0) {
        "%d:%02d:%02d".format(DISPLAY_LOCALE, hours, minutes, seconds)
    } else {
        "%02d:%02d".format(DISPLAY_LOCALE, minutes, seconds)
    }
}

/**
 * Short label for the speaker layout button.
 *
 * Names the *output* rather than claiming a format: "5.1" here means "send to six speakers", and
 * the app does not pretend a stereo track became a 5.1 mix by arriving at more of them.
 */
private fun audioOutputLabel(mode: AudioOutputMode): String =
    when (mode) {
        AudioOutputMode.SYSTEM -> "Áudio: padrão"
        AudioOutputMode.STEREO -> "Áudio: 2.0"
        AudioOutputMode.SURROUND_51 -> "Áudio: 5.1"
        AudioOutputMode.SURROUND_71 -> "Áudio: 7.1"
        AudioOutputMode.HEADPHONES -> "Áudio: fone"
    }
