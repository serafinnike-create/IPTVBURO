package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.CastMessage
import java.io.BufferedReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.channels.DatagramChannel
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
 * so the user sees a four-digit code and types it once into the phone. Without the code a message
 * is discarded before anything is looked up.
 *
 * The code is what makes "someone else's wifi" safe — a shared building, a hotel, a café. Being on
 * the same network is not a permission: on a shared one, everybody is.
 *
 * It is kept per machine rather than minted each session. Rotating it meant the number on screen
 * was new on every launch and had to be retyped every time, and a pairing step repeated daily is
 * one people escape by switching the feature off — which protects nothing at all. A stored code
 * still stops a stranger reaching this screen; what it gives up is only defence against someone who
 * saw the code once and came back later. `regenerateCastPairingCode` is the way to revoke one.
 */
class CastReceiver(
    /** How this machine introduces itself in the device list on the phone. */
    private val displayName: String,
) {
    private val tcpSocket = AtomicReference<ServerSocket?>(null)
    private val udpSocket = AtomicReference<DatagramSocket?>(null)
    private val received = AtomicReference<CastMessage?>(null)

    /** Wrong pairing codes seen in a row, which is what the guessing brake counts. */
    private val consecutiveFailures = java.util.concurrent.atomic.AtomicInteger(0)

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

    /**
     * The address the discovery socket is bound to, or null when stopped.
     *
     * Published for the same reason [listeningPort] is: the family this lands on decides whether a
     * broadcast can arrive at all, and it has been wrong. Asserting it directly beats asserting it
     * through a datagram on a CI runner with no dependable broadcast loopback.
     */
    val discoveryBindAddress: java.net.InetAddress?
        get() = udpSocket.get()?.localAddress

    @Volatile
    private var onMessage: ((CastMessage) -> Unit)? = null

    /**
     * Starts listening and returns the pairing code, or null when the sockets cannot be opened.
     *
     * Null is a real outcome rather than an exception: a firewall refusing the bind is a reason for
     * the feature to be unavailable, not a reason for the app to fail.
     *
     * [existingCode] is this machine's kept code. Passing null mints a fresh one, which is what a
     * first run and an explicit "new code" both want.
     */
    fun start(
        existingCode: String? = null,
        onMessage: (CastMessage) -> Unit,
    ): String? {
        stop()
        return runCatching {
            // The machine's own code, reused across sessions.
            //
            // This used to mint a new one on every start, so the code on screen was different each
            // time the app opened and the phone had to be told again — every session, for a feature
            // whose entire appeal is not having to think about it. Reusing it means it is typed
            // once. What the code defends against is a stranger on a shared network reaching this
            // screen, and it defends against that just as well when it stays the same.
            val code =
                existingCode?.takeIf(::isWellFormedCode)
                    ?: (1..CastMessage.PAIRING_CODE_LENGTH)
                        .map { Random.nextInt(0, 10) }
                        .joinToString("")

            val tcp = ServerSocket(0, TCP_BACKLOG)
            // Opened through a channel forced to INET, with address reuse set explicitly.
            //
            // Reuse first: the discovery port is fixed, so a socket left in TIME_WAIT by the
            // previous session makes the next bind fail — and `start` reports that failure as "the
            // feature is unavailable", a poor answer to "I turned it off and on again".
            //
            //
            // A plain DatagramSocket binds the family the JVM prefers, and on Windows that is IPv6:
            // `netstat` showed this on `::`, and asking it for 0.0.0.0 was not enough either, since
            // a dual-stack socket maps the request straight back onto the IPv6 wildcard. Discovery
            // is an IPv4 *broadcast*, and an IPv4 broadcast is never delivered to an IPv6-bound
            // socket — so the receiver listened on a port no sender could reach. The phone was on
            // the same network with the app open and the desktop still said no screens were found.
            //
            // StandardProtocolFamily.INET is the one way to say "IPv4, really": it opens a socket
            // that has no IPv6 form to fall back to.
            val channel =
                DatagramChannel.open(StandardProtocolFamily.INET).apply {
                    setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_PORT))
                }
            val udp = channel.socket()

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
                // Each connection is handled on its own thread so the brake below can hold *one*
                // sender without holding the queue.
                //
                // Sleeping on this loop instead looked equivalent and was not: accept stops while
                // it sleeps, the listen backlog fills after TCP_BACKLOG connections, and the OS
                // then refuses everything else outright — including the person who owns the screen
                // and typed the right code. A guessing brake that locks out the owner is a denial
                // of service handed to anyone on the network for free.
                //
                // Daemon, like the accept thread, so a handler waiting on a read cannot keep the
                // app alive.
                Thread {
                    runCatching {
                    client.use { connection ->
                        connection.soTimeout = READ_TIMEOUT_MILLIS
                        val line = readBoundedLine(connection.getInputStream().bufferedReader(Charsets.UTF_8))
                        // Decoded, not trusted. The pairing code is checked inside decode, and a
                        // message that fails any check is simply dropped — an exception here would
                        // be a way for anyone on the network to stop the listener.
                        val message = CastMessage.decode(line, code)
                        if (message == null) {
                            // A wrong code costs the sender a wait that grows.
                            //
                            // The code is four digits: ten thousand possibilities, one TCP
                            // connection each, which a machine on the same network works through
                            // in seconds. Constant-time comparison stops the code leaking through
                            // timing but does nothing about simply trying them all, and the
                            // listener binds every interface — so on the café wifi this protects
                            // nothing without a brake.
                            //
                            // The brake: the guesser's own connection is held open, doing nothing,
                            // for a wait that grows with each consecutive miss. Ten thousand codes
                            // at several seconds each stops being an attack anyone runs.
                            //
                            // Held on the handler thread rather than the accept loop, so the queue
                            // keeps draining and the owner's correct code still gets through.
                            // Capped, and reset by any message that decodes.
                            val penalty = guessPenaltyMillis(consecutiveFailures.incrementAndGet())
                            if (penalty > 0) runCatching { Thread.sleep(penalty) }
                        } else {
                            consecutiveFailures.set(0)
                            received.set(message)
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
                    // Sanitised on the way out as well as on the way in. The name comes from
                    // COMPUTERNAME, which is not hostile but is not guaranteed to be free of the
                    // separator either, and a name carrying one would split the reply into four
                    // fields, which every reader rejects. Cleaning both ends means the wire format
                    // holds whatever the machine happens to be called.
                    val safeName = displayNameFrom(displayName, "IPTV BURO")
                    val reply = "$DISCOVERY_REPLY\u001F$tcpPort\u001F$safeName".toByteArray(Charsets.UTF_8)
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

        /**
         * Whether a stored code is still usable as one.
         *
         * A preferences value is a file on disk that anything can edit. A blank or truncated one
         * would otherwise become the code the receiver checks against, and an empty string matching
         * an empty string is a receiver that accepts anybody.
         */
        internal fun isWellFormedCode(candidate: String): Boolean =
            candidate.length == CastMessage.PAIRING_CODE_LENGTH && candidate.all(Char::isDigit)

        /**
         * How long a wrong pairing code costs, given how many have arrived in a row.
         *
         * Exposed so the rule can be asserted directly. Timing it from the outside measures the
         * scheduler rather than the policy: sending is fire-and-forget, so the wrong codes are
         * processed while the sender is still writing them.
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

        private const val TCP_BACKLOG = 2
        private const val READ_TIMEOUT_MILLIS = 3_000

        /**
         * Wrong codes tolerated at full speed before the brake engages.
         *
         * A person mistyping the code on a phone gets a couple of free goes; a machine working
         * through ten thousand possibilities does not.
         */
        private const val FAILURES_BEFORE_DELAY = 3

        /** Added per wrong code beyond the free ones, so guessing gets slower the longer it runs. */
        private const val FAILURE_DELAY_MILLIS = 500L

        /**
         * Ceiling on that delay.
         *
         * Without it a long burst of junk would park the accept thread for minutes and the feature
         * would be unusable for the person it belongs to — a denial of service handed to whoever
         * sent the junk. Five seconds per attempt still puts ten thousand guesses well beyond a
         * session on someone else's wifi.
         */
        private const val MAX_FAILURE_DELAY_MILLIS = 5_000L
        private const val DISCOVERY_BUFFER_BYTES = 256

        /** Finds receivers on this network, for the sender's device list. */
        fun discover(timeoutMillis: Int = 1_200): List<CastTarget> =
            runCatching {
                // Forced to IPv4 for the reason the receiver is: an unqualified socket may open on
                // IPv6, and an IPv6 socket cannot send an IPv4 broadcast at all — every send below
                // would be refused and the search would come back empty with nothing to show.
                DatagramChannel
                    .open(StandardProtocolFamily.INET)
                    .socket()
                    .use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = timeoutMillis
                    val probe = DISCOVERY_PROBE.toByteArray(Charsets.UTF_8)

                    // Every interface's own broadcast address, not just 255.255.255.255.
                    //
                    // A limited broadcast leaves by whichever single interface the routing table
                    // picks, and this machine has five: Wi-Fi plus Bluetooth and three link-local
                    // stubs sitting on 169.254/16. The probe left by one of those and the phone on
                    // the Wi-Fi never heard it — the app reported "no screens found" while both
                    // devices were on the same network, which is indistinguishable from the feature
                    // being broken. Sending to 192.168.1.255 *and* the global address covers both
                    // the ordinary case and the stacks that only honour one of them.
                    (broadcastAddresses() + InetAddress.getByName("255.255.255.255")).forEach { address ->
                        // Per address: one interface refusing the send — a disconnected adapter,
                        // a stack that rejects the global address — must not stop the others.
                        runCatching {
                            socket.send(
                                DatagramPacket(
                                    probe,
                                    probe.size,
                                    InetSocketAddress(address, DISCOVERY_PORT),
                                ),
                            )
                        }
                    }

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
                        // This machine's own reply, discarded.
                        //
                        // The receiver now starts with the app, so a probe sent to the broadcast
                        // address comes straight back to the socket that sent it and the machine
                        // lists *itself* as a screen to send to. Choosing it asks for a pairing
                        // code that is on this very screen, which reads as the phone having been
                        // found when the phone was never involved — and sending would deliver a
                        // title to the app it was sent from.
                        if (packet.address.hostAddress in localAddresses()) continue
                        val name = displayNameFrom(parts[2], packet.address.hostAddress)
                        // Keyed by address, so a machine answering twice appears once.
                        //
                        // The address is the packet's own source rather than anything the reply
                        // claims, which is what stops a responder pointing this app at a third
                        // machine: it can only offer itself.
                        found[packet.address.hostAddress] = CastTarget(packet.address.hostAddress, port, name)
                    }
                    found.values.toList()
                }
            }.getOrDefault(emptyList())

        /**
         * The broadcast address of every interface that is actually carrying traffic.
         *
         * Loopback and interfaces that are down are skipped, as is any interface with no broadcast
         * address of its own — a point-to-point link has none, and IPv6 has no broadcast at all.
         *
         * Link-local stubs (169.254/16) are kept rather than filtered: an adapter that failed DHCP
         * is a poor bet, but two machines on a cable with no router talk over exactly that range,
         * and one extra datagram costs nothing.
         */
        internal fun broadcastAddresses(): List<InetAddress> =
            runCatching {
                NetworkInterface
                    .getNetworkInterfaces()
                    .toList()
                    .filter { candidate ->
                        runCatching { candidate.isUp && !candidate.isLoopback }.getOrDefault(false)
                    }.flatMap { candidate -> candidate.interfaceAddresses }
                    .mapNotNull { address -> address.broadcast }
                    .distinct()
            }.getOrDefault(emptyList())

        /**
         * Every address this machine answers on, for recognising its own reply.
         *
         * Loopback included, and interfaces that are down as well: the cost of listing an address
         * this machine no longer uses is nothing, while missing one puts the machine back in its
         * own list of screens. Compared as text because that is the form the reply arrives in.
         */
        internal fun localAddresses(): Set<String> =
            runCatching {
                NetworkInterface
                    .getNetworkInterfaces()
                    .toList()
                    .flatMap { candidate -> candidate.inetAddresses.toList() }
                    .mapNotNull { address -> address.hostAddress }
                    .toSet()
            }.getOrDefault(emptySet())

        /**
         * The name a discovered screen is allowed to show, or the address when it offers none.
         *
         * The reply's name is free text from whoever answered, and anyone on the network can
         * answer. It ends up in a list the user picks from, so it is the one part of discovery that
         * can *deceive* rather than merely be wrong: a newline lets a responder paint extra lines
         * into the list, and a right-to-left override lets it reverse what is drawn, so a machine
         * can present itself as a name it does not have.
         *
         * Only characters that draw as themselves survive. Control characters and the bidirectional
         * formatting marks are dropped rather than escaped — there is no legitimate device name
         * that needs them, and showing an escape sequence would be its own kind of confusing.
         * Runs of whitespace collapse, so padding cannot push a suffix out of view.
         */
        internal fun displayNameFrom(claimed: String, address: String): String =
            claimed
                // Whitespace first, so a newline becomes a space rather than vanishing: dropping it
                // would weld "Sala" and "Administrador" into one word, which is harder to read than
                // the truth and no safer.
                .replace(WHITESPACE_RUN, " ")
                .filter { character ->
                    !Character.isISOControl(character) && character.category != CharCategory.FORMAT
                }.trim()
                .take(MAX_NAME_LENGTH)
                .ifBlank { address }

        private val WHITESPACE_RUN = Regex("\\s+")
        private const val MAX_NAME_LENGTH = 60

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
