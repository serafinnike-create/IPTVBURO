package com.lucasserafin94.iptvburo.desktop.user

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    /**
     * A profile with no key of its own uses the shared one.
     *
     * This is the default and the common case: a household has one TMDb key and no reason for two,
     * so an empty field must mean "use the other one" rather than "use nothing" — otherwise adding
     * a profile would silently switch its posters and synopses off.
     */
    @Test
    fun `a profile with no key of its own inherits the shared one`() {
        withStore { store ->
            store.setMetadataApiKey("shared-key")

            assertEquals("shared-key", store.effectiveMetadataApiKey("anyone"))
            assertNull(store.profileMetadataApiKey("anyone"), "the profile has no key of its own")
        }
    }

    /** A profile that sets its own key uses it, so its requests spend its own quota. */
    @Test
    fun `a profile key overrides the shared one`() {
        withStore { store ->
            store.setMetadataApiKey("shared-key")
            store.setProfileMetadataApiKey("lucas", "lucas-key")

            assertEquals("lucas-key", store.effectiveMetadataApiKey("lucas"))
            assertEquals("shared-key", store.effectiveMetadataApiKey("someone-else"))
        }
    }

    /** Clearing a profile key falls back rather than leaving that profile with none. */
    @Test
    fun `clearing a profile key restores the shared one`() {
        withStore { store ->
            store.setMetadataApiKey("shared-key")
            store.setProfileMetadataApiKey("lucas", "lucas-key")

            store.setProfileMetadataApiKey("lucas", "")

            assertEquals("shared-key", store.effectiveMetadataApiKey("lucas"))
        }
    }

    /** Keys are per profile, so one person's does not leak into another's requests. */
    @Test
    fun `profile keys are isolated from each other`() {
        withStore { store ->
            store.setProfileMetadataApiKey("lucas", "lucas-key")
            store.setProfileMetadataApiKey("ana", "ana-key")

            assertEquals("lucas-key", store.effectiveMetadataApiKey("lucas"))
            assertEquals("ana-key", store.effectiveMetadataApiKey("ana"))
        }
    }

    /** Whitespace is not a key: pasting spaces must inherit, not configure an unusable one. */
    @Test
    fun `a blank profile key is treated as absent`() {
        withStore { store ->
            store.setMetadataApiKey("shared-key")
            store.setProfileMetadataApiKey("lucas", "   ")

            assertEquals("shared-key", store.effectiveMetadataApiKey("lucas"))
        }
    }

    /** With nothing configured at all the caller decides — here, the bundled key. */
    @Test
    fun `no key anywhere resolves to nothing`() {
        withStore { store ->
            assertNull(store.effectiveMetadataApiKey("lucas"))
            assertNull(store.effectiveMetadataApiKey(null))
        }
    }

    /**
     * A machine that has never been resized has no geometry, and the caller opens maximised.
     *
     * Null rather than a default size: a catalogue of posters is a poor fit for a small window, so
     * the first launch fills the screen — and only a deliberate resize changes that.
     */
    @Test
    fun `a fresh install remembers no window geometry`() {
        withStore { store ->
            assertNull(store.windowGeometry())
        }
    }

    @Test
    fun `a resized window is remembered exactly`() {
        withStore { store ->
            store.setWindowGeometry(
                StoredWindowGeometry(maximised = false, width = 1200f, height = 800f, x = 40f, y = 25f),
            )

            val restored = store.windowGeometry()

            assertEquals(false, restored?.maximised)
            assertEquals(1200f, restored?.width)
            assertEquals(800f, restored?.height)
            assertEquals(40f, restored?.x)
            assertEquals(25f, restored?.y)
        }
    }

    /** Maximised is a state of its own: reopening must maximise, not restore the pre-maximise size. */
    @Test
    fun `a maximised window is remembered as maximised`() {
        withStore { store ->
            store.setWindowGeometry(
                StoredWindowGeometry(maximised = true, width = 1380f, height = 860f, x = 0f, y = 0f),
            )

            assertEquals(true, store.windowGeometry()?.maximised)
        }
    }

    /**
     * A half-written or corrupted value reads as absent rather than placing a window nowhere.
     *
     * Written straight into the preferences node, because that is the only way this state arises:
     * a build that stored a different shape, or a truncated write.
     */
    @Test
    fun `a malformed geometry is ignored`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            listOf("", "1|2|3", "not|a|geometry|at|all", "1|2|3|4|5|6") .forEach { corrupt ->
                node.put("window-geometry", corrupt)
                assertNull(store.windowGeometry(), "'$corrupt' must not produce a geometry")
            }
        } finally {
            node.removeNode()
        }
    }

    /**
     * The loading screen's backdrop comes from the previous session.
     *
     * It has to: the wall is meant to fill the wait, and anything derived from the current session
     * only exists once that wait is over. A fresh install has none, and the wall draws nothing.
     */
    @Test
    fun `backdrop posters survive to the next launch`() {
        withStore { store ->
            assertTrue(store.backdropPosters().isEmpty(), "a fresh install has no backdrop")

            store.setBackdropPosters(listOf("https://images.invalid/a.jpg", "https://images.invalid/b.jpg"))

            assertEquals(
                listOf("https://images.invalid/a.jpg", "https://images.invalid/b.jpg"),
                store.backdropPosters(),
            )
        }
    }

    /** Capped, so a long catalogue cannot push the value past what a preference will hold. */
    @Test
    fun `backdrop posters are capped`() {
        withStore { store ->
            store.setBackdropPosters((1..200).map { index -> "https://images.invalid/$index.jpg" })

            assertEquals(18, store.backdropPosters().size)
        }
    }

    /** Clearing them means the wall draws nothing, rather than drawing empty tiles. */
    @Test
    fun `an empty backdrop list clears the stored one`() {
        withStore { store ->
            store.setBackdropPosters(listOf("https://images.invalid/a.jpg"))
            store.setBackdropPosters(emptyList())

            assertTrue(store.backdropPosters().isEmpty())
        }
    }

    private fun withStore(block: (DesktopUserStore) -> Unit) {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            block(DesktopUserStore(node))
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
    fun `the parental lock survives a restart`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("mine", "Lucas", false)))

            store.setParentalLock(
                "mine",
                StoredParentalLock(
                    salt = "s1",
                    hash = "h1",
                    lockAdultCategories = true,
                    lockedCategoryIds = setOf("42", "cat|with,separators"),
                ),
            )

            val reloaded = DesktopUserStore(node).parentalLock("mine")
            assertTrue(reloaded.hasPin)
            assertEquals("s1", reloaded.salt)
            assertTrue(reloaded.lockAdultCategories)
            // The id containing this format's own separators must come back whole.
            assertEquals(setOf("42", "cat|with,separators"), reloaded.lockedCategoryIds)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `a profile with no lock configured has none`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("mine", "Lucas", false)))

            val lock = store.parentalLock("mine")
            assertTrue(!lock.hasPin, "a profile that never set a PIN must not appear to have one")
            assertTrue(lock.lockedCategoryIds.isEmpty())
        } finally {
            node.removeNode()
        }
    }

    /** Turning adult locking off must persist: defaulting it back on would override the user. */
    @Test
    fun `switching adult locking off is remembered`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("mine", "Lucas", false)))
            store.setParentalLock("mine", StoredParentalLock(salt = "s", hash = "h", lockAdultCategories = false))

            assertTrue(!DesktopUserStore(node).parentalLock("mine").lockAdultCategories)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `each profile keeps its own lock`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("a", "A", false), DesktopProfile("b", "B", false)))
            store.setParentalLock("a", StoredParentalLock(salt = "s", hash = "h", lockedCategoryIds = setOf("1")))

            assertTrue(store.parentalLock("a").hasPin)
            assertTrue(!store.parentalLock("b").hasPin, "the lock leaked to another profile")
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
