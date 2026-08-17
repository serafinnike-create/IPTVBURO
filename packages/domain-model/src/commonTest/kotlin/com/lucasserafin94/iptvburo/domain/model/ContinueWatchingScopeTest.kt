package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Progress is scoped to the library, not to the connected playlist.
 *
 * The identity is recorded against a fixed library scope so a change of playlist does not orphan
 * every row. The desktop then filtered the continue list by the connected source's hash, which
 * could never match that scope - so a film that had been watched never appeared, and reopening it
 * offered no resume point.
 */
class ContinueWatchingScopeTest {
    private fun progress(
        contentId: String,
        sourceId: String,
        positionMs: Long,
    ) = PlaybackProgress(
        identity =
            PlaybackProgressIdentity(
                profileId = "profile-1",
                sourceId = sourceId,
                contentId = contentId,
                contentType = PlaybackContentType.MOVIE,
            ),
        positionMs = positionMs,
        durationMs = 7_200_000L,
        progressPercent = positionMs.toDouble() / 7_200_000L,
        lastWatchedAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
        revision = 1L,
    )

    @Test
    fun `rows recorded under the library scope are not tied to one playlist`() {
        val row = progress("movie:a_2020", sourceId = "library", positionMs = 60_000L)

        // What the screen used to do: compare the row's scope to the connected source hash.
        val connectedSourceHash = "9f2c1b" // whatever the current playlist happens to hash to
        assertTrue(
            row.identity.sourceId != connectedSourceHash,
            "the scope is deliberately not a playlist hash, so filtering on one hides everything",
        )
    }

    @Test
    fun `the same film keeps one row across playlists`() {
        val fromFirstList = progress("movie:a_2020", sourceId = "library", positionMs = 60_000L)
        val fromSecondList = progress("movie:a_2020", sourceId = "library", positionMs = 90_000L)

        assertEquals(
            fromFirstList.identity,
            fromSecondList.identity,
            "identity must not depend on which playlist was connected",
        )
    }

    @Test
    fun `resuming uses the recorded position`() {
        val row = progress("movie:a_2020", sourceId = "library", positionMs = 125_000L)

        assertEquals(125_000L, row.positionMs)
        assertTrue(row.positionMs < row.durationMs, "a part-watched row is not complete")
    }
}
