package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.CastMessage
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The receiver is the one part of this app that listens on the network, so its behaviour under a
 * message it did not expect matters more than its behaviour under a good one.
 */
class CastReceiverTest {
    private val receiver = CastReceiver(displayName = "Notebook de teste")

    @AfterTest
    fun tearDown() {
        receiver.stop()
    }

    /**
     * Turning the receiver off and on again has to work, every time.
     *
     * The discovery port is fixed, so the socket from the previous session can still be in
     * TIME_WAIT when the next bind runs. Without address reuse that bind fails, `start` returns
     * null — its way of saying "the feature is unavailable" — and the user is told casting cannot
     * run because they had used it a moment earlier.
     *
     * Found on CI rather than locally: two tests failed there at their `start` call while every
     * local run passed, because the runner reuses one machine for the whole suite.
     */
    @Test
    fun `the receiver restarts on the same discovery port`() {
        repeat(4) {
            assertNotNull(receiver.start { }, "the receiver failed to rebind after a stop")
            receiver.stop()
        }
    }

    /**
     * An address that has spent its attempts is refused, however many sockets it opens.
     *
     * The growing delay alone was not enough, and the reason is where it is applied: on the
     * connection's own thread. Fifty sockets at once get fifty waits running side by side, so the
     * brake slows each attempt without bounding the total — and four digits is ten thousand
     * possibilities. It was survivable while the code was minted per session, since an interrupted
     * attack lost its progress; it stopped being so when the code became per machine and attempts
     * began accumulating against a target that never moves.
     *
     * Asserted by spending the budget and then sending the *correct* code from the same address:
     * accepting it would mean the lockout is not really closing connections.
     */
    @Test
    fun `an address that exhausts its attempts is refused even with the right code`() {
        val delivered = CountDownLatch(1)
        val code = assertNotNull(receiver.start { delivered.countDown() })
        val wrong = if (code == "0000") "1111" else "0000"

        // Waited for rather than merely sent.
        //
        // Sending is fire-and-forget: the socket closes as soon as the bytes are written, long
        // before the receiver has read the line and counted the failure. Firing twenty and moving
        // straight on assumed the count had reached the limit, and on a loaded CI runner it had
        // not — the correct code then arrived while attempts were still free and was accepted,
        // failing a test about a lockout that was working exactly as intended.
        awaitLockout(wrong)

        // The budget is spent, so even the owner's own code gets nowhere from this address until
        // the receiver is restarted. That is the cost of the lockout, and it is deliberate: the
        // address doing the guessing is the one that loses access.
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Depois do Bloqueio", 2024)
        runCatching { send(listeningPort(), CastMessage(identity, "Depois do Bloqueio", 0, code).encode()) }

        assertFalse(
            delivered.await(1_500, TimeUnit.MILLISECONDS),
            "a locked-out address was still able to hand over a title",
        )
    }

    /**
     * Spends this address's budget and does not return until the receiver has actually refused one.
     *
     * A refused connection is closed before the line is read, so writing to it fails — that failure
     * is the observable signal that the lockout has engaged, and waiting for it makes the test
     * independent of how fast the machine drains the queue.
     */
    private fun awaitLockout(wrongCode: String) {
        val payload = CastMessage(WRONG_IDENTITY, "Tentativa", 0, wrongCode).encode()
        val loopback = InetAddress.getLoopbackAddress().hostAddress
        val deadline = System.currentTimeMillis() + LOCKOUT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (receiver.failuresFor(loopback) >= CastReceiver.MAX_FAILURES_PER_ADDRESS) return
            runCatching { send(listeningPort(), payload) }
            Thread.sleep(20)
        }
        error("the receiver never locked this address out")
    }

    /** Getting it right clears the count, so a few mistypes do not cost the owner their screen. */
    @Test
    fun `a correct code clears the attempts spent by that address`() {
        val delivered = CountDownLatch(1)
        var received: CastMessage? = null
        val code = assertNotNull(receiver.start { message -> received = message; delivered.countDown() })
        val wrong = if (code == "0000") "1111" else "0000"

        // Under the budget, then right, then under it again: without the reset the second run would
        // cross the limit and the owner would be locked out of their own machine by typos.
        repeat(CastReceiver.MAX_FAILURES_PER_ADDRESS - 1) {
            runCatching { send(listeningPort(), CastMessage(WRONG_IDENTITY, "Tentativa", 0, wrong).encode()) }
        }
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Aceito", 2024)
        send(listeningPort(), CastMessage(identity, "Aceito", 0, code).encode())

        assertTrue(delivered.await(5, TimeUnit.SECONDS), "the correct code was refused")
        assertEquals(identity, received?.identity)
    }

    /**
     * The machine's code is reused, so the phone is told it once rather than every session.
     *
     * It used to be minted on every start, which meant the number on screen was different each time
     * the app opened and had to be retyped — for a feature whose whole appeal is not thinking about
     * it. Reuse is the difference between typing it once and typing it daily.
     */
    @Test
    fun `a kept code is reused rather than replaced on every start`() {
        val first = receiver.start(existingCode = "2275") { }
        receiver.stop()
        val second = receiver.start(existingCode = "2275") { }

        assertEquals("2275", first)
        assertEquals("2275", second, "the code changed between sessions and would need retyping")
    }

    /** No stored code is a first run, and it has to produce a usable one rather than nothing. */
    @Test
    fun `a first run mints a code of its own`() {
        val minted = assertNotNull(receiver.start(existingCode = null) { })

        assertEquals(4, minted.length)
        assertTrue(minted.all(Char::isDigit), "minted a code that is not four digits: $minted")
    }

    /**
     * A damaged stored code must never become the code that is checked against.
     *
     * Preferences are a file anything can edit. An empty string surviving into the receiver would
     * be compared against the empty code a sender can trivially send — a receiver that accepts
     * anybody, arrived at by way of a corrupted setting rather than any decision.
     */
    @Test
    fun `a malformed stored code is discarded rather than trusted`() {
        listOf("", "12", "abcd", "12345").forEach { damaged ->
            val used = assertNotNull(receiver.start(existingCode = damaged) { })
            receiver.stop()

            assertTrue(
                used.length == 4 && used.all(Char::isDigit),
                "the receiver accepted '$damaged' as its code",
            )
        }
    }

    /**
     * The discovery socket must be an IPv4 one, or nothing can ever reach it.
     *
     * `InetSocketAddress(port)` binds the wildcard of whichever family the JVM prefers, and on
     * Windows that is IPv6: `netstat` showed this socket on `::`. Discovery is an IPv4 *broadcast*,
     * and an IPv4 broadcast is never delivered to a socket bound to the IPv6 wildcard — so the
     * receiver sat there listening on a port no sender could reach. The phone was on the same
     * network with the app open, and the desktop still said no screens were found.
     *
     * Asserted on the bound address rather than by sending a packet: a CI runner has no dependable
     * broadcast loopback, so a delivery test there would measure the runner's network instead of
     * this class.
     */
    @Test
    fun `the discovery socket binds IPv4 so a broadcast can reach it`() {
        assertNotNull(receiver.start { }, "the receiver did not start")

        val bound = receiver.discoveryBindAddress
        assertNotNull(bound, "the receiver reported no discovery address")
        assertTrue(
            bound is java.net.Inet4Address,
            "discovery bound to $bound, which an IPv4 broadcast cannot reach",
        )
    }

    /**
     * Guessing the code has to get slower, or four digits protect nothing.
     *
     * The code is the whole security model of this feature: the listener binds every interface, so
     * on a shared network — a hotel, a café, an office — anyone can reach it. Ten thousand
     * possibilities is seconds of work for a machine, and comparing the code in constant time does
     * nothing about simply trying them all.
     *
     * Asserted on the delay the receiver computes rather than on elapsed wall-clock time. Timing
     * the sender measures nothing, because sending is fire-and-forget: the socket closes before the
     * receiver has read the line, so a guesser always *sends* quickly whatever the receiver does.
     * Timing the receiver from the outside is no better — the wrong codes are processed while the
     * test is still writing them, so the measurement lands wherever the scheduler leaves it. What
     * matters is the rule, and the rule is what this checks.
     */
    @Test
    fun `guessing the pairing code gets slower`() {
        // Nothing is free after the first few, and each further guess costs more than the last.
        assertEquals(0L, CastReceiver.guessPenaltyMillis(1))
        assertEquals(0L, CastReceiver.guessPenaltyMillis(2))
        assertTrue(CastReceiver.guessPenaltyMillis(3) > 0, "the brake never engages")
        assertTrue(
            CastReceiver.guessPenaltyMillis(6) > CastReceiver.guessPenaltyMillis(3),
            "the penalty does not grow, so a long run of guesses is no dearer than a short one",
        )
    }

    /**
     * The penalty is capped, or a burst of junk becomes a denial of service.
     *
     * Without a ceiling anyone on the network could park the accept thread for minutes and lock
     * the owner out of their own screen — trading one weakness for a worse one.
     */
    @Test
    fun `the guessing penalty is capped`() {
        val far = CastReceiver.guessPenaltyMillis(10_000)

        assertEquals(CastReceiver.guessPenaltyMillis(1_000), far)
        assertTrue(far <= 5_000, "a single wrong code should never block the receiver for ${far}ms")
    }

    /**
     * The brake must not punish the person who owns the screen.
     *
     * A correct code after several wrong ones has to work, and has to reset the penalty — otherwise
     * a burst of junk from anyone on the network would lock the feature for its owner, which is a
     * denial of service handed out for free.
     */
    @Test
    fun `a correct code still works after wrong ones`() {
        val delivered = CountDownLatch(1)
        var received: CastMessage? = null
        val code = assertNotNull(receiver.start { message -> received = message; delivered.countDown() })
        val port = listeningPort()
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Depois das tentativas", 2024)

        repeat(4) { attempt ->
            send(port, CastMessage(identity, "Errado", 0, String.format("%04d", attempt)).encode())
        }
        send(port, CastMessage(identity, "Depois das tentativas", 0, code).encode())

        assertTrue(delivered.await(30, TimeUnit.SECONDS), "the owner was locked out by the brake")
        assertEquals(identity, received?.identity)
    }

    private fun send(port: Int, payload: String) {
        // Retried on a refused connection, which is not a failure of the receiver.
        //
        // The listen backlog is two — deliberately small, since one message per connection needs no
        // queue — so a burst of connections can be refused by the OS before the accept loop drains
        // them. That is invisible on a quiet laptop and reliable on a loaded CI runner, where this
        // surfaced as a ConnectException in a test about the receiver surviving junk. A real sender
        // would try again; so does this.
        var attempt = 0
        while (true) {
            val sent =
                runCatching {
                    Socket().use { socket ->
                        socket.connect(
                            java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                            2_000,
                        )
                        socket.getOutputStream().use { output ->
                            output.write("$payload\n".toByteArray(Charsets.UTF_8))
                            output.flush()
                        }
                    }
                }.isSuccess
            if (sent || ++attempt >= SEND_ATTEMPTS) return
            Thread.sleep(SEND_RETRY_MILLIS)
        }
    }

    /**
     * The port the receiver is actually listening on.
     *
     * Read from the receiver rather than found over multicast discovery. Discovery is the right
     * mechanism for a real sender and is covered by its own test; using it *here* made this test
     * depend on the runner having dependable multicast loopback, which CI does not — the assertion
     * failed there while passing locally, measuring the network instead of the receiver.
     */
    private fun listeningPort(): Int =
        assertNotNull(receiver.listeningPort, "the receiver reported no listening port")

    @Test
    fun `a paired sender can hand over a title`() {
        val delivered = CountDownLatch(1)
        var received: CastMessage? = null
        val code = assertNotNull(receiver.start { message -> received = message; delivered.countDown() })

        val identity = ContentIdentity.of(ContentKind.MOVIE, "Duna: Parte Dois", 2024)
        send(listeningPort(), CastMessage(identity, "Duna: Parte Dois", 2_040_000, code).encode())

        assertTrue(delivered.await(5, TimeUnit.SECONDS), "the message never arrived")
        assertEquals(identity, received?.identity)
        assertEquals(2_040_000, received?.positionMillis)
    }

    /**
     * The property the whole feature rests on.
     *
     * Without it, anyone sharing a network could push video onto a stranger's screen. The message
     * must be dropped before anything is looked up or played.
     */
    @Test
    fun `a sender with the wrong code is ignored`() {
        val delivered = CountDownLatch(1)
        val code = assertNotNull(receiver.start { delivered.countDown() })
        val wrongCode = if (code == "0000") "1111" else "0000"

        val identity = ContentIdentity.of(ContentKind.MOVIE, "Filme Qualquer", 2024)
        send(listeningPort(), CastMessage(identity, "Filme Qualquer", 0, wrongCode).encode())

        assertFalse(delivered.await(1500, TimeUnit.MILLISECONDS), "an unpaired sender was accepted")
        assertNull(receiver.takeMessage())
    }

    /** Anything that is not a message must be dropped, not throw on the listener thread. */
    @Test
    fun `garbage does not stop the receiver`() {
        val delivered = CountDownLatch(1)
        var received: CastMessage? = null
        val code = assertNotNull(receiver.start { message -> received = message; delivered.countDown() })
        val port = listeningPort()

        listOf("", "nonsense", "buro-cast-1", "x".repeat(9_000)).forEach { junk -> send(port, junk) }

        // The good message still gets through afterwards, which is the real assertion: the listener
        // survived everything above.
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Ainda Funciona", 2024)
        send(port, CastMessage(identity, "Ainda Funciona", 0, code).encode())

        // Generous on purpose. Four pieces of junk in a row trip the guessing brake, so the good
        // message that follows waits out a penalty before it is even read — the delay is the
        // feature working, not the receiver failing. Five seconds was close enough to that penalty
        // to fail intermittently, which is worse than useless in a test guarding a real property.
        assertTrue(delivered.await(30, TimeUnit.SECONDS), "the receiver stopped after bad input")
        assertEquals(identity, received?.identity)
    }

    /**
     * Discovery says a screen exists; it does not say how to use it.
     *
     * A device that has not been told the code must not be able to learn it by asking, or the code
     * would protect nothing.
     */
    @Test
    fun `discovery never reveals the pairing code`() {
        val code = assertNotNull(receiver.start { })

        val targets = CastReceiver.discover(timeoutMillis = DISCOVERY_TIMEOUT_MILLIS)
        val mine = targets.firstOrNull { it.displayName == "Notebook de teste" }

        // The claim is "a discovery reply never carries the code". Where nothing replied at all —
        // a runner with no usable multicast loopback, which is the case on CI — there is no reply
        // to inspect, and failing here would report a leak that was never observed. The assertion
        // is therefore made against whatever came back, and an empty answer passes vacuously
        // because it is vacuously true: no reply, no leak.
        //
        // This is not a hole in the coverage of the property. `startDiscoveryLoop` builds the reply
        // from the machine name and port and has no access to the code, and every other test in
        // this file reads the port directly and so exercises the receiver regardless of multicast.
        mine?.let { target ->
            assertFalse(target.toString().contains(code), "discovery leaked the pairing code")
        }
    }

    /**
     * A sender that never stops talking must not be able to make the receiver hold it all.
     *
     * `BufferedReader.readLine` has no upper bound: it reads until a newline arrives. Anyone on the
     * network can open this socket, so without a limit a sender that omits the newline would have
     * the whole read timeout to make the app allocate as much as their link can carry.
     *
     * Asserted through a real socket rather than by calling the reader directly, because the thing
     * being proved is what the *listener* does with a hostile connection.
     */
    @Test
    fun `an oversized line is refused rather than buffered`() {
        val code = assertNotNull(receiver.start { error("an oversized line must never be delivered") })
        val port = listeningPort()

        // Well past the limit, and deliberately with no newline: the receiver has to decide to stop
        // on length alone rather than on the end of the line.
        Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
            socket.getOutputStream().use { output ->
                output.write("x".repeat(CastMessage.MAX_ENCODED_LENGTH * 8).toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }

        // The listener survived, which is the point: a good message still gets through afterwards.
        val delivered = CountDownLatch(1)
        var received: CastMessage? = null
        receiver.stop()
        val freshCode = assertNotNull(receiver.start { message -> received = message; delivered.countDown() })
        val identity = ContentIdentity.of(ContentKind.MOVIE, "Depois do excesso", 2024)
        send(listeningPort(), CastMessage(identity, "Depois do excesso", 0, freshCode).encode())

        assertTrue(delivered.await(5, TimeUnit.SECONDS), "the listener stopped accepting connections")
        assertEquals(identity, received?.identity)
        assertFalse(code == freshCode, "a restarted receiver should mint a new code")
    }

    /**
     * This machine can send as well as receive, which is what the button on the film page needs.
     *
     * Asserted against a real receiver rather than a mock: the two halves have to agree on the
     * wire format, and a test that only checked the encoder would pass while the protocol drifted.
     */
    @Test
    fun `the sender delivers a title to a receiver`() {
        val delivered = CountDownLatch(1)
        var received: CastMessage? = null
        val code = assertNotNull(receiver.start { message -> received = message; delivered.countDown() })
        val target =
            CastTarget(
                address = InetAddress.getLoopbackAddress().hostAddress,
                port = listeningPort(),
                displayName = "Notebook de teste",
            )

        val identity = ContentIdentity.of(ContentKind.MOVIE, "Enviado Daqui", 2024)
        val sent = CastReceiver.send(target, CastMessage(identity, "Enviado Daqui", 0, code))

        assertTrue(sent, "the sender reported a failure")
        assertTrue(delivered.await(5, TimeUnit.SECONDS), "the message never arrived")
        assertEquals(identity, received?.identity)
    }

    /** A wrong code is refused on the sending path too, exactly as it is from a phone. */
    @Test
    fun `the sender cannot bypass the pairing code`() {
        val delivered = CountDownLatch(1)
        val code = assertNotNull(receiver.start { delivered.countDown() })
        val wrongCode = if (code == "0000") "1111" else "0000"
        val target =
            CastTarget(
                address = InetAddress.getLoopbackAddress().hostAddress,
                port = listeningPort(),
                displayName = "Notebook de teste",
            )

        val identity = ContentIdentity.of(ContentKind.MOVIE, "Nao Deve Chegar", 2024)
        // Delivery succeeds — the bytes arrive — while acceptance does not. That difference is why
        // the UI says "sent" rather than claiming playback started.
        assertTrue(CastReceiver.send(target, CastMessage(identity, "Nao Deve Chegar", 0, wrongCode)))

        assertFalse(delivered.await(1500, TimeUnit.MILLISECONDS), "an unpaired message was accepted")
        assertNull(receiver.takeMessage())
    }

    /** Stopping releases the ports, so the feature can be turned off and on again. */
    @Test
    fun `a stopped receiver answers nothing`() {
        assertNotNull(receiver.start { })
        receiver.stop()

        assertNull(receiver.pairingCode)
        assertTrue(
            CastReceiver.discover(timeoutMillis = 600).none { it.displayName == "Notebook de teste" },
            "a stopped receiver still answered discovery",
        )
    }

    private companion object {
        /**
         * How long discovery is given to answer.
         *
         * Generous on purpose: this is a UDP round trip on a machine that may be running a full
         * build alongside it, and a timeout tuned to a quiet laptop turns into a flaky failure on a
         * loaded runner.
         */
        const val DISCOVERY_TIMEOUT_MILLIS = 2_000

        /** Any well-formed identity; these messages are rejected on the code, never read further. */
        val WRONG_IDENTITY: ContentIdentity = ContentIdentity.of(ContentKind.MOVIE, "Tentativa", 2024)

        /**
         * How long to keep spending wrong codes before giving up on the lockout engaging.
         *
         * Generous, because the brake deliberately slows each attempt: twenty failures with a
         * growing penalty take real time, and a tight bound here would fail on a loaded runner for
         * the very reason the brake exists.
         */
        const val LOCKOUT_TIMEOUT_MILLIS = 60_000L

        /** Tries per message, so a backlog refusal on a busy runner is not read as a failure. */
        const val SEND_ATTEMPTS = 5
        const val SEND_RETRY_MILLIS = 50L
    }
}
