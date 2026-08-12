package com.lucasserafin94.iptvburo.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opening a credit selects the title in a way the screens can actually see.
 *
 * This took four attempts, and each one fixed something real without fixing the report. What made it
 * hard is that the log said `credit: opened from playlist` every time: the search worked, the item
 * was found, and the selection was made. It simply was not visible afterwards.
 *
 * The app holds two selections. `dailySelectedItem` is an item; `selectedXtreamItemId` is an id
 * resolved against the current catalogue page. `selectedXtreamItem` reads the first, and returns
 * null outright while the destination is Home:
 *
 *     dailySelectedItem ?: if (destination == HOME) null else xtreamPage.items.firstOrNull { … }
 *
 * `selectXtreamItem` clears `dailySelectedItem` and sets only the id — so from the Home screen the
 * selection resolved to nothing, the details branch was never taken, and the user was returned to
 * the start. `selectDailyItem` sets both, which is what every working path in the app already uses.
 */
class CreditSelectionTest {
    private val source: String =
        Files.readString(
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt"),
        )

    private fun body(function: String): String =
        source.substringAfter("fun $function").substringBefore("\n    fun ")

    @Test
    fun `opening a credit selects the item, not merely its id`() {
        val opening = body("openTitleFromCredit")

        assertTrue(
            "selectDailyItem(" in opening,
            "the credit must be selected the way the screens read it back; selecting only an id " +
                "resolves to null on the Home screen and the details page never opens",
        )
        assertTrue(
            "selectXtreamItem(" !in opening,
            "selectXtreamItem clears dailySelectedItem, which is the field the details branch reads",
        )
    }

    /**
     * The trap itself, pinned so it stays visible.
     *
     * Anyone reading `selectXtreamItem` in isolation would take it for the obvious way to select a
     * title. It is — from the catalogue. From Home it selects nothing at all, and nothing about the
     * name says so.
     */
    @Test
    fun `selecting by id alone is null on the home screen`() {
        val resolver = source.substringAfter("val selectedXtreamItem:").substringBefore("\n    /**")

        assertTrue(
            "dailySelectedItem" in resolver,
            "the resolver reads dailySelectedItem first; if that changed, this whole class of bug " +
                "moved somewhere else and these tests are guarding the wrong thing",
        )
        assertTrue(
            "DesktopDestination.HOME" in resolver,
            "the Home destination short-circuits to null, which is what made an id-only selection " +
                "invisible from that screen",
        )
    }

    /**
     * `selectXtreamItem` really does drop the item, which is why it cannot be used here.
     *
     * Stated as a test rather than a comment so that if the clearing is ever removed, the reason
     * this code avoids the function disappears with it and someone is told.
     */
    @Test
    fun `selecting by id clears the item selection`() {
        assertTrue(
            "dailySelectedItem = null" in body("selectXtreamItem"),
            "if this no longer clears the item, selectXtreamItem is safe again and the comment in " +
                "openTitleFromCredit should be revisited",
        )
    }
}
