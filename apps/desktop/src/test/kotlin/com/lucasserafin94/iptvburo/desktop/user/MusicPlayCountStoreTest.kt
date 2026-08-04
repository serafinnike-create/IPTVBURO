package com.lucasserafin94.iptvburo.desktop.user

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicPlayCountStoreTest {
    private fun node(): Preferences =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")

    @Test
    fun `a play is counted and survives a reload`() {
        val node = node()
        try {
            val store = MusicPlayCountStore(node)
            store.recordPlay("profile", "music:7")
            store.recordPlay("profile", "music:7")
            store.recordPlay("profile", "music:9")

            val reloaded = MusicPlayCountStore(node).countsFor("profile")
            assertEquals(2, reloaded["music:7"])
            assertEquals(1, reloaded["music:9"])
        } finally {
            node.removeNode()
        }
    }

    /** The music library is per profile, exactly as favourites are. */
    @Test
    fun `counts stay isolated between profiles`() {
        val node = node()
        try {
            val store = MusicPlayCountStore(node)
            store.recordPlay("adult", "music:1")
            store.recordPlay("kids", "music:2")

            assertEquals(mapOf("music:1" to 1), store.countsFor("adult"))
            assertEquals(mapOf("music:2" to 1), store.countsFor("kids"))
            assertTrue(store.countsFor(null).isEmpty())
        } finally {
            node.removeNode()
        }
    }

    /**
     * A track id comes from the playlist's own tvg-id, which may contain the characters this
     * format separates on. Without encoding, such an id would corrupt every count after it.
     */
    @Test
    fun `a track id containing separators round trips`() {
        val node = node()
        try {
            val awkward = "music:artist;album:track"
            val store = MusicPlayCountStore(node)
            store.recordPlay("profile", awkward)
            store.recordPlay("profile", "music:plain")

            val reloaded = MusicPlayCountStore(node).countsFor("profile")
            assertEquals(1, reloaded[awkward])
            assertEquals(1, reloaded["music:plain"])
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `an unreadable value reads as nothing played rather than throwing`() {
        val node = node()
        try {
            node.put("plays.profile", "this is not a count list")
            assertTrue(MusicPlayCountStore(node).countsFor("profile").isEmpty())
        } finally {
            node.removeNode()
        }
    }

    /** Playing with no profile selected must not throw; there is nowhere to record it. */
    @Test
    fun `recording a play without a profile is a no-op`() {
        val node = node()
        try {
            assertTrue(MusicPlayCountStore(node).recordPlay(null, "music:1").isEmpty())
        } finally {
            node.removeNode()
        }
    }
}
