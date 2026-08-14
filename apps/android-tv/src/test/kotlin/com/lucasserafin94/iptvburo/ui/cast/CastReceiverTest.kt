package com.lucasserafin94.iptvburo.ui.cast

import com.lucasserafin94.iptvburo.domain.model.CastMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the phone's receiver enforces, asserted without opening a socket.
 *
 * The parts that need a network — binding, accepting, answering a probe — are left to a real device:
 * a CI runner has no dependable broadcast loopback, and a test that binds one would be measuring the
 * runner rather than this class. What is checked here is everything a wrong answer would make unsafe
 * or unreachable.
 */
class CastReceiverTest {
    @Test
    fun `the discovery port matches the sender's, or nothing is ever found`() {
        // Three copies of this number exist — here, in CastSender, and in the desktop receiver —
        // and discovery fails silently if any drifts. The desktop's is asserted in its own suite;
        // this pins the two Android halves against each other.
        assertEquals(CastSender.DISCOVERY_PORT, CastReceiver.DISCOVERY_PORT)
    }

    @Test
    fun `the probe and reply words match the sender's`() {
        assertEquals(CastSender.DISCOVERY_PROBE, CastReceiver.DISCOVERY_PROBE)
        assertEquals(CastSender.DISCOVERY_REPLY, CastReceiver.DISCOVERY_REPLY)
    }

    @Test
    fun `a stored code is only usable when it is really four digits`() {
        assertTrue(CastReceiver.isWellFormedCode("0000"))
        assertTrue(CastReceiver.isWellFormedCode("4821"))
        // A blank or truncated stored value would become the code checked against, and an empty
        // string matching an empty string is a receiver that accepts anybody.
        assertFalse(CastReceiver.isWellFormedCode(""))
        assertFalse(CastReceiver.isWellFormedCode("482"))
        assertFalse(CastReceiver.isWellFormedCode("48210"))
        assertFalse(CastReceiver.isWellFormedCode("48a1"))
        assertFalse(CastReceiver.isWellFormedCode("    "))
    }

    @Test
    fun `guessing the code gets slower, and the brake is capped`() {
        // Four digits is ten thousand tries, one connection each — seconds of work for a machine on
        // the same network. The first few misses are free so an ordinary typo costs nothing.
        assertEquals(0L, CastReceiver.guessPenaltyMillis(1))
        assertEquals(0L, CastReceiver.guessPenaltyMillis(2))
        assertTrue(CastReceiver.guessPenaltyMillis(3) > 0L)
        assertTrue(CastReceiver.guessPenaltyMillis(6) > CastReceiver.guessPenaltyMillis(4))
        // Capped, so a guesser cannot drive the wait up and hold a handler thread indefinitely.
        assertEquals(
            CastReceiver.guessPenaltyMillis(500),
            CastReceiver.guessPenaltyMillis(10_000),
        )
    }

    @Test
    fun `an announced name cannot break the reply format or deceive the reader`() {
        // The reply is three fields joined by 0x1F. A name carrying one would split it into four,
        // which every reader rejects — so the phone would answer and never be listed.
        val withSeparator = CastReceiver.displayNameFrom("Sala\u001FAdministrador")
        assertFalse(withSeparator.contains('\u001F'))

        // A newline lets a responder paint extra lines into the list on the other end.
        assertEquals("Sala Lucas", CastReceiver.displayNameFrom("Sala\nLucas"))
        assertEquals("Lucas", CastReceiver.displayNameFrom("   Lucas   "))

        // Never blank: an empty field would leave the sender with an unnamed row to choose from.
        assertEquals("IPTV BURO", CastReceiver.displayNameFrom(""))
        assertEquals("IPTV BURO", CastReceiver.displayNameFrom("   "))

        // Bounded, so a long name cannot pad the datagram past the buffer the other end reads into.
        assertTrue(CastReceiver.displayNameFrom("a".repeat(500)).length <= 48)
    }

    @Test
    fun `a message only decodes against the code this device is showing`() {
        val message =
            CastMessage(
                identity = com.lucasserafin94.iptvburo.domain.model.ContentIdentity.of(
                    com.lucasserafin94.iptvburo.domain.model.ContentKind.MOVIE,
                    "Duna",
                    2021,
                ),
                title = "Duna",
                positionMillis = 0L,
                pairingCode = "4821",
            )
        val encoded = message.encode()

        assertEquals(message.identity, CastMessage.decode(encoded, "4821")?.identity)
        // The whole point of the code: the same message offered to a device showing another number
        // is not accepted, so being on the network is not by itself a permission.
        assertEquals(null, CastMessage.decode(encoded, "1234"))
    }

    /**
     * The parallel attack the growing delay does not stop.
     *
     * This receiver shipped without the cap, ported from the desktop's own pre-fix version. The
     * delay runs on each connection's thread, so fifty sockets opened at once get fifty waits side
     * by side — every attempt is slowed and the total is not. Four digits is ten thousand
     * possibilities, and discovery hands out the port to anyone who asks, so on a shared network
     * finding the target costs nothing.
     *
     * The cap is what bounds it. This pins the number rather than the mechanism, because the
     * mechanism needs a socket and the number is the part that must not quietly grow.
     */
    @Test
    fun `an address gets a bounded number of wrong codes, not an unbounded one`() {
        assertTrue(
            "A cap is what bounds a parallel attack; the growing delay alone does not.",
            CastReceiver.MAX_FAILURES_PER_ADDRESS in 1..50,
        )
        // A fifth of a percent of the four-digit space: generous for a typo, useless for a machine.
        // Stated as the fraction it is, rather than as arithmetic that happens to hold — the point
        // is that one address can reach only a sliver of the ten thousand codes.
        val spaceReachablePerAddress =
            CastReceiver.MAX_FAILURES_PER_ADDRESS.toDouble() / 10_000.0
        assertTrue(
            "One address must not be able to try a meaningful share of the code space.",
            spaceReachablePerAddress < 0.01,
        )
    }
}
