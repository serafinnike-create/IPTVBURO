package com.lucasserafin94.iptvburo.desktop.user

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopUserStoreTest {
    @Test
    fun `favorites remain isolated between family profiles`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            val adult = DesktopProfile("adult", "Adulto", false)
            val kids = DesktopProfile("kids", "Kids", true)
            store.saveProfiles(listOf(adult, kids))

            store.setFavorites(adult.id, setOf("MOVIE:10"))
            store.setFavorites(kids.id, setOf("SERIES:20"))

            assertEquals(setOf("MOVIE:10"), store.favoritesForProfile(adult.id))
            assertEquals(setOf("SERIES:20"), store.favoritesForProfile(kids.id))
            assertTrue(store.favoritesForProfile(null).isEmpty())
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `a profile remembers the playlist it signs in to`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            val mine = DesktopProfile("mine", "Lucas", false, avatarIndex = 2, sourceId = "source-a")
            val theirs = DesktopProfile("theirs", "Ana", false, avatarIndex = 3, sourceId = "source-b")
            store.saveProfiles(listOf(mine, theirs))

            val reloaded = DesktopUserStore(node).load().profiles
            assertEquals("source-a", reloaded.first { it.id == "mine" }.sourceId)
            assertEquals("source-b", reloaded.first { it.id == "theirs" }.sourceId)
        } finally {
            node.removeNode()
        }
    }

    /** A household on one subscription: same playlist, separate favourites. */
    @Test
    fun `two profiles may share one playlist`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            val first = DesktopProfile("first", "Lucas", false, sourceId = "shared")
            val second = DesktopProfile("second", "Ana", false, sourceId = "shared")
            store.saveProfiles(listOf(first, second))
            store.setFavorites(first.id, setOf("MOVIE:1"))

            val reloaded = DesktopUserStore(node)
            assertEquals("shared", reloaded.load().profiles.first { it.id == "second" }.sourceId)
            assertEquals(setOf("MOVIE:1"), reloaded.favoritesForProfile("first"))
            assertTrue(reloaded.favoritesForProfile("second").isEmpty())
        } finally {
            node.removeNode()
        }
    }

    /** Rows written by builds before per-profile playlists must still load. */
    @Test
    fun `profiles saved without a playlist still decode`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("solo", "Meu perfil", false, avatarIndex = 1)))

            val reloaded = DesktopUserStore(node).load().profiles.single()
            assertEquals(null, reloaded.sourceId)
            assertEquals(1, reloaded.avatarIndex)
            assertEquals("Meu perfil", reloaded.name)
        } finally {
            node.removeNode()
        }
    }
}
