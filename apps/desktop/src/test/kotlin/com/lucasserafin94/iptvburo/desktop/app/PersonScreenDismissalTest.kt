package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The filmography screen closing completely, not half.
 *
 * Two flags decide whether it is on screen: `personOpen`, which is local to each screen that can
 * show it, and `selectedPerson`, which lives in the app state. Clearing only the second left the
 * branch still taken with nothing to draw — so pressing a credit made the actor's page vanish and
 * dropped the user back at the start, which is exactly what was reported.
 *
 * Checked against the source rather than by composing the screen: what matters is that every place
 * which sets `personOpen = true` also clears it on every way out, and that is a property of the
 * file. A UI test would need a live catalogue, a metadata key and a network to reach the same
 * conclusion.
 */
class PersonScreenDismissalTest {
    private fun source(name: String): String =
        Files.readString(Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/$name"))

    private fun personBlock(file: String): String =
        source(file).substringAfter("if (personOpen) {").substringBefore("\n    if (")

    @Test
    fun `opening a credit closes the filmography on both screens`() {
        listOf("XtreamWorkspace.kt", "XtreamDailyHome.kt").forEach { file ->
            val block = personBlock(file)

            assertTrue(
                "onOpenCredit" in block,
                "$file no longer wires the credit handler; this test is checking the wrong place",
            )
            assertTrue(
                Regex("onOpenCredit\\s*=\\s*\\{").containsMatchIn(block),
                "$file passes the handler directly, so the screen's own personOpen flag is never " +
                    "cleared — the page falls through to Home with nothing drawn",
            )
            val handler = block.substringAfter("onOpenCredit").substringBefore("person =")
            assertTrue(
                handler.contains("personOpen = false"),
                "$file must clear personOpen when a credit opens, alongside the shared state",
            )

            // The half that was missed the first time round. Selecting a title is not showing it:
            // without this the user was returned to the Home with the right film selected and no
            // page displaying it, which is indistinguishable from the press doing nothing.
            assertTrue(
                handler.contains("detailsOpen = true"),
                "$file selects the title but never opens its details page, so the press appears " +
                    "to do nothing and drops the user back to the previous screen",
            )
        }
    }

    /**
     * Every other way out already does this, and the new one must not be the exception.
     *
     * Pinned because `onBack` and `onOpenItem` were both correct — which is what made the omission
     * in the new handler easy to miss when reading the code.
     */
    @Test
    fun `back and opening an item also close the screen`() {
        listOf("XtreamWorkspace.kt", "XtreamDailyHome.kt").forEach { file ->
            val block = personBlock(file)

            assertTrue(
                block.substringAfter("onBack").substringBefore("onOpenItem")
                    .contains("personOpen = false"),
                "$file leaves the filmography open when going back",
            )
            assertTrue(
                block.substringAfter("onOpenItem").contains("personOpen = false"),
                "$file leaves the filmography open when opening a title from it",
            )
        }
    }
}
