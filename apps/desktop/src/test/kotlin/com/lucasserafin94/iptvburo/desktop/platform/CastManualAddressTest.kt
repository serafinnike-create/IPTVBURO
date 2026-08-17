package com.lucasserafin94.iptvburo.desktop.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the typed-address field will send a packet to.
 *
 * The field exists because a router that drops broadcast between clients makes the search return
 * nothing while both machines sit on the same network listening — seen on a real network here. It
 * is a text field wired to an outbound datagram, so what it accepts decides where that packet can
 * go, and these assertions are what stop the rule widening by accident.
 *
 * The same rule is asserted on the Android side, in its own suite. Two copies of a check are two
 * chances to drift, and this one guards the desktop's own probe.
 */
class CastManualAddressTest {
    @Test
    fun `accepts the private ranges a home network actually uses`() {
        assertTrue(CastReceiver.isPlausibleHost("192.168.1.200"))
        assertTrue(CastReceiver.isPlausibleHost("192.168.0.1"))
        assertTrue(CastReceiver.isPlausibleHost("10.0.0.5"))
        assertTrue(CastReceiver.isPlausibleHost("172.16.4.9"))
        assertTrue(CastReceiver.isPlausibleHost("172.31.255.254"))
        // Two machines on a cable with no router: no DHCP, so this range is all they have.
        assertTrue(CastReceiver.isPlausibleHost("169.254.10.20"))
    }

    @Test
    fun `refuses addresses outside the private ranges`() {
        // A public address would make this field a way to send a packet to an arbitrary host on the
        // internet from inside the user's network. The screen being looked for is never out there.
        assertFalse(CastReceiver.isPlausibleHost("8.8.8.8"))
        assertFalse(CastReceiver.isPlausibleHost("172.15.0.1"))
        assertFalse(CastReceiver.isPlausibleHost("172.32.0.1"))
        assertFalse(CastReceiver.isPlausibleHost("193.168.1.1"))
    }

    @Test
    fun `refuses a hostname, so a typo cannot resolve somewhere on the internet`() {
        assertFalse(CastReceiver.isPlausibleHost("meu-notebook"))
        assertFalse(CastReceiver.isPlausibleHost("example.com"))
        assertFalse(CastReceiver.isPlausibleHost("localhost"))
    }

    @Test
    fun `refuses leading zeros, which two stacks would read differently`() {
        // "192.168.01.1" is octal to some resolvers and decimal to others, so the two ends could
        // disagree about which machine was meant.
        assertFalse(CastReceiver.isPlausibleHost("192.168.01.1"))
        assertFalse(CastReceiver.isPlausibleHost("010.0.0.1"))
        // A single zero octet is ordinary and must still pass.
        assertTrue(CastReceiver.isPlausibleHost("192.168.0.10"))
    }

    @Test
    fun `refuses malformed text rather than guessing at it`() {
        assertFalse(CastReceiver.isPlausibleHost(""))
        assertFalse(CastReceiver.isPlausibleHost("192.168.1"))
        assertFalse(CastReceiver.isPlausibleHost("192.168.1.1.1"))
        assertFalse(CastReceiver.isPlausibleHost("192.168.1.256"))
        assertFalse(CastReceiver.isPlausibleHost("192.168.1.-1"))
        assertFalse(CastReceiver.isPlausibleHost("192.168.1.a"))
        assertFalse(CastReceiver.isPlausibleHost("192.168 .1.1"))
    }
}
