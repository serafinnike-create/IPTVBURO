package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The per-kind progress rules from GDD 8 section 18.
 *
 * The threshold and the position rules are the substance here. A naive implementation counts a play
 * the moment the user clicks — which is what this repository did before — and that manufactures a
 * "most played" ranking out of tracks that were skipped after two seconds.
 */
class ListeningHistoryTest {
    private val threshold = ListeningHistoryRules.DEFAULT_MUSIC_PLAY_THRESHOLD_MILLIS

    private fun record(
        existing: ListeningHistoryEntry? = null,
        kind: ListeningKind = ListeningKind.MUSIC,
        listenedMillis: Long = 0L,
        positionMillis: Long? = null,
        durationMillis: Long? = null,
        atEpochMillis: Long = 1_000L,
        completed: Boolean = false,
    ) = ListeningHistoryRules.record(
        existing = existing,
        profileId = "p1",
        mediaIdentity = "music:1",
        kind = kind,
        atEpochMillis = atEpochMillis,
        listenedMillis = listenedMillis,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        completed = completed,
    )

    // -----------------------------------------------------------------------------------------
    // The music threshold
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a threshold crossing play increments the count and one below it does not`() {
        val counted = record(listenedMillis = threshold)
        val notCounted = record(listenedMillis = threshold - 1)

        assertEquals(1, counted.playCount)
        assertEquals(0, notCounted.playCount)
    }

    /**
     * The row still exists below the threshold. That is what lets "never played" tell a skipped
     * track from an untouched one, so the row must not be withheld.
     */
    @Test
    fun `a below threshold play still creates a row that reports never played`() {
        val entry = record(listenedMillis = 1_000L)

        assertEquals(0, entry.playCount)
        assertFalse(entry.hasBeenPlayed)
    }

    @Test
    fun `repeated plays accumulate only when they cross the threshold`() {
        var entry = record(listenedMillis = threshold, atEpochMillis = 1_000L)
        entry = record(existing = entry, listenedMillis = 5L, atEpochMillis = 2_000L)
        entry = record(existing = entry, listenedMillis = threshold + 500L, atEpochMillis = 3_000L)

        assertEquals(2, entry.playCount)
        assertEquals(3_000L, entry.lastPlayedAtEpochMillis)
        assertEquals(1_000L, entry.startedAtEpochMillis, "startedAt is the first touch and never moves")
    }

    /** Podcast and audiobook count on open: the listener returns to the same item repeatedly. */
    @Test
    fun `a podcast counts a play without any elapsed time`() {
        assertEquals(1, record(kind = ListeningKind.PODCAST, listenedMillis = 0L).playCount)
        assertEquals(1, record(kind = ListeningKind.AUDIOBOOK, listenedMillis = 0L).playCount)
    }

    @Test
    fun `the threshold is configurable`() {
        assertTrue(ListeningHistoryRules.countsAsPlay(ListeningKind.MUSIC, 5_000L, thresholdMillis = 4_000L))
        assertFalse(ListeningHistoryRules.countsAsPlay(ListeningKind.MUSIC, 5_000L, thresholdMillis = 6_000L))
    }

    // -----------------------------------------------------------------------------------------
    // Position, per kind
    // -----------------------------------------------------------------------------------------

    /** Section 18: music does not resume. Storing a position would be a resume nobody asked for. */
    @Test
    fun `music never stores a position even when one is reported`() {
        val entry = record(kind = ListeningKind.MUSIC, listenedMillis = threshold, positionMillis = 45_000L)

        assertNull(entry.lastPositionMillis)
        assertNull(ListeningHistoryRules.positionToStore(ListeningKind.MUSIC, 45_000L))
    }

    @Test
    fun `podcast and audiobook persist their position`() {
        val podcast = record(kind = ListeningKind.PODCAST, positionMillis = 60_000L)
        val audiobook = record(kind = ListeningKind.AUDIOBOOK, positionMillis = 90_000L)

        assertEquals(60_000L, podcast.lastPositionMillis)
        assertEquals(90_000L, audiobook.lastPositionMillis)
    }

    /** A live stream has no offset to return to, so radio has history but no position. */
    @Test
    fun `radio has history but never a position`() {
        val entry = record(kind = ListeningKind.RADIO, positionMillis = 10_000L)

        assertEquals(1, entry.playCount)
        assertNull(entry.lastPositionMillis)
        assertFalse(ListeningKind.RADIO.resumesPosition)
    }

    /** A stop event carries no position; erasing the stored one would lose the listener's place. */
    @Test
    fun `a null position leaves an existing one intact`() {
        val first = record(kind = ListeningKind.PODCAST, positionMillis = 120_000L)
        val second = record(existing = first, kind = ListeningKind.PODCAST, positionMillis = null)

        assertEquals(120_000L, second.lastPositionMillis)
    }

    // -----------------------------------------------------------------------------------------
    // Completion
    // -----------------------------------------------------------------------------------------

    @Test
    fun `listening past the completion fraction counts as completed`() {
        val entry =
            record(
                kind = ListeningKind.PODCAST,
                positionMillis = 99_000L,
                durationMillis = 100_000L,
            )

        assertEquals(1, entry.completedCount)
    }

    @Test
    fun `stopping halfway does not count as completed`() {
        val entry =
            record(
                kind = ListeningKind.PODCAST,
                positionMillis = 50_000L,
                durationMillis = 100_000L,
            )

        assertEquals(0, entry.completedCount)
    }

    @Test
    fun `an explicit completion is honoured without a duration`() {
        assertEquals(1, record(kind = ListeningKind.RADIO, completed = true).completedCount)
    }

    // -----------------------------------------------------------------------------------------
    // The record itself
    // -----------------------------------------------------------------------------------------

    @Test
    fun `progress is null when the duration is unknown`() {
        val radio = record(kind = ListeningKind.RADIO)

        assertNull(radio.progressFraction)
    }

    @Test
    fun `progress is a bounded fraction`() {
        val entry = record(kind = ListeningKind.PODCAST, positionMillis = 25_000L, durationMillis = 100_000L)

        assertEquals(0.25f, entry.progressFraction)
    }

    /** The identity is derived from the user's own playlist and the source names their provider. */
    @Test
    fun `toString redacts identity and source`() {
        val rendered =
            ListeningHistoryEntry(
                profileId = "profile-secret",
                mediaIdentity = "music:secret-id",
                kind = ListeningKind.MUSIC,
                startedAtEpochMillis = 0L,
                lastPlayedAtEpochMillis = 0L,
                sourceId = "source-secret",
            ).toString()

        assertFalse("profile-secret" in rendered)
        assertFalse("secret-id" in rendered)
        assertFalse("source-secret" in rendered)
        assertTrue("<redacted>" in rendered)
    }
}
