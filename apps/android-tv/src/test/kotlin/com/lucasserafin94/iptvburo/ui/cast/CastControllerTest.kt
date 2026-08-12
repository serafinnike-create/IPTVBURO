package com.lucasserafin94.iptvburo.ui.cast

import com.lucasserafin94.iptvburo.domain.model.CastMessage
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastControllerTest {
    private val identity = ContentIdentity.of(ContentKind.MOVIE, "Duna: Parte Dois", 2024)
    private val notebook = CastTarget(address = "192.168.0.10", port = 51_000, displayName = "Notebook")
    private val tv = CastTarget(address = "192.168.0.20", port = 51_001, displayName = "TV da sala")

    /** A stand-in for the sockets, so the flow can be tested without a network. */
    private class FakeSender(
        private val targets: List<CastTarget>,
        private val deliver: Boolean = true,
    ) : CastTransport {
        var sent: CastMessage? = null
        var sentTo: CastTarget? = null
        var searches = 0

        override fun discover(timeoutMillis: Int): List<CastTarget> {
            searches += 1
            return targets
        }

        override fun send(target: CastTarget, message: CastMessage): Boolean {
            sentTo = target
            sent = message
            return deliver
        }
    }

    private fun controller(sender: CastTransport) = CastController(sender, io = Dispatchers.Unconfined)

    @Test
    fun `searching lists the screens that answered`() = runTest {
        val control = controller(FakeSender(listOf(notebook, tv)))

        control.search()

        assertEquals(listOf(notebook, tv), (control.state as CastUiState.Found).targets)
    }

    /**
     * An empty result is a real answer, not a failure.
     *
     * Many home routers separate wifi from ethernet and drop the broadcast, so "none found" needs
     * its own wording rather than looking like the search never ran.
     */
    @Test
    fun `finding nothing is a normal outcome`() = runTest {
        val control = controller(FakeSender(emptyList()))

        control.search()

        assertTrue((control.state as CastUiState.Found).targets.isEmpty())
    }

    @Test
    fun `a delivered title reports the screen it went to`() = runTest {
        val sender = FakeSender(listOf(notebook))
        val control = controller(sender)
        control.search()
        control.choose(notebook)

        control.send("1234", identity, "Duna: Parte Dois", positionMillis = 2_040_000)

        assertEquals(notebook, (control.state as CastUiState.Sent).target)
        assertEquals(identity, sender.sent?.identity)
        assertEquals(2_040_000L, sender.sent?.positionMillis)
        assertEquals("1234", sender.sent?.pairingCode)
    }

    /**
     * A malformed code never reaches the network.
     *
     * The receiver answers a wrong code with silence, so sending one would tell the user nothing at
     * all — worse than refusing it here, where the field can say what is wrong.
     */
    @Test
    fun `a malformed code is refused without sending anything`() = runTest {
        val sender = FakeSender(listOf(notebook))
        val control = controller(sender)
        control.search()
        control.choose(notebook)

        listOf("", "12", "abcd", "12345").forEach { bad ->
            control.send(bad, identity, "Duna", positionMillis = 0)
            assertTrue("'$bad' should be refused", (control.state as CastUiState.NeedsCode).badCode)
        }
        assertNull("nothing should have been sent", sender.sent)
    }

    /** A screen that went away is reported as a failure rather than as success. */
    @Test
    fun `an undelivered title reports failure`() = runTest {
        val control = controller(FakeSender(listOf(notebook), deliver = false))
        control.search()
        control.choose(notebook)

        control.send("1234", identity, "Duna", positionMillis = 0)

        assertEquals(notebook, (control.state as CastUiState.Failed).target)
    }

    /**
     * Going back keeps the list.
     *
     * Discovery takes over a second, and searching again because somebody tapped the wrong row
     * would make correcting a mistake slower than making it.
     */
    @Test
    fun `going back to the list does not search again`() = runTest {
        val sender = FakeSender(listOf(notebook, tv))
        val control = controller(sender)
        control.search()
        control.choose(tv)

        control.back()

        assertEquals(listOf(notebook, tv), (control.state as CastUiState.Found).targets)
        assertEquals("going back must not re-run discovery", 1, sender.searches)
    }

    /** Sending before choosing a screen does nothing rather than guessing which one. */
    @Test
    fun `sending with no screen chosen does nothing`() = runTest {
        val sender = FakeSender(listOf(notebook))
        val control = controller(sender)

        control.send("1234", identity, "Duna", positionMillis = 0)

        assertNull(sender.sent)
        (control.state as CastUiState.Idle)
    }

    /** A negative position would ask the receiver to seek somewhere that does not exist. */
    @Test
    fun `a negative position is clamped to the start`() = runTest {
        val sender = FakeSender(listOf(notebook))
        val control = controller(sender)
        control.search()
        control.choose(notebook)

        control.send("1234", identity, "Duna", positionMillis = -5_000)

        assertEquals(0L, sender.sent?.positionMillis)
    }
}
