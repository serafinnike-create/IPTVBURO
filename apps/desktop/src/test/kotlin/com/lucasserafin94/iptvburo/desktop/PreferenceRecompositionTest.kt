package com.lucasserafin94.iptvburo.desktop

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Anything written to the preferences store must also bump a revision counter.
 *
 * The store is plain Java Preferences — it is not Compose snapshot state, so writing to it changes
 * nothing the UI is watching. A screen that reads through it will not redraw, and the symptom is a
 * switch that does not move: the value on disk is right, the pixel is stale.
 *
 * This shipped twice in one screen. Hiding a category *appeared* to work only because that path
 * also rewrote `xtreamCategories`, which is observed; restoring one wrote the same unobserved store
 * and redrew nothing, so the button did nothing at all. The lock switches never redrew either way.
 *
 * The pattern this enforces is the one `historyEntries` already used: a `…Revision` counter read by
 * the getter and incremented by every writer.
 */
class PreferenceRecompositionTest {
    private val source: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    /**
     * Every function that writes one of these settings must bump its counter.
     *
     * Listed explicitly rather than detected: a general rule over "any userStore.set…" would sweep
     * in writers whose value is also mirrored into observable state, where the counter would be
     * redundant. These are the ones read straight back out of the store by a screen.
     */
    @Test
    fun `settings writers bump the revision their readers watch`() {
        val cases =
            listOf(
                "setCategoryHidden" to "hiddenCategoriesRevision",
                "setCategoryLocked" to "parentalRevision",
                "setLockAdultCategories" to "parentalRevision",
                "setParentalPin" to "parentalRevision",
                "clearParentalPin" to "parentalRevision",
            )

        val offenders =
            cases.filterNot { (function, counter) ->
                bodyOf(function)?.contains("$counter += 1") == true
            }

        assertTrue(
            offenders.isEmpty(),
            "These writers change a preference without telling Compose, so the switch on screen " +
                "will not move:\n" +
                offenders.joinToString("\n") { (function, counter) -> "  $function must bump $counter" },
        )
    }

    /** And the readers must actually read the counter, or bumping it achieves nothing. */
    @Test
    fun `settings readers observe their revision`() {
        listOf(
            "hiddenCategoryIds" to "hiddenCategoriesRevision",
            "parentalLock" to "parentalRevision",
            "hasParentalPin" to "parentalRevision",
        ).forEach { (property, counter) ->
            val body = propertyBodyOf(property)
            assertTrue(
                body != null && body.contains(counter),
                "$property reads the preferences store without reading $counter, so it will never " +
                    "recompose when the value changes",
            )
        }
    }

    /**
     * The body of a function, from its declaration to the next one at the same indentation.
     *
     * Crude, and sufficient: these are all short members of one class, and the alternative is
     * parsing Kotlin to catch a missing line.
     */
    private fun bodyOf(name: String): String? {
        val lines = source.lines()
        val start = lines.indexOfFirst { line -> line.trimStart().startsWith("fun $name(") }
        if (start < 0) return null
        val end =
            lines.drop(start + 1).indexOfFirst { line -> line == "    }" }.takeIf { it >= 0 }
                ?: return null
        return lines.subList(start, start + end + 2).joinToString("\n")
    }

    private fun propertyBodyOf(name: String): String? {
        val lines = source.lines()
        val start = lines.indexOfFirst { line -> line.trimStart().startsWith("val $name:") }
        if (start < 0) return null
        // Property getters here are short; twenty lines covers the longest of them.
        return lines.subList(start, minOf(lines.size, start + 20)).joinToString("\n")
    }
}
