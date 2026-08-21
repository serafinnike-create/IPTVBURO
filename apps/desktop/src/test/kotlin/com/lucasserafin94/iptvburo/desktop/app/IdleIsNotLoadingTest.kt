package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An idle screen must not draw itself as a loading one.
 *
 * This is the "continua carregando" report. Opening a film from Descobrir left the page on its
 * spinner for ever: the fetch is cancelled whenever the effect that started it recomposes — which
 * happens reliably on that route — and the cancellation correctly resets the status to Idle. But
 * Idle shared a `when` branch with Loading, so the screen went on showing "Carregando ficha do
 * filme…" while nothing was in flight, and neither effect that starts a load is keyed on the
 * status, so nothing ever ran again.
 *
 * The earlier fix stopped the Loading guard from refusing retries. It did not make anything retry,
 * which is why the symptom survived it.
 *
 * Series had it right all along — its Idle branch offers a button — so this is really about the two
 * screens that did not follow it.
 */
class IdleIsNotLoadingTest {
    private val source: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt").readText()

    private fun branch(after: String): String =
        source.substringAfter(after).substringBefore("        LiveEpgStatus.Unavailable")
            .substringBefore("        is MovieDetailsStatus.Error")

    @Test
    fun `idle no longer shares a branch with loading`() {
        assertFalse(
            source.contains("MovieDetailsStatus.Idle,\n        MovieDetailsStatus.Loading,"),
            "Idle drawn as Loading is the defect: a spinner over a screen where nothing is happening.",
        )
        assertFalse(
            source.contains("LiveEpgStatus.Idle,\n        LiveEpgStatus.Loading,"),
            "The guide had the same defect as the film details.",
        )
    }

    @Test
    fun `an idle film page asks again by itself`() {
        val idle = branch("MovieDetailsStatus.Idle -> {")
        assertTrue(
            idle.contains("LaunchedEffect(Unit) { onRetry() }"),
            "The user already asked by opening the film; the screen should not wait to be asked twice.",
        )
    }

    @Test
    fun `an idle film page still offers a way out`() {
        val idle = branch("MovieDetailsStatus.Idle -> {")
        assertTrue(
            idle.contains("OutlinedButton(onClick = onRetry)"),
            "The case this exists for is the one where the automatic attempt does not arrive.",
        )
    }

    @Test
    fun `an idle guide asks again by itself`() {
        val idle = branch("LiveEpgStatus.Idle -> {")
        assertTrue(
            idle.contains("LaunchedEffect(Unit) { onRetry() }"),
            "A guide stuck on Idle spun for ever exactly as the film details did.",
        )
    }

    /** The model the other two now follow. Worth pinning so it cannot regress into a spinner. */
    @Test
    fun `the series page keeps its idle button`() {
        val idle = source.substringAfter("SeriesDetailsStatus.Idle -> {").substringBefore("SeriesDetailsStatus.Loading")
        assertTrue(idle.contains("onClick = onLoadSeries"), "Series must keep its explicit action.")
        assertFalse(
            idle.contains("CircularProgressIndicator"),
            "Series is the example the others copy; a spinner here would undo that.",
        )
    }
}
