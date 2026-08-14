package com.lucasserafin94.iptvburo.ui.cast

import android.content.Context
import android.net.wifi.WifiManager
import com.lucasserafin94.iptvburo.domain.model.CastMessage
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.StandardProtocolFamily
import java.nio.channels.DatagramChannel

/** A screen on this network that answered discovery. */
data class CastTarget(
    val address: String,
    val port: Int,
    val displayName: String,
)

/**
 * Finds BURO screens on the local network and hands one a title to play.
 *
 * The counterpart of the desktop receiver, speaking the same protocol: a UDP probe to find screens,
 * then one TCP connection carrying a single [CastMessage]. What travels is *which film*, not the
 * film — so the television streams from the provider itself and this phone's credentials never
 * leave it.
 *
 * ## The multicast lock
 *
 * Android silently drops broadcast and multicast packets that are not addressed to the device, to
 * save battery. Discovery therefore returns nothing at all without a [WifiManager.MulticastLock]
 * held across the probe — the packets are sent, the replies arrive at the interface, and the OS
 * throws them away before this code ever sees them. It is released immediately afterwards, because
 * holding it costs power for as long as it is held.
 *
 * ## Everything here blocks
 *
 * These are plain sockets, called from a coroutine on the IO dispatcher. There is no coroutine
 * machinery inside them so that where the waiting happens stays visible at the call site.
 */
/**
 * The socket work behind casting, as an interface so the flow above it can be tested without a
 * device — [CastController] needs no Android runtime, and neither do its tests.
 */
interface CastTransport {
    fun discover(timeoutMillis: Int = 1_200): List<CastTarget>

    fun send(target: CastTarget, message: CastMessage): Boolean
}

class CastSender(private val context: Context) : CastTransport {
    /**
     * Screens that answered within [timeoutMillis], newest answer last.
     *
     * An empty list is the ordinary result on a network that blocks broadcast — many home routers
     * separate wifi from ethernet — so the caller offers a manual address instead of treating it as
     * a failure.
     */
    override fun discover(timeoutMillis: Int): List<CastTarget> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply { setReferenceCounted(true) }

        return try {
            runCatching { lock?.acquire() }
            probe(timeoutMillis)
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    private fun probe(timeoutMillis: Int): List<CastTarget> =
        runCatching {
            // Forced to IPv4: an unqualified socket opens on the family the runtime prefers, which
            // is IPv6, and an IPv6 socket cannot send an IPv4 broadcast at all — every send below
            // would be refused and the scan would come back empty with nothing to show for it.
            val ipv4Socket = DatagramChannel.open(StandardProtocolFamily.INET).socket()
            ipv4Socket.use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMillis

                val probe = DISCOVERY_PROBE.toByteArray(Charsets.UTF_8)
                // Every interface's own broadcast address as well as the global one.
                //
                // A limited broadcast leaves by whichever single interface the routing table picks.
                // A phone usually has several — Wi-Fi, mobile data, sometimes a hotspot or a VPN —
                // and the probe going out over mobile data reaches nothing on the home network.
                // Sending to 192.168.1.255 as well is what puts it on the wire the screens are on.
                (broadcastAddresses() + InetAddress.getByName("255.255.255.255")).forEach { address ->
                    // Per address: one interface refusing the send must not stop the others.
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

                // Keyed by address so a machine that answers twice appears once, and ordered so the
                // list does not reshuffle between scans while somebody is reaching for it.
                val found = LinkedHashMap<String, CastTarget>()
                val buffer = ByteArray(DISCOVERY_BUFFER_BYTES)
                val deadline = System.currentTimeMillis() + timeoutMillis
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    if (!runCatching { socket.receive(packet) }.isSuccess) break
                    parseReply(packet)?.let { target -> found[target.address] = target }
                }
                found.values.toList()
            }
        }.getOrDefault(emptyList())

    /** Null for anything that is not a reply from this protocol. */
    private fun parseReply(packet: DatagramPacket): CastTarget? {
        val parts = String(packet.data, 0, packet.length, Charsets.UTF_8).split(FIELD_SEPARATOR.toString())
        if (parts.size != 3 || parts[0] != DISCOVERY_REPLY) return null
        val port = parts[1].toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
        val address = packet.address?.hostAddress ?: return null
        val name = parts[2].take(MAX_NAME_LENGTH).ifBlank { address }
        return CastTarget(address = address, port = port, displayName = name)
    }

    /**
     * Sends [message] to [target].
     *
     * True only means the bytes were delivered. Whether the screen *accepted* them depends on the
     * pairing code, and the receiver answers a wrong code with silence rather than a refusal — so
     * the phone cannot tell a mistyped code from a screen that stopped listening. The UI says so
     * rather than claiming success.
     */
    override fun send(target: CastTarget, message: CastMessage): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target.address, target.port), CONNECT_TIMEOUT_MILLIS)
                socket.getOutputStream().use { output ->
                    // Newline-terminated: the receiver reads one line, which is what bounds how much
                    // it will take from a connection.
                    output.write("${message.encode()}\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
            true
        }.getOrDefault(false)

    // Internal rather than private so the receiver's suite can assert the two halves agree: the
    // port and the probe words exist in three places, and discovery goes quiet with nothing to show
    // for it if any one of them drifts.
    internal companion object {
        /** Must match the desktop receiver. */
        const val DISCOVERY_PORT = 45_517
        const val DISCOVERY_PROBE = "buro-cast-discover-1"
        const val DISCOVERY_REPLY = "buro-cast-here-1"
        const val FIELD_SEPARATOR = '\u001F'

        const val DISCOVERY_TIMEOUT_MILLIS = 1_200
        const val DISCOVERY_BUFFER_BYTES = 256
        const val CONNECT_TIMEOUT_MILLIS = 2_500
        const val MAX_NAME_LENGTH = 60
        const val MULTICAST_LOCK_TAG = "iptvburo-cast-discovery"

        /**
         * The broadcast address of every interface actually carrying traffic.
         *
         * Loopback and interfaces that are down are skipped, as is any interface with no broadcast
         * address of its own — a point-to-point link has none, and IPv6 has no broadcast at all.
         *
         * A phone usually has more than one: Wi-Fi, mobile data, sometimes a hotspot or a VPN. A
         * probe sent only to 255.255.255.255 leaves by whichever one the routing table picks, and
         * over mobile data it reaches nothing on the home network.
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
    }
}
