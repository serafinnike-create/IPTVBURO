package com.lucasserafin94.iptvburo.desktop.playback

import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.download.DISPLAY_LOCALE
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroIconButton
import com.lucasserafin94.iptvburo.desktop.ui.BuroIconSize
import com.lucasserafin94.iptvburo.desktop.ui.BuroIcons
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.domain.model.AudioOutputMode
import com.lucasserafin94.iptvburo.domain.model.PlaybackBuffering
import java.awt.event.KeyEvent as AwtKey
import kotlinx.coroutines.delay

/** How long the pointer must rest before the controls fade in full screen. */
private const val CONTROLS_IDLE_MILLIS = 3_000L

/** How long a one-line notice ("Volume 60%", "+10 s") stays in the bar. */
private const val NOTICE_MILLIS = 1_400L

/** How long before the end the next episode is offered — roughly where the credits begin. */
private const val UP_NEXT_LEAD_MILLIS = 30_000.0

/** The speeds the button cycles through, in order. */
private val PLAYBACK_RATES = listOf(1.0, 1.25, 1.5, 2.0, 0.75)

/** Volume step for the arrow keys and the wheel. */
private const val VOLUME_STEP = 0.05

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopPlayerOverlay(
    request: DesktopPlaybackRequest,
    /**
     * The same title from another subscription, or null when there is no other to try.
     *
     * Takes how many have already been tried, so a second failure moves on rather than offering
     * the same dead stream again.
     */
    onFindAlternative: (DesktopPlaybackRequest, Int) -> DesktopPlaybackRequest? = { _, _ -> null },
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
    /** The language the player's own words are in. */
    language: DesktopLanguage = DesktopLanguage.PORTUGUESE_BRAZIL,
    /** The next episode's title, or null for anything that is not a series episode or the last. */
    nextEpisodeTitle: String? = null,
    onPlayNext: () -> Unit = {},
    onClose: () -> Unit,
) {
    val text = remember(language) { PlayerText.of(language) }
    // Keyed on the style and the audio mode as well as the request: VLC builds its text renderer
    // and its audio chain with the video chain, so a change to either only reaches the engine
    // through a fresh player.
    // Read here rather than inside `remember`: the engine's failure messages have to be in the
    // language the app is running in, and a player kept across a language change would otherwise
    // go on answering in the old one.
    val screenText = strings.shareStrings.screens
    val controller =
        remember(request, subtitleStyle, audioOutput, screenText) {
            VlcDesktopPlayer(
                subtitleStyle,
                audioOutput,
                text = screenText,
                // Two minutes ahead for a film or an episode, so a connection that drops and comes
                // back never reaches the picture. A live channel keeps its small reservoir: what
                // has not been broadcast cannot be read early, and buffering it heavily only starts
                // it later and leaves it behind.
                //
                // Set at construction because VLC fixes its caching when the process launches, and
                // this player is already rebuilt per request — so the size always matches what is
                // about to play.
                networkCachingMillis = PlaybackBuffering.millisFor(isLive = request.isLive),
            )
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

    // Live when the request says so, or when the engine reports no length once it is running: a
    // channel opened from a plain playlist does not know it is live until VLC tells it.
    val isLive = request.isLive || (state.ready && state.durationMillis <= 0.0)

    // The next episode, offered over the credits and played when this one ends. Cancelling holds
    // for this title only, so the offer comes back on the next one.
    var upNextCancelled by remember(request) { mutableStateOf(false) }
    val remainingMillis = (state.durationMillis - state.positionMillis).coerceAtLeast(0.0)
    val upNextOffered =
        nextEpisodeTitle != null && !upNextCancelled && !isLive && state.durationMillis > 0.0 &&
            (state.ended || (state.ready && remainingMillis <= UP_NEXT_LEAD_MILLIS))
    LaunchedEffect(state.ended) {
        if (!state.ended || state.durationMillis <= 0.0) return@LaunchedEffect
        onEnded(state.durationMillis.toLong())
        if (nextEpisodeTitle != null && !upNextCancelled) onPlayNext()
    }

    // A one-line notice in the bar, for what a key or the wheel just did. The bar is drawn outside
    // the video surface, which a native window covers completely, so this is where it can be seen.
    var notice by remember(request) { mutableStateOf<String?>(null) }
    var noticeSerial by remember(request) { mutableIntStateOf(0) }
    LaunchedEffect(noticeSerial) {
        if (notice == null) return@LaunchedEffect
        delay(NOTICE_MILLIS)
        notice = null
    }

    // Controls reveal on pointer movement and hide again after a pause, the behaviour every video
    // player uses. Only meaningful in full screen; windowed mode keeps them pinned.
    var controlsVisible by remember(request) { mutableStateOf(true) }
    var videoPanel by remember(controller) { mutableStateOf<javax.swing.JPanel?>(null) }
    var wakeCounter by remember { mutableIntStateOf(0) }
    // Open menus and the pointer resting on the bar keep it up. Without this a track menu opened
    // in full screen vanished three seconds later with the bar that held it.
    var menusOpen by remember(request) { mutableIntStateOf(0) }
    var pointerOverBar by remember(request) { mutableStateOf(false) }
    // A single loop that watches the clock, rather than an effect restarted by each wake. Keying an
    // effect on the counter meant a wake arriving *while it was already running* was swallowed:
    // Compose only restarts on a changed key, and the canvas fires many moves in a row. That is why
    // the controls never came back in full screen no matter how much the pointer moved.
    var lastWakeAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(wakeCounter) { lastWakeAt = System.currentTimeMillis() }
    val keepBarUp by rememberUpdatedState(
        menusOpen > 0 || pointerOverBar || upNextOffered || state.reconnecting || state.errorMessage != null,
    )
    LaunchedEffect(isFullScreen, controller) {
        while (true) {
            val idleFor = System.currentTimeMillis() - lastWakeAt
            val shouldShow = !isFullScreen || idleFor < CONTROLS_IDLE_MILLIS || keepBarUp
            if (shouldShow != controlsVisible) {
                controlsVisible = shouldShow
                videoPanel?.let { panel -> controller.setPointerVisible(panel, shouldShow) }
            }
            delay(200)
        }
    }

    val showNotice: (String) -> Unit = { message ->
        notice = message
        noticeSerial++
        wakeCounter++
    }

    val closePlayer = {
        if (state.durationMillis > 0.0) {
            onCheckpoint(state.positionMillis.toLong(), state.durationMillis.toLong())
        }
        onClose()
    }

    // Mute keeps the level it silenced, so un-muting returns to it rather than to a default.
    var volumeBeforeMute by remember(request) { mutableStateOf<Double?>(null) }
    val setVolume: (Double) -> Unit = { value ->
        val safe = value.coerceIn(0.0, 1.0)
        volumeBeforeMute = null
        controller.setVolume(safe)
        showNotice("${text.volume} ${(safe * 100).toInt()}%")
    }
    val toggleMute: () -> Unit = {
        val restore = volumeBeforeMute
        if (restore != null) {
            volumeBeforeMute = null
            controller.setVolume(restore)
            showNotice("${text.volume} ${(restore * 100).toInt()}%")
        } else {
            volumeBeforeMute = state.volume
            controller.setVolume(0.0, remember = false)
            showNotice(text.muted)
        }
    }
    val seekBy: (Double) -> Unit = { millis ->
        if (state.ready && !isLive) {
            controller.seekBy(millis)
            val seconds = (millis / 1_000).toInt()
            showNotice(if (seconds >= 0) "+$seconds s" else "−${-seconds} s")
        }
    }
    val togglePlayback: () -> Unit = {
        if (state.ready) controller.togglePlayback()
    }
    val cycleSpeed: () -> Unit = {
        val current = PLAYBACK_RATES.minByOrNull { rate -> kotlin.math.abs(rate - state.playbackRate) }
        val next = PLAYBACK_RATES[(PLAYBACK_RATES.indexOf(current) + 1) % PLAYBACK_RATES.size]
        controller.setPlaybackRate(next)
        showNotice("${text.speed} ${formatRate(next)}")
    }
    val cycleAspect: () -> Unit = {
        val values = PlaybackAspectRatio.entries
        val next = values[(values.indexOf(state.aspectRatio) + 1) % values.size]
        controller.setAspectRatio(next)
        showNotice("${text.aspect}: ${text.aspectLabel(next)}")
    }

    /**
     * Every key the player understands, in one place.
     *
     * Three paths deliver keys — a process-wide dispatcher, Compose, and the video canvas once it
     * holds focus — and each used to carry its own copy of three keys, so they drifted apart. They
     * all call this now. Returns whether the key was used.
     */
    val handleKey: (Int, Boolean) -> Boolean = { code, shift ->
        wakeCounter++
        when (code) {
            AwtKey.VK_ESCAPE ->
                // An open menu closes itself on Escape; taking the key here closed the whole player
                // instead, with the menu still open over nothing.
                if (menusOpen > 0) {
                    false
                } else {
                    if (isFullScreen) onToggleFullScreen() else closePlayer()
                    true
                }
            AwtKey.VK_F11, AwtKey.VK_F -> {
                if (!isCompact) onToggleFullScreen()
                true
            }
            AwtKey.VK_SPACE, AwtKey.VK_K -> {
                togglePlayback()
                state.ready
            }
            AwtKey.VK_LEFT -> {
                seekBy(if (shift) -60_000.0 else -10_000.0)
                !isLive
            }
            AwtKey.VK_RIGHT -> {
                seekBy(if (shift) 60_000.0 else 10_000.0)
                !isLive
            }
            AwtKey.VK_UP -> {
                setVolume((volumeBeforeMute ?: state.volume) + VOLUME_STEP)
                true
            }
            AwtKey.VK_DOWN -> {
                setVolume((volumeBeforeMute ?: state.volume) - VOLUME_STEP)
                true
            }
            AwtKey.VK_M -> {
                toggleMute()
                true
            }
            AwtKey.VK_N -> {
                if (nextEpisodeTitle != null) onPlayNext()
                nextEpisodeTitle != null
            }
            AwtKey.VK_HOME -> {
                if (state.ready && !isLive) controller.seekToFraction(0.0)
                !isLive
            }
            else ->
                // 1–9 jump to that tenth of the title, as in most players.
                if (code in AwtKey.VK_1..AwtKey.VK_9 && state.ready && !isLive && state.durationMillis > 0.0) {
                    controller.seekToFraction((code - AwtKey.VK_0) / 10.0)
                    true
                } else {
                    false
                }
        }
    }

    // A global AWT listener, because focus is the whole problem here: the embedded video surface,
    // the SwingPanel and Compose all take turns owning it, and whichever holds it swallows the key.
    // This sees every key in the process before any component does, so Escape always works no
    // matter what has focus - the difference between a player you can leave and one that forces
    // the user to kill the app.
    val currentHandleKey by rememberUpdatedState(handleKey)
    DisposableEffect(controller) {
        val dispatcher =
            java.awt.KeyEventDispatcher { event ->
                if (event.id != java.awt.event.KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
                currentHandleKey(event.keyCode, event.isShiftDown)
            }
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        onDispose {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
        }
    }

    // Likewise for the pointer: a move anywhere in the window wakes the controls, so hovering the
    // strip where the bar belongs brings it back even when the video surface is eating the events.
    DisposableEffect(Unit) {
        val awtListener = java.awt.event.AWTEventListener { wakeCounter++ }
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(awtListener, java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK)
        onDispose { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(awtListener) }
    }

    // Keyed on the request, so each title asks for focus again. Remembered once, the next title
    // started without it and its keys went nowhere until somebody clicked.
    val playerFocus = remember(request) { FocusRequester() }
    var playerFocusAttached by remember(request) { mutableStateOf(false) }
    LaunchedEffect(playerFocusAttached) {
        if (playerFocusAttached) runCatching { playerFocus.requestFocus() }
    }

    // A stream that fails is asked for from another subscription before the viewer is told
    // anything. Counted, so a second failure moves on to the next list rather than offering the
    // same dead stream for ever; once every list has been tried the error is the honest answer.
    // Decided in an effect, not during composition: looking for an alternative builds a URL, and
    // doing that on every frame an error was showing was work the screen repeated four times a
    // second.
    var alternativesTried by remember(request) { mutableIntStateOf(0) }
    var lastTried by remember(request) { mutableStateOf(request) }
    var showError by remember(request) { mutableStateOf(false) }
    LaunchedEffect(state.errorMessage, request) {
        if (state.errorMessage == null) {
            showError = false
            return@LaunchedEffect
        }
        val next = onFindAlternative(request, alternativesTried + 1)
        if (next == null) {
            showError = true
            return@LaunchedEffect
        }
        alternativesTried += 1
        lastTried = next
        controller.retry(next)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPointerEvent(PointerEventType.Move) { wakeCounter++ }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent as? java.awt.event.KeyEvent ?: return@onPreviewKeyEvent false
                handleKey(native.keyCode, event.isShiftPressed)
            }
            .focusRequester(playerFocus)
            // A FocusRequester cannot be used until its modifier is attached. Requesting from a
            // plain LaunchedEffect raced the first layout and emitted "not initialized", leaving
            // F11 and Space inactive until the user clicked the player.
            .onGloballyPositioned { playerFocusAttached = true }
            .focusable(),
    ) {
        // Hidden in full screen so the picture is not letterboxed, and in the compact window, where
        // a 64 dp bar left the video no height at all.
        if (!isFullScreen && !isCompact) {
            TopBar(
                title = request.title,
                isLive = isLive,
                text = text,
                onBack = closePlayer,
                onMiniPlayer = onToggleCompact,
                onFullScreen = onToggleFullScreen,
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // `key(controller)` is what makes changing the speaker layout work.
            //
            // SwingPanel calls `factory` once and then keeps that AWT component for the life of the
            // composition — it is not keyed on anything the lambda captures. Selecting a new layout
            // builds a fresh VlcDesktopPlayer (VLC fixes its audio chain at process start, so there
            // is no other way to apply it) and disposes the old one, killing the process that owned
            // the canvas on screen. Keying the subtree discards the old panel and builds the new
            // player its own.
            key(controller) {
                SwingPanel(
                    factory = {
                        controller
                            .createComponent(
                                request = request,
                                onPointerActivity = { wakeCounter++ },
                                // Click the picture to pause; double-click for full screen. The
                                // canvas waits out the double-click interval before pausing, so a
                                // double click no longer pauses and resumes on the way.
                                onClick = { togglePlayback() },
                                onDoubleClick = { if (!isCompact) onToggleFullScreen() },
                                onWheel = { notches ->
                                    setVolume((volumeBeforeMute ?: controller.snapshot().volume) - notches * VOLUME_STEP)
                                },
                                // The same keys, wired to the canvas so they keep working once it
                                // holds the focus.
                                onKey = { keyCode -> currentHandleKey(keyCode, false) },
                            ).also { panel -> videoPanel = panel }
                    },
                    update = { panel ->
                        controller.setPointerVisible(panel, controlsVisible || !isFullScreen)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (isCompact) {
            CompactStrip(
                playing = state.playing,
                text = text,
                onTogglePlayback = togglePlayback,
                onBackToApp = onToggleCompact,
            )
        } else if (!isFullScreen || controlsVisible) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC0A0C0F), Color(0xF20A0C0F)),
                        ),
                    )
                    .onPointerEvent(PointerEventType.Enter) { pointerOverBar = true }
                    .onPointerEvent(PointerEventType.Exit) { pointerOverBar = false }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                // What is happening, in words, above the controls: this is the one place over the
                // video that can always be seen. An error, a reconnect, the loading state, the next
                // episode, or what the last key did.
                StatusLine(
                    state = state,
                    showError = showError,
                    notice = notice,
                    upNextOffered = upNextOffered,
                    nextEpisodeTitle = nextEpisodeTitle,
                    secondsLeft = (remainingMillis / 1_000).toInt(),
                    text = text,
                    onRetry = {
                        showError = false
                        controller.retry(lastTried)
                    },
                    onPlayNext = onPlayNext,
                    onCancelNext = { upNextCancelled = true },
                )
                if (!isLive) {
                    SeekBar(state = state, onSeek = controller::seekToFraction)
                }
                ControlsRow(
                    state = state,
                    isLive = isLive,
                    isFullScreen = isFullScreen,
                    isFavorite = isFavorite,
                    muted = volumeBeforeMute != null,
                    audioOutput = audioOutput,
                    hasNext = nextEpisodeTitle != null,
                    text = text,
                    onTogglePlayback = togglePlayback,
                    onSeekBy = seekBy,
                    onToggleFavorite = onToggleFavorite,
                    onToggleMute = toggleMute,
                    onVolume = { value ->
                        volumeBeforeMute = null
                        controller.setVolume(value.toDouble())
                    },
                    onCycleSpeed = cycleSpeed,
                    onCycleAspect = cycleAspect,
                    onCycleAudioOutput = {
                        // Two channels assumed, which is what the great majority of provider streams
                        // carry. Assuming stereo only ever offers one option too many, never one too
                        // few, and every option remains safe to pick.
                        val options = AudioOutputMode.optionsFor(channels = 2)
                        val next = options[(options.indexOf(audioOutput) + 1) % options.size]
                        // The live position goes with the mode, because the disposal checkpoint is
                        // written after this returns and the stored one is up to twelve seconds old.
                        onSelectAudioOutput(next, state.positionMillis.toLong())
                    },
                    onBrightness = { controller.setBrightness(it.toDouble()) },
                    onSelectAudio = controller::selectAudioTrack,
                    onSelectSubtitle = controller::selectSubtitleTrack,
                    onMenuOpenChange = { open -> menusOpen = (menusOpen + if (open) 1 else -1).coerceAtLeast(0) },
                    onPlayNext = onPlayNext,
                    onMiniPlayer = onToggleCompact,
                    onFullScreen = onToggleFullScreen,
                    onClose = closePlayer,
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    isLive: Boolean,
    text: PlayerText,
    onBack: () -> Unit,
    onMiniPlayer: () -> Unit,
    onFullScreen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuroIconButton(icon = BuroIcons.Back, label = text.back, shortcut = "Esc", onClick = onBack)
        Spacer(Modifier.width(8.dp))
        if (isLive) {
            LiveBadge(text.live)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BuroIconButton(icon = BuroIcons.MiniPlayer, label = text.miniPlayer, onClick = onMiniPlayer)
        BuroIconButton(icon = BuroIcons.FullScreen, label = text.fullScreen, shortcut = "F", onClick = onFullScreen)
    }
}

@Composable
private fun LiveBadge(label: String) {
    Row(
        modifier =
            Modifier
                .background(Color(0xFFD64545), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BuroIcons.Live, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/**
 * The line above the controls: an error with its retry, a reconnect, loading, the next episode, or
 * the last notice — in that order of importance, one at a time.
 */
@Composable
internal fun StatusLine(
    state: DesktopPlaybackSnapshot,
    showError: Boolean,
    notice: String?,
    upNextOffered: Boolean,
    nextEpisodeTitle: String?,
    secondsLeft: Int,
    text: PlayerText,
    onRetry: () -> Unit,
    onPlayNext: () -> Unit,
    onCancelNext: () -> Unit,
) {
    val message = state.errorMessage
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            showError && message != null -> {
                Text(message, color = Color(0xFFFF8A80), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = BuroColors.Primary, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) { Text(text.retry) }
            }
            state.reconnecting -> {
                CircularProgressIndicator(color = BuroColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(text.reconnecting, color = Color.White)
            }
            state.loading && message == null -> {
                CircularProgressIndicator(color = BuroColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(text.loading, color = Color.White.copy(alpha = 0.85f))
            }
            upNextOffered && nextEpisodeTitle != null -> {
                Icon(BuroIcons.Next, contentDescription = null, tint = BuroColors.Primary, modifier = Modifier.size(BuroIconSize.Small))
                Text(
                    text = "${text.nextEpisodeIn.format(DISPLAY_LOCALE, secondsLeft.coerceAtLeast(0))} · $nextEpisodeTitle",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Button(
                    onClick = onPlayNext,
                    colors = ButtonDefaults.buttonColors(containerColor = BuroColors.Primary, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) { Text(text.playNow) }
                TextButton(onClick = onCancelNext) { Text(text.cancel, color = Color.White.copy(alpha = 0.8f)) }
            }
            notice != null -> Text(notice, color = BuroColors.Primary, fontWeight = FontWeight.SemiBold)
            else -> Unit
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SeekBar(
    state: DesktopPlaybackSnapshot,
    onSeek: (Double) -> Unit,
) {
    // Where the thumb is being dragged to, before it is committed. Seeking on every pixel of the
    // drag flooded VLC with commands; the seek happens once, when the thumb is released.
    var scrubTo by remember { mutableStateOf<Float?>(null) }
    // Where the pointer is over the bar, so hovering shows where a click would land.
    var hoverFraction by remember { mutableStateOf<Float?>(null) }
    var barWidth by remember { mutableIntStateOf(0) }
    val playedFraction =
        if (state.durationMillis > 0.0) (state.positionMillis / state.durationMillis).toFloat().coerceIn(0f, 1f) else 0f

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatPlaybackTime(state.positionMillis), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(12.dp))
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .onSizeChanged { size -> barWidth = size.width }
                    .onPointerEvent(PointerEventType.Move) { event ->
                        val x = event.changes.firstOrNull()?.position?.x ?: return@onPointerEvent
                        hoverFraction = if (barWidth > 0) (x / barWidth).coerceIn(0f, 1f) else null
                    }
                    .onPointerEvent(PointerEventType.Exit) { hoverFraction = null },
        ) {
            ThinSlider(
                value = scrubTo ?: playedFraction,
                onValueChange = { scrubTo = it },
                onValueChangeFinished = {
                    scrubTo?.let { fraction -> onSeek(fraction.toDouble()) }
                    scrubTo = null
                },
                enabled = state.ready && state.durationMillis > 0.0,
                accent = BuroColors.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            // The time under the pointer or the thumb: VLC's control interface cannot render an
            // arbitrary frame without interrupting playback, but the timestamp answers the same
            // question — where will this land?
            (scrubTo ?: hoverFraction.takeIf { state.durationMillis > 0.0 })?.let { fraction ->
                val targetMillis = state.durationMillis * fraction
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(bottom = 34.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .scrubberOffset(fraction)
                                .background(Color(0xF00A0C0F), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatPlaybackTime(targetMillis),
                                color = BuroColors.Primary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            val deltaSeconds = ((targetMillis - state.positionMillis) / 1_000.0).toInt()
                            Text(
                                text = if (deltaSeconds >= 0) "+${deltaSeconds}s" else "${deltaSeconds}s",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(formatPlaybackTime(state.durationMillis), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The transport row: playback on the left, what is playing in the middle, how it plays on the
 * right. Every control is an icon with a tooltip naming it and its key; the rarely used ones —
 * speed, picture shape, speaker layout, brightness — sit in one settings menu, which is what lets
 * the row fit without scrolling sideways.
 */
@Composable
internal fun ControlsRow(
    state: DesktopPlaybackSnapshot,
    isLive: Boolean,
    isFullScreen: Boolean,
    isFavorite: Boolean?,
    muted: Boolean,
    audioOutput: AudioOutputMode,
    hasNext: Boolean,
    text: PlayerText,
    onTogglePlayback: () -> Unit,
    onSeekBy: (Double) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMute: () -> Unit,
    onVolume: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleAspect: () -> Unit,
    onCycleAudioOutput: () -> Unit,
    onBrightness: (Float) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
    onPlayNext: () -> Unit,
    onMiniPlayer: () -> Unit,
    onFullScreen: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!isLive) {
            BuroIconButton(BuroIcons.Rewind10, text.rewind, { onSeekBy(-10_000.0) }, enabled = state.ready, shortcut = "←")
        }
        BuroIconButton(
            icon = if (state.playing) BuroIcons.Pause else BuroIcons.Play,
            label = if (state.playing) text.pause else text.play,
            onClick = onTogglePlayback,
            enabled = state.ready,
            shortcut = text.spaceKey,
            emphasised = true,
        )
        if (!isLive) {
            BuroIconButton(BuroIcons.Forward30, text.forward, { onSeekBy(30_000.0) }, enabled = state.ready, shortcut = "→")
        }
        if (hasNext) {
            BuroIconButton(BuroIcons.Next, text.nextEpisode, onPlayNext, shortcut = "N")
        }
        Spacer(Modifier.width(8.dp))
        BuroIconButton(
            icon = if (muted || state.volume <= 0.0) BuroIcons.VolumeOff else if (state.volume < 0.5) BuroIcons.VolumeLow else BuroIcons.VolumeHigh,
            label = if (muted) text.unmute else text.mute,
            onClick = onToggleMute,
            shortcut = "M",
        )
        ThinSlider(
            value = if (muted) 0f else state.volume.toFloat(),
            onValueChange = onVolume,
            enabled = state.ready,
            accent = Color.White,
            modifier = Modifier.width(110.dp),
        )
        if (isLive) {
            Spacer(Modifier.width(10.dp))
            LiveBadge(text.live)
        }
        isFavorite?.let { favourite ->
            Spacer(Modifier.width(6.dp))
            BuroIconButton(
                icon = if (favourite) BuroIcons.Favorite else BuroIcons.NotFavorite,
                label = if (favourite) text.unfavorite else text.favorite,
                onClick = onToggleFavorite,
                tint = if (favourite) BuroColors.Primary else Color.White.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.weight(1f))

        // Shown whenever a track exists, not only when there is a choice: a single-entry menu still
        // answers "what language is this?".
        if (state.subtitleTracks.isNotEmpty()) {
            TrackMenu(
                icon = BuroIcons.Subtitles,
                label = text.subtitles,
                tracks = state.subtitleTracks,
                activeId = state.activeSubtitleTrackId,
                onSelect = onSelectSubtitle,
                onOpenChange = onMenuOpenChange,
            )
        }
        if (state.audioTracks.isNotEmpty()) {
            TrackMenu(
                icon = BuroIcons.Audio,
                label = text.audio,
                tracks = state.audioTracks,
                activeId = state.activeAudioTrackId,
                onSelect = onSelectAudio,
                onOpenChange = onMenuOpenChange,
            )
        }
        SettingsMenu(
            state = state,
            isLive = isLive,
            audioOutput = audioOutput,
            text = text,
            onCycleSpeed = onCycleSpeed,
            onCycleAspect = onCycleAspect,
            onCycleAudioOutput = onCycleAudioOutput,
            onBrightness = onBrightness,
            onOpenChange = onMenuOpenChange,
        )
        BuroIconButton(BuroIcons.MiniPlayer, text.miniPlayer, onMiniPlayer)
        BuroIconButton(
            icon = if (isFullScreen) BuroIcons.ExitFullScreen else BuroIcons.FullScreen,
            label = if (isFullScreen) text.exitFullScreen else text.fullScreen,
            onClick = onFullScreen,
            shortcut = "F",
        )
        // The way back, on screen rather than only on a key. In full screen the top bar is hidden,
        // and the embedded video surface can hold the keyboard focus so Escape never arrives.
        if (isFullScreen) {
            BuroIconButton(BuroIcons.Close, text.backToApp, onClose)
        }
    }
}

/**
 * Picks one track from the ones the title carries. The tooltip names the active track, so the bar
 * answers "what am I hearing?" without being opened.
 */
@Composable
private fun TrackMenu(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tracks: List<MediaTrack>,
    activeId: Int?,
    onSelect: (Int) -> Unit,
    onOpenChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = tracks.firstOrNull { it.id == activeId }
    val setExpanded: (Boolean) -> Unit = { value ->
        if (value != expanded) {
            expanded = value
            onOpenChange(value)
        }
    }
    DisposableEffect(Unit) { onDispose { if (expanded) onOpenChange(false) } }
    Box {
        BuroIconButton(
            icon = icon,
            label = active?.label?.let { "$label: $it" } ?: label,
            onClick = { setExpanded(true) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            modifier = Modifier.background(BuroColors.SurfaceRaised),
        ) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = {
                        Text(track.label, color = if (track.id == activeId) BuroColors.Primary else BuroColors.Text)
                    },
                    leadingIcon = {
                        if (track.id == activeId) {
                            Icon(BuroIcons.Check, contentDescription = null, tint = BuroColors.Primary)
                        } else {
                            Spacer(Modifier.size(BuroIconSize.Medium))
                        }
                    },
                    onClick = {
                        onSelect(track.id)
                        setExpanded(false)
                    },
                )
            }
        }
    }
}

/** Speed, picture shape, speaker layout and brightness: set once, rarely changed mid-film. */
@Composable
private fun SettingsMenu(
    state: DesktopPlaybackSnapshot,
    isLive: Boolean,
    audioOutput: AudioOutputMode,
    text: PlayerText,
    onCycleSpeed: () -> Unit,
    onCycleAspect: () -> Unit,
    onCycleAudioOutput: () -> Unit,
    onBrightness: (Float) -> Unit,
    onOpenChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val setExpanded: (Boolean) -> Unit = { value ->
        if (value != expanded) {
            expanded = value
            onOpenChange(value)
        }
    }
    DisposableEffect(Unit) { onDispose { if (expanded) onOpenChange(false) } }
    Box {
        BuroIconButton(icon = BuroIcons.Settings, label = text.settings, onClick = { setExpanded(true) })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            modifier = Modifier.background(BuroColors.SurfaceRaised),
        ) {
            // Each row cycles in place and the menu stays open, so the effect can be watched and
            // the next value tried without reopening anything.
            if (!isLive) {
                SettingsRow(BuroIcons.Speed, text.speed, formatRate(state.playbackRate), enabled = state.ready, onClick = onCycleSpeed)
            }
            SettingsRow(BuroIcons.Aspect, text.aspect, text.aspectLabel(state.aspectRatio), enabled = state.ready, onClick = onCycleAspect)
            SettingsRow(BuroIcons.Speakers, text.audio, text.audioOutputLabel(audioOutput), enabled = state.ready, onClick = onCycleAudioOutput)
            Column(Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(BuroIcons.Brightness, contentDescription = null, tint = BuroColors.TextMuted, modifier = Modifier.size(BuroIconSize.Small))
                    Spacer(Modifier.width(12.dp))
                    Text(text.brightness, color = BuroColors.Text, modifier = Modifier.weight(1f))
                    Text("${(state.brightness * 100).toInt()}%", color = BuroColors.TextMuted)
                }
                ThinSlider(
                    value = state.brightness.toFloat(),
                    onValueChange = onBrightness,
                    valueRange = 0.5f..1.8f,
                    accent = BuroColors.Primary,
                )
                // Says when it takes effect, because it cannot take effect now: VLC builds the
                // adjust filter with the video chain and its control interface cannot change it.
                Text(text.brightnessNextTitle, color = BuroColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(Modifier.width(240.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = BuroColors.Text, modifier = Modifier.weight(1f))
                Text(value, color = BuroColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = BuroColors.TextMuted, modifier = Modifier.size(BuroIconSize.Small)) },
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * The picture-in-picture window's controls: a translucent strip, because at 480x300 every row the
 * chrome takes is a row the film loses.
 */
@Composable
private fun CompactStrip(
    playing: Boolean,
    text: PlayerText,
    onTogglePlayback: () -> Unit,
    onBackToApp: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB30A0C0F))))
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuroIconButton(
            icon = if (playing) BuroIcons.Pause else BuroIcons.Play,
            label = if (playing) text.pause else text.play,
            onClick = onTogglePlayback,
            size = BuroIconSize.Small,
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBackToApp, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
            Text(text.backToApp, color = BuroColors.Primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * A slim slider: a 4 dp track and a round thumb.
 *
 * Material's current default is a thick track split by a gap around a tall bar-shaped thumb, which
 * suits a phone settings screen and looked heavy across the bottom of a film.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors =
        SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            disabledThumbColor = Color.White.copy(alpha = 0.3f),
            disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
            disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f),
        )
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        valueRange = valueRange,
        interactionSource = interaction,
        colors = colors,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interaction,
                colors = colors,
                enabled = enabled,
                thumbSize = DpSize(14.dp, 14.dp),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                enabled = enabled,
                modifier = Modifier.height(4.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
        modifier = modifier,
    )
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

/** "1.25×", "2×": VLC reports the rate as a float, so 1.2499999 is formatted rather than shown. */
private fun formatRate(rate: Double): String =
    "%.2f".format(DISPLAY_LOCALE, rate).trimEnd('0').trimEnd('.', ',') + "×"
