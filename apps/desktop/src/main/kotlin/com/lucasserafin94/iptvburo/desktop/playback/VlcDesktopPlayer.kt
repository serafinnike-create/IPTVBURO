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
import java.net.URLEncoder
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
class VlcDesktopPlayer {
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

    /** One silent retry per player, for the cold-start stall described in pollState. */
    private val firstRetryUsed = AtomicBoolean(false)

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

    fun createComponent(
        request: DesktopPlaybackRequest,
        onPointerActivity: () -> Unit = {},
        onKey: (Int) -> Boolean = { false },
    ): JPanel {
        val canvas =
            object : Canvas() {
                override fun addNotify() {
                    super.addNotify()
                    SwingUtilities.invokeLater { startIfNeeded(this, request) }
                }
            }.apply {
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

    fun setVolume(value: Double) {
        val safe = value.coerceIn(0.0, 1.0)
        snapshot = snapshot.copy(volume = safe)
        executeCommand("volume", mapOf("val" to (safe * VLC_VOLUME_MAX).toInt().toString()))
    }

    /** Switches audio track; the id is VLC's own, taken from the status it reported. */
    fun selectAudioTrack(trackId: Int) = executeCommand("audio_track", mapOf("val" to trackId.toString()))

    /** Switches or disables subtitles. VLC uses -1 for off, which is why the "off" entry carries it. */
    fun selectSubtitleTrack(trackId: Int) = executeCommand("subtitle_track", mapOf("val" to trackId.toString()))

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
        val password = randomPassword()

        // The native peer is not always realised by the time addNotify fires. When it is not, the
        // pointer comes back null and VLC is told to draw into window handle 0 — it starts, reports
        // no error, and renders nowhere. That is the intermittent black screen that a close and
        // reopen "fixed": the second attempt happened to win the race.
        val windowHandle = awaitComponentHandle(canvas)
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
                // Hardware decoding disabled. It is what fails first on 4K HDR and Dolby Vision
                // files: the picture never arrives while the controls sit there at 00:00. Software
                // decoding is slower but plays everything this catalogue carries.
                "--avcodec-hw=none",
                "--quiet",
            ).directory(executable.parentFile)
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
            val playing = stateName == "playing"
            val paused = stateName == "paused"
            val ready = playing || paused || stateName == "stopped"
            val lengthSeconds = status.long("length")
            if (playing) everPlayed = true
            val ended = everPlayed && stateName == "stopped" && lengthSeconds > 0L
            val tracks = status.readTracks()

            // The first title of a session often stops without ever playing: VLC has to bring up
            // the Direct3D device and attach it to a surface Compose is still settling, and the
            // second attempt worked only because that work was already done. Retried once,
            // silently, which is exactly what the user was doing by hand.
            if (
                !everPlayed &&
                stateName == "stopped" &&
                System.currentTimeMillis() - mediaStartedAt > FIRST_START_RETRY_MILLIS &&
                firstRetryUsed.compareAndSet(false, true)
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
            }
        }
    }

    private fun locateVlcExecutable(): File? {
        val resources = System.getProperty("compose.application.resources.dir")?.let(::File)
        val workingDirectory = File(System.getProperty("user.dir"))
        return listOfNotNull(
            resources?.resolve("vlc/vlc.exe"),
            resources?.resolve("windows/vlc/vlc.exe"),
            workingDirectory.resolve("apps/desktop/build/generated/app-resources/windows/vlc/vlc.exe"),
            File("C:/Program Files/VideoLAN/VLC/vlc.exe"),
            File("C:/Program Files (x86)/VideoLAN/VLC/vlc.exe"),
        ).firstOrNull(File::isFile)
    }

    private fun freeLoopbackPort(): Int =
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    private fun randomPassword(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        /** Roughly two seconds in total; the peer normally arrives within a frame or two. */
        const val HANDLE_ATTEMPTS = 100
        const val HANDLE_POLL_MILLIS = 20L

        const val VLC_VOLUME_MAX = 256
        const val CONNECT_TIMEOUT_MILLIS = 12_000L
        const val START_TIMEOUT_MILLIS = 25_000L

        /**
         * How long to let a title sit at "stopped" before retrying it once.
         *
         * Long enough that a genuinely slow provider is not interrupted mid-open, short enough that
         * the user has not yet reached for the close button.
         */
        const val FIRST_START_RETRY_MILLIS = 4_000L

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

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

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
private fun JsonObject.readTracks(): PlaybackTracks {
    val information = get("information")?.takeUnless { it.isJsonNull }?.asJsonObject
        ?: return PlaybackTracks()
    val category = information.get("category")?.takeUnless { it.isJsonNull }?.asJsonObject
        ?: return PlaybackTracks()

    val audio = mutableListOf<MediaTrack>()
    val subtitles = mutableListOf<MediaTrack>()
    category.entrySet().forEach { (key, value) ->
        if (!key.startsWith("Stream ") || !value.isJsonObject) return@forEach
        val stream = value.asJsonObject
        val id = key.removePrefix("Stream ").trim().toIntOrNull() ?: return@forEach
        // Language first, then codec, then a numbered fallback: a track row with a blank name is
        // unusable, and plenty of files name neither.
        val label =
            stream.string("Language")
                ?: stream.string("Description")
                ?: stream.string("Codec")
                ?: "Faixa ${id + 1}"
        when (stream.string("Type")?.lowercase()) {
            "audio" -> audio += MediaTrack(id, label)
            "subtitle", "subtitles" -> subtitles += MediaTrack(id, label)
            else -> Unit
        }
    }

    return PlaybackTracks(
        audio = audio,
        // The off entry is synthesised: VLC exposes no track for "no subtitles", but it accepts -1
        // to turn them off, and without a row for it they could be switched on and never off.
        subtitles = if (subtitles.isEmpty()) emptyList() else listOf(MediaTrack(-1, "Desligado")) + subtitles,
        activeAudio = long("audio_track").toInt().takeIf { it >= 0 },
        activeSubtitle = long("subtitle_track").toInt(),
    )
}

private data class PlaybackTracks(
    val audio: List<MediaTrack> = emptyList(),
    val subtitles: List<MediaTrack> = emptyList(),
    val activeAudio: Int? = null,
    val activeSubtitle: Int? = null,
)

private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

private fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L

private fun JsonObject.double(name: String): Double? = get(name)?.takeUnless { it.isJsonNull }?.asDouble
