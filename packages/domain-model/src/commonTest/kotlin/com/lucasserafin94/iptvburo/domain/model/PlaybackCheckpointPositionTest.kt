package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a checkpoint must record, and what it must never record.
 *
 * These exist because of a real fault found on a device: four minutes into a film, leaving the
 * player saved `positionMs == durationMs` and the title was filed as fully watched. Continue
 * watching stayed empty, and — because a completed entry refuses to be moved backwards — the film
 * could never return to it. The player was reading the position after ExoPlayer had already
 * reported it as the duration.
 *
 * The rules below are the domain's half of that contract. The player's half is to hand over a
 * position sampled while playback was genuinely running.
 */
class PlaybackCheckpointPositionTest {
    private val identity =
        PlaybackProgressIdentity(
            profileId = "profile",
            sourceId = "source",
            contentId = "movie-1",
            contentType = PlaybackContentType.MOVIE,
        )

    /** Two hours, the shape of the film this was found on. */
    private val twoHoursMs = 2L * 60L * 60L * 1_000L

    @Test
    fun `an early position is stored as partly watched, not as finished`() {
        val repository = InMemoryProgressRepository()
        val checkpoint = SavePlaybackCheckpointUseCase(repository) { 1_000L }

        // Four minutes in: what the user had actually seen.
        val saved = checkpoint(identity, positionMs = 4L * 60L * 1_000L, durationMs = twoHoursMs, seekable = true)

        assertNotNull(saved)
        assertNull(
            saved.completedAtEpochMillis,
            "Four minutes of a two-hour film is not a film someone finished.",
        )
        assertTrue(
            saved.progressPercent < 0.05,
            "The stored percentage has to reflect what was watched: it is what Continue " +
                "watching reads to decide whether to show the title at all.",
        )
    }

    @Test
    fun `a position equal to the duration is what marks a film finished`() {
        val repository = InMemoryProgressRepository()
        val checkpoint = SavePlaybackCheckpointUseCase(repository) { 1_000L }

        // This is precisely the value the player was sending after four minutes.
        val saved = checkpoint(identity, positionMs = twoHoursMs, durationMs = twoHoursMs, seekable = true)

        assertNotNull(saved)
        assertNotNull(
            saved.completedAtEpochMillis,
            "The domain is right to treat this as finished — which is exactly why the player " +
                "must never send it for a film still playing.",
        )
    }

    @Test
    fun `a finished film is not dragged backwards by a later checkpoint`() {
        val repository = InMemoryProgressRepository()
        val checkpoint = SavePlaybackCheckpointUseCase(repository) { 1_000L }
        checkpoint(identity, positionMs = twoHoursMs, durationMs = twoHoursMs, seekable = true)

        // Someone scrubbing back through the credits must not un-finish the film.
        val afterRewind =
            checkpoint(identity, positionMs = 4L * 60L * 1_000L, durationMs = twoHoursMs, seekable = true)

        assertNotNull(afterRewind?.completedAtEpochMillis)
        assertEquals(
            twoHoursMs,
            afterRewind?.positionMs,
            "The completed position stands. This rule is correct, and it is also why a wrong " +
                "completion can never be corrected by watching again — the fault has to be " +
                "prevented at the source.",
        )
    }

    /**
     * The scale of [PlaybackProgress.progressPercent], stated as a test because its name says the
     * opposite of what it holds.
     *
     * Two screens read it as 0..100 and divided by a hundred: a half-watched film showed "0%" and
     * every episode looked barely started. Nothing failed — they just drew the wrong number, which
     * is exactly the kind of mistake a test has to catch.
     */
    @Test
    fun `progressPercent is a fraction from zero to one, not a percentage`() {
        val repository = InMemoryProgressRepository()
        val checkpoint = SavePlaybackCheckpointUseCase(repository) { 1_000L }

        // Half way through the two-hour film.
        val halfway = checkpoint(identity, positionMs = twoHoursMs / 2, durationMs = twoHoursMs, seekable = true)

        assertNotNull(halfway)
        assertTrue(
            halfway.progressPercent > 0.49 && halfway.progressPercent < 0.51,
            "Half of a film is 0.5, not 50.0: got ${halfway.progressPercent}.",
        )

        val finished = checkpoint(identity, positionMs = twoHoursMs, durationMs = twoHoursMs, seekable = true)
        assertEquals(1.0, finished?.progressPercent, "A finished title stores exactly 1.0.")
    }

    @Test
    fun `a stream with no duration records nothing`() {
        val repository = InMemoryProgressRepository()
        val checkpoint = SavePlaybackCheckpointUseCase(repository) { 1_000L }

        // Live television: no duration, so no position within one, and nothing to resume.
        assertNull(checkpoint(identity, positionMs = 5_000L, durationMs = 0L, seekable = false))
        assertTrue(repository.saved.isEmpty())
    }

    private class InMemoryProgressRepository : PlaybackProgressRepository {
        val saved = mutableMapOf<PlaybackProgressIdentity, PlaybackProgress>()

        override fun find(identity: PlaybackProgressIdentity): PlaybackProgress? = saved[identity]

        override fun save(progress: PlaybackProgress) {
            saved[progress.identity] = progress
        }

        override fun remove(identity: PlaybackProgressIdentity) {
            saved.remove(identity)
        }

        override fun continueWatching(profileId: String, limit: Int): List<PlaybackProgress> =
            saved.values.filter { it.identity.profileId == profileId }.take(limit)
    }
}
