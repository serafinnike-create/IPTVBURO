package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps one running app, and forwards share links to it.
 *
 * Clicking a shared link asks Windows to run the registered handler, and Windows does that by
 * starting the program again — it has no idea one is already open. Without an owner check that
 * means a second window, a second catalogue held in memory against the same subscription, and the
 * window the user was actually looking at never moving to the title they clicked.
 *
 * The mechanism is a loopback [ServerSocket] on an ephemeral port, with the port written to a file
 * under the user's own app data:
 *
 * - the **first** instance binds the socket, records the port and keeps listening;
 * - a **later** instance reads the port, connects, sends its link and exits.
 *
 * A socket rather than a lock file alone, because a lock file answers "is one running?" and the
 * problem also requires *handing something over*. Bound to loopback explicitly, so this never
 * listens on a real network interface, and the payload is validated by [TitleShareLink.parse] on
 * arrival — the same rule applied to a link from any other origin.
 */
object SingleInstance {
    private val listener = AtomicReference<ServerSocket?>(null)

    /**
     * A link handed over by a second instance, waiting to be picked up by the UI.
     *
     * Held rather than dispatched because a link can arrive before the window is composed; the app
     * polls this once it is ready to navigate.
     */
    private val handedOver = AtomicReference<TitleShareLink?>(null)

    /**
     * True when this process should carry on and become the app.
     *
     * False means another instance is already running and has been given [link] — this process has
     * nothing left to do and should exit without drawing anything.
     */
    fun claim(link: TitleShareLink?): Boolean {
        // Anything unexpected here must not stop the app from starting. A failure to claim
        // ownership degrades to the old behaviour — a second window — which is worse than one
        // window and far better than no window.
        val existingPort = runCatching { readPort() }.getOrNull()
        if (existingPort != null && runCatching { forward(existingPort, link) }.getOrDefault(false)) {
            return false
        }

        return runCatching {
            // Port 0: the OS picks a free one, so nothing is hardcoded and two users on the same
            // machine cannot collide.
            val socket = ServerSocket(0, SOCKET_BACKLOG, InetAddress.getLoopbackAddress())
            listener.set(socket)
            writePort(socket.localPort)
            handedOver.set(link)
            startListening(socket)
            true
        }.getOrDefault(true)
    }

    /** Takes the link handed over by a second instance, if there is one. Consumes it. */
    fun takeHandedOverLink(): TitleShareLink? = handedOver.getAndSet(null)

    /**
     * Called on the listener thread when a link arrives, so the UI need not poll for one.
     *
     * The first version of this woke a coroutine once a second for the whole session to look at a
     * slot that is almost always empty — 3,600 wakeups an hour on an idle app, for an event most
     * users never trigger. The listener already knows the moment a link lands; it just had no way
     * to say so.
     */
    @Volatile
    private var onLinkReceived: (() -> Unit)? = null

    fun setLinkListener(listener: (() -> Unit)?) {
        onLinkReceived = listener
    }

    /** Releases the port file and stops listening. Safe to call when nothing was ever claimed. */
    fun release() {
        runCatching { listener.getAndSet(null)?.close() }
        runCatching { Files.deleteIfExists(portFile()) }
    }

    private fun startListening(socket: ServerSocket) {
        Thread {
            // Runs until the socket is closed by `release()`, at which point accept throws and the
            // loop ends.
            while (!socket.isClosed) {
                val accepted = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    accepted.use { client ->
                        client.soTimeout = READ_TIMEOUT_MILLIS
                        val line =
                            client.getInputStream()
                                .bufferedReader(Charsets.UTF_8)
                                .let(BufferedReader::readLine)
                                .orEmpty()
                        // Bounded before parsing: this socket is reachable by any process on the
                        // machine, so the input is treated as untrusted regardless of how it got
                        // here. Anything that is not a share link is simply dropped.
                        if (line.length <= MAX_PAYLOAD_LENGTH) {
                            TitleShareLink.parse(line)?.let { link ->
                                handedOver.set(link)
                                // Published before waking anyone, so the listener always finds it.
                                runCatching { onLinkReceived?.invoke() }
                            }
                        }
                    }
                }
            }
        }.apply {
            name = "iptvburo-single-instance"
            // Daemon, so a listener waiting on accept can never keep the app from exiting.
            isDaemon = true
        }.start()
    }

    /**
     * Sends [link] to the instance listening on [port].
     *
     * Returns false when nothing answers, which is the ordinary case for a stale port file left by
     * a process that crashed. The caller then takes ownership itself.
     */
    private fun forward(
        port: Int,
        link: TitleShareLink?,
    ): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MILLIS)
                socket.getOutputStream().use { output ->
                    // A newline terminates the payload; the reader is line-based.
                    output.write("${link?.appUri().orEmpty()}\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
            true
        }.getOrDefault(false)

    private fun readPort(): Int? =
        portFile()
            .takeIf(Files::isRegularFile)
            ?.let { path -> runCatching { Files.readString(path).trim().toIntOrNull() } .getOrNull() }
            ?.takeIf { it in 1..65_535 }

    private fun writePort(port: Int) {
        val file = portFile()
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            port.toString(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    /**
     * Under the user's own app data, beside the rest of this app's state.
     *
     * Not the system temp directory: that is world-writable on a shared machine, and another user
     * could point this file at a port of their choosing.
     */
    private fun portFile(): Path {
        val base =
            System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
        return Path.of(base, "IPTVBURO", "single-instance.port")
    }

    private const val SOCKET_BACKLOG = 4
    private const val CONNECT_TIMEOUT_MILLIS = 800
    private const val READ_TIMEOUT_MILLIS = 2_000

    /** A share link is a few hundred bytes; this is generous and still bounds the read. */
    private const val MAX_PAYLOAD_LENGTH = 8 * 1024
}
