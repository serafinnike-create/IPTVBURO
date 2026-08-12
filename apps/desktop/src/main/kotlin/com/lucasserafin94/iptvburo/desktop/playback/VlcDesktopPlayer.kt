package com.lucasserafin94.iptvburo.desktop.playback

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Embedded Windows playback backed by the official VLC executable shipped beside the app.
 *
 * The credential-bearing media URI is sent only to VLC's password-protected loopback interface;
 * it is never placed in the process command line, persisted, or logged.
 */
class VlcDesktopPlayer(
    /**
     * How subtitles are drawn.
     *
     * Passed in at construction because VLC builds its text renderer with the video chain: these
     * take effect on the next title, not on the one already playing. Changing them mid-film and
     * seeing nothing happen would read as a broken setting, so the UI says the change applies to
     * what is played next.
     */
    private val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /**
     * Decoder policy for this player process.
     *
     * The single-title player keeps software decoding as its conservative compatibility default.
     * Multiview opts into [VlcHardwareDecoding.AUTOMATIC]: four simultaneous software decoders can
     * saturate the CPU even on capable notebooks, while VLC's `any` mode selects the available
     * D3D11VA/DXVA path and falls back according to the bundled engine's own codec support.
     */
    private val hardwareDecoding: VlcHardwareDecoding = VlcHardwareDecoding.DISABLED,
    /** Optional delay used by multiview so four provider requests do not arrive as one burst. */
    private val startupDelayMillis: Long = 0L,
    /**
     * How much stream to buffer before playing, in milliseconds.
     *
     * The single-title player keeps VLC's own default: one stream has the connection to itself, and
     * a short buffer means the picture appears quickly. Multiview asks for considerably more —
     * four streams share one connection, and the log showed every tile falling to `stopped` and
     * recovering seconds later, over and over, which is a starved buffer rather than a dead channel.
     */
    private val networkCachingMillis: Int = DEFAULT_NETWORK_CACHING_MILLIS,
    /**
     * A short label for the log, so four players can be told apart.
     *
     * Every line used to read `[player]`, which with a grid of four made the log almost useless:
     * "one tile did not start" and "one tile started and stopped" produce the same set of words in
     * a different order, and there was no way to know which tile any line belonged to.
     *
     * A tile number, never a channel name or provider id — those can carry an address.
     */
    private val logTag: String = "player",
) {
    init {
        require(startupDelayMillis in 0L..MAX_STARTUP_DELAY_MILLIS) {
            "startupDelayMillis is outside the safe range"
        }
    }

    @Volatile
    // Opens showing the brightness that is actually in force, which is the one the last title was
    // given. Starting at 1.0 would put the slider somewhere the picture is not.
    private var snapshot = DesktopPlaybackSnapshot(engineName = "VLC", brightness = pendingBrightness)

    private val disposed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, CONTROL_THREAD_NAME).apply { isDaemon = true }
        }

    /**
     * Guards [process] and [remote], which are written on the control executor and read from the
     * AWT/Compose thread in [dispose].
     *
     * Without it, closing the player while VLC was still launching leaked the process: dispose ran
     * on the UI thread, saw `process == null` because the executor had not assigned it yet, and
     * shut the executor down; the in-flight startVlc then stored a live VLC nobody owned any more.
     * ProcessBuilder.start is not interruptible, so shutdownNow could not prevent the spawn.
     */
    private val processLock = Any()
    private var process: Process? = null
    private var remote: VlcHttpControl? = null

    @Volatile
    private var mediaStartedAt = 0L

    @Volatile
    private var everPlayed = false

    /**
     * Until when a `stopped` report should be ignored, because a track switch caused it.
     *
     * Changing audio or subtitles makes VLC rebuild the stream, and during that it reports states
     * that look exactly like a film ending or a start that never happened. Both of those have
     * handlers that close or restart playback, so without this window a user switching to
     * Portuguese had the player shut on them mid-film.
     */
    @Volatile
    private var switchingTrackUntil = 0L

    /** Last state printed, so the poll does not log the same word twice a second. */
    private var lastLoggedState: String? = null

    /** Whether this player has already reported giving up, so it says so once rather than forever. */
    @Volatile
    private var exhaustionLogged = false

    /** When playback first began, so a stream that dies young can be told from one that ran. */
    @Volatile
    private var firstPlayingAt = 0L

    /** Reported once per run, not on every poll of a stopped tile. */
    @Volatile
    private var shortLifeLogged = false

    /**
     * When playback stopped looking healthy, or 0 while it is fine.
     *
     * A live stream reports `stopped` for a poll or two over ordinary events — a buffer refilling, a
     * segment boundary, a missing keyframe — and recovers on its own. Reconnecting at the first such
     * report tore down a video output that was about to come back, and rebuilding it is exactly the
     * black flash the user reported: `playing` → `stopped` → `retrying (1/3)` → `playing`, over and
     * over, with the retry count never rising because the stream kept recovering.
     *
     * The app was causing the flicker it was trying to repair. A stop is only real once it has
     * lasted, so the moment it began is recorded and judged by [shouldTreatStopAsDrop].
     */
    @Volatile
    private var unhealthySince = 0L

    /** One silent retry per player, for the cold-start stall described in pollState. */
    /**
     * How many silent restarts this media has been given.
     *
     * One was not always enough: the device and the surface can both still be settling four seconds
     * in, and a single retry then failed the same way the first attempt did. Three attempts spread
     * over about twelve seconds cover it without the user ever seeing a retry happen.
     */
    private val startRetries = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * When the current run of uninterrupted playback began, or 0 while nothing is playing.
     *
     * The retry budget is for one bad patch, not for a whole evening. Left cumulative it made
     * multiview decay exactly as reported: four decoders competing means every tile stumbles
     * occasionally, each stumble spent one of three lifetime attempts, and within minutes the grid
     * was down to whichever channel had been luckiest. A stream that has been playing steadily has
     * demonstrably recovered, so it earns its attempts back.
     */
    @Volatile
    private var steadySince = 0L

    /** The request currently playing, so the retry knows what to re-open. */
    @Volatile
    private var lastRequest: DesktopPlaybackRequest? = null

    /**
     * The surface VLC was told to draw into.
     *
     * Held so [retry] can relaunch the engine when the first start never produced one: `addNotify`
     * fires once per canvas, so without this there was no path back to [startIfNeeded] and the
     * Retry button was permanently inert.
     */
    @Volatile
    private var canvas: Canvas? = null

    /** The loopback port this player holds, released on dispose so it can be handed out again. */
    @Volatile
    private var claimedPort: Int? = null

    fun createComponent(
        request: DesktopPlaybackRequest,
        onPointerActivity: () -> Unit = {},
        /**
         * A deliberate press on the picture, as distinct from mere activity.
         *
         * Multiview moves the sound to the tile that is clicked, and it cannot use
         * [onPointerActivity] for that: activity fires on movement, so the audio would follow the
         * pointer across the grid — worse than the control not working at all.
         */
        onClick: () -> Unit = {},
        onKey: (Int) -> Boolean = { false },
    ): JPanel {
        val canvas =
            object : Canvas() {
                override fun addNotify() {
                    super.addNotify()
                    SwingUtilities.invokeLater { startIfNeeded(this, request) }
                }
            }.apply {
                // The second chance, and the one that matters.
                //
                // addNotify fires exactly once, and at that moment the canvas can still be 0x0
                // while Compose lays the SwingPanel out. awaitComponentHandle then polls for two
                // seconds and gives up, and because addNotify has already fired there was no path
                // back into startIfNeeded: the title never started and the window stayed black
                // with no error, until the user closed and reopened it.
                //
                // A resize is precisely the event that says the surface now has real dimensions,
                // so it is the right signal to try again on. startIfNeeded is idempotent — it
                // latches on `started` — so an engine that did come up ignores this entirely.
                addComponentListener(
                    object : java.awt.event.ComponentAdapter() {
                        override fun componentResized(event: java.awt.event.ComponentEvent) {
                            if (width > 0 && height > 0) startIfNeeded(this@apply, request)
                        }
                    },
                )
                background = Color.BLACK
                isFocusable = true
                // The AWT canvas sits over the video and swallows pointer events, so Compose never
                // saw the movement that wakes the controls: they stayed up for ever in full screen.
                // Forwarding it from here is the only place the movement actually arrives.
                addMouseMotionListener(
                    object : java.awt.event.MouseMotionAdapter() {
                        override fun mouseMoved(event: java.awt.event.MouseEvent) = onPointerActivity()

                        override fun mouseDragged(event: java.awt.event.MouseEvent) = onPointerActivity()
                    },
                )
                // Keys too. Once the canvas takes focus - which it does as soon as the pointer is
                // over the video - Compose stops receiving key events entirely, so Escape and F11
                // did nothing and there was no way out of full screen at all.
                addKeyListener(
                    object : java.awt.event.KeyAdapter() {
                        override fun keyPressed(event: java.awt.event.KeyEvent) {
                            if (onKey(event.keyCode)) event.consume()
                        }
                    },
                )
                // A click on the video counts as activity, so tapping the picture brings the
                // controls back the way it does in every other player.
                addMouseListener(
                    object : java.awt.event.MouseAdapter() {
                        override fun mousePressed(event: java.awt.event.MouseEvent) {
                            requestFocusInWindow()
                            onPointerActivity()
                            // A deliberate press, separate from mere activity.
                            //
                            // Multiview moves the sound to the tile that is clicked, and it cannot
                            // use onPointerActivity for that: activity fires on movement, so the
                            // audio would follow the pointer across the grid — worse than the
                            // control not working at all.
                            onClick()
                        }

                        override fun mouseEntered(event: java.awt.event.MouseEvent) = onPointerActivity()
                    },
                )
            }
        return JPanel(BorderLayout()).apply {
            background = Color.BLACK
            add(canvas, BorderLayout.CENTER)
        }
    }

    /**
     * The bare video surface, for callers that place it in an AWT container of their own.
     *
     * Multiview needs this rather than [createComponent]. Compose cuts a hole in its scene for the
     * one component handed to `SwingPanel` and no deeper: with a `JPanel` per tile, the canvases
     * were grandchildren inside that hole, Compose kept painting its own surface over the region,
     * and every tile stayed black — while VLC reported `playing` and the audio came through, which
     * is exactly what made it look like a decoding failure rather than a compositing one.
     *
     * Returning the canvas itself makes each video surface a direct child of the interop component,
     * which is the arrangement the single-title player has always used and the reason it works.
     */
    fun createCanvas(
        request: DesktopPlaybackRequest,
        onPointerActivity: () -> Unit = {},
        onClick: () -> Unit = {},
        onKey: (Int) -> Boolean = { false },
    ): Canvas =
        createComponent(
            request = request,
            onPointerActivity = onPointerActivity,
            onClick = onClick,
            onKey = onKey,
        ).let { panel ->
            val canvas = panel.components.first() as Canvas
            // Detached from the wrapper, which exists only for the single-title player. Left in
            // place it would be reparented on the first add and AWT would log a warning.
            panel.remove(canvas)
            canvas
        }

    /**
     * Hides or restores the pointer over the video surface.
     *
     * A blank cursor rather than a real hide: AWT has no "hide" and this is how every Java video
     * player does it.
     */
    fun setPointerVisible(component: JPanel, visible: Boolean) {
        val canvas = component.components.firstOrNull() as? Canvas ?: return
        canvas.cursor =
            if (visible) {
                java.awt.Cursor.getDefaultCursor()
            } else {
                java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
                    java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                    java.awt.Point(0, 0),
                    "hidden",
                )
            }
    }

    fun snapshot(): DesktopPlaybackSnapshot = snapshot

    fun togglePlayback() = executeCommand("pl_pause")

    fun seekToFraction(fraction: Double) {
        if (snapshot.durationMillis > 0.0) {
            seekToMillis((snapshot.durationMillis * fraction.coerceIn(0.0, 1.0)).toLong())
        }
    }

    fun seekBy(millis: Double) {
        val target =
            (snapshot.positionMillis + millis)
                .coerceIn(0.0, snapshot.durationMillis.coerceAtLeast(0.0))
        seekToMillis(target.toLong())
    }

    /**
     * How the picture fills the window.
     *
     * Two different VLC settings, which is why this takes one value rather than two: `crop` stretches
     * the image to fill, `aspectratio` reshapes it. Sending both keeps them from fighting — a crop
     * left over from a previous title would otherwise survive a change of aspect ratio and produce
     * a shape neither setting asked for.
     */
    fun setAspectRatio(ratio: PlaybackAspectRatio) {
        snapshot = snapshot.copy(aspectRatio = ratio)
        when (ratio) {
            // Default clears both: the source's own shape, letterboxed as the film intends.
            PlaybackAspectRatio.DEFAULT -> {
                executeCommand("aspectratio", mapOf("val" to ""))
                executeCommand("crop", mapOf("val" to ""))
            }
            // Fill crops to 16:9 rather than stretching: a stretched face is worse than a trimmed
            // edge, and cropping is what other players mean by "fill" on a widescreen display.
            PlaybackAspectRatio.FILL -> {
                executeCommand("aspectratio", mapOf("val" to ""))
                executeCommand("crop", mapOf("val" to "16:9"))
            }
            else -> {
                executeCommand("crop", mapOf("val" to ""))
                executeCommand("aspectratio", mapOf("val" to ratio.vlcValue))
            }
        }
    }

    /**
     * Remembers a brightness for the next title, 0.0 to 2.0 with 1.0 as the source's own.
     *
     * It cannot be changed on a title already playing, and the previous implementation quietly
     * pretended otherwise. VLC's HTTP control interface supports a fixed list of commands — its own
     * README names them: volume, seek, rate, the track selectors, the playlist verbs. `brightness`
     * and `adjust` are not among them, so both were accepted by the socket, ignored by the engine,
     * and the slider moved while the picture never changed.
     *
     * The value is therefore stored and passed on the command line when the next title starts,
     * which is where the adjust filter is actually built. This is the same limitation the subtitle
     * appearance settings have, and it is handled the same way rather than with a second mechanism.
     */
    fun setBrightness(value: Double) {
        val safe = value.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
        snapshot = snapshot.copy(brightness = safe)
        pendingBrightness = safe
    }

    fun setVolume(value: Double) {
        val safe = value.coerceIn(0.0, 1.0)
        snapshot = snapshot.copy(volume = safe)
        executeCommand("volume", mapOf("val" to (safe * VLC_VOLUME_MAX).toInt().toString()))
    }

    /**
     * Switches audio track; the id is VLC's own, taken from the status it reported.
     *
     * VLC tears the stream down and reopens it to do this, and for a poll or two reports `stopped`
     * — sometimes with the position back at zero. [switchingTrackUntil] tells [pollState] to leave
     * that window alone, so a track change is not mistaken for the film ending or for a start that
     * failed. Without it, choosing Portuguese closed the player mid-film.
     */
    fun selectAudioTrack(trackId: Int) {
        switchingTrackUntil = System.currentTimeMillis() + TRACK_SWITCH_GRACE_MILLIS
        executeCommand("audio_track", mapOf("val" to trackId.toString()))
    }

    /**
     * Switches or disables subtitles.
     *
     * VLC's HTTP interface takes `subtitle_track` with a `val`, and -1 turns them off — but only for
     * a track it is managing. Captions burned into the video, and some broadcast teletext, are part
     * of the picture as far as the decoder is concerned, and no command can remove them.
     *
     * Logged because "I pressed off and nothing happened" has two very different causes: the command
     * never left, or it left and the stream ignored it. The response tells them apart.
     */
    fun selectSubtitleTrack(trackId: Int) {
        // Same grace window as the audio switch: subtitles are re-attached to the running chain and
        // can produce the same brief `stopped`.
        switchingTrackUntil = System.currentTimeMillis() + TRACK_SWITCH_GRACE_MILLIS
        println("[$logTag] subtitle_track -> $trackId")
        executeCommand("subtitle_track", mapOf("val" to trackId.toString()))
    }

    fun setPlaybackRate(value: Double) {
        val safe = value.coerceIn(0.5, 2.0)
        snapshot = snapshot.copy(playbackRate = safe)
        executeCommand("rate", mapOf("val" to safe.toString()))
    }

    /**
     * Restarts playback after a failure.
     *
     * Two failures used to make this button do nothing at all. When the engine never started, there
     * is no [remote] to send `in_play` to, and `addNotify` has already fired for good, so nobody
     * ever called [startIfNeeded] again — the error stayed on screen for ever. And the snapshot was
     * flipped to `loading` before either command ran, so a no-op retry replaced the error with a
     * spinner that nothing could clear: pollState returns immediately while `remote` is null.
     */
    fun retry(request: DesktopPlaybackRequest) {
        if (disposed.get()) return
        runCatching {
            executor.execute { retryOnControlThread(request) }
        }
    }

    private fun retryOnControlThread(request: DesktopPlaybackRequest) {
        if (disposed.get()) return
        lastRequest = request
        val control = synchronized(processLock) { remote }
        if (control == null) {
            // No engine to talk to: relaunch it from scratch rather than reporting success.
            val surface = canvas
            if (surface == null) {
                snapshot =
                    snapshot.copy(
                        loading = false,
                        playing = false,
                        errorMessage = START_FAILURE_MESSAGE,
                    )
                return
            }
            started.set(false)
            startIfNeeded(surface, request)
            return
        }
        runCatching {
            control.command("pl_stop")
            control.command("pl_empty")
            control.command("in_play", mapOf("input" to request.uri.toVlcInput()))
        }.onSuccess {
            mediaStartedAt = System.currentTimeMillis()
            everPlayed = false
            // Per media, not per player: without this the second title of a session inherits the
            // first one's exhausted retries and gets none of its own.
            startRetries.set(0)
            snapshot = snapshot.copy(errorMessage = null, loading = true, ended = false)
        }.onFailure {
            // Leaving `loading` set here is what stranded the spinner: the poll task cannot
            // report an error for a control interface that is no longer answering.
            snapshot =
                snapshot.copy(
                    loading = false,
                    playing = false,
                    errorMessage = START_FAILURE_MESSAGE,
                )
        }
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        canvas = null
        val (control, child) = synchronized(processLock) { remote to process }
        runCatching { control?.command("pl_stop") }
        runCatching { child?.destroy() }
        // Shut the executor down first, then sweep again under the lock. A startVlc already past
        // its `disposed` check can still spawn VLC after the sweep above - ProcessBuilder.start is
        // not interruptible - and that orphan used to keep running with the video surface held.
        executor.shutdownNow()
        val leftBehind =
            synchronized(processLock) {
                val late = process
                process = null
                remote = null
                late
            }
        runCatching { leftBehind?.destroy() }
        // Insisted on, not merely requested.
        //
        // `destroy` sends a polite termination that a VLC busy in a network read can take seconds
        // to act on, or ignore. Waiting briefly and then forcing it is what makes the guarantee
        // real: without this, four multiview tiles could leave four engines behind, each holding a
        // loopback port and a few hundred megabytes.
        listOfNotNull(child, leftBehind).forEach { process ->
            runCatching {
                if (!process.waitFor(PROCESS_EXIT_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
            }
        }
        // Released only after both process sweeps. Releasing before destroy opened a small window
        // where another tile could receive this port while the old VLC still owned it.
        releaseClaimedPort()
    }

    /**
     * Waits for the AWT peer to exist and returns its native window handle.
     *
     * Polls rather than assuming, because the peer is created asynchronously and a zero handle is
     * accepted silently by VLC: it plays into nothing and reports success.
     */
    private fun awaitComponentHandle(canvas: Canvas): Long {
        repeat(HANDLE_ATTEMPTS) {
            // Size as well as handle. A realised peer can still be 0x0 while Compose is laying the
            // SwingPanel out, and VLC attached to a zero-sized surface renders nothing and never
            // recovers - that is why the first open was always black and the second worked.
            if (canvas.isDisplayable && canvas.width > 0 && canvas.height > 0) {
                val pointer = runCatching { Native.getComponentPointer(canvas) }.getOrNull()
                val handle = pointer?.let(Pointer::nativeValue) ?: 0L
                if (handle != 0L) return handle
            }
            Thread.sleep(HANDLE_POLL_MILLIS)
        }
        // Logged before throwing, because from outside this is indistinguishable from a file that
        // will not play: the window is black and the clock reads 00:00 either way. Dimensions
        // only — no path, no MRL.
        println(
            "[$logTag] surface never became usable: displayable=${canvas.isDisplayable} " +
                "size=${canvas.width}x${canvas.height}",
        )
        error("The video surface was not ready")
    }

    private fun startIfNeeded(canvas: Canvas, request: DesktopPlaybackRequest) {
        if (disposed.get()) return
        // The poller re-opens live streams after a transient stop. Previously the retry budget was
        // consumed while lastRequest stayed null, so no command was sent and a black tile could
        // never recover.
        lastRequest = request
        if (!started.compareAndSet(false, true)) return
        this.canvas = canvas
        snapshot = snapshot.copy(loading = true, errorMessage = null)
        val launch = {
            runCatching {
                if (startupDelayMillis > 0L) Thread.sleep(startupDelayMillis)
                if (disposed.get()) error("The player was closed before its delayed start")
                startVlc(canvas, request)
            }
                .onFailure { error ->
                    // Said out loud, because this is the silent case.
                    //
                    // A start that throws left no trace at all: the tile went black and the log
                    // simply never mentioned that player again, which is indistinguishable from a
                    // tile that started fine and is quietly playing. With four of them that made a
                    // grid coming up three-of-four impossible to account for.
                    //
                    // The exception type and message, never the cause chain — a wrapped IOException
                    // from the HTTP control can carry the MRL, and the MRL carries the credentials.
                    println("[$logTag] start failed: ${error::class.simpleName}")
                    // The failed attempt leaves a process behind and a latch that would refuse the
                    // next one, so a second try did nothing at all while the first VLC was still
                    // holding the video surface - two engines, one window, a broken picture.
                    val orphan =
                        synchronized(processLock) {
                            val previous = process
                            process = null
                            remote = null
                            previous
                    }
                    runCatching { orphan?.destroy() }
                    // A retry allocates a new port. Keeping the failed attempt claimed leaked one
                    // entry per retry and eventually made the in-process collision guard useless.
                    releaseClaimedPort()
                    started.set(false)
                    snapshot =
                        snapshot.copy(
                            loading = false,
                            playing = false,
                            errorMessage = START_FAILURE_MESSAGE,
                        )
                }
            Unit
        }
        // A retry arrives on the executor itself. Submitting from there would deadlock nothing but
        // would silently do nothing once the executor is shutting down, so run it inline instead.
        if (Thread.currentThread().name == CONTROL_THREAD_NAME) {
            launch()
        } else {
            runCatching { executor.execute(launch) }
        }
    }

    private fun startVlc(canvas: Canvas, request: DesktopPlaybackRequest) {
        val executable = locateVlcExecutable() ?: error("Bundled VLC runtime was not found")
        val port = freeLoopbackPort()
        claimedPort = port
        val password = randomPassword()

        // The native peer is not always realised by the time addNotify fires. When it is not, the
        // pointer comes back null and VLC is told to draw into window handle 0 — it starts, reports
        // no error, and renders nowhere. That is the intermittent black screen that a close and
        // reopen "fixed": the second attempt happened to win the race.
        val windowHandle = awaitComponentHandle(canvas)
        // Handle and surface size, never the input. A zero handle or a zero-sized canvas is the
        // difference between "VLC is running and drawing nowhere" and "VLC never started", and
        // from outside the two look identical: a black window with 00:00 / 00:00.
        println("[$logTag] starting: handle=$windowHandle surface=${canvas.width}x${canvas.height}")
        val child =
            ProcessBuilder(
                executable.absolutePath,
                "-I",
                "dummy",
                "--extraintf=http",
                "--http-host=127.0.0.1",
                "--http-port=$port",
                "--http-password=$password",
                "--drawable-hwnd=$windowHandle",
                "--no-video-title-show",
                "--no-qt-error-dialogs",
                // The buffer, sized for how many streams are competing.
                //
                // 1500ms is VLC's sensible default for one stream. Four at once share the same
                // connection, the same disk and the same CPU, and the log showed every tile falling
                // to `stopped` and recovering seconds later, over and over — a starved buffer, not
                // a dead channel. A larger reservoir costs a slightly longer start and buys a grid
                // that does not blink.
                "--network-caching=$networkCachingMillis",
                // The live path has its own reservoir, and it is the one that governs a channel.
                //
                // `network-caching` does not cover every live input; a stream arriving through the
                // live path keeps buffering to its own default however large the network one is,
                // which is why raising only that cut the stalls without stopping them.
                "--live-caching=$networkCachingMillis",
                "--file-caching=1000",
                // Compensate for a wandering clock rather than resetting playback.
                //
                // A provider's live stream carries timestamps that drift, and beyond what the
                // synchronisation algorithm is told to absorb, VLC restarts its clock — which stops
                // and restarts the picture. This value is the *maximum jitter to compensate*, so it
                // is raised, not lowered: zero would switch the compensation off entirely.
                //
                // Four streams from one provider drift together, which matches the measured
                // pattern — every tile stalling on a cycle far too regular to be a network fault.
                "--clock-jitter=$networkCachingMillis",
                // Let VLC repair the connection itself.
                //
                // Without this the only recovery was the app's: stop, empty the playlist, reopen —
                // which tears down the video output and rebuilds it, and that rebuild is the black
                // flash the user sees. VLC's own reconnection re-establishes the HTTP stream while
                // the output stays up, so a brief network stumble never reaches the screen.
                "--http-reconnect",
                // Keep the picture up while the buffer refills, rather than showing black.
                //
                // The default drops late frames, which on a stuttering live stream means the tile
                // goes black for the moment it is behind. Holding the last frame reads as a pause;
                // black reads as a fault.
                "--no-drop-late-frames",
                "--no-skip-frames",
                // Pinned to the Direct3D 11 output. Left to choose, VLC can pick a module that
                // ignores --drawable-hwnd entirely and opens its own window - or, off screen,
                // renders nowhere at all, which looks exactly like a film that refuses to start.
                "--vout=direct3d11",
                // The adjust filter has to be in the chain when the video output is built. Enabling
                // it later over the control interface returns success and changes nothing: the
                // chain is already running, and with a GPU output there is no software stage left
                // to insert it into. Declared here at its neutral value, it costs nothing until the
                // brightness control is actually moved.
                "--video-filter=adjust",
                // The remembered value, not a fixed 1.0.
                //
                // This is the only place brightness can be set at all: the filter is built with the
                // video chain, and the HTTP control interface has no command to change it after the
                // fact. Hardcoding 1.0 here is what made the slider permanently decorative.
                "--brightness=$pendingBrightness",
                // Subtitle appearance, declared at startup for the same reason as the adjust
                // filter: the text renderer is built with the video chain, and changing these
                // afterwards over the control interface has no effect on a running one.
                "--freetype-rel-fontsize=${subtitleStyle.vlcRelativeSize}",
                "--freetype-color=${subtitleStyle.textColour.vlcValue}",
                "--freetype-opacity=255",
                // A background box behind the text, which is the difference between readable and
                // not over a bright scene.
                "--freetype-background-opacity=${if (subtitleStyle.background) 160 else 0}",
                hardwareDecoding.vlcArgument,
                "--quiet",
            ).directory(executable.parentFile)
                // Discarded on purpose: VLC logs the MRL it was given, and for a provider source
                // that string carries the username and password. A log file of those is exactly
                // what this project's credential rules forbid, so failures are diagnosed through
                // the status the control interface reports instead.
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        ChildProcessJob.adopt(child)
        // Published immediately and under the lock. Assigning it only after the connect handshake
        // meant a dispose during those twelve seconds found no process to kill, and VLC kept
        // playing audio over a window the user had already closed.
        val abandoned =
            synchronized(processLock) {
                if (disposed.get()) {
                    child
                } else {
                    process = child
                    null
                }
            }
        // Registered as soon as it exists, so the shutdown hook can reach it even if this player is
        // never disposed. Removed again when it dies, so the set does not grow across an evening of
        // channel changes.
        liveProcesses.add(child)
        child.onExit().thenRun { liveProcesses.remove(child) }

        if (abandoned != null) {
            abandoned.destroy()
            error("The player was closed while the engine was starting")
        }

        val control = VlcHttpControl.connect(port, password, CONNECT_TIMEOUT_MILLIS)
        val stillWanted =
            synchronized(processLock) {
                if (disposed.get()) {
                    false
                } else {
                    remote = control
                    true
                }
            }
        if (!stillWanted) {
            child.destroy()
            error("The player was closed while the engine was starting")
        }
        control.command("volume", mapOf("val" to (snapshot.volume * VLC_VOLUME_MAX).toInt().toString()))
        control.command("in_play", mapOf("input" to request.uri.toVlcInput()))
        mediaStartedAt = System.currentTimeMillis()
        // Rejected submissions are expected once dispose has shut the executor down; without the
        // guard the closing player threw RejectedExecutionException out of the control thread.
        runCatching {
            if (request.startPositionMillis > 0L) {
                executor.schedule({ seekToMillis(request.startPositionMillis) }, 2, TimeUnit.SECONDS)
            }
            executor.scheduleWithFixedDelay(::pollState, 250, 500, TimeUnit.MILLISECONDS)
        }
    }

    private fun pollState() {
        if (disposed.get()) return
        val control = synchronized(processLock) { remote } ?: return
        runCatching {
            val status = control.status()
            val stateName = status.string("state")?.lowercase()
            // The state name and length only — never the input, which for a provider source carries
            // the credentials. Enough to tell "never started" from "started and stopped", which is
            // the distinction that has cost the most time here.
            if (stateName != lastLoggedState) {
                lastLoggedState = stateName
                println("[$logTag] state=$stateName length=${status.long("length")}")
            }
            val playing = stateName == "playing"
            val paused = stateName == "paused"
            val ready = playing || paused || stateName == "stopped"
            val lengthSeconds = status.long("length")
            if (playing) {
                if (!everPlayed) firstPlayingAt = System.currentTimeMillis()
                everPlayed = true
            }


            // Steady playback returns the retry budget.
            //
            // Without this the three attempts were a lifetime allowance, and a live channel that
            // hiccuped three times across an evening was dead for good. The wait matters as much as
            // the reset: restoring on the first `playing` poll would let a stream that flaps between
            // playing and stopped retry for ever, hammering the provider. Half a minute of real
            // playback is a recovery; two seconds is a bounce.
            if (playing && steadySince == 0L) steadySince = System.currentTimeMillis()
            if (!playing) steadySince = 0L
            if (
                shouldRestoreRetryBudget(
                    playing = playing,
                    retriesUsed = startRetries.get(),
                    steadyForMillis =
                        if (steadySince == 0L) 0L else System.currentTimeMillis() - steadySince,
                )
            ) {
                startRetries.set(0)
            }
            // "Ended" means the title ran out, not merely that VLC is momentarily stopped.
            //
            // Switching audio track makes VLC tear the stream down and reopen it, and for a poll or
            // two in between it reports `stopped` with the length still set. Treating that as the
            // end closed the player mid-film — which is exactly what choosing Portuguese did.
            //
            // A real ending leaves the position at or near the duration. A track switch leaves it
            // wherever the user was, so the position is what tells the two apart.
            val positionSeconds = status.long("time")
            val switchingTrack = System.currentTimeMillis() < switchingTrackUntil
            val ended =
                everPlayed &&
                    !switchingTrack &&
                    isEnded(stateName, positionSeconds, lengthSeconds)
            val tracks = status.readTracks()

            // The first title of a session often stops without ever playing: VLC has to bring up
            // the Direct3D device and attach it to a surface Compose is still settling, and the
            // second attempt worked only because that work was already done. Retried once,
            // silently, which is exactly what the user was doing by hand.
            // Any state that is not playback counts, not just "stopped". A start that hangs in
            // "opening" never reaches "stopped", so the retry never fired and the window stayed
            // black indefinitely — the case a user hit on a downloaded episode that VLC could open
            // perfectly well from a command line.
            // A live channel that drops is reconnected; a film that ends is not.
            //
            // `!everPlayed` alone confined the retry to a first start that never happened, which is
            // right for a film — one that reaches its end has ended, and re-opening it would replay
            // it. A live stream carries no end: dropping out is a network event, and the only
            // correct response is to open it again.
            //
            // This is what made multiview lose tiles one by one. Four decoders pulling four streams
            // is the heaviest thing the app does, and any of them can stumble; with no reconnection
            // each stumble was permanent, so the grid decayed to whichever tile happened to survive.
            // A stream with no declared length is live. VLC reports -1 there, and 0 while it is
            // still working the duration out — neither is a film, and a film always reports one
            // once it has started. Read from the engine rather than carried on the request because
            // the engine is what actually knows.
            val liveStream = lengthSeconds <= 0L

            val recoverable = !everPlayed || liveStream

            // How long this player has looked unhealthy, so a momentary stop is not treated as a
            // drop. Reset the instant playback resumes, so two brief stops minutes apart never add
            // up to one long one.
            // A stream that plays briefly and then stops is a different fault from one that stops
            // after an hour.
            //
            // Playing for about as long as the buffer holds and then ending means no more data
            // arrived: the source accepted the connection, delivered what it had, and closed. On a
            // provider that limits simultaneous connections — or refuses several to one channel —
            // that is what it looks like from here, and it is not something the player can fix.
            // Logged with the duration so the two cases can be told apart at a glance.
            if (!playing && !paused && !switchingTrack && everPlayed && !shortLifeLogged) {
                val playedFor = System.currentTimeMillis() - firstPlayingAt
                if (playedFor in 1..SHORT_LIFE_THRESHOLD_MILLIS) {
                    shortLifeLogged = true
                    println(
                        "[$logTag] stream ended after only ${playedFor}ms of playback — " +
                            "the source stopped sending",
                    )
                }
            }
            if (playing) shortLifeLogged = false

            val healthy = playing || paused || switchingTrack
            if (healthy) {
                // How long the stumble lasted, reported once on recovery.
                //
                // This is the only direct measurement of the blinking: a stop that resolves itself
                // never triggers anything, so without this it left no trace at all and there was no
                // way to tell a grid that blinks constantly from one that is steady.
                if (unhealthySince != 0L) {
                    val stoppedFor = System.currentTimeMillis() - unhealthySince
                    if (stoppedFor >= STUMBLE_LOG_THRESHOLD_MILLIS) {
                        println("[$logTag] recovered by itself after ${stoppedFor}ms")
                    }
                }
                unhealthySince = 0L
            } else if (unhealthySince == 0L) {
                unhealthySince = System.currentTimeMillis()
            }
            val unhealthyForMillis =
                if (unhealthySince == 0L) 0L else System.currentTimeMillis() - unhealthySince

            if (
                recoverable &&
                !playing &&
                !paused &&
                // Not while a track switch is settling: the stream is deliberately down, and
                // re-issuing `in_play` here would restart the film from the beginning.
                !switchingTrack &&
                // Only once the stop has lasted. Reconnecting at the first `stopped` poll tore down
                // a video output that was about to recover on its own, and rebuilding it is the
                // black flash itself — the app producing the fault it was trying to repair.
                shouldTreatStopAsDrop(unhealthyForMillis, networkCachingMillis) &&
                // A live channel may always try again; a film gets a small allowance.
                //
                // Capping a live channel is what made the fourth tile go permanently black while
                // its stream was alive: the log showed it stop and recover four times in under a
                // minute, never reaching the thirty seconds of steady playback that would have
                // returned its budget, and then give up for good. A channel carries no end, so a
                // drop is always a network event and reopening is always right — the spacing below
                // is what keeps that from becoming a flood.
                mayReconnect(liveStream = liveStream, consecutiveFailures = startRetries.get()) &&
                System.currentTimeMillis() - mediaStartedAt >
                    reconnectBackoffMillis(startRetries.getAndIncrement())
            ) {
                // The duration is the number that matters. "Reconnected" alone never said whether
                // the grace was too short — a stop of 5s and a stop of 5 minutes read identically,
                // and only one of them is a channel that actually went away.
                println(
                    "[$logTag] reconnecting (attempt ${startRetries.get()}) " +
                        "after ${unhealthyForMillis}ms stopped",
                )
                lastRequest?.let { request ->
                    runCatching {
                        // `in_play` appends to VLC's playlist. Retrying without clearing left several
                        // identical entries behind and did not reliably release the failed network
                        // input. Stop and empty first so every attempt is a genuinely fresh stream.
                        control.command("pl_stop")
                        control.command("pl_empty")
                        control.command("in_play", mapOf("input" to request.uri.toVlcInput()))
                        mediaStartedAt = System.currentTimeMillis()
                    }
                }
                return@runCatching
            }

            // The moment a tile gives up, said plainly.
            //
            // Without this the log simply stops mentioning a player, and "exhausted its retries",
            // "is a film that ended" and "is quietly fine" are indistinguishable — which is what
            // made a grid decaying from four to three so hard to account for. Logged once, on the
            // transition, so a stopped tile does not print twice a second for ever.
            // Only a film can give up now, so only a film reports it. A live channel that has been
            // trying for a while is a channel that is off air, and its attempts are already spaced
            // out by the backoff — saying "gave up" about something still trying would be a lie.
            if (recoverable && !playing && !paused && !switchingTrack && !exhaustionLogged) {
                if (!liveStream && startRetries.get() >= MAX_FILM_RECONNECTS) {
                    exhaustionLogged = true
                    println("[$logTag] gave up after $MAX_FILM_RECONNECTS attempts, state=$stateName")
                }
            }
            if (playing) exhaustionLogged = false

            snapshot =
                snapshot.copy(
                    loading = !ready,
                    ready = ready,
                    playing = playing,
                    positionMillis = status.long("time") * 1_000.0,
                    durationMillis = lengthSeconds * 1_000.0,
                    volume = (status.long("volume").toDouble() / VLC_VOLUME_MAX).coerceIn(0.0, 1.0),
                    playbackRate = status.double("rate")?.coerceIn(0.5, 2.0) ?: snapshot.playbackRate,
                    ended = ended,
                    audioTracks = tracks.audio,
                    subtitleTracks = tracks.subtitles,
                    activeAudioTrackId = tracks.activeAudio,
                    activeSubtitleTrackId = tracks.activeSubtitle,
                    errorMessage =
                        if (!ready && System.currentTimeMillis() - mediaStartedAt > START_TIMEOUT_MILLIS) {
                            STALLED_MESSAGE
                        } else {
                            null
                        },
                )
        }.onFailure {
            val alive = synchronized(processLock) { process }?.isAlive ?: false
            if (!alive) {
                snapshot =
                    snapshot.copy(
                        loading = false,
                        playing = false,
                        errorMessage = "O motor de vídeo foi encerrado inesperadamente.",
                    )
            } else if (System.currentTimeMillis() - mediaStartedAt > START_TIMEOUT_MILLIS) {
                // A VLC that is alive but has stopped answering its control interface used to leave
                // the overlay spinning for ever: the timeout that reports a stalled title only ran
                // in the success branch, which this poll never reaches.
                snapshot =
                    snapshot.copy(
                        loading = false,
                        playing = false,
                        errorMessage = STALLED_MESSAGE,
                    )
            }
        }
    }

    private fun seekToMillis(positionMillis: Long) {
        executeCommand("seek", mapOf("val" to "${positionMillis.coerceAtLeast(0L) / 1_000L}S"))
    }

    private fun executeCommand(command: String, parameters: Map<String, String> = emptyMap()) {
        if (disposed.get()) return
        // Guarded: every control (volume, seek, pause) threw RejectedExecutionException straight
        // into the UI thread when pressed on a player that was already closing.
        runCatching {
            executor.execute {
                runCatching { synchronized(processLock) { remote }?.command(command, parameters) }
                    .onFailure { error ->
                        // Only the command name and the exception type. A failure here used to be
                        // entirely silent, which made "I pressed it and nothing happened"
                        // indistinguishable from "the stream ignored it" — and the two need
                        // opposite fixes.
                        println("[$logTag] command $command failed: ${error.javaClass.simpleName}")
                    }
            }
        }
    }

    private fun locateVlcExecutable(): File? = findVlcExecutable()

    /**
     * A loopback port no other player in this process has just taken.
     *
     * Asking the OS for port 0 and closing the socket leaves a window between the answer and VLC
     * binding it. One player at a time never noticed. Four starting together did: two could be
     * handed the same port, the second VLC failed to bind its control interface, and its tile stayed
     * black — which matched the report exactly, including that the number of black tiles varied
     * between attempts.
     *
     * The claimed set closes that window from this process's side. It cannot help against another
     * program taking the port, so the loop retries rather than trusting the first answer.
     */
    private fun freeLoopbackPort(): Int {
        repeat(PORT_ATTEMPTS) {
            val candidate = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            synchronized(claimedPorts) {
                if (claimedPorts.add(candidate)) return candidate
            }
        }
        // Every attempt collided, which should not happen. Returning the last answer is better than
        // failing to start: a port clash produces one black tile, and giving up produces no player.
        return ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    }

    private fun releaseClaimedPort() {
        val port = claimedPort ?: return
        synchronized(claimedPorts) {
            claimedPorts.remove(port)
            if (claimedPort == port) claimedPort = null
        }
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        /** Roughly two seconds in total; the peer normally arrives within a frame or two. */
        const val HANDLE_ATTEMPTS = 100
        const val HANDLE_POLL_MILLIS = 20L

        /** How long a VLC is given to exit politely before it is forced. */
        const val PROCESS_EXIT_GRACE_MILLIS = VLC_PROCESS_EXIT_GRACE_MILLIS

        /**
         * Every VLC this process has started and not yet reaped.
         *
         * [dispose] is careful, but it only runs when the app closes in an orderly way. A crash, a
         * kill from Task Manager, or a Stop-Process leaves each engine running — holding a loopback
         * port and a few hundred megabytes with nothing left to own it. Four were found alive after
         * a session had ended, which is what put this here.
         *
         * The registry is what the shutdown hook below has to work with: by the time it runs, the
         * player objects may already be unreachable.
         */
        private val liveProcesses: MutableSet<Process> =
            java.util.Collections.synchronizedSet(java.util.LinkedHashSet())

        init {
            // The last line of defence. It runs on Ctrl-C, on System.exit, and on an orderly kill;
            // it cannot run on a power cut or a SIGKILL, and nothing in a JVM can.
            //
            // Failures are swallowed: this fires while the process is already going away, and an
            // exception here would only turn a clean exit into an ugly one.
            runCatching {
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        synchronized(liveProcesses) { liveProcesses.toList() }
                            .forEach { process -> runCatching { process.destroyForcibly() } }
                    }.apply { name = "iptvburo-vlc-reaper" },
                )
            }
        }

        /**
         * Loopback ports handed out to players in this process and not yet released.
         *
         * Shared across instances on purpose: the collision this prevents only happens between
         * players starting at the same moment, which is what multiview does four times over.
         */
        val claimedPorts = mutableSetOf<Int>()

        /** Enough to clear a burst of four starting together; a collision is already unlikely. */
        const val PORT_ATTEMPTS = 8

        const val VLC_VOLUME_MAX = 256

        /** VLC's own bounds for the adjust filter's brightness. 1.0 is the source untouched. */
        const val BRIGHTNESS_MIN = 0.0
        const val BRIGHTNESS_MAX = 2.0
        const val CONNECT_TIMEOUT_MILLIS = 12_000L
        const val START_TIMEOUT_MILLIS = 25_000L

        /**
         * How long to let a title sit at "stopped" before retrying it once.
         *
         * Long enough that a genuinely slow provider is not interrupted mid-open, short enough that
         * the user has not yet reached for the close button.
         */
        const val FIRST_START_RETRY_MILLIS = 4_000L

        /** Silent restarts before giving up. Three covers a cold device; more would just stall. */
        const val MAX_START_RETRIES = 3

        /** The fourth multiview tile starts after 2.25 s; anything longer feels broken. */
        const val MAX_STARTUP_DELAY_MILLIS = 3_000L

        /**
         * How long a `stopped` report is ignored after a track switch.
         *
         * Changing audio makes VLC rebuild the stream; while it does, the status looks identical to
         * a film that ended and to a start that never happened, and both of those close or restart
         * playback. Three seconds covers the rebuild on a slow file without leaving a genuinely
         * failed switch hanging.
         */
        const val TRACK_SWITCH_GRACE_MILLIS = 3_000L

        /** Matches the thread name given to the single control executor. */
        const val CONTROL_THREAD_NAME = "iptvburo-vlc-control"

        const val START_FAILURE_MESSAGE = "O motor de vídeo do Windows não pôde ser iniciado."
        const val STALLED_MESSAGE =
            "O servidor respondeu, mas este vídeo não iniciou. Tente novamente ou escolha outro título."
    }
}

/** VLC's accepted hardware-decoder modes used by the desktop player. */
enum class VlcHardwareDecoding(
    val vlcValue: String,
) {
    /** Compatibility-first default for one full-size title. */
    DISABLED("none"),

    /** Let VLC select D3D11VA/DXVA when the codec and GPU support it. */
    AUTOMATIC("any"),
    ;

    val vlcArgument: String
        get() = "--avcodec-hw=$vlcValue"
}

/**
 * The MRL to hand to VLC for this source.
 *
 * A downloaded file lives under `Videos/IPTV BURO`, and `Path.toUri` percent-encodes that space.
 * Passing the encoded form through the HTTP control interface left VLC opening a path that does not
 * exist, so an offline title reported no error and simply never started. The native path has no such
 * ambiguity. Remote sources keep their URI: for them the encoding is part of the address.
 */
/**
 * Encodes one value for VLC's HTTP control query string.
 *
 * Not `URLEncoder`, which implements **HTML form** encoding. Its one famous difference is the
 * space: a form writes `+`, a URL writes `%20`. VLC decodes this query as a URL, so a form-encoded
 * path arrived with a literal plus — `Videos\IPTV+BURO\…` — naming a directory that does not exist.
 * VLC reports no error for a path it cannot open; it starts, plays nothing, and the app shows a
 * black screen with `state=stopped length=0`. Every offline title went through this, because the
 * download directory is `Videos/IPTV BURO`.
 *
 * Everything except the unreserved set is escaped, so a stream URL's own `&` and `=` cannot be read
 * as further commands.
 */
internal fun encodeQueryValue(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    return buildString {
        value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
            // Masked to 0..255: a Kotlin Byte is signed, so anything above 127 — every accented
            // character, once UTF-8 encoded — would format as a negative and produce a broken escape.
            val unsigned = byte.toInt() and 0xFF
            val character = unsigned.toChar()
            if (character in unreserved) append(character) else append("%%%02X".format(unsigned))
        }
    }
}

internal fun URI.toVlcInput(): String =
    if (scheme.equals("file", ignoreCase = true)) {
        runCatching { java.nio.file.Path.of(this).toString() }.getOrElse { toASCIIString() }
    } else {
        toASCIIString()
    }

private class VlcHttpControl private constructor(
    private val port: Int,
    private val authorization: String,
) {
    fun status(): JsonObject = request(emptyMap())

    fun command(name: String, parameters: Map<String, String> = emptyMap()): JsonObject =
        request(linkedMapOf("command" to name) + parameters)

    private fun request(parameters: Map<String, String>): JsonObject {
        val query =
            parameters.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        val endpoint = "http://127.0.0.1:$port/requests/status.json" + if (query.isBlank()) "" else "?$query"
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 2_000
            connection.readTimeout = 3_000
            connection.setRequestProperty("Authorization", authorization)
            connection.setRequestProperty("User-Agent", "IPTV-BURO-Local-Player")
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = encodeQueryValue(value)

    companion object {
        fun connect(port: Int, password: String, timeoutMillis: Long): VlcHttpControl {
            val token = Base64.getEncoder().encodeToString(":$password".toByteArray(StandardCharsets.UTF_8))
            val control = VlcHttpControl(port, "Basic $token")
            val deadline = System.currentTimeMillis() + timeoutMillis
            var lastFailure: Throwable? = null
            while (System.currentTimeMillis() < deadline) {
                runCatching { control.status() }
                    .onSuccess { return control }
                    .onFailure { lastFailure = it }
                Thread.sleep(150)
            }
            throw IllegalStateException("VLC local control did not start", lastFailure)
        }
    }
}

/**
 * The audio and subtitle tracks VLC reports for the playing title.
 *
 * The status document describes them under `information.category` as `Stream 0`, `Stream 1` and so
 * on, each with a `Type` of Audio, Video or Subtitle. The numeric suffix is the track id the
 * control interface expects back, so it is parsed out of the key rather than guessed from position.
 */
/**
 * Whether VLC's report means the title ran out.
 *
 * `stopped` alone is not enough, and assuming it was is what closed the player mid-film: switching
 * audio track makes VLC tear the stream down and reopen it, reporting `stopped` in between with the
 * length still set. A genuine ending leaves the position at the duration; a track switch leaves it
 * wherever the user was, so the position is what tells them apart.
 *
 * The tolerance exists because VLC frequently stops a second or two short of the declared length.
 */
private const val END_TOLERANCE_SECONDS = 2L

internal fun isEnded(
    state: String?,
    positionSeconds: Long,
    lengthSeconds: Long,
): Boolean =
    state == "stopped" &&
        lengthSeconds > 0L &&
        positionSeconds >= lengthSeconds - END_TOLERANCE_SECONDS

/**
 * Whether a player that has been playing steadily should have its retry budget returned.
 *
 * The budget covers one bad patch, not an entire evening. Held cumulative it made multiview decay
 * exactly as reported — four decoders competing means every tile stumbles sooner or later, each
 * stumble spent one of three lifetime attempts, and within minutes the grid was down to whichever
 * channel had been luckiest. Entering full screen finished the job: resizing the window rebuilds
 * every swap chain at once, and with the budget already spent nothing came back.
 *
 * The dwell is what separates a recovery from a bounce. A stream flapping between playing and
 * stopped would otherwise restore its budget on every flap and retry against the provider for ever.
 *
 * @param steadyForMillis how long playback has been uninterrupted, or 0 when nothing is playing
 */
internal fun shouldRestoreRetryBudget(
    playing: Boolean,
    retriesUsed: Int,
    steadyForMillis: Long,
): Boolean =
    playing && retriesUsed > 0 && steadyForMillis > STEADY_PLAYBACK_DWELL_MILLIS

/** The dwell required, exposed so the test and the player cannot drift apart. */
internal const val STEADY_PLAYBACK_DWELL_MILLIS = 30_000L

/**
 * How long a VLC is given to exit politely before it is forced.
 *
 * `destroy` is a request that an engine busy in a network read can take seconds to act on, or
 * ignore. Four were found alive after a session had ended, each holding a loopback port and a few
 * hundred megabytes — so the wait is bounded and then the kill is insisted on.
 */
internal const val VLC_PROCESS_EXIT_GRACE_MILLIS = 1_500L

/** VLC's own default, kept for the single-title player where the stream has the line to itself. */
internal const val DEFAULT_NETWORK_CACHING_MILLIS = 1_500

/**
 * The brightness the next title will start with, shared by every player in this process.
 *
 * It cannot be applied to a title already playing: the adjust filter is built with the video chain,
 * and VLC's HTTP control interface has no command for it — the ones it does accept are listed in
 * its own README, and `brightness` is not there. Sending it moved the slider and changed nothing.
 *
 * Held here rather than per instance so the setting survives moving from one title to the next,
 * which is what a viewer who darkened a film expects when they start the next one.
 */
@Volatile
internal var pendingBrightness: Double = 1.0

/**
 * The buffer each multiview tile asks for.
 *
 * Four streams share one connection, one disk and one CPU. At the single-stream default the log
 * showed every tile falling to `stopped` and recovering seconds later, repeatedly — a starved
 * buffer, not a dead channel. Five seconds is enough to ride out the gaps four decoders create for
 * one another; the cost is a start that takes a few seconds longer, which is the right trade for a
 * grid somebody is going to watch for an hour.
 */
internal const val MULTIVIEW_NETWORK_CACHING_MILLIS = 5_000

/**
 * Whether a player may attempt another reconnection.
 *
 * A live channel has no finite allowance. It carries no end, so a drop is always a network event and
 * reopening is always the right answer — the only question is how often. Capping the attempts made
 * the fourth tile go permanently black while its stream was perfectly alive: the log showed it stop
 * and recover four times in under a minute, never reaching the thirty seconds of steady playback
 * that would have returned its budget, and then give up for good.
 *
 * A film is the opposite case. One that reaches its end has ended, and reopening it would replay it
 * from the beginning — so a title with a declared length keeps a strict, small allowance.
 *
 * The spacing is what protects the provider: attempts are separated by [reconnectBackoffMillis],
 * which grows with each consecutive failure, so a channel that is genuinely off air is polled less
 * and less rather than hammered.
 *
 * @param consecutiveFailures attempts since playback was last steady
 */
internal fun mayReconnect(
    liveStream: Boolean,
    consecutiveFailures: Int,
): Boolean = if (liveStream) true else consecutiveFailures < MAX_FILM_RECONNECTS

/**
 * How long to wait before the next attempt, given how many have just failed.
 *
 * Doubling from four seconds, capped at a minute. A channel that comes back on the first attempt is
 * interrupted for a few seconds; one that is genuinely off air settles into a quiet retry a minute
 * apart rather than a request every four seconds for the rest of the evening.
 */
internal fun reconnectBackoffMillis(consecutiveFailures: Int): Long {
    val doublings = consecutiveFailures.coerceIn(0, MAX_BACKOFF_DOUBLINGS)
    return (FIRST_RECONNECT_DELAY_MILLIS shl doublings).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
}

/** A film that will not start gets a handful of tries, then reports failure rather than looping. */
internal const val MAX_FILM_RECONNECTS = 3

internal const val FIRST_RECONNECT_DELAY_MILLIS = 4_000L
internal const val MAX_RECONNECT_DELAY_MILLIS = 60_000L
private const val MAX_BACKOFF_DOUBLINGS = 4

/**
 * Whether a stop has lasted long enough to be treated as a real drop.
 *
 * This is the rule behind the flicker: a live stream reports `stopped` for a poll or two over
 * ordinary events — a buffer refilling, a segment boundary, a missing keyframe — and comes back on
 * its own. Reconnecting at the first such report tore down a video output that was about to recover,
 * and rebuilding it is the black flash itself. The log showed the loop plainly: `playing` →
 * `stopped` → `retrying (1/3)` → `playing`, repeatedly, with the count never rising because the
 * stream kept recovering. The app was causing the fault it was trying to repair.
 *
 * @param unhealthyForMillis how long playback has looked stopped, or 0 while it is healthy
 */
internal fun shouldTreatStopAsDrop(
    unhealthyForMillis: Long,
    networkCachingMillis: Int = DEFAULT_NETWORK_CACHING_MILLIS,
): Boolean = unhealthyForMillis > stopGraceFor(networkCachingMillis)

/**
 * How long a stop must last, given how much this player buffers.
 *
 * The grace has to clear the buffer, not merely the poll interval. A player holding five seconds of
 * stream can sit at `stopped` for most of that while it refills, so a fixed four-second grace would
 * fire in the middle of an ordinary recovery — reconnecting a stream that was already coming back,
 * which is precisely the flicker this whole mechanism exists to prevent.
 */
internal fun stopGraceFor(networkCachingMillis: Int): Long =
    (networkCachingMillis + STOP_GRACE_HEADROOM_MILLIS)
        .toLong()
        .coerceAtLeast(STOP_GRACE_DWELL_MILLIS)

/** The floor, for a player with little or no buffering configured. */
internal const val STOP_GRACE_DWELL_MILLIS = 4_000L

/**
 * Added on top of the buffer, so the grace outlasts a full refill rather than matching it.
 *
 * Generous, and deliberately so. The measured session showed every stall lasting ~8400ms and every
 * "recovery" arriving ~600ms after the app reconnected — the same 600ms every time, across every
 * tile. That regularity is the signature of the app's own reconnection completing, not of a stream
 * healing: playback was being interrupted while it was still refilling.
 *
 * `--http-reconnect` means VLC is already repairing the connection itself, without disturbing the
 * video output. The app's reconnection tears that output down and rebuilds it, which is the black
 * flash. So the app now waits far longer than any refill could take, and intervenes only when VLC
 * has plainly failed rather than merely taken its time.
 */
internal const val STOP_GRACE_HEADROOM_MILLIS = 25_000

/**
 * Below this a stumble is not worth a log line.
 *
 * One poll's worth of `stopped` is normal on a live stream and invisible on screen. Logging those
 * would bury the ones that matter under a line every few seconds, per tile.
 */
private const val STUMBLE_LOG_THRESHOLD_MILLIS = 1_000L

/**
 * Playback shorter than this counts as a stream that never really started.
 *
 * Fifteen seconds is well past any buffer the app asks for, so a stream that ends inside it did not
 * stall — it ran out. That is the shape of a source which accepted the connection, sent what it had
 * and closed, and it is worth naming because no amount of buffering or reconnection will fix it.
 */
private const val SHORT_LIFE_THRESHOLD_MILLIS = 15_000L

/** Exposed under a distinct name so the test reads as what it checks rather than as internals. */
internal fun isEndedForTesting(
    state: String,
    positionSeconds: Long,
    lengthSeconds: Long,
): Boolean = isEnded(state, positionSeconds, lengthSeconds)

/**
 * The track parsing, exposed for tests.
 *
 * What is worth pinning is the mapping from VLC's status document to the id sent back — getting it
 * wrong stops playback, and that failure only appears on files with an unusual stream layout.
 */
internal fun JsonObject.readTracksForTesting(): PlaybackTracks = readTracks()

private fun JsonObject.readTracks(): PlaybackTracks {
    val information = get("information")?.takeUnless { it.isJsonNull }?.asJsonObject
        ?: return PlaybackTracks()
    val category = information.get("category")?.takeUnless { it.isJsonNull }?.asJsonObject
        ?: return PlaybackTracks()

    // Sorted by stream number before anything is read out of them.
    //
    // `entrySet()` returns the keys in whatever order they were parsed, and a JSON object promises
    // no order at all. Iterating it directly meant the picker could list "English, Portuguese" for
    // a file whose streams are the other way round — so choosing the first row selected the second
    // track. Everything below depends on position, so the ordering has to come first.
    val streams =
        category
            .entrySet()
            .mapNotNull { (key, value) ->
                if (!key.startsWith("Stream ") || !value.isJsonObject) return@mapNotNull null
                val number = key.removePrefix("Stream ").trim().toIntOrNull() ?: return@mapNotNull null
                number to value.asJsonObject
            }.sortedBy { (number, _) -> number }

    val audio = mutableListOf<MediaTrack>()
    val subtitles = mutableListOf<MediaTrack>()
    streams.forEach { (number, stream) ->
        // Language first, then codec, then a numbered fallback: a track row with a blank name is
        // unusable, and plenty of files name neither.
        val label =
            stream.string("Language")
                ?: stream.string("Description")
                ?: stream.string("Codec")
                ?: "Faixa ${number + 1}"
        // VLC's own remote-control interface lists the elementary-stream id beside each track.
        // On the bundled VLC 3.0.23 those ids are the numbers in "Stream N" exactly. Adding one
        // made Portuguese (Stream 1) select English (id 2), then made English send nonexistent id
        // 3 and stop playback.
        val trackId = number
        // Matched loosely on purpose. VLC reports subtitle streams under several type names
        // depending on the source — "Subtitle" from a file, "Teletext" and "DVB Subtitle" from a
        // broadcast stream, which is most of what live TV carries. Matching only "subtitle" left
        // live channels with visible captions and no control to turn them off, because as far as
        // the player was concerned they had no subtitle tracks at all.
        val type = stream.string("Type")?.lowercase().orEmpty()
        when {
            type == "audio" -> audio += MediaTrack(trackId, label)
            type.contains("subtitle") || type.contains("teletext") ->
                subtitles += MediaTrack(trackId, label)
            else -> Unit
        }
    }

    // Whether anything is being displayed right now, whatever the stream list says.
    //
    // A broadcast can have a subtitle track selected that never appeared in the status document's
    // stream list — the demuxer knows about it, the enumeration does not. Without this the player
    // showed captions the viewer could see and offered no way to remove them.
    val reportedSubtitle = longOrNull("subtitle_track")?.toInt()
    val subtitleShowing = reportedSubtitle != null && reportedSubtitle >= 0

    return PlaybackTracks(
        audio = audio,
        // The off entry is synthesised: VLC exposes no track for "no subtitles", but it accepts -1
        // to turn them off, and without a row for it they could be switched on and never off.
        //
        // ## Captions this cannot remove
        //
        // Many live channels — sports especially — have the operator's captions burned into the
        // picture upstream. To the decoder they are pixels, and no command removes them. Verified
        // on a real channel: `subtitle_track -> -1` was sent, VLC accepted it without error, and the
        // captions stayed.
        //
        // The off row is still offered whenever a track is selected, because the two cases are
        // indistinguishable from here and the control is harmless when it has nothing to do. What is
        // *not* done is offering it when no track exists at all: a button that visibly fails is
        // worse than one that is absent, and on a burned-in stream that is the only honest state.
        subtitles =
            when {
                subtitles.isNotEmpty() -> listOf(MediaTrack(-1, "Desligado")) + subtitles
                subtitleShowing -> listOf(MediaTrack(-1, "Desligado"))
                else -> emptyList()
            },
        // Stock VLC 3.0 status.json omits both fields. Missing is unknown, not track zero.
        activeAudio = longOrNull("audio_track")?.toInt()?.takeIf { it >= 0 },
        activeSubtitle = reportedSubtitle,
    )
}

internal data class PlaybackTracks(
    val audio: List<MediaTrack> = emptyList(),
    val subtitles: List<MediaTrack> = emptyList(),
    val activeAudio: Int? = null,
    val activeSubtitle: Int? = null,
)

private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

private fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L

private fun JsonObject.longOrNull(name: String): Long? =
    get(name)?.takeUnless { it.isJsonNull }?.runCatching { asLong }?.getOrNull()

private fun JsonObject.double(name: String): Double? = get(name)?.takeUnless { it.isJsonNull }?.asDouble
