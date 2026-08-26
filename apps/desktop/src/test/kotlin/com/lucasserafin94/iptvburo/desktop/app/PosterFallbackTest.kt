package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a poster looks like when the provider sent no artwork.
 *
 * Whole categories arrive without any: adult catalogues nearly always, and plenty of regional
 * ones. So this is the normal appearance of those rows, not an edge case — and it was two letters
 * floating in a 208dp rectangle, which says nothing about the film and makes a shelf of them
 * indistinguishable.
 *
 * The app already had a better answer on the discovery shelf. This is the same idea, applied where
 * the catalogue actually draws posters.
 */
class PosterFallbackTest {
    private val workspace =
        Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
            .readText()

    private fun fallbackBody(): String {
        val marker = "private fun XtreamPosterFallback(name: String) {"
        // substringAfter returns the whole file when its marker is missing, which would leave the
        // checks below searching all of XtreamWorkspace and passing on an unrelated line.
        assertTrue(workspace.contains(marker), "the fallback was renamed; this test needs updating")
        return workspace.substringAfter(marker).substringBefore("\n}")
    }

    @Test
    fun `a poster without artwork shows its title, not two letters`() {
        assertTrue(fallbackBody().contains("text = name"), "the whole title is drawn")
    }

    @Test
    fun `a long title is cut rather than drawn over the next card`() {
        val body = fallbackBody()
        assertTrue(body.contains("maxLines = 4"))
        assertTrue(body.contains("TextOverflow.Ellipsis"))
    }

    @Test
    fun `neighbouring cards do not come out the same colour`() {
        // A shelf in one flat colour reads as one block rather than as separate things.
        assertTrue(fallbackBody().contains("POSTER_FALLBACK_TINTS["), "the tint varies by title")
        assertTrue(
            fallbackBody().contains("name.hashCode().toUInt()"),
            "unsigned before the modulo: hashCode is often negative, and a negative index crashes",
        )
    }

    @Test
    fun `every full-size poster uses it, not just one screen`() {
        // The details poster, the similar-titles shelf and an actor's filmography all draw at
        // poster size. Leaving one on the monogram would look like a bug on that screen alone.
        assertTrue(
            workspace.split("XtreamPosterFallback(").size - 1 >= 4,
            "declared once and used at every poster-shaped card",
        )
        assertFalse(
            workspace.contains("XtreamMonogram(item.name, 86)"),
            "the 208dp poster must not be a monogram",
        )
    }

    @Test
    fun `small thumbnails keep the monogram`() {
        // A monogram is right at 48px, where a title would be unreadable anyway. Replacing it
        // everywhere would trade one bad rendering for another.
        assertTrue(workspace.contains("XtreamMonogram(item.name, 30)"), "the row thumbnail")
        assertTrue(workspace.contains("private fun XtreamMonogram("), "and it still exists")
    }
}
