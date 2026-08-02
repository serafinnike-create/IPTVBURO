package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopPlaybackProgressStoreTest {
    private val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
    private val store = DesktopPlaybackProgressStore(node)

    @AfterTest
    fun cleanUp() = node.removeNode()

    @Test
    fun `progress survives a fresh store instance and remains profile isolated`() {
        val identity = PlaybackProgressIdentity("profile-a", "source-a", "movie-42", PlaybackContentType.MOVIE)
        store.save(progress(identity, revision = 1))

        val reopened = DesktopPlaybackProgressStore(node)
        assertEquals(120_000L, reopened.find(identity)?.positionMs)
        assertEquals(1, reopened.continueWatching("profile-a").size)
        assertEquals(0, reopened.continueWatching("profile-b").size)
    }

    @Test
    fun `older revision and incomplete checkpoint cannot overwrite completion`() {
        val identity = PlaybackProgressIdentity("profile", "source", "episode", PlaybackContentType.EPISODE)
        store.save(progress(identity, revision = 3, completedAt = 500))
        store.save(progress(identity, revision = 2))
        store.save(progress(identity, revision = 4))

        assertEquals(3, store.find(identity)?.revision)
        assertFalse(node.keys().any { it.contains("episode") || it.contains("profile") })
    }

    private fun progress(
        identity: PlaybackProgressIdentity,
        revision: Long,
        completedAt: Long? = null,
    ) = PlaybackProgress(
        identity = identity,
        positionMs = 120_000,
        durationMs = 1_000_000,
        progressPercent = 0.12,
        lastWatchedAtEpochMillis = 100,
        completedAtEpochMillis = completedAt,
        updatedAtEpochMillis = 100,
        revision = revision,
    )
}
