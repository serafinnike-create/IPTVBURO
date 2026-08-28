package com.lucasserafin94.iptvburo.desktop.data

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Choosing between an Xtream server and a Stalker portal, per subscription.
 *
 * One repository lives for the whole life of the app, so deciding the protocol at startup would
 * leave somebody with an Xtream account and a Stalker portal able to use only whichever was picked
 * first. The switch delegates instead, and holds nothing of its own — no catalogue to fall out of
 * step, and no second place a password could be left behind.
 */
class ProtocolSwitchingTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val switching =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/SwitchingCatalogueRepository.kt")
    private val main = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/Main.kt")
    private val dialog =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamLoginDialog.kt")
    private val app = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")
    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")

    @Test
    fun `the app is built with both protocols available`() {
        // Matched without the empty parentheses: the switcher now takes the Xtream delegate, so
        // that it can be the merging repository when the viewer asked for one catalogue. What this
        // guards is unchanged — building one protocol directly is what made the other unreachable.
        assertTrue(
            main.contains("SwitchingCatalogueRepository("),
            "building one protocol directly is what made the other unreachable",
        )
    }

    @Test
    fun `the form can say which protocol this subscription is`() {
        // A portal is a different protocol, not a different server: the address alone does not
        // say which, so it has to be chosen rather than detected.
        assertTrue(dialog.contains("onProtocolChosen"), "the form reports the choice")
        assertTrue(dialog.contains("Portal Stalker/Ministra (MAC)"), "and offers it")
        assertTrue(
            app.contains("onProtocolChosen = appState::useStalkerForNextConnection"),
            "and it reaches the repository, or the choice changes nothing",
        )
    }

    @Test
    fun `the fields say what they want for a portal`() {
        // A portal takes an address and a MAC where Xtream takes a username and a password. Same
        // three boxes, different meaning — and a box labelled "Usuário" asking for a MAC is how
        // somebody types the wrong thing and is told their subscription is invalid.
        assertTrue(dialog.contains("""if (portal) "Portal" else "Servidor""""))
        assertTrue(dialog.contains("""if (portal) "MAC" else "Usuário""""))
    }

    @Test
    fun `switching protocol drops what the previous one had loaded`() {
        // A catalogue from the previous subscription would otherwise sit behind a session that no
        // longer owns it, and a search would return titles the new account cannot play.
        val useStalker = switching.substringAfter("fun useStalker() {").substringBefore("\n    }")
        assertTrue(useStalker.contains("active.clear()"), "the old session is ended first")
        assertTrue(useStalker.contains("active = stalker"))
    }

    @Test
    fun `signing out leaves neither protocol holding a session`() {
        // A catalogue held by the protocol that is not currently selected is still somebody's
        // subscription sitting in memory.
        val clear = switching.substringAfter("override fun clear() {").substringBefore("\n    }")
        assertTrue(clear.contains("xtream.clear()") && clear.contains("stalker.clear()"))
    }

    @Test
    fun `the switch holds no catalogue of its own`() {
        // Its whole safety rests on being a pass-through: a second copy of a subscription's data
        // would be one more thing to keep in step and one more place to leak from.
        assertFalse(
            switching.contains("mutableMapOf") || switching.contains("mutableListOf"),
            "state here would be a duplicate of the delegate's",
        )
    }

    @Test
    fun `a repository that cannot switch is tolerated rather than fatal`() {
        // Tests inject a single-protocol double. A form that refused to connect there would fail
        // tests about something else entirely.
        val body =
            state
                .substringAfter("fun useStalkerForNextConnection(stalker: Boolean) {")
                .substringBefore("\n    }")
        assertTrue(body.contains("as? SwitchingCatalogueRepository ?: return"))
    }
}
