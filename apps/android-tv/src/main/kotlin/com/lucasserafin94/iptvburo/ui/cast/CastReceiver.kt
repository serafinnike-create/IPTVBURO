package com.lucasserafin94.iptvburo.ui.cast

import android.content.Context
import android.net.wifi.WifiManager
import com.lucasserafin94.iptvburo.domain.model.CastMessage
import java.io.BufferedReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Lets a computer on the same network send this phone a title to open.
 *
 * The mirror image of [CastSender], and the counterpart of the desktop's own receiver: the phone
 * could already find screens and send to them, but nothing on this side ever answered a probe. The
 * desktop would search, find nothing, and report an empty network — while the phone sat on the same
 * wifi with the app open. Sending only ever worked phone → computer.
 *
 * Two sockets, both local and neither reachable from the internet:
 *
 * - a **UDP** socket on the fixed discovery port, answering "any BURO screens here?" with this
 *   phone's name and TCP port;
 * - a **TCP** socket taking one [CastMessage] per connection.
 *
 * ## The pairing code
 *
 * Being on the same network is not a permission — on a café or a building's wifi, everybody is. The
 * sender has to type four digits shown on this screen, and a message that fails that check is
 * dropped before anything is looked up.
 *
 * ## Why this only listens while the app is open
 *
 * Deliberate, and different from the desktop, which keeps its receiver up for the life of the
 * process. A phone that listened in the background would need a foreground service and its
 * permanent notification, to receive something whose only effect is opening a page in an app the
 * user would have to bring forward anyway. The socket closes when the screen does.
 */
class CastReceiver(
    private val context: Context,
    /** How this phone introduces itself in the sender's list. */
    private val displayName: String,
) {
    private val tcpSocket = AtomicReference<ServerSocket?>(null)
    private val udpSocket = AtomicReference<DatagramSocket?>(null)
    private val multicastLock = AtomicReference<WifiManager.MulticastLock?>(null)

    /** Wrong pairing codes seen in a row, which is what the guessing brake counts. */
    private val consecutiveFailures = AtomicInteger(0)

    /**
     * Wrong codes spent by each sender address.
     *
     * Keyed on the socket's own peer rather than anything the sender claims, which is what makes it
     * worth having: a value read out of the message would be chosen by the attacker.
     */
    private val failuresByAddress = ConcurrentHashMap<String, AtomicInteger>()

    /** The code the user reads off this screen and types into the sender. Null when stopped. */
    @Volatile
    var pairingCode: String? = null
        private set

    /** The TCP port senders connect to, or null when stopped. Published for tests. */
    @Volatile
    var listeningPort: Int? = null
        private set

    @Volatile
    private var onMessage: ((CastMessage) -> Unit)? = null

    /**
     * Starts listening and returns the pairing code, or null when the sockets cannot be opened.
     *
     * Null is an outcome rather than an exception: a network that refuses the bind — or no network
     * at all — is a reason for the feature to be unavailable, not for the screen to fail.
     *
     * [existingCode] is this device's kept code, so the number does not change on every launch and
     * have to be retyped. Passing null mints a fresh one.
     */
    fun start(
        existingCode: String? = null,
        onMessage: (CastMessage) -> Unit,
    ): String? {
        stop()
        return runCatching {
            val code =
                existingCode?.takeIf(::isWellFormedCode)
                    ?: (1..CastMessage.PAIRING_CODE_LENGTH)
                        .map { Random.nextInt(0, 10) }
                        .joinToString("")

            // Android drops broadcast and multicast packets not addressed to the device, to save
            // power. Without this lock the discovery probe never reaches the socket below and the
            // computer finds nothing — the exact failure [CastSender] holds the same lock to avoid.
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock =
                wifi?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply {
                    setReferenceCounted(true)
                    runCatching { acquire() }
                }
            multicastLock.set(lock)

            val tcp = ServerSocket(0, TCP_BACKLOG)
            // Opened through a channel forced to INET, with address reuse set explicitly.
            //
            // Reuse first, so a socket left in TIME_WAIT by the previous session does not make the
            // next bind fail on a fixed port — which would report as "unavailable" to somebody who
            // had merely closed and reopened the screen.
            //
            // IPv4 second, and that part is what made this device invisible. An unqualified bind
            // takes the family the runtime prefers, which is IPv6; discovery is an IPv4 *broadcast*
            // and an IPv4 broadcast is never delivered to an IPv6-bound socket. The phone was
            // listening on a port the desktop's search could not reach, so "no screens found" was
            // reported while both devices sat on the same network with both apps open. Asking for
            // 0.0.0.0 is not enough either — a dual-stack socket maps that back onto the IPv6
            // wildcard — so the family is stated outright. Fixed first on the desktop receiver.
            // See Ipv4Sockets for why the family has to be stated and why there are two ways of
            // doing it: the call that says "IPv4, really" needs API 24 and this app supports 23.
            val udp = Ipv4Sockets.openListener(DISCOVERY_PORT)
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
        consecutiveFailures.set(0)
        // Cleared with the sockets, so restarting the receiver is the way back for anyone who
        // genuinely locked themselves out by mistyping.
        failuresByAddress.clear()
        pairingCode = null
        listeningPort = null
        onMessage = null
        runCatching { tcpSocket.getAndSet(null)?.close() }
        runCatching { udpSocket.getAndSet(null)?.close() }
        runCatching {
            multicastLock.getAndSet(null)?.let { lock -> if (lock.isHeld) lock.release() }
        }
    }

    private fun startAcceptLoop(socket: ServerSocket, code: String) {
        Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                // Each connection on its own thread, so the brake below holds one sender without
                // holding the queue. Sleeping on the accept loop instead would fill the backlog and
                // then have the OS refuse everyone — including the owner typing the right code.
                Thread {
                    runCatching {
                        client.use { connection ->
                            // Refused outright once this address has spent its attempts.
                            //
                            // The delay below is not enough on its own, and this receiver shipped
                            // without the cap because it was ported from the desktop's own
                            // pre-fix version. The flaw is that the wait runs on the connection's
                            // thread: fifty sockets opened at once get fifty waits side by side, so
                            // each attempt is slowed and the total is not. Four digits is ten
                            // thousand possibilities, and discovery hands out this device's port to
                            // anyone who asks — so on a shared network finding the target is free.
                            //
                            // Closing beats sleeping: a wait still lets the next attempt run in
                            // parallel, which is the whole defect. The count is per address, so one
                            // guesser cannot lock the household out of its own screen.
                            val peer = connection.inetAddress?.hostAddress.orEmpty()
                            val spent = failuresByAddress[peer]?.get() ?: 0
                            if (spent >= MAX_FAILURES_PER_ADDRESS) return@use

                            connection.soTimeout = READ_TIMEOUT_MILLIS
                            val line =
                                readBoundedLine(
                                    connection.getInputStream().bufferedReader(Charsets.UTF_8),
                                )
                            // Decoded, not trusted: the pairing code is checked inside decode, and
                            // anything failing a check is dropped. Throwing here would hand anyone
                            // on the network a way to stop the listener.
                            val message = CastMessage.decode(line, code)
                            if (message == null) {
                                // Counted per address as well as globally: the growing wait is what
                                // makes a serial attack tedious, and the per-address cap is what
                                // bounds a parallel one.
                                failuresByAddress
                                    .computeIfAbsent(peer) { AtomicInteger(0) }
                                    .incrementAndGet()
                                val penalty = guessPenaltyMillis(consecutiveFailures.incrementAndGet())
                                if (penalty > 0) runCatching { Thread.sleep(penalty) }
                            } else {
                                consecutiveFailures.set(0)
                                // The right code clears this address, so somebody who mistyped a few
                                // times and then got it right is not locked out of their own screen.
                                failuresByAddress.remove(peer)
                                runCatching { onMessage?.invoke(message) }
                            }
                        }
                    }
                }.apply {
                    name = "iptvburo-cast-client"
                    isDaemon = true
                }.start()
            }
        }.apply {
            name = "iptvburo-cast-accept"
            isDaemon = true
        }.start()
    }

    /**
     * Answers discovery probes with this phone's name and TCP port.
     *
     * The reply says where to connect, never whether the sender may: proving that is the pairing
     * code's job, one step later.
     */
    private fun startDiscoveryLoop(socket: DatagramSocket, tcpPort: Int) {
        Thread {
            val buffer = ByteArray(DISCOVERY_BUFFER_BYTES)
            while (!socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                if (!runCatching { socket.receive(packet) }.isSuccess) break
                runCatching {
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (text != DISCOVERY_PROBE) return@runCatching
                    // Cleaned on the way out as well as in: a device name carrying the field
                    // separator would split the reply into four fields, which every reader
                    // rejects — and the name comes from a user-editable setting.
                    val safeName = displayNameFrom(displayName)
                    val reply =
                        "$DISCOVERY_REPLY\u001F$tcpPort\u001F$safeName".toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                }
            }
        }.apply {
            name = "iptvburo-cast-discovery"
            isDaemon = true
        }.start()
    }

    /**
     * Reads one line, refusing to grow past what a valid message can be.
     *
     * `readLine` has no upper bound, and anyone on the network can open this socket: a sender that
     * never sends a newline would otherwise have the whole read timeout to make this allocate as
     * much memory as their link can carry.
     */
    private fun readBoundedLine(reader: BufferedReader): String {
        val builder = StringBuilder()
        while (builder.length <= CastMessage.MAX_ENCODED_LENGTH) {
            val next = reader.read()
            if (next < 0 || next == '\n'.code) return builder.toString()
            if (next != '\r'.code) builder.append(next.toChar())
        }
        // Oversized. Empty rather than the truncated head: a prefix of something too long is not a
        // message that was sent, and decoding it would be inventing one.
        return ""
    }

    companion object {
        /**
         * Fixed for discovery only; the port carrying a message is ephemeral and announced.
         *
         * Must equal the sender's and the desktop receiver's, which is asserted in
         * `CastReceiverTest` rather than left to a comment — three copies of a number are three
         * chances for one of them to drift and for discovery to go quiet with nothing to show why.
         */
        const val DISCOVERY_PORT = 45_517

        const val DISCOVERY_PROBE = "buro-cast-discover-1"
        const val DISCOVERY_REPLY = "buro-cast-here-1"

        /**
         * Whether a stored code is still usable as one.
         *
         * A stored value is a file that anything can edit. A blank or truncated one would otherwise
         * become the code checked against, and an empty string matching an empty string is a
         * receiver that accepts anybody.
         */
        internal fun isWellFormedCode(candidate: String): Boolean =
            candidate.length == CastMessage.PAIRING_CODE_LENGTH && candidate.all(Char::isDigit)

        /**
         * The name this phone is allowed to announce.
         *
         * The separator and control characters are dropped rather than escaped: the name comes from
         * the profile, which is the user's own text, and one carrying a separator would produce a
         * reply no reader accepts. Bounded so a long name cannot pad a datagram past the buffer the
         * other end reads into.
         */
        internal fun displayNameFrom(claimed: String): String =
            claimed
                .map { character -> if (character.isISOControl()) ' ' else character }
                .joinToString("")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_DISPLAY_NAME_LENGTH)
                .ifBlank { "IPTV BURO" }

        /**
         * How long a wrong pairing code costs, given how many arrived in a row.
         *
         * Exposed so the rule can be asserted directly: timing it from the outside measures the
         * scheduler rather than the policy.
         */
        fun guessPenaltyMillis(consecutiveFailures: Int): Long =
            if (consecutiveFailures < FAILURES_BEFORE_DELAY) {
                0L
            } else {
                minOf(
                    FAILURE_DELAY_MILLIS * (consecutiveFailures - FAILURES_BEFORE_DELAY + 1),
                    MAX_FAILURE_DELAY_MILLIS,
                )
            }

        private const val MULTICAST_LOCK_TAG = "iptvburo-cast-receiver"
        private const val TCP_BACKLOG = 2
        private const val READ_TIMEOUT_MILLIS = 3_000
        private const val DISCOVERY_BUFFER_BYTES = 256
        private const val MAX_DISPLAY_NAME_LENGTH = 48

        /**
         * Wrong codes one address may spend before it is refused outright.
         *
         * Generous for somebody mistyping a four-digit number, and a fifth of a percent of the
         * space for a machine working through it. Cleared by a correct code from that address, and
         * by restarting the receiver — which is the way back for anyone who locked themselves out.
         */
        internal const val MAX_FAILURES_PER_ADDRESS = 20

        /** Wrong codes tolerated at full speed before the brake engages. */
        private const val FAILURES_BEFORE_DELAY = 3
        private const val FAILURE_DELAY_MILLIS = 1_000L
        private const val MAX_FAILURE_DELAY_MILLIS = 8_000L
    }
}
