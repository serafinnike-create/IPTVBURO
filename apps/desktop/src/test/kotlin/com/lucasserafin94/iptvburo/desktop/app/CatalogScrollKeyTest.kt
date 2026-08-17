package com.lucasserafin94.iptvburo.desktop.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The catalogue's remembered scroll position must be keyed on the list it belongs to.
 *
 * `RememberedScroll` keeps an offset per key so that returning from a title's page lands where the
 * user was. The key therefore has to distinguish lists that are genuinely different — a position
 * saved in Ação restored onto Terror is worse than starting at the top, because it looks deliberate.
 *
 * The grid keyed itself on `selectedCategoryId` and `searchQuery`, which belong to the **imported
 * M3U** catalogue. In an Xtream session — which is how the app is normally used — those two stay
 * empty for ever, so every category and every search collapsed onto a single shared key.
 *
 * A source-reading test because the failure is a wrong field name that compiles perfectly and
 * produces a plausible-looking key; nothing observable distinguishes it until two lists trade
 * positions.
 */
class CatalogScrollKeyTest {
    private val source =
        File("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
            .also { file -> assertTrue(file.isFile, "Expected to find ${file.path}") }
            .readText()

    /**
     * The key expression itself.
     *
     * Read to the end of the string literal rather than to the first `)`, which falls inside
     * `orEmpty()` and truncated the key mid-way — a test that reads source has to parse it as
     * carefully as anything else.
     */
    private val gridKey =
        Regex("""key = "catalog:[^"]*"""")
            .find(source)
            ?.value
            .orEmpty()
            .also { key -> assertTrue(key.isNotBlank(), "Could not find the grid's scroll key.") }

    @Test
    fun `the grid key uses the xtream category, not the local playlist one`() {
        assertTrue(
            gridKey.contains("selectedXtreamCategoryId"),
            "The scroll key must use the Xtream category; was: $gridKey",
        )
        assertFalse(
            gridKey.contains("appState.selectedCategoryId"),
            "The scroll key is using the imported-M3U category, which is empty in an Xtream session.",
        )
    }

    @Test
    fun `the grid key uses the xtream search, not the local playlist one`() {
        assertTrue(
            gridKey.contains("xtreamSearchQuery"),
            "The scroll key must use the Xtream search; was: $gridKey",
        )
        assertFalse(
            gridKey.contains("appState.searchQuery"),
            "The scroll key is using the imported-M3U search, which is empty in an Xtream session.",
        )
    }

    /**
     * Returning from a title must not scroll the grid to the top.
     *
     * A `LaunchedEffect` also runs on first composition, and this composable is composed again on
     * every return from a title's page — so an unconditional `scrollToItem(0)` in that effect threw
     * away the position that had just been restored.
     */
    @Test
    fun `the top-scroll only runs when the list actually changed`() {
        val effect =
            source
                .substringAfter("LaunchedEffect(page.pageIndex, appState.xtreamContentType")
                .substringBefore("\n    }")

        assertTrue(
            effect.contains("lastScrolledList != current"),
            "The grid scrolls to the top unconditionally, which discards the restored position.",
        )
    }
}
