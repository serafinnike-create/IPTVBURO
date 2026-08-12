package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.CastMessage
import java.io.BufferedReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
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
            val udp = DatagramSocket(DISCOVERY_PORT)

            this.onMessage = onMessage
            pairingCode = code
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
                        val line =
                            connection.getInputStream()
                                .bufferedReader(Charsets.UTF_8)
                                .let(BufferedReader::readLine)
                                .orEmpty()
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
                    val reply = "$DISCOVERY_REPLY$tcpPort$displayName".toByteArray(Charsets.UTF_8)
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
                            String(packet.data, 0, packet.length, Charsets.UTF_8).split('')
                        if (parts.size != 3 || parts[0] != DISCOVERY_REPLY) continue
                        val port = parts[1].toIntOrNull()?.takeIf { it in 1..65_535 } ?: continue
                        val name = parts[2].take(60).ifBlank { packet.address.hostAddress }
                        // Keyed by address, so a machine answering twice appears once.
                        found[packet.address.hostAddress] = CastTarget(packet.address.hostAddress, port, name)
                    }
                    found.values.toList()
                }
            }.getOrDefault(emptyList())
    }
}

/** A screen that answered discovery and can be sent a title. */
data class CastTarget(
    val address: String,
    val port: Int,
    val displayName: String,
)
