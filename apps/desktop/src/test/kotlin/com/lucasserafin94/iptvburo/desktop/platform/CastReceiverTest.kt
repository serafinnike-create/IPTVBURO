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

    private fun send(port: Int, payload: String) {
        Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
            socket.getOutputStream().use { output ->
                output.write("$payload\n".toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
    }

    private fun listeningPort(): Int {
        // The accept socket is ephemeral and private, so it is found the way a sender finds it:
        // by asking. Discovery answers on loopback in a test environment.
        val targets = CastReceiver.discover(timeoutMillis = 800)
        return targets.firstOrNull { it.displayName == "Notebook de teste" }?.port
            ?: error("the receiver did not answer discovery")
    }

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

        assertTrue(delivered.await(5, TimeUnit.SECONDS), "the receiver stopped after bad input")
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

        val targets = CastReceiver.discover(timeoutMillis = 800)
        val mine = targets.firstOrNull { it.displayName == "Notebook de teste" }

        assertNotNull(mine, "the receiver should be discoverable")
        assertFalse(mine.toString().contains(code), "discovery leaked the pairing code")
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
}
