package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A series with several seasons opens with all of them closed.
 *
 * Opening the first season by default put eighteen episode rows between the synopsis and everything
 * under it, so the rest of the page — the similar-titles shelf included — sat a long scroll away on
 * a series of any size. Asked for as: one click opens a season, another closes it.
 *
 * One season is not a choice, so it stays open and the tabs are not drawn at all. Collapsing it
 * would hide the only thing on the page behind a press that has no alternative.
 */
class SeasonsStartCollapsedTest {
    private val source: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt").readText()

    @Test
    fun `nothing is open when there is a choice to make`() {
        assertTrue(
            source.contains("mutableStateOf(if (seasons.size > 1) null else seasons.keys.firstOrNull())"),
            "Several seasons start closed; a single season stays open because it is not a choice.",
        )
    }

    @Test
    fun `pressing the open season closes it again`() {
        assertTrue(
            source.contains("onClick = { openSeason = if (selected) null else season }"),
            "An expander that cannot be collapsed leaves no way back to a short page.",
        )
    }

    @Test
    fun `a closed season lists no episodes`() {
        // Not merely hidden: the rows are built straight into the parent's scrolling column, so
        // leaving them composed would keep the cost that collapsing is meant to avoid.
        assertTrue(
            source.contains("val visible = if (openSeason == null) emptyList() else seasons[openSeason] ?: episodes"),
            "With nothing open there is nothing to list.",
        )
    }

    @Test
    fun `the season header is drawn only when a season is open`() {
        // "Temporada 1 · 18 episódios" and the per-season download button both describe a season
        // that is on screen. With none open they would be describing nothing.
        val body = source.substringAfter("val visible = if (openSeason == null)")
        assertTrue(
            body.take(200).contains("if (openSeason != null) {"),
            "The header belongs to the open season, so it appears with it.",
        )
    }
}
