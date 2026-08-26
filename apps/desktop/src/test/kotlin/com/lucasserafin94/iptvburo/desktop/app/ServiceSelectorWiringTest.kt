package com.lucasserafin94.iptvburo.desktop.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Serviço selector has to work on a playlist whose categories name no service.
 *
 * Reported twice. The selector reads the playlist's category names, and a list that files films under
 * "Filmes | Ação" rather than "Filmes | Netflix" gives it nothing — measured on the reporter's own
 * list, all 31 film categories were genres. The first attempt replaced the missing control with the
 * text "não informado na sua lista", which is true and useless: the service a film came from is
 * knowable, it is simply not in the playlist.
 *
 * These pin the parts that make the answer come from TMDb instead, because each is silent when
 * missing — nothing throws, and the selector just stays empty.
 */
class ServiceSelectorWiringTest {
    private fun read(path: String) =
        File(path).also { assertTrue(it.isFile, "Expected to find $path") }.readText()

    private val workspace =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")

    private val state =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")

    private val repository =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/SessionXtreamRepository.kt")

    /**
     * The contract, which is where the parameter's default now lives.
     *
     * Kotlin forbids a default on an overriding function, so extracting [CatalogueRepository] moved
     * every default from the class to the interface. The capability is unchanged; only the file
     * that declares it moved.
     */
    private val contract =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/CatalogueRepository.kt")

    /** The index is built, and only for the tab that needs it. */
    @Test
    fun `the film tab builds a service index`() {
        assertTrue(
            workspace.contains("appState.ensureServiceTitleIndex()"),
            "Nothing asks TMDb which services carry this library's films.",
        )
        val effect =
            workspace.substringAfter("LaunchedEffect(appState.xtreamContentType, appState.xtreamCategories)")
                .substringBefore("}")
        assertTrue(
            effect.contains("XtreamContentType.MOVIE"),
            "The index should be built for Filmes; Ao vivo already files channels by service.",
        )
    }

    /**
     * The lookup runs per service, not per title.
     *
     * This is the whole reason the feature is affordable. Two requests per film over a 42,000-item
     * catalogue is tens of thousands of calls, which TMDb rate-limits long before finishing — so a
     * `titlesOnProvider` loop is correct and a per-title loop would be a defect, not a slow path.
     */
    @Test
    fun `the index asks each service what it carries`() {
        val builder = state.substringAfter("suspend fun ensureServiceTitleIndex()").substringBefore("\n    }")

        assertTrue(
            builder.contains("titlesOnProvider"),
            "The index must ask each service for its titles.",
        )
        assertTrue(
            builder.contains("watchProviderDirectory"),
            "The service list must come from TMDb's directory.",
        )
        assertTrue(
            !builder.contains("findAudienceScore") && !builder.contains("watchProviders("),
            "A per-title lookup would be tens of thousands of requests; see the note on the builder.",
        )
    }

    /** Selecting a service actually filters the grid. */
    @Test
    fun `a chosen service restricts the catalogue`() {
        assertTrue(
            state.contains("allowedLocalIds = serviceIds"),
            "The chosen service is never passed to the page fetch, so the grid would not change.",
        )
        assertTrue(
            contract.contains("allowedLocalIds: Set<String>? = null"),
            "The contract does not offer a filter by library id.",
        )
        assertTrue(
            repository.contains("allowedLocalIds: Set<String>?"),
            "The repository cannot filter by library id.",
        )
    }

    /**
     * And the filter is applied before an object is built.
     *
     * The paging loop runs over every row of the catalogue on every keystroke; a filter placed after
     * `itemAt` would allocate an item for each row it then discards.
     */
    @Test
    fun `the id filter runs before the row is built`() {
        val loop = repository.substringAfter("allowedLocalIds != null").substringBefore("pageItems +=")

        assertTrue(
            loop.contains("providerIdAt(index)"),
            "The filter should read the id column, not build the row.",
        )
    }

    /**
     * An empty set is a real answer.
     *
     * "This service carries nothing you own" must produce an empty grid, not the whole catalogue —
     * which is what would happen if empty and null were treated alike.
     */
    @Test
    fun `an empty service is distinguished from no filter`() {
        // The declaration wraps onto the next line, so the window has to span both.
        val capture = state.substringAfter("val serviceIds =").take(220)

        assertTrue(
            capture.contains("takeIf { it.isNotEmpty() }"),
            "An empty id set must not silently mean \"no filter\".",
        )
    }
}
