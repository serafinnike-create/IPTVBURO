package com.lucasserafin94.iptvburo.desktop.diagnostics

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * What the machine itself can say about why playback might be struggling.
 *
 * Everything here is read locally — no request leaves the machine for any of it. That matters
 * twice: it works with no Internet at all, which is precisely when somebody opens this screen, and
 * it means the diagnostics cannot themselves become a way to leak where somebody is.
 */
object MachineDiagnostics {
    /** How the machine reaches the network, as far as it can tell. */
    enum class LinkKind { WIRED, WIRELESS, OTHER, NONE }

    /**
     * The address the machine holds on its local network.
     *
     * Private addresses only — this is what a router handed out, not the public address the
     * Internet sees. The public one identifies a household and has no place on a screen the viewer
     * may photograph for support.
     */
    data class Network(
        val kind: LinkKind,
        val interfaceName: String?,
        val address: String?,
        val netmask: String?,
        val gateway: String?,
    )

    /**
     * Reads the active network interface.
     *
     * Picks the lowest-indexed real interface rather than the first one up: a machine with virtual
     * adapters — Docker, a VPN, VirtualBox — has several, and naming the wrong one sends somebody
     * to check a cable that is not the one in use.
     */
    fun network(): Network {
        val active =
            runCatching {
                NetworkInterface
                    .getNetworkInterfaces()
                    .toList()
                    .filter { candidate ->
                        candidate.isUp &&
                            !candidate.isLoopback &&
                            !candidate.isVirtual &&
                            candidate.inetAddresses.toList().any { address ->
                                address is Inet4Address && address.isSiteLocalAddress
                            }
                    }.minByOrNull { candidate -> candidate.index }
            }.getOrNull()
                ?: return Network(LinkKind.NONE, null, null, null, null)

        val binding =
            active.interfaceAddresses.firstOrNull { entry -> entry.address is Inet4Address }

        return Network(
            kind = linkKind(active.name, active.displayName),
            interfaceName = active.displayName ?: active.name,
            address = (binding?.address as? Inet4Address)?.hostAddress,
            netmask = binding?.networkPrefixLength?.let(::netmaskFor),
            // The router's address, derived from the network rather than asked for: Java exposes no
            // route table, and shelling out for one line would be worse than a convention that
            // holds on essentially every home network.
            gateway = (binding?.address as? Inet4Address)?.let(::likelyGateway),
        )
    }

    /**
     * Wired or wireless, from the interface's own name.
     *
     * A guess, and honest about it: Java exposes no link type, so this reads the names the
     * operating systems use. Wrong on an oddly named adapter, which costs a wrong word on one line
     * rather than a wrong verdict.
     */
    private fun linkKind(
        name: String,
        displayName: String?,
    ): LinkKind {
        val text = "$name ${displayName.orEmpty()}".lowercase()
        return when {
            WIRELESS_HINTS.any { hint -> hint in text } -> LinkKind.WIRELESS
            WIRED_HINTS.any { hint -> hint in text } -> LinkKind.WIRED
            else -> LinkKind.OTHER
        }
    }

    /** A prefix length as the dotted mask people recognise from their router's page. */
    fun netmaskFor(prefixLength: Short): String {
        val mask = if (prefixLength <= 0) 0 else (-1 shl (32 - prefixLength.toInt()))
        return listOf(24, 16, 8, 0).joinToString(".") { shift -> ((mask ushr shift) and 0xFF).toString() }
    }

    /** The router, by the convention consumer routers follow: the first host on the network. */
    fun likelyGateway(address: Inet4Address): String? =
        runCatching {
            val octets = address.address
            octets[3] = 1
            InetAddress.getByAddress(octets).hostAddress
        }.getOrNull()

    /**
     * Memory this process can still take, in megabytes.
     *
     * The JVM's own headroom rather than the machine's, because that is what actually stops
     * playback: a machine with free memory and an exhausted heap still stutters, and the viewer
     * needs to be told to restart the app rather than to buy more memory.
     */
    fun freeMemoryMegabytes(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return (runtime.maxMemory() - used) / (1024 * 1024)
    }

    fun usedMemoryMegabytes(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private val WIRELESS_HINTS = listOf("wi-fi", "wifi", "wireless", "wlan", "802.11")
    private val WIRED_HINTS = listOf("ethernet", "gigabit", "eth", "lan", "realtek pcie", "intel(r) i2")
}
