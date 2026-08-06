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

    /**
     * The avatar set grew from eight to sixteen. Clamping on load would have rewritten a stored
     * choice of, say, 12 into a different face every time the app started.
     */
    @Test
    fun `an avatar index beyond the old set survives a round trip`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            DesktopUserStore(node).saveProfiles(
                listOf(DesktopProfile("solo", "Lucas", false, avatarIndex = 12)),
            )

            assertEquals(12, DesktopUserStore(node).load().profiles.single().avatarIndex)
        } finally {
            node.removeNode()
        }
    }

    /** The music playlist is optional; a profile without one must decode as having none. */
    @Test
    fun `a music playlist round trips with the profile`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            DesktopUserStore(node).saveProfiles(
                listOf(
                    DesktopProfile("with", "Lucas", false, musicPlaylistPath = "D:\\Musicas\\lista.m3u"),
                    DesktopProfile("without", "Ana", false),
                ),
            )

            val reloaded = DesktopUserStore(node).load().profiles
            assertEquals("D:\\Musicas\\lista.m3u", reloaded.first { it.id == "with" }.musicPlaylistPath)
            assertEquals(null, reloaded.first { it.id == "without" }.musicPlaylistPath)
        } finally {
            node.removeNode()
        }
    }

    /**
     * A Windows path carries the drive's own ':', which is this format's field separator. Stored
     * raw it would split the row into seven fields and take it out of the decodable range,
     * silently discarding the profile along with its favourites.
     */
    @Test
    fun `a profile survives a music path containing the field separator`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            DesktopUserStore(node).saveProfiles(
                listOf(DesktopProfile("solo", "Lucas", false, avatarIndex = 4, sourceId = "src", musicPlaylistPath = "C:\\a;b\\c.m3u")),
            )

            val reloaded = DesktopUserStore(node).load().profiles.single()
            assertEquals("C:\\a;b\\c.m3u", reloaded.musicPlaylistPath)
            assertEquals("src", reloaded.sourceId)
            assertEquals(4, reloaded.avatarIndex)
            assertEquals("Lucas", reloaded.name)
        } finally {
            node.removeNode()
        }
    }

    /**
     * Rows written before the music field existed have five fields. Decoding must default the new
     * field rather than reject the row — an older build's profiles are still the user's profiles.
     */
    @Test
    fun `five field rows from older builds still decode`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val name = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Lucas".toByteArray(Charsets.UTF_8))
            node.put("profiles", "legacy:$name:0:2:source-a")

            val reloaded = DesktopUserStore(node).load().profiles.single()
            assertEquals("Lucas", reloaded.name)
            assertEquals("source-a", reloaded.sourceId)
            assertEquals(2, reloaded.avatarIndex)
            assertEquals(null, reloaded.musicPlaylistPath)
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

    @Test
    fun `each profile keeps its own streaming services`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("mine", "Lucas", false), DesktopProfile("theirs", "Ana", false)))

            store.setStreamingPreference(
                "mine",
                StoredStreamingPreference(region = "BR", currency = "BRL", subscribedProviderIds = setOf("a", "b")),
            )
            store.setStreamingPreference("theirs", StoredStreamingPreference(region = "PT"))

            val reloaded = DesktopUserStore(node)
            assertEquals(setOf("a", "b"), reloaded.streamingPreference("mine").subscribedProviderIds)
            assertEquals("BR", reloaded.streamingPreference("mine").region)
            assertEquals("BRL", reloaded.streamingPreference("mine").currency)

            assertTrue(reloaded.streamingPreference("theirs").subscribedProviderIds.isEmpty())
            assertEquals("PT", reloaded.streamingPreference("theirs").region)
            assertEquals(null, reloaded.streamingPreference("theirs").currency)
        } finally {
            node.removeNode()
        }
    }

    /**
     * A profile that predates this feature has no stored row. It must come back as "nothing stated",
     * not as an error and not as a guessed region — guessing would quietly filter the catalogue.
     */
    @Test
    fun `a profile that never opened the screen has no stated preference`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("old", "Perfil antigo", false)))

            val stored = DesktopUserStore(node).streamingPreference("old")
            assertEquals(StoredStreamingPreference(), stored)
            assertEquals(StoredStreamingPreference(), store.streamingPreference(null))
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `clearing every service is remembered rather than read as unset`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("mine", "Lucas", false)))
            store.setStreamingPreference("mine", StoredStreamingPreference(region = "BR", subscribedProviderIds = setOf("a")))

            store.setStreamingPreference("mine", StoredStreamingPreference(region = "BR"))

            val stored = DesktopUserStore(node).streamingPreference("mine")
            assertTrue(stored.subscribedProviderIds.isEmpty())
            assertEquals("BR", stored.region)
        } finally {
            node.removeNode()
        }
    }
}
