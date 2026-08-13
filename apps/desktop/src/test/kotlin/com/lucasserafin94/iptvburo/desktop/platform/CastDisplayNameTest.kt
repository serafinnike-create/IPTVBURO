package com.lucasserafin94.iptvburo.desktop.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The name a discovered screen shows in the device list.
 *
 * Anyone on the network can answer discovery, and the name in that reply is free text. It is the
 * one part of discovery that can *deceive* rather than merely be wrong: the user reads this list
 * and picks from it, so a responder that can paint extra lines or reverse the text can present
 * itself as a machine it is not.
 */
class CastDisplayNameTest {
    private fun clean(claimed: String) = CastReceiver.displayNameFrom(claimed, "192.168.0.9")

    @Test
    fun `an ordinary name is left alone`() {
        assertEquals("Notebook da sala", clean("Notebook da sala"))
    }

    @Test
    fun `a newline cannot paint extra lines into the list`() {
        val result = clean("Sala\nAdministrador")

        assertFalse('\n' in result, "a newline survived into the device list")
        assertEquals("Sala Administrador", result)
    }

    @Test
    fun `a right-to-left override cannot reverse what is drawn`() {
        // U+202E flips the rendering of everything after it, which is how a name is made to read as
        // something else entirely on screen.
        val result = clean("Sala\u202Eanigap")

        assertFalse('\u202E' in result, "a bidirectional override survived")
    }

    @Test
    fun `control characters are dropped`() {
        val result = clean("Sala\u0000TV\u0007")

        assertTrue(result.none(Character::isISOControl), "a control character survived")
    }

    @Test
    fun `padding cannot push a suffix out of view`() {
        // Whitespace runs collapse, so a name cannot be spaced out until the honest part scrolls
        // off and only a reassuring tail is visible.
        assertEquals("TV da Sala (nao confiavel)", clean("TV da Sala${" ".repeat(40)}(nao confiavel)"))
    }

    @Test
    fun `a name that is only decoration falls back to the address`() {
        assertEquals("192.168.0.9", clean("\u0000\u202E   "))
    }

    @Test
    fun `an over-long name is cut rather than allowed to fill the list`() {
        assertTrue(clean("x".repeat(500)).length <= 60)
    }
}
