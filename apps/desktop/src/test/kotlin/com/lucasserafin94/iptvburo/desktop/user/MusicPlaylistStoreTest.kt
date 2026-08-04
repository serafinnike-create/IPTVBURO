package com.lucasserafin94.iptvburo.desktop.user

import com.lucasserafin94.iptvburo.domain.model.MusicPlaylist
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistKind
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Persistence for the playlists of GDD 8 section 17. */
class MusicPlaylistStoreTest {
    private fun node(): Preferences =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")

    @Test
    fun `a created playlist survives a reload`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            store.create("profile", "Road trip", trackIds = listOf("music:1", "music:2"))

            val reloaded = MusicPlaylistStore(node).playlistsFor("profile")

            assertEquals(1, reloaded.size)
            assertEquals("Road trip", reloaded.single().name)
            assertEquals(listOf("music:1", "music:2"), reloaded.single().trackIds)
        } finally {
            node.removeNode()
        }
    }

    /**
     * Identity is a UUID, not the name, so a rename cannot orphan the playlist. This is the
     * persistence half of the same guarantee `MusicPlaylistTest` asserts on the model.
     */
    @Test
    fun `renaming keeps the stored identity and tracks`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            val created = store.create("profile", "Old name", trackIds = listOf("music:1")).single()

            store.update("profile", created.id) { it.renamed("New name") }
            val reloaded = MusicPlaylistStore(node).playlistsFor("profile").single()

            assertEquals(created.id, reloaded.id)
            assertEquals("New name", reloaded.name)
            assertEquals(listOf("music:1"), reloaded.trackIds)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `reordering survives a reload`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            val created = store.create("profile", "Set", trackIds = listOf("a", "b", "c")).single()

            store.update("profile", created.id) { it.reordered(0, 2) }

            assertEquals(listOf("b", "c", "a"), MusicPlaylistStore(node).playlistsFor("profile").single().trackIds)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `deleting removes only the named playlist`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            val first = store.create("profile", "First").single()
            store.create("profile", "Second")

            val remaining = store.delete("profile", first.id)

            assertEquals(listOf("Second"), remaining.map(MusicPlaylist::name))
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `playlists stay isolated between profiles`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            store.create("adult", "Mine")
            store.create("kids", "Theirs")

            assertEquals(listOf("Mine"), store.playlistsFor("adult").map(MusicPlaylist::name))
            assertEquals(listOf("Theirs"), store.playlistsFor("kids").map(MusicPlaylist::name))
            assertTrue(store.playlistsFor(null).isEmpty())
        } finally {
            node.removeNode()
        }
    }

    /** A name and a track id both come from user data and may contain the format's separators. */
    @Test
    fun `names and ids containing separators round trip`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            store.create("p", "Rock; Pop: Best, Ever", trackIds = listOf("music:a;b,c", "music:plain"))

            val reloaded = MusicPlaylistStore(node).playlistsFor("p").single()

            assertEquals("Rock; Pop: Best, Ever", reloaded.name)
            assertEquals(listOf("music:a;b,c", "music:plain"), reloaded.trackIds)
        } finally {
            node.removeNode()
        }
    }

    /** A computed list has no stored membership; storing one would be a second source of truth. */
    @Test
    fun `a smart playlist is never persisted`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            store.save(
                "p",
                listOf(
                    MusicPlaylist("id-1", "Smart", MusicPlaylistKind.SMART),
                    MusicPlaylist("id-2", "Manual", MusicPlaylistKind.MANUAL, trackIds = listOf("a")),
                ),
            )

            assertEquals(listOf("Manual"), MusicPlaylistStore(node).playlistsFor("p").map(MusicPlaylist::name))
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `a saved queue playlist is persisted like a manual one`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            store.create("p", "Queue 1", kind = MusicPlaylistKind.SAVED_QUEUE, trackIds = listOf("a", "b"))

            val reloaded = MusicPlaylistStore(node).playlistsFor("p").single()

            assertEquals(MusicPlaylistKind.SAVED_QUEUE, reloaded.kind)
            assertEquals(listOf("a", "b"), reloaded.trackIds)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `a corrupt row does not cost the others`() {
        val node = node()
        try {
            val store = MusicPlaylistStore(node)
            store.create("p", "Good", trackIds = listOf("a"))
            node.put("playlists.p", node.get("playlists.p", "") + ";not-a-playlist-row")

            assertEquals(listOf("Good"), MusicPlaylistStore(node).playlistsFor("p").map(MusicPlaylist::name))
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `a blank name creates nothing`() {
        val node = node()
        try {
            assertTrue(MusicPlaylistStore(node).create("p", "   ").isEmpty())
        } finally {
            node.removeNode()
        }
    }
}
