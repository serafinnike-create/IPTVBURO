package com.lucasserafin94.iptvburo.desktop.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every sidebar destination has to be reachable on an ordinary laptop screen.
 *
 * The navigation column holds fifteen destinations plus the sources section, and it was a plain
 * `Column` of fixed height. On the 1536x816 work area this was reported from, Assinaturas, Perfil and
 * **Configurações** fall below the bottom edge — and there was no scroll at all, so the settings
 * screen could not be opened from the sidebar by any means.
 *
 * This is the third surface in the app to ship that mistake: the settings dialog twice
 * (`ScrollableSettingsUiTest`) and now the sidebar. The rule both times was the same — a scrolling
 * child must be `weight(1f)`, never unbounded height, or it lays its whole content out past the
 * window and scrolls nothing.
 *
 * Read from the source rather than by driving a window, because the failure depends on the host's
 * screen size: on a tall display the bug is invisible, so a rendering test would pass on the very
 * machines least able to see it.
 */
class SidebarReachableUiTest {
    private val source =
        File("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")
            .also { file -> assertTrue(file.isFile, "Expected to find ${file.path}") }
            .readText()

    /** The sidebar's destination list scrolls. */
    @Test
    fun `the navigation list is scrollable`() {
        assertTrue(
            source.contains("verticalScroll(navScroll)"),
            "The sidebar's destination list is not scrollable; the last destinations are " +
                "unreachable on a short screen.",
        )
    }

    /**
     * And it is weighted, which is the half that makes scrolling actually work.
     *
     * `verticalScroll` on an unweighted child of a Column is measured against infinite height: the
     * content is composed, drawn past the bottom of the window, and never scrolls. Both halves are
     * required, so both are checked.
     */
    @Test
    fun `the scrolling list is given bounded height`() {
        val scrollBlock = source.substringAfter("val navScroll = rememberScrollState()")
        val weightBeforeScroll =
            scrollBlock.substringBefore("verticalScroll(navScroll)").contains(".weight(1f)")

        assertTrue(
            weightBeforeScroll,
            "The scrolling sidebar column must be weight(1f); unbounded height scrolls nothing.",
        )
    }

    /**
     * Settings is still in the list.
     *
     * The destination being unreachable was the reported symptom; a later tidy-up that removed the
     * row entirely would "fix" the test above while making the situation worse.
     */
    @Test
    fun `settings is still a destination`() {
        assertTrue(
            source.contains("label = text.settings"),
            "The sidebar no longer offers a route to settings.",
        )
    }
}
