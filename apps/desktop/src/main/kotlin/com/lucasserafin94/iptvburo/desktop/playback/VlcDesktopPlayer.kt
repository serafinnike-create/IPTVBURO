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
            Thread(task, "iptvburo-vlc-control").apply { isDaemon = true }
        }
    private var process: Process? = null
    private var remote: VlcHttpControl? = null
    private var mediaStartedAt = 0L
    private var everPlayed = false

    fun createComponent(request: DesktopPlaybackRequest): JPanel {
        val canvas =
            object : Canvas() {
                override fun addNotify() {
                    super.addNotify()
                    SwingUtilities.invokeLater { startIfNeeded(this, request) }
                }
            }.apply {
                background = Color.BLACK
                isFocusable = true
            }
        return JPanel(BorderLayout()).apply {
            background = Color.BLACK
            add(canvas, BorderLayout.CENTER)
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

    fun setPlaybackRate(value: Double) {
        val safe = value.coerceIn(0.5, 2.0)
        snapshot = snapshot.copy(playbackRate = safe)
        executeCommand("rate", mapOf("val" to safe.toString()))
    }

    fun retry(request: DesktopPlaybackRequest) {
        executor.execute {
            runCatching {
                remote?.command("pl_stop")
                remote?.command("in_play", mapOf("input" to request.uri.toASCIIString()))
                snapshot = snapshot.copy(errorMessage = null, loading = true, ended = false)
                mediaStartedAt = System.currentTimeMillis()
                everPlayed = false
            }
        }
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        runCatching { remote?.command("pl_stop") }
        remote = null
        runCatching { process?.destroy() }
        process = null
        executor.shutdownNow()
    }

    private fun startIfNeeded(canvas: Canvas, request: DesktopPlaybackRequest) {
        if (disposed.get() || !started.compareAndSet(false, true)) return
        snapshot = snapshot.copy(loading = true, errorMessage = null)
        executor.execute {
            runCatching { startVlc(canvas, request) }
                .onFailure {
                    snapshot =
                        snapshot.copy(
                            loading = false,
                            playing = false,
                            errorMessage = "O motor de vídeo do Windows não pôde ser iniciado.",
                        )
                }
        }
    }

    private fun startVlc(canvas: Canvas, request: DesktopPlaybackRequest) {
        val executable = locateVlcExecutable() ?: error("Bundled VLC runtime was not found")
        val port = freeLoopbackPort()
        val password = randomPassword()
        val windowHandle = Pointer.nativeValue(Native.getComponentPointer(canvas))
        process =
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
                "--avcodec-hw=any",
                "--quiet",
            ).directory(executable.parentFile)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        val control = VlcHttpControl.connect(port, password, CONNECT_TIMEOUT_MILLIS)
        remote = control
        control.command("volume", mapOf("val" to (snapshot.volume * VLC_VOLUME_MAX).toInt().toString()))
        control.command("in_play", mapOf("input" to request.uri.toASCIIString()))
        mediaStartedAt = System.currentTimeMillis()
        if (request.startPositionMillis > 0L) {
            executor.schedule({ seekToMillis(request.startPositionMillis) }, 2, TimeUnit.SECONDS)
        }
        executor.scheduleWithFixedDelay(::pollState, 250, 500, TimeUnit.MILLISECONDS)
    }

    private fun pollState() {
        if (disposed.get()) return
        val control = remote ?: return
        runCatching {
            val status = control.status()
            val stateName = status.string("state")?.lowercase()
            val playing = stateName == "playing"
            val paused = stateName == "paused"
            val ready = playing || paused || stateName == "stopped"
            val lengthSeconds = status.long("length")
            if (playing) everPlayed = true
            val ended = everPlayed && stateName == "stopped" && lengthSeconds > 0L
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
                    errorMessage =
                        if (!ready && System.currentTimeMillis() - mediaStartedAt > START_TIMEOUT_MILLIS) {
                            "O servidor respondeu, mas este vídeo não iniciou. Tente novamente ou escolha outro título."
                        } else {
                            null
                        },
                )
        }.onFailure {
            if (process?.isAlive == false) {
                snapshot =
                    snapshot.copy(
                        loading = false,
                        playing = false,
                        errorMessage = "O motor de vídeo foi encerrado inesperadamente.",
                    )
            }
        }
    }

    private fun seekToMillis(positionMillis: Long) {
        executeCommand("seek", mapOf("val" to "${positionMillis.coerceAtLeast(0L) / 1_000L}S"))
    }

    private fun executeCommand(command: String, parameters: Map<String, String> = emptyMap()) {
        executor.execute { runCatching { remote?.command(command, parameters) } }
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
        const val VLC_VOLUME_MAX = 256
        const val CONNECT_TIMEOUT_MILLIS = 12_000L
        const val START_TIMEOUT_MILLIS = 25_000L
    }
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

private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

private fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L

private fun JsonObject.double(name: String): Double? = get(name)?.takeUnless { it.isJsonNull }?.asDouble
