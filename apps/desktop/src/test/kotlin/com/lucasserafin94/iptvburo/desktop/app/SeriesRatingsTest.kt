package com.lucasserafin94.iptvburo.desktop.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A series page shows the same ratings a film page does.
 *
 * Reported plainly: "em série não aparece Metacritica, avaliações, símbolo da plataforma". It did
 * not, and for two reasons stacked on each other — the kind that a passing test suite does not
 * notice because neither is a broken function, just a missing call.
 *
 *  - `loadAudienceScore` was only ever called from the film loader, so a series never asked TMDb
 *    for anything. It also defaulted `isSeries` to false, and TMDb keeps films and series in
 *    separate catalogues matched on different date fields, so even a stray call would have searched
 *    the wrong one and found nothing.
 *  - `RatingsBlock` was called once in the whole file, from `MovieDetailContent`. The series
 *    content never drew it, so even a loaded score had nowhere to appear.
 *
 * Read from the source because what failed is an absence: nothing threw, nothing logged, and the
 * page rendered perfectly well without the block. Only a comparison against the film page shows it.
 */
class SeriesRatingsTest {
    private val workspace =
        File("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
            .also { file -> assertTrue(file.isFile, "Expected to find ${file.path}") }
            .readText()

    private val state =
        File("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
            .also { file -> assertTrue(file.isFile, "Expected to find ${file.path}") }
            .readText()

    /** Both kinds of page draw the block, not just one. */
    @Test
    fun `the ratings block is drawn on both film and series pages`() {
        val uses = Regex("""RatingsBlock\(score""").findAll(workspace).count()

        assertTrue(
            uses >= 2,
            "RatingsBlock is drawn $uses time(s); films and series both need it.",
        )
    }

    /** The series content receives the scores it is meant to draw. */
    @Test
    fun `series content takes the audience and critic scores`() {
        val signature =
            workspace
                .substringAfter("private fun SeriesDetailContent(")
                .substringBefore(") {")

        assertTrue(
            signature.contains("audienceScore"),
            "SeriesDetailContent cannot draw a score it is never given.",
        )
        assertTrue(
            signature.contains("criticScores"),
            "SeriesDetailContent cannot draw the critics' row it is never given.",
        )
    }

    /**
     * And the lookup runs for a series, against the series catalogue.
     *
     * `isSeries = true` is the half that is easy to leave out and impossible to see: TMDb answers
     * the film search for a series name with nothing at all, so the page would look exactly as it
     * did before — empty — while appearing to have been fixed.
     */
    @Test
    fun `the series loader asks TMDb for a series`() {
        val loader =
            state
                .substringAfter("seriesDetailsStatus = SeriesDetailsStatus.Loaded(details)")
                .substringBefore("} else {")

        assertTrue(
            loader.contains("loadAudienceScore"),
            "Loading a series never asks for its audience score.",
        )
        assertTrue(
            loader.contains("isSeries = true"),
            "The series lookup must search TMDb's series catalogue, not the film one.",
        )
    }
}
