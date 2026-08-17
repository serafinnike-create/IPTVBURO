package com.lucasserafin94.iptvburo.desktop.ui

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scroll positions that survive leaving a screen.
 *
 * Opening a title removes the catalogue from the composition, and a plain `rememberLazyGridState`
 * goes with it — so pressing back landed at the top after however far the user had scrolled. That is
 * the app's most repeated action, so the cost was paid constantly.
 *
 * The risk in the fix is the key. Two lists sharing one would restore the film offset onto the
 * series grid, which looks deliberate and is worse than starting at the top.
 */
class RememberedScrollTest {

    @AfterTest
    fun tearDown() = RememberedScroll.clear()

    @Test
    fun `a stored position comes back`() {
        RememberedScroll.storeList("films", index = 42, offset = 17)

        assertEquals(42 to 17, RememberedScroll.rememberedList("films"))
    }

    @Test
    fun `an unvisited list starts at the top`() {
        assertEquals(0 to 0, RememberedScroll.rememberedList("never-opened"))
        assertEquals(0 to 0, RememberedScroll.rememberedGrid("never-opened"))
    }

    /**
     * Lists do not borrow each other's positions.
     *
     * The failure this prevents is subtle: a series grid opening at the offset of the film grid
     * looks like a deliberate position rather than a bug, so it would be reported as "it jumps to a
     * random place" rather than as a scroll problem.
     */
    @Test
    fun `separate keys keep separate positions`() {
        RememberedScroll.storeGrid("catalog:MOVIE::", index = 100, offset = 5)
        RememberedScroll.storeGrid("catalog:SERIES::", index = 3, offset = 0)

        assertEquals(100 to 5, RememberedScroll.rememberedGrid("catalog:MOVIE::"))
        assertEquals(3 to 0, RememberedScroll.rememberedGrid("catalog:SERIES::"))
    }

    @Test
    fun `lists and grids do not share a namespace`() {
        // Same key, different kind of list. A screen with both would otherwise have one overwrite
        // the other's position on every recomposition.
        RememberedScroll.storeList("shared", index = 10, offset = 0)
        RememberedScroll.storeGrid("shared", index = 20, offset = 0)

        assertEquals(10 to 0, RememberedScroll.rememberedList("shared"))
        assertEquals(20 to 0, RememberedScroll.rememberedGrid("shared"))
    }

    @Test
    fun `a newer position replaces the older one`() {
        RememberedScroll.storeList("films", index = 10, offset = 0)
        RememberedScroll.storeList("films", index = 60, offset = 30)

        assertEquals(60 to 30, RememberedScroll.rememberedList("films"))
    }

    @Test
    fun `searches cannot grow the cache without limit`() {
        repeat(RememberedScroll.MAX_ENTRIES_PER_TYPE + 1) { index ->
            RememberedScroll.storeGrid("search-$index", index + 1, 0)
        }

        assertEquals(0 to 0, RememberedScroll.rememberedGrid("search-0"))
        assertEquals(
            RememberedScroll.MAX_ENTRIES_PER_TYPE + 1 to 0,
            RememberedScroll.rememberedGrid("search-${RememberedScroll.MAX_ENTRIES_PER_TYPE}"),
        )
    }

    /**
     * Everything is forgotten when the profile changes.
     *
     * The next profile's catalogue is a different set of titles, and restoring a position from
     * somebody else's browsing means landing somewhere arbitrary.
     */
    @Test
    fun `clearing forgets every list`() {
        RememberedScroll.storeList("a", 5, 0)
        RememberedScroll.storeGrid("b", 5, 0)

        RememberedScroll.clear()

        assertEquals(0 to 0, RememberedScroll.rememberedList("a"))
        assertEquals(0 to 0, RememberedScroll.rememberedGrid("b"))
    }

    @Test
    fun `profile selection clears remembered positions`() {
        val source = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
            .readText()
        val selection = source.substringAfter("fun selectProfile(id: String?)").take(1_200)

        assertTrue(
            selection.contains("RememberedScroll.clear()"),
            "profile switching must not inherit another profile's scroll position",
        )
    }

    /**
     * The catalogue key distinguishes what makes a list different.
     *
     * Content type, category and search each change which titles are shown. A key missing any of
     * them restores a position from a different set of results.
     *
     * The field names matter as much as their presence. This asserted `selectedCategoryId` and
     * `searchQuery`, which are the **imported-M3U** catalogue's fields — and the key really did use
     * them, so the check passed while an Xtream session, where both stay empty for ever, collapsed
     * every category and every search onto one shared key. The Xtream names are the ones this screen
     * browses with.
     */
    @Test
    fun `the catalogue key covers type, category and search`() {
        val source = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
            .readText()

        val key = Regex("""key = "catalog:[^"]*"""").find(source)?.value.orEmpty()

        assertTrue(key.contains("xtreamContentType"), "films and series must not share a position")
        assertTrue(key.contains("selectedXtreamCategoryId"), "categories show different titles")
        assertTrue(key.contains("xtreamSearchQuery"), "a search is a different list of results")
    }

    @Test
    fun `every long list restores its position`() {
        // The screens somebody scrolls a long way down and then opens something from. A new one
        // added without this is a regression of the behaviour, not a missing feature.
        val screens = mapOf(
            "XtreamWorkspace.kt" to "rememberRestoredGridState",
            "SubscriptionsWorkspace.kt" to "rememberRestoredListState",
            "ContinueWatchingWorkspace.kt" to "rememberRestoredListState",
            "HistoryGallery.kt" to "rememberRestoredListState",
        )

        screens.forEach { (file, _) ->
            val source = Path
                .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/$file")
                .readText()
            assertTrue(
                source.contains("rememberRestored"),
                "$file does not restore its scroll position",
            )
        }
    }

    @Test
    fun `a changed key creates a different compose state`() {
        val source = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/RememberedScroll.kt")
            .readText()

        assertTrue(source.contains("remember(key) { LazyListState(index, offset) }"))
        assertTrue(source.contains("remember(key) { LazyGridState(index, offset) }"))
    }
}
