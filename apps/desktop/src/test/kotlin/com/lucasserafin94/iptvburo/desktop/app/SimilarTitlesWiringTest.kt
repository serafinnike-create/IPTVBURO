package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shelf of other titles to open, under the cast on both detail pages.
 *
 * Every piece of this is easy to break without anything failing: a default parameter left unfilled
 * makes the shelf silently empty, and a click handler that goes nowhere is indistinguishable from a
 * title TMDb does not know. So the wiring is asserted rather than the rendering.
 */
class SimilarTitlesWiringTest {
    private val workspace: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt").readText()

    private val home: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamDailyHome.kt").readText()

    private val state: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    @Test
    fun `both detail panes draw the shelf`() {
        // Films and series, because the request was for both and the series page has historically
        // been the one that missed out — its ratings were absent for exactly this reason.
        assertTrue(
            workspace.split("SimilarTitlesShelf(similarTitles, onOpenSimilar,").size - 1 == 2,
            "The shelf must be drawn on the film page and the series page.",
        )
    }

    @Test
    fun `the shelf ends the film page, under the cast`() {
        // A film ends with its cast, so the shelf follows it there.
        val body = workspace.substringAfter("private fun MovieDetailContent(")
        val castAt = body.indexOf("CastButtons(")
        val shelfAt = body.indexOf("SimilarTitlesShelf(")
        assertTrue(castAt > 0, "The film pane should draw the cast.")
        assertTrue(shelfAt > castAt, "The shelf must come after the cast.")
    }

    @Test
    fun `the shelf ends the series page, under the episodes`() {
        // A series does not end with its cast — the episodes follow — and a shelf wedged between
        // the two interrupts the thing the viewer opened the page for. Asked for in those terms.
        val body = workspace.substringAfter("private fun SeriesDetailContent(")
        val castAt = body.indexOf("CastButtons(")
        val episodesAt = body.indexOf("EpisodeRow(")
        val shelfAt = body.indexOf("SimilarTitlesShelf(")
        assertTrue(castAt > 0 && episodesAt > 0, "The series pane draws a cast and episodes.")
        assertTrue(
            shelfAt > episodesAt,
            "On a series the shelf belongs after the episode list, not between cast and seasons.",
        )
    }

    @Test
    fun `the shelf is given the real titles rather than its default`() {
        // The parameter defaults to an empty list, so forgetting this line leaves a shelf that is
        // wired, compiles, and never appears.
        assertTrue(
            workspace.contains("similarTitles = appState.similarTitles,"),
            "The page must pass the loaded titles, not fall back to the empty default.",
        )
    }

    @Test
    fun `a card opens through the route that handles titles outside this playlist`() {
        // openCredit searches the catalogue first and falls back to Assinaturas. A handler that
        // only selected the item would leave the user on whatever was underneath.
        listOf(workspace, home).forEach { source ->
            val handler = source.substringAfter("onOpenSimilar = { credit ->").substringBefore("},")
            assertTrue(
                handler.contains("appState.openCredit(credit)"),
                "The shelf must reuse the credit route rather than selecting the item directly.",
            )
            assertTrue(
                handler.contains("CreditDestination.PLAYLIST_ITEM") &&
                    handler.contains("detailsOpen = true"),
                "Opening a title that is in the playlist has to show its page, not just select it.",
            )
        }
    }

    @Test
    fun `the lookup reuses the id the audience score already resolved`() {
        // Resolving a name to a TMDb id costs a search request, and that request has already been
        // made by the time this runs. Asking again would pay twice for the same answer.
        val chained = state.substringAfter("loadCriticScores(requested, found?.tmdbId)")
        assertTrue(
            chained.take(200).contains("loadSimilarTitles(requested, found?.tmdbId"),
            "The shelf should hang off the resolved id rather than searching again.",
        )
    }

    @Test
    fun `an answer for another title is dropped`() {
        // The viewer can open something else while this is in flight. Attaching one film's
        // recommendations to another's page is worse than showing no shelf.
        val loader = state.substringAfter("private fun loadSimilarTitles(")
        assertTrue(
            loader.contains("if (similarTitlesFor != requested) return@launch"),
            "A late answer must not land on whichever title is open now.",
        )
    }

    @Test
    fun `the title itself is not offered`() {
        val loader = state.substringAfter("private fun loadSimilarTitles(")
        assertTrue(
            loader.contains("filterNot { it.title.equals(requested, ignoreCase = true) }"),
            "A shelf that offers the film you are looking at reads as a bug.",
        )
    }

    @Test
    fun `the heading is translated`() {
        assertFalse(
            workspace.contains("\"Títulos parecidos\""),
            "The heading belongs in the string tables, like every other visible word.",
        )
        assertTrue(workspace.contains("text.shareStrings.screens.similarTitles"))
    }
}
