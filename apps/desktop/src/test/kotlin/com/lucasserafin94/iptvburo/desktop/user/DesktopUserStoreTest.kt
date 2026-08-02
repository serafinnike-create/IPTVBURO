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
}
