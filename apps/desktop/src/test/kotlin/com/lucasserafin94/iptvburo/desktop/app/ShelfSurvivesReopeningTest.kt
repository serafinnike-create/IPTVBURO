package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reopening a film must not cost it its shelf.
 *
 * Reported after 3.1.0 shipped: the ratings were there and the shelf was empty. That combination is
 * the whole diagnosis. Both hang off one TMDb lookup, so a rating on screen proves the lookup ran
 * and resolved an id — and an empty shelf beside it proves the second half never happened.
 *
 * The cause was the guard that avoids paying for the same score twice. It remembered the *title*
 * and returned on a match, which is right for the score it already holds and wrong for the shelf,
 * because opening a film clears the shelf first. So the second visit cleared it and then returned
 * before anything refilled it, and it stayed empty for the rest of the session.
 */
class ShelfSurvivesReopeningTest {
    private val state: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    @Test
    fun `the cache guard does not fire when the shelf is empty`() {
        assertFalse(
            state.contains("if (audienceScoreFor == requested) return\n"),
            "Returning on the remembered title alone skips the shelf's own lookup.",
        )
        assertTrue(
            state.contains("if (audienceScoreFor == requested && similarTitles.isNotEmpty()) return"),
            "The answer may only be reused when it is actually still on hand.",
        )
    }

    @Test
    fun `the shelf is still cleared before a new lookup`() {
        // The clearing is not the bug and must stay: a stale shelf under a different film would be
        // worse than an empty one. The bug was returning *after* clearing without refilling.
        val loader = state.substringAfter("private fun loadSimilarTitles(")
        assertTrue(
            loader.substringBefore("if (").contains("similarTitles = emptyList()"),
            "A new title must not inherit the previous one's shelf.",
        )
    }

    @Test
    fun `the shelf lookup is still reached from the score lookup`() {
        // The chain itself: score resolves the id, and the shelf hangs off it. If this line moves
        // out of the success branch the shelf stops loading entirely.
        val after = state.substringAfter("loadCriticScores(requested, found?.tmdbId)")
        assertTrue(
            after.take(200).contains("loadSimilarTitles(requested, found?.tmdbId"),
            "The shelf must still be asked for once the id is known.",
        )
    }
}
