package com.lucasserafin94.iptvburo.desktop

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which categories the settings panel offers.
 *
 * It read the section the catalogue happened to be showing, so a user in Filmes could not reach a
 * series category at all: the switch was not on the screen and nothing explained why. Hiding is
 * per-category and permanent until undone, so a category that cannot be reached is one that cannot
 * be un-hidden either — the same class of bug as the one this list was built to fix.
 */
class SettingsCategoriesTest {
    private val stateSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    private val dialogSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/SettingsDialog.kt").readText()

    @Test
    fun `settings can ask for any section rather than only the open one`() {
        assertTrue(
            stateSource.contains("fun categoriesForSettings(contentType: XtreamContentType)"),
            "the state holder must expose categories per section",
        )
    }

    @Test
    fun `the panel lists all three sections`() {
        for (type in listOf("XtreamContentType.LIVE", "XtreamContentType.MOVIE", "XtreamContentType.SERIES")) {
            assertTrue(dialogSource.contains(type), "settings does not offer $type")
        }
    }

    /**
     * Each section is headed.
     *
     * Several hundred rows from three sections read as one list, and a category named the same in
     * films and series would be indistinguishable — hiding the wrong one is silent and only shows up
     * later as content missing from a screen the user did not touch.
     */
    @Test
    fun `each section carries a heading`() {
        assertTrue(dialogSource.contains("cat-head-"), "sections must be headed")
    }

    /**
     * Keys include the section.
     *
     * Two sections can carry the same provider category id. A key of only the id would make Compose
     * treat two different rows as one and reuse the wrong state between them.
     */
    @Test
    fun `row keys distinguish sections`() {
        assertTrue(
            dialogSource.contains("\"cat-\$type-\${category.providerId}\""),
            "a row key must include its section",
        )
    }

    /**
     * Hidden categories still appear here.
     *
     * The original bug: the list was built from the already-filtered set, so hiding a category also
     * removed it from the switch that hid it, and there was no way back.
     */
    @Test
    fun `the list is unfiltered`() {
        assertFalse(
            dialogSource.contains("appState.xtreamCategories"),
            "settings must not read the filtered list",
        )
        assertTrue(stateSource.contains("categoriesForSettings"))
    }

    /**
     * A Kids profile still sees no adult categories, even in settings.
     *
     * The point of that profile is that the content is not present, and a settings list is not an
     * exception to it — a category name alone can be explicit.
     */
    @Test
    fun `kids profiles never see adult categories in settings`() {
        val body = stateSource.substringAfter("fun categoriesForSettings").take(800)

        assertTrue(body.contains("isKids"), "the Kids filter must still apply")
        assertTrue(body.contains("FamilyContentPolicy.isExplicitAdultLabel"))
    }
}
