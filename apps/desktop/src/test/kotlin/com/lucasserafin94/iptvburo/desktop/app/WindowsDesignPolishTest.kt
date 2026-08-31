package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression checks for the Windows visual hierarchy shared by mouse and keyboard navigation. */
class WindowsDesignPolishTest {
    private val app =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()
    private val home =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamDailyHome.kt").readText()
    private val workspace =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt").readText()
    private val interaction =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/BuroInteractive.kt").readText()

    @Test
    fun `shared interactive surfaces expose button and selection semantics`() {
        assertTrue(interaction.contains("role = Role.Button"))
        assertTrue(interaction.contains("this.selected = selected"))
        assertTrue(interaction.contains("semantics(mergeDescendants = true)"))
    }

    @Test
    fun `sidebar groups discovery content personal library and account destinations`() {
        val sidebar = app.substringAfter("private fun SourceSidebar(").substringBefore("private fun CollapsedSidebar(")
        val discover = sidebar.indexOf("onClick = onDiscover")
        val movies = sidebar.indexOf("onClick = onMovies")
        val subscriptions = sidebar.indexOf("onClick = onSubscriptions")
        val continueWatching = sidebar.indexOf("onClick = onContinueWatching")
        val profiles = sidebar.indexOf("onClick = onProfiles")
        val settings = sidebar.indexOf("onClick = onSettings")

        assertTrue(discover in 0 until movies)
        assertTrue(movies in 0 until subscriptions)
        assertTrue(subscriptions in 0 until continueWatching)
        assertTrue(continueWatching in 0 until profiles)
        assertTrue(profiles in 0 until settings)
        assertTrue(sidebar.contains("thickness = 6.dp"))
    }

    @Test
    fun `continue watching cards have a visible resume affordance`() {
        val rail = home.substringAfter("private fun ContinueWatchingRow(").substringBefore("// Small parts")
        assertTrue(rail.contains("val cardWidth = if (metrics.wide) 336.dp"))
        assertTrue(rail.contains(".height(6.dp)"))
        assertTrue(rail.contains("Text(\"▶\""))
    }

    /**
     * A trailer that never starts gives the banner back to the artwork.
     *
     * The panel is no longer held at one pixel until playback is confirmed. That was a loop with no
     * exit: a video inside a one-pixel panel is never drawn, so it never reaches PLAYING, so the
     * panel was never resized — and no trailer appeared anywhere, on the banner or the Descobrir
     * card, while the lookup logged real video ids the whole time.
     *
     * What the guard was protecting against is handled where it belongs: Chromium's own background
     * defaults to white and is now set at the engine (see TrailerBrowser), and the artwork is drawn
     * underneath in every case. The readiness timeout stays, because a video that never starts must
     * still be reported so the next rotation does not wait on it again.
     */
    @Test
    fun `a trailer that never starts is reported and gives up the banner`() {
        val trailer = home.substringAfter("internal fun HeroTrailer(").substringBefore("private fun DailyHero(")
        assertTrue(trailer.contains("TRAILER_READY_TIMEOUT_MILLIS"))
        assertTrue(trailer.contains("if (!playbackConfirmed) onFailed()"))
        // And it is not hidden by being shrunk, which is what deadlocked it.
        assertFalse(trailer.contains("Modifier.size(1.dp)"))
    }

    @Test
    fun `movie detail keeps four primary decisions and folds secondary actions`() {
        val detail = workspace.substringAfter("internal fun XtreamItemDetail(").substringBefore("private fun LiveEpgContent(")
        val play = detail.indexOf("playbackButtonLabel")
        val trailer = detail.indexOf("movieTrailerId?.let")
        val favorite = detail.indexOf("onClick = onToggleFavorite")
        val more = detail.indexOf("secondaryActionsVisible = !secondaryActionsVisible")
        val reminder = detail.indexOf("onToggleReminder?.let")

        assertTrue(play in 0 until trailer)
        assertTrue(trailer in 0 until favorite)
        assertTrue(favorite in 0 until more)
        assertTrue(more in 0 until reminder)
        assertTrue(detail.contains("item.name.editorialTitle()"))
    }

    @Test
    fun `catalogue primary controls wrap instead of clipping`() {
        val toolbar = workspace.substringAfter("private fun XtreamToolbar(").substringBefore("private fun YearAndRatingFilters(")
        assertTrue(toolbar.contains("FlowRow("))
        assertTrue(toolbar.contains("verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)"))
    }
}
