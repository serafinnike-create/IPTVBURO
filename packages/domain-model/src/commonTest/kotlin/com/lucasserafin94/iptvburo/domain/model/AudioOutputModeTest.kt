package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioOutputModeTest {
    /**
     * The default must not touch the sound card.
     *
     * Asking for more speakers than Windows is configured for can silence playback rather than
     * improve it, so "do not interfere" is the only choice guaranteed to produce sound everywhere.
     */
    @Test
    fun `the system mode passes no audio arguments at all`() {
        assertTrue(AudioOutputMode.SYSTEM.vlcArguments().isEmpty())
    }

    @Test
    fun `a speaker mode asks for exactly that speaker layout`() {
        assertEquals(listOf("--directx-audio-speaker=5.1"), AudioOutputMode.SURROUND_51.vlcArguments())
        assertEquals(listOf("--directx-audio-speaker=7.1"), AudioOutputMode.SURROUND_71.vlcArguments())
    }

    /**
     * Headphones are two channels plus the binaural filter, never a speaker count.
     *
     * Sending "7.1" to a headphone jack asks the sound card for a layout it does not have, which is
     * the configuration most likely to produce silence.
     */
    @Test
    fun `headphones stay stereo and add the binaural filter`() {
        val arguments = AudioOutputMode.HEADPHONES.vlcArguments()

        assertTrue("--directx-audio-speaker=Stereo" in arguments)
        assertTrue("--audio-filter=headphone" in arguments)
        assertFalse(arguments.any { "7.1" in it || "5.1" in it })
    }

    /** A stereo track can be spread to either layout, and both are offered. */
    @Test
    fun `a stereo track is offered both upmixes`() {
        val options = AudioOutputMode.optionsFor(channels = 2)

        assertTrue(AudioOutputMode.SURROUND_51 in options)
        assertTrue(AudioOutputMode.SURROUND_71 in options)
    }

    /**
     * A 5.1 track is not offered an upmix to 5.1.
     *
     * There is nothing to upmix, and an option that claims to add what is already there is the kind
     * of decoration that teaches people the controls mean nothing.
     */
    @Test
    fun `a 5_1 track is not offered an upmix to 5_1`() {
        val options = AudioOutputMode.optionsFor(channels = 6)

        assertFalse(AudioOutputMode.SURROUND_51 in options)
        assertTrue(AudioOutputMode.SURROUND_71 in options, "eight speakers is still more than six")
    }

    /** Headphones are offered whatever the track carries — a real 5.1 mix is the ideal input. */
    @Test
    fun `headphones are offered for every track`() {
        listOf(1, 2, 6, 8).forEach { channels ->
            assertTrue(
                AudioOutputMode.HEADPHONES in AudioOutputMode.optionsFor(channels),
                "$channels channels should still offer headphones",
            )
        }
    }

    /** The safe default is always available, so there is always a way back. */
    @Test
    fun `the system default is always offered`() {
        listOf(0, 1, 2, 6, 8).forEach { channels ->
            assertEquals(AudioOutputMode.SYSTEM, AudioOutputMode.optionsFor(channels).first())
        }
    }
}
