package com.lucasserafin94.iptvburo.desktop.user

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Corrections the user made to track names.
 *
 * Two properties matter more than the rest. Corrections must survive the source M3U being replaced,
 * because that is the whole reason they are an overlay rather than a rewrite. And a title containing
 * the storage separators must read back intact — a song legitimately called "A; B: C" is not a
 * reason to lose somebody's other corrections.
 */
class MusicCorrectionStoreTest {

    private val node =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/test/corrections/${System.nanoTime()}")

    private val store = MusicCorrectionStore(preferences = node)

    @AfterTest
    fun tearDown() {
        runCatching { node.removeNode() }
    }

    @Test
    fun `a correction round trips`() {
        store.put("p1", MusicCorrection("music:12", "Time", "Pink Floyd"))

        val stored = store.correctionsFor("p1")["music:12"]

        assertEquals("Time", stored?.title)
        assertEquals("Pink Floyd", stored?.artist)
    }

    /**
     * A title containing the separators survives.
     *
     * Fields are base64 encoded rather than escaped, precisely so this cannot go wrong: escaping is
     * one forgotten case away from a record that reads back as two.
     */
    @Test
    fun `separators inside a title are harmless`() {
        val awkward = "A; B: C — with; every: separator"
        store.put("p1", MusicCorrection("music:1", awkward, "Artist; With: Both"))

        val stored = store.correctionsFor("p1")

        assertEquals(1, stored.size, "the record must not split into several")
        assertEquals(awkward, stored["music:1"]?.title)
        assertEquals("Artist; With: Both", stored["music:1"]?.artist)
    }

    @Test
    fun `a track with genuinely no artist is distinguishable from an uncorrected one`() {
        store.put("p1", MusicCorrection("music:1", "Sound effect", artist = null))

        val stored = store.correctionsFor("p1")

        assertTrue(stored.containsKey("music:1"), "the correction exists")
        assertNull(stored["music:1"]?.artist, "and says the artist is genuinely absent")
    }

    @Test
    fun `corrections are per profile`() {
        store.put("p1", MusicCorrection("music:1", "Time", "Pink Floyd"))
        store.put("p2", MusicCorrection("music:1", "Tempo", "Outro"))

        assertEquals("Time", store.correctionsFor("p1")["music:1"]?.title)
        assertEquals("Tempo", store.correctionsFor("p2")["music:1"]?.title)
    }

    @Test
    fun `a bulk tidy stores every proposal`() {
        store.putAll(
            "p1",
            listOf(
                MusicCorrection("music:1", "Time", "Pink Floyd"),
                MusicCorrection("music:2", "Money", "Pink Floyd"),
                MusicCorrection("music:3", "Echoes", "Pink Floyd"),
            ),
        )

        assertEquals(3, store.correctionsFor("p1").size)
    }

    @Test
    fun `correcting the same track twice keeps the newer answer`() {
        store.put("p1", MusicCorrection("music:1", "Wrong", "Wrong"))
        store.put("p1", MusicCorrection("music:1", "Right", "Right"))

        assertEquals(1, store.correctionsFor("p1").size)
        assertEquals("Right", store.correctionsFor("p1")["music:1"]?.title)
    }

    @Test
    fun `removing one correction restores that track only`() {
        store.putAll(
            "p1",
            listOf(
                MusicCorrection("music:1", "Time", "Pink Floyd"),
                MusicCorrection("music:2", "Money", "Pink Floyd"),
            ),
        )

        store.remove("p1", "music:1")

        assertNull(store.correctionsFor("p1")["music:1"])
        assertNotNull(store.correctionsFor("p1")["music:2"], "the other must survive")
    }

    @Test
    fun `clearing is the way back from a tidy gone wrong`() {
        store.putAll("p1", (1..20).map { MusicCorrection("music:$it", "T$it", "A$it") })

        store.clear("p1")

        assertTrue(store.correctionsFor("p1").isEmpty())
    }

    @Test
    fun `a large tidy keeps the newest corrections too`() {
        val many = (1..600).map { index ->
            MusicCorrection("music:$index", "A reasonably long track title number $index", "Artist $index")
        }

        assertEquals(many.size, store.putAll("p1", many))
        val stored = store.correctionsFor("p1")

        assertEquals(many.size, stored.size)
        assertEquals("A reasonably long track title number 600", stored["music:600"]?.title)
    }

    @Test
    fun `a profile with no corrections reads as empty rather than failing`() {
        assertTrue(store.correctionsFor("never-used").isEmpty())
        assertTrue(store.correctionsFor(null).isEmpty())
    }

    @Test
    fun `writing without a profile is ignored rather than throwing`() {
        // Reached during first-run setup, before a profile exists. A crash there would be worse
        // than silently doing nothing, since there is nowhere to store it anyway.
        store.put(null, MusicCorrection("music:1", "Time", "Pink Floyd"))
        store.putAll(null, listOf(MusicCorrection("music:2", "Money", "Pink Floyd")))
        store.clear(null)
    }

    @Test
    fun `a corrupt row costs only itself`() {
        store.putAll(
            "p1",
            listOf(
                MusicCorrection("music:1", "Time", "Pink Floyd"),
                MusicCorrection("music:2", "Money", "Pink Floyd"),
            ),
        )

        // Whatever the encoding, a record that cannot be decoded must not take the others with it.
        val profiles = node.node("profiles")
        val profile = profiles.node(profiles.childrenNames().single())
        profile.put(profile.keys().first(), "!!!not-base64!!!")

        val stored = store.correctionsFor("p1")

        assertEquals(1, stored.size, "the other readable row must survive")
    }
}
