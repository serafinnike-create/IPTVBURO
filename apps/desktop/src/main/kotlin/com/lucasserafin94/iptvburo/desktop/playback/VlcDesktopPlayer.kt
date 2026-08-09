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
) {
    @Volatile
    private var snapshot = DesktopPlaybackSnapshot(engineName = "VLC")

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

    /** One silent retry per player, for the cold-start stall described in pollState. */
    /**
     * How many silent restarts this media has been given.
     *
     * One was not always enough: the device and the surface can both still be settling four seconds
     * in, and a single retry then failed the same way the first attempt did. Three attempts spread
     * over about twelve seconds cover it without the user ever seeing a retry happen.
     */
    private val startRetries = java.util.concurrent.atomic.AtomicInteger(0)

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
     * Brightens or darkens the picture, 0.0 to 2.0 with 1.0 as the source's own.
     *
     * VLC's adjust filter has to be switched on before any of its values take effect — sending
     * `brightness` alone changes nothing at all, which is easy to mistake for a broken control.
     */
    fun setBrightness(value: Double) {
        val safe = value.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
        snapshot = snapshot.copy(brightness = safe)
        executeCommand("adjust", mapOf("val" to "1"))
        executeCommand("brightness", mapOf("val" to safe.toString()))
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
        println("[player] subtitle_track -> $trackId")
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
            "[player] surface never became usable: displayable=${canvas.isDisplayable} " +
                "size=${canvas.width}x${canvas.height}",
        )
        error("The video surface was not ready")
    }

    private fun startIfNeeded(canvas: Canvas, request: DesktopPlaybackRequest) {
        if (disposed.get() || !started.compareAndSet(false, true)) return
        this.canvas = canvas
        snapshot = snapshot.copy(loading = true, errorMessage = null)
        val launch = {
            runCatching { startVlc(canvas, request) }
                .onFailure {
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
        println("[player] starting: handle=$windowHandle surface=${canvas.width}x${canvas.height}")
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
                "--network-caching=1500",
                "--file-caching=1000",
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
                "--brightness=1.0",
                // Subtitle appearance, declared at startup for the same reason as the adjust
                // filter: the text renderer is built with the video chain, and changing these
                // afterwards over the control interface has no effect on a running one.
                "--freetype-rel-fontsize=${subtitleStyle.vlcRelativeSize}",
                "--freetype-color=${subtitleStyle.textColour.vlcValue}",
                "--freetype-opacity=255",
                // A background box behind the text, which is the difference between readable and
                // not over a bright scene.
                "--freetype-background-opacity=${if (subtitleStyle.background) 160 else 0}",
                // Hardware decoding disabled. It is what fails first on 4K HDR and Dolby Vision
                // files: the picture never arrives while the controls sit there at 00:00. Software
                // decoding is slower but plays everything this catalogue carries.
                "--avcodec-hw=none",
                "--quiet",
            ).directory(executable.parentFile)
                // Discarded on purpose: VLC logs the MRL it was given, and for a provider source
                // that string carries the username and password. A log file of those is exactly
                // what this project's credential rules forbid, so failures are diagnosed through
                // the status the control interface reports instead.
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
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
                println("[player] state=$stateName length=${status.long("length")}")
            }
            val playing = stateName == "playing"
            val paused = stateName == "paused"
            val ready = playing || paused || stateName == "stopped"
            val lengthSeconds = status.long("length")
            if (playing) everPlayed = true
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
            if (
                !everPlayed &&
                !playing &&
                !paused &&
                // Not while a track switch is settling: the stream is deliberately down, and
                // re-issuing `in_play` here would restart the film from the beginning.
                !switchingTrack &&
                System.currentTimeMillis() - mediaStartedAt > FIRST_START_RETRY_MILLIS &&
                startRetries.get() < MAX_START_RETRIES &&
                startRetries.incrementAndGet() <= MAX_START_RETRIES
            ) {
                lastRequest?.let { request ->
                    runCatching {
                        control.command("in_play", mapOf("input" to request.uri.toVlcInput()))
                        mediaStartedAt = System.currentTimeMillis()
                    }
                }
                return@runCatching
            }

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
                        println("[player] command $command failed: ${error.javaClass.simpleName}")
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
