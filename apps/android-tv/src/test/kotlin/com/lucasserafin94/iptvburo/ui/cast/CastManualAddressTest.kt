package com.lucasserafin94.iptvburo.ui.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the typed-address field will send a packet to.
 *
 * This is a text field wired to an outbound datagram, so what it accepts decides where that packet
 * can go. The rule is deliberately narrow — a private IPv4 address and nothing else — and these
 * assertions are what stop it widening by accident.
 */
class CastManualAddressTest {
    @Test
    fun `accepts the private ranges a home network actually uses`() {
        assertTrue(CastSender.isPlausibleHost("192.168.1.200"))
        assertTrue(CastSender.isPlausibleHost("192.168.0.1"))
        assertTrue(CastSender.isPlausibleHost("10.0.0.5"))
        assertTrue(CastSender.isPlausibleHost("172.16.4.9"))
        assertTrue(CastSender.isPlausibleHost("172.31.255.254"))
        // Two machines on a cable with no router: no DHCP, so this range is all they have.
        assertTrue(CastSender.isPlausibleHost("169.254.10.20"))
    }

    @Test
    fun `refuses addresses outside the private ranges`() {
        // A public address would make this field a way to send a packet to an arbitrary host on the
        // internet from inside the user's network. The screen being looked for is never out there.
        assertFalse(CastSender.isPlausibleHost("8.8.8.8"))
        assertFalse(CastSender.isPlausibleHost("172.15.0.1"))
        assertFalse(CastSender.isPlausibleHost("172.32.0.1"))
        assertFalse(CastSender.isPlausibleHost("193.168.1.1"))
    }

    @Test
    fun `refuses a hostname, so a typo cannot resolve somewhere on the internet`() {
        assertFalse(CastSender.isPlausibleHost("meu-notebook"))
        assertFalse(CastSender.isPlausibleHost("example.com"))
        assertFalse(CastSender.isPlausibleHost("localhost"))
    }

    @Test
    fun `refuses leading zeros, which two stacks would read differently`() {
        // "192.168.01.1" is octal to some resolvers and decimal to others, so the two ends could
        // disagree about which machine was meant.
        assertFalse(CastSender.isPlausibleHost("192.168.01.1"))
        assertFalse(CastSender.isPlausibleHost("010.0.0.1"))
        // A single zero octet is ordinary and must still pass.
        assertTrue(CastSender.isPlausibleHost("192.168.0.10"))
    }

    @Test
    fun `refuses malformed text rather than guessing at it`() {
        assertFalse(CastSender.isPlausibleHost(""))
        assertFalse(CastSender.isPlausibleHost("192.168.1"))
        assertFalse(CastSender.isPlausibleHost("192.168.1.1.1"))
        assertFalse(CastSender.isPlausibleHost("192.168.1.256"))
        assertFalse(CastSender.isPlausibleHost("192.168.1.-1"))
        assertFalse(CastSender.isPlausibleHost("192.168.1.a"))
        assertFalse(CastSender.isPlausibleHost("192.168 .1.1"))
    }
}
