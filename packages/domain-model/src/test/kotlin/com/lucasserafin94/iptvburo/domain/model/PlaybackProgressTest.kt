package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaybackProgressTest {
    @Test
    fun `progress clamps positions and rejects unknown duration`() {
        assertEquals(0.0, PlaybackProgressPolicy.percent(50_000, 0))
        assertEquals(1.0, PlaybackProgressPolicy.percent(150_000, 100_000))
    }

    @Test
    fun `eligibility requires thirty seconds and two percent`() {
        assertFalse(PlaybackProgressPolicy.isEligible(29_999, 100_000, true))
        assertFalse(PlaybackProgressPolicy.isEligible(30_000, 2_000_000, true))
        assertTrue(PlaybackProgressPolicy.isEligible(40_000, 1_000_000, true))
        assertFalse(PlaybackProgressPolicy.isEligible(40_000, 1_000_000, false))
    }

    @Test
    fun `completion uses ninety percent or final five minutes conservatively`() {
        assertTrue(PlaybackProgressPolicy.isCompleted(900_000, 1_000_000))
        assertTrue(PlaybackProgressPolicy.isCompleted(700_001, 1_000_000))
        assertFalse(PlaybackProgressPolicy.isCompleted(60_000, 360_000))
        assertFalse(PlaybackProgressPolicy.isCompleted(1_000, 120_000))
    }

    @Test
    fun `checkpoint revisions increase and completed progress does not regress`() {
        val repository = MemoryProgressRepository()
        val identity = identity("movie-1", PlaybackContentType.MOVIE)
        val save = SavePlaybackCheckpointUseCase(repository) { 1_000 }
        assertEquals(null, save(identity, 20_000, 1_000_000, true))
        assertEquals(1L, save(identity, 50_000, 1_000_000, true)?.revision)
        val completed = MarkPlaybackCompletedUseCase(repository) { 2_000 }(identity, 1_000_000)
        assertEquals(2L, completed?.revision)
        assertEquals(completed, save(identity, 60_000, 1_000_000, true))
        assertIs<ResumeDecision.WatchAgain>(GetResumeDecisionUseCase(repository)(identity))
    }

    @Test
    fun `movie and episode identities do not collide`() {
        val repository = MemoryProgressRepository()
        val save = SavePlaybackCheckpointUseCase(repository) { 1_000 }
        val movie = identity("42", PlaybackContentType.MOVIE)
        val episode = identity("42", PlaybackContentType.EPISODE)
        save(movie, 40_000, 1_000_000, true)
        save(episode, 80_000, 1_000_000, true)
        assertEquals(40_000, repository.find(movie)?.positionMs)
        assertEquals(80_000, repository.find(episode)?.positionMs)
    }

    private fun identity(id: String, type: PlaybackContentType) =
        PlaybackProgressIdentity("profile", "source", id, type)
}

private class MemoryProgressRepository : PlaybackProgressRepository {
    private val values = linkedMapOf<PlaybackProgressIdentity, PlaybackProgress>()
    override fun find(identity: PlaybackProgressIdentity) = values[identity]
    override fun save(progress: PlaybackProgress) { values[progress.identity] = progress }
    override fun remove(identity: PlaybackProgressIdentity) { values.remove(identity) }
    override fun continueWatching(profileId: String, limit: Int) =
        values.values.filter { it.identity.profileId == profileId }.take(limit)
}
