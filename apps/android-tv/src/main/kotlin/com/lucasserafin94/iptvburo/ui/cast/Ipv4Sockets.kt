package com.lucasserafin94.iptvburo.ui.cast

import android.os.Build
import androidx.annotation.RequiresApi
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.channels.DatagramChannel

/**
 * Datagram sockets that are IPv4, on a runtime that may not let us say so.
 *
 * ## Why the family has to be stated
 *
 * Discovery is an IPv4 *broadcast*, and an IPv4 broadcast is never delivered to a socket bound to
 * the IPv6 wildcard. An unqualified bind takes whichever family the runtime prefers — IPv6 — so the
 * phone sat listening on a port the desktop's search could not reach: both devices on the same
 * network, both apps open, and "no screens found" on screen. Asking for `0.0.0.0` is not enough
 * either, because a dual-stack socket maps that straight back onto the IPv6 wildcard.
 *
 * ## Why there are two ways to do it
 *
 * `DatagramChannel.open(ProtocolFamily)` is the one call that means "IPv4, really", and it arrived
 * in API 24. This app supports 23, and lint is right to refuse the unguarded call: on Android 6 it
 * would not merely behave differently, it would not resolve at all.
 *
 * So the channel is used where it exists and a plain socket bound to `0.0.0.0` stands in below it.
 * The fallback is genuinely weaker — a dual-stack runtime may still widen it to IPv6 — and it is
 * the best available there. It is also no worse than what shipped before any of this, which is the
 * bar a fallback has to clear.
 */
internal object Ipv4Sockets {
    /** An unbound socket for sending. Bound by the OS on first use, as a client socket should be. */
    fun openSender(): DatagramSocket =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            openSenderChannel()
        } else {
            DatagramSocket()
        }

    /** A socket bound to [port] on every interface, with address reuse, for listening. */
    fun openListener(port: Int): DatagramSocket =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            openListenerChannel(port)
        } else {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(WILDCARD), port))
            }
        }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun openSenderChannel(): DatagramSocket =
        DatagramChannel.open(StandardProtocolFamily.INET).socket()

    @RequiresApi(Build.VERSION_CODES.N)
    private fun openListenerChannel(port: Int): DatagramSocket =
        DatagramChannel
            .open(StandardProtocolFamily.INET)
            .apply {
                // Reuse first: the discovery port is fixed, so a socket left in TIME_WAIT by the
                // previous session would otherwise make this bind fail, and the feature would
                // report itself unavailable to somebody who had merely reopened the screen.
                setOption(StandardSocketOptions.SO_REUSEADDR, true)
                bind(InetSocketAddress(InetAddress.getByName(WILDCARD), port))
            }.socket()

    private const val WILDCARD = "0.0.0.0"
}
