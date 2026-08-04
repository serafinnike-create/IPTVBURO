package com.lucasserafin94.iptvburo.desktop.user

import com.lucasserafin94.iptvburo.domain.model.ListeningHistoryRules
import com.lucasserafin94.iptvburo.domain.model.ListeningKind
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Persistence for the listening history of GDD 8 section 18.
 *
 * The migration tests matter most: this store supersedes [MusicPlayCountStore], and a migration
 * that ran twice would silently double every play count the user had accumulated.
 */
class ListeningHistoryStoreTest {
    private fun node(): Preferences =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")

    private fun legacyNode(): Preferences =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-legacy-${UUID.randomUUID()}")

    private val threshold = ListeningHistoryRules.DEFAULT_MUSIC_PLAY_THRESHOLD_MILLIS

    @Test
    fun `a counted play survives a reload`() {
        val node = node()
        val legacy = legacyNode()
        try {
            val store = ListeningHistoryStore(node, MusicPlayCountStore(legacy))
            store.record("profile", "music:7", ListeningKind.MUSIC, listenedMillis = threshold)
            store.record("profile", "music:7", ListeningKind.MUSIC, listenedMillis = threshold)

            val reloaded = ListeningHistoryStore(node, MusicPlayCountStore(legacy)).historyFor("profile")

            assertEquals(2, reloaded.getValue("music:7").playCount)
        } finally {
            node.removeNode()
            legacy.removeNode()
        }
    }

    /** Section 18: a play below the threshold is recorded but not counted. */
    @Test
    fun `a below threshold play is stored without counting`() {
        val node = node()
        val legacy = legacyNode()
        try {
            val store = ListeningHistoryStore(node, MusicPlayCountStore(legacy))
            store.record("profile", "music:1", ListeningKind.MUSIC, listenedMillis = 1_000L)

            val history = store.historyFor("profile")

            assertEquals(0, history.getValue("music:1").playCount)
            assertTrue(store.playCountsFor("profile").isEmpty(), "an uncounted play must not rank")
        } finally {
            node.removeNode()
            legacy.removeNode()
        }
    }

    @Test
    fun `a podcast position round trips but a music position is never stored`() {
        val node = node()
        val legacy = legacyNode()
        try {
            val store = ListeningHistoryStore(node, MusicPlayCountStore(legacy))
            store.record("p", "pod:1", ListeningKind.PODCAST, positionMillis = 60_000L, durationMillis = 600_000L)
            store.record("p", "music:1", ListeningKind.MUSIC, listenedMillis = threshold, positionMillis = 45_000L)

            val history = ListeningHistoryStore(node, MusicPlayCountStore(legacy)).historyFor("p")

            assertEquals(60_000L, history.getValue("pod:1").lastPositionMillis)
            assertEquals(600_000L, history.getValue("pod:1").durationMillis)
            assertNull(history.getValue("music:1").lastPositionMillis)
        } finally {
            node.removeNode()
            legacy.removeNode()
        }
    }

    @Test
    fun `history stays isolated between profiles`() {
        val node = node()
        val legacy = legacyNode()
        try {
            val store = ListeningHistoryStore(node, MusicPlayCountStore(legacy))
            store.record("adult", "music:1", ListeningKind.MUSIC, listenedMillis = threshold)
            store.record("kids", "music:2", ListeningKind.MUSIC, listenedMillis = threshold)

            assertEquals(setOf("music:1"), store.historyFor("adult").keys)
            assertEquals(setOf("music:2"), store.historyFor("kids").keys)
            assertTrue(store.historyFor(null).isEmpty())
        } finally {
            node.removeNode()
            legacy.removeNode()
        }
    }

    /** Ids come from the playlist's own tvg-id and may contain the format's separators. */
    @Test
    fun `an identity containing separators round trips`() {
        val node = node()
        val legacy = legacyNode()
        try {
            val awkward = "music:artist;album:track,2"
            val store = ListeningHistoryStore(node, MusicPlayCountStore(legacy))
            store.record("p", awkward, ListeningKind.MUSIC, listenedMillis = threshold)
            store.record("p", "music:plain", ListeningKind.MUSIC, listenedMillis = threshold)

            val history = ListeningHistoryStore(node, MusicPlayCountStore(legacy)).historyFor("p")

            assertEquals(1, history.getValue(awkward).playCount)
            assertEquals(1, history.getValue("music:plain").playCount)
        } finally {
            node.removeNode()
            legacy.removeNode()
        }
    }

    @Test
    fun `an unreadable value reads as no history rather than throwing`() {
        val node = node()
        val legacy = legacyNode()
        try {
            node.put("history.profile", "this is not a history list")
            assertTrue(ListeningHistoryStore(node, MusicPlayCountStore(legacy)).historyFor("profile").isEmpty())
        } finally {
            node.removeNode()
            legacy.removeNode()
        }
    }

    @Test
    fun `recording without a profile is a no-op`() {
        val node = node()
        val legacy = legacyNode()
        try {
            val store = ListeningHistoryStore(node, MusicPlayCountStore(legacy))
            assertTrue(store.record(null, "music:1", ListeningKind.MUSIC).isEmpty())
        } finally {
            node.removeNode()
            legacy.removeNode()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Migration from the legacy count store
    // -----------------------------------------------------------------------------------------

    @Test
    fun `legacy play counts are migrated so the most played shelf survives`() {
        val node = node()
        val legacyPrefs = legacyNode()
        try {
            val legacy = MusicPlayCountStore(legacyPrefs)
            legacy.recordPlay("profile", "music:7")
            legacy.recordPlay("profile", "music:7")
            legacy.recordPlay("profile", "music:9")

            val history = ListeningHistoryStore(node, legacy).historyFor("profile")

            assertEquals(2, history.getValue("music:7").playCount)
            assertEquals(1, history.getValue("music:9").playCount)
            assertEquals(ListeningKind.MUSIC, history.getValue("music:7").kind)
        } finally {
            node.removeNode()
            legacyPrefs.removeNode()
        }
    }

    /** Running twice would double every count the user had accumulated. */
    @Test
    fun `migration runs only once`() {
        val node = node()
        val legacyPrefs = legacyNode()
        try {
            val legacy = MusicPlayCountStore(legacyPrefs)
            legacy.recordPlay("profile", "music:7")

            val store = ListeningHistoryStore(node, legacy)
            store.historyFor("profile")
            store.historyFor("profile")
            val afterReload = ListeningHistoryStore(node, legacy).historyFor("profile")

            assertEquals(1, afterReload.getValue("music:7").playCount)
        } finally {
            node.removeNode()
            legacyPrefs.removeNode()
        }
    }

    /** A new play after migration must build on the migrated count, not restart from zero. */
    @Test
    fun `a play after migration increments the migrated count`() {
        val node = node()
        val legacyPrefs = legacyNode()
        try {
            val legacy = MusicPlayCountStore(legacyPrefs)
            legacy.recordPlay("profile", "music:7")
            legacy.recordPlay("profile", "music:7")

            val store = ListeningHistoryStore(node, legacy)
            store.record("profile", "music:7", ListeningKind.MUSIC, listenedMillis = threshold)

            assertEquals(3, store.historyFor("profile").getValue("music:7").playCount)
        } finally {
            node.removeNode()
            legacyPrefs.removeNode()
        }
    }

    @Test
    fun `migration does not overwrite existing history`() {
        val node = node()
        val legacyPrefs = legacyNode()
        try {
            val legacy = MusicPlayCountStore(legacyPrefs)
            legacy.recordPlay("profile", "music:legacy")

            // History written first, by a build that had already migrated a different device.
            ListeningHistoryStore(node, MusicPlayCountStore(legacyNode()))
                .record("profile", "music:new", ListeningKind.MUSIC, listenedMillis = threshold)
            node.remove("history-migrated.profile")

            val history = ListeningHistoryStore(node, legacy).historyFor("profile")

            assertTrue("music:new" in history, "existing history must survive")
            assertTrue("music:legacy" !in history, "migration must not merge into existing history")
        } finally {
            node.removeNode()
            legacyPrefs.removeNode()
        }
    }
}
