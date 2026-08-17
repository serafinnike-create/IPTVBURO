package com.lucasserafin94.iptvburo.desktop.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opening a title's page has to take the whole route, not part of it.
 *
 * The details loaders live in `XtreamWorkspace`, which composes for `DesktopDestination.CATALOG` and
 * nothing else. A caller that selects a title without also moving the destination therefore opens a
 * page that says "Carregando ficha do filme…" with no request in flight, no error, and no way to
 * recover — the spinner is simply the initial state of a screen nothing ever asked to load.
 *
 * That is what the search results did: `selectDailyItem(item)` alone, leaving the destination on
 * SEARCH. Reported as a film opening from the home screen and never loading.
 *
 * `openTitle` now performs all four steps together, and these tests require the callers that need it
 * to use it rather than assembling the sequence by hand — which is how three separate callers each
 * got a different part of it wrong.
 */
class OpenTitleRouteTest {
    private fun read(path: String) =
        File(path).also { file -> assertTrue(file.isFile, "Expected to find $path") }.readText()

    private val state =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")

    private val app =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")

    /** The route exists, and does the part that was missing. */
    @Test
    fun `openTitle moves the destination to the catalogue`() {
        val body = state.substringAfter("fun openTitle(item: XtreamCatalogItem) {").substringBefore("\n    }")

        assertTrue(body.contains("selectDailyItem(item)"), "openTitle must select the item properly.")
        assertTrue(
            body.contains("destination = DesktopDestination.CATALOG"),
            "openTitle must move to CATALOG, or the details loaders never compose.",
        )
        assertTrue(
            body.contains("pendingDetailsRequest = item.providerId"),
            "openTitle must request the details.",
        )
        assertTrue(body.contains("xtreamContentType = item.contentType"), "openTitle must set the type.")
    }

    /**
     * The search results use it.
     *
     * Named specifically because this is the caller that was broken, and a regression here is
     * invisible until somebody opens a search result and waits.
     */
    @Test
    fun `search results open through openTitle`() {
        val searchBlock = app.substringAfter("SearchWorkspace(").substringBefore("text = strings,")

        assertTrue(
            searchBlock.contains("appState.openTitle(item)"),
            "The search results must open through openTitle; selecting alone leaves a stuck spinner.",
        )
    }

    /** And reminders, which already worked, must keep working the same way. */
    @Test
    fun `reminders open through openTitle`() {
        assertTrue(
            state.contains("fun openReminder(item: XtreamCatalogItem) = openTitle(item)"),
            "openReminder should delegate rather than repeat the sequence.",
        )
    }
}
