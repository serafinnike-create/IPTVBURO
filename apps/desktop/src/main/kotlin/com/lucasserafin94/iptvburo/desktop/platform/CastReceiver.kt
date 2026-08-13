package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.CastMessage
import java.io.BufferedReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Lets a phone on the same network send this computer a title to play.
 *
 * ## The shape of it
 *
 * Two sockets, both bound to the local network and neither reachable from the internet:
 *
 * - a **UDP** socket that answers "are there any BURO screens here?" with this machine's name and
 *   TCP port, so the phone can list what is available without being told an address;
 * - a **TCP** socket that receives one [CastMessage] per connection.
 *
 * ## Why this is off unless the user turns it on
 *
 * Everything else this app does reaches outwards. This listens, which is a different kind of risk,
 * so it is not running by default: the user opens the receiver, sees a four-digit code, and types
 * that code once into the phone. Without the code a message is discarded before anything is looked
 * up, and the code changes every time the receiver is started.
 *
 * The code is what makes "someone else's wifi" safe — a shared building, a hotel, a café. It is not
 * a secret worth protecting for long, which is why it is short and regenerated rather than stored.
 */
class CastReceiver(
    /** How this machine introduces itself in the device list on the phone. */
    private val displayName: String,
) {
    private val tcpSocket = AtomicReference<ServerSocket?>(null)
    private val udpSocket = AtomicReference<DatagramSocket?>(null)
    private val received = AtomicReference<CastMessage?>(null)

    /** The code the user reads off this screen and types into the sender. Null when stopped. */
    @Volatile
    var pairingCode: String? = null
        private set

    /**
     * The TCP port senders connect to, or null when stopped.
     *
     * Published because the port is ephemeral and otherwise only discoverable over multicast. A
     * sender on the network should still find it that way; a *test* should not have to, since a CI
     * runner has no dependable multicast loopback and the check would then be measuring the
     * runner's network rather than this class.
     */
    @Volatile
    var listeningPort: Int? = null
        private set

    @Volatile
    private var onMessage: ((CastMessage) -> Unit)? = null

    /**
     * Starts listening and returns the pairing code, or null when the sockets cannot be opened.
     *
     * Null is a real outcome rather than an exception: a firewall refusing the bind is a reason for
     * the feature to be unavailable, not a reason for the app to fail.
     */
    fun start(onMessage: (CastMessage) -> Unit): String? {
        stop()
        return runCatching {
            // A fresh code per session. It is a proof of being in the room, not a password, and
            // regenerating it means a code glimpsed once does not work tomorrow.
            val code = (1..CastMessage.PAIRING_CODE_LENGTH)
                .map { Random.nextInt(0, 10) }
                .joinToString("")

            val tcp = ServerSocket(0, TCP_BACKLOG)
            // Bound with address reuse rather than by the convenience constructor.
            //
            // The discovery port is fixed, so a socket left in TIME_WAIT by the previous session
            // makes the next bind fail — and `start` reports that failure as "the feature is
            // unavailable", which is a poor answer to "I turned it off and on again". Reuse is the
            // ordinary setting for a server that expects to be restarted on the same port, and it
            // is what makes stopping and starting the receiver dependable.
            val udp =
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }

            this.onMessage = onMessage
            pairingCode = code
            listeningPort = tcp.localPort
            tcpSocket.set(tcp)
            udpSocket.set(udp)

            startAcceptLoop(tcp, code)
            startDiscoveryLoop(udp, tcp.localPort)
            code
        }.getOrElse {
            stop()
            null
        }
    }

    fun stop() {
        pairingCode = null
        listeningPort = null
        onMessage = null
        runCatching { tcpSocket.getAndSet(null)?.close() }
        runCatching { udpSocket.getAndSet(null)?.close() }
        received.set(null)
    }

    /** The last message received, consumed. */
    fun takeMessage(): CastMessage? = received.getAndSet(null)

    private fun startAcceptLoop(socket: ServerSocket, code: String) {
        Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { connection ->
                        connection.soTimeout = READ_TIMEOUT_MILLIS
                        val line = readBoundedLine(connection.getInputStream().bufferedReader(Charsets.UTF_8))
                        // Decoded, not trusted. The pairing code is checked inside decode, and a
                        // message that fails any check is simply dropped — an exception here would
                        // be a way for anyone on the network to stop the listener.
                        CastMessage.decode(line, code)?.let { message ->
                            received.set(message)
                            runCatching { onMessage?.invoke(message) }
                        }
                    }
                }
            }
        }.apply {
            name = "iptvburo-cast-accept"
            // Daemon, so a listener waiting on accept can never keep the app from exiting.
            isDaemon = true
        }.start()
    }

    /**
     * Reads one line, refusing to grow past what a valid message can be.
     *
     * `BufferedReader.readLine` reads until a newline arrives, with no upper bound. Anyone on the
     * network can open this socket, so a sender that never sends a newline would have three seconds
     * — the read timeout — to make this allocate as much memory as their link can carry. The
     * timeout ends the connection but does nothing about what was already buffered.
     *
     * [CastMessage.MAX_ENCODED_LENGTH] is the whole budget: a longer line cannot decode into
     * anything, so there is no reason to hold it. Reading one character past the limit is enough to
     * know the line is oversized, and the answer is the same as for any other malformed input —
     * drop it, silently, without throwing.
     */
    private fun readBoundedLine(reader: BufferedReader): String {
        val builder = StringBuilder()
        while (builder.length <= CastMessage.MAX_ENCODED_LENGTH) {
            val next = reader.read()
            // End of stream or end of line: what was collected is the whole message.
            if (next < 0 || next == '\n'.code) return builder.toString()
            if (next != '\r'.code) builder.append(next.toChar())
        }
        // Oversized. Returning empty rather than the truncated head, because a prefix of something
        // too long is not a message that was sent — decoding it would be inventing one.
        return ""
    }

    /**
     * Answers discovery probes with this machine's name and port.
     *
     * The reply deliberately carries **no** pairing code: a device that has not been told the code
     * must not be able to learn it by asking. Discovery says "there is a screen here called X";
     * proving you are allowed to use it is a separate step.
     */
    private fun startDiscoveryLoop(socket: DatagramSocket, tcpPort: Int) {
        Thread {
            val buffer = ByteArray(DISCOVERY_BUFFER_BYTES)
            while (!socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                val probe = runCatching { socket.receive(packet) }.isSuccess
                if (!probe) break
                runCatching {
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (text != DISCOVERY_PROBE) return@runCatching
                    val reply = "$DISCOVERY_REPLY\u001F$tcpPort\u001F$displayName".toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                }
            }
        }.apply {
            name = "iptvburo-cast-discovery"
            isDaemon = true
        }.start()
    }

    companion object {
        /**
         * Fixed port for discovery only.
         *
         * The port that actually carries a message is ephemeral and announced in the reply, so two
         * machines on one network do not collide on it.
         */
        const val DISCOVERY_PORT = 45_517

        const val DISCOVERY_PROBE = "buro-cast-discover-1"
        const val DISCOVERY_REPLY = "buro-cast-here-1"

        private const val TCP_BACKLOG = 2
        private const val READ_TIMEOUT_MILLIS = 3_000
        private const val DISCOVERY_BUFFER_BYTES = 256

        /** Finds receivers on this network, for the sender's device list. */
        fun discover(timeoutMillis: Int = 1_200): List<CastTarget> =
            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = timeoutMillis
                    val probe = DISCOVERY_PROBE.toByteArray(Charsets.UTF_8)
                    socket.send(
                        DatagramPacket(
                            probe,
                            probe.size,
                            InetSocketAddress(InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT),
                        ),
                    )

                    val found = LinkedHashMap<String, CastTarget>()
                    val buffer = ByteArray(DISCOVERY_BUFFER_BYTES)
                    val deadline = System.currentTimeMillis() + timeoutMillis
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        if (!runCatching { socket.receive(packet) }.isSuccess) break
                        val parts =
                            String(packet.data, 0, packet.length, Charsets.UTF_8).split('\u001F')
                        if (parts.size != 3 || parts[0] != DISCOVERY_REPLY) continue
                        val port = parts[1].toIntOrNull()?.takeIf { it in 1..65_535 } ?: continue
                        val name = parts[2].take(60).ifBlank { packet.address.hostAddress }
                        // Keyed by address, so a machine answering twice appears once.
                        found[packet.address.hostAddress] = CastTarget(packet.address.hostAddress, port, name)
                    }
                    found.values.toList()
                }
            }.getOrDefault(emptyList())

        /**
         * Hands [message] to [target].
         *
         * The other half of the protocol, so this machine can send as well as receive: a title
         * opened on the computer can be pushed to the television in the next room, or to a phone.
         *
         * True only means the bytes were delivered. Whether the screen *accepted* them depends on
         * the pairing code, and a receiver answers a wrong code with silence rather than a refusal
         * — so the sender genuinely cannot tell a mistyped code from a screen that stopped
         * listening, and the UI says "sent" rather than claiming playback started.
         *
         * What travels is which title, never a URL: the receiving screen looks it up in its own
         * catalogue and plays from the provider itself, so this machine's credentials stay here.
         */
        fun send(target: CastTarget, message: CastMessage): Boolean =
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(target.address, target.port), CONNECT_TIMEOUT_MILLIS)
                    socket.getOutputStream().use { output ->
                        // Newline-terminated: the receiver reads one line, which is what bounds how
                        // much it will take from a connection.
                        output.write("${message.encode()}\n".toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                }
                true
            }.getOrDefault(false)

        private const val CONNECT_TIMEOUT_MILLIS = 2_500
    }
}

/** A screen that answered discovery and can be sent a title. */
data class CastTarget(
    val address: String,
    val port: Int,
    val displayName: String,
)
