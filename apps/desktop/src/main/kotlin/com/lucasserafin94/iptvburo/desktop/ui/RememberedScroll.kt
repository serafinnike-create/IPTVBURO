package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

/**
 * Scroll positions that survive leaving a screen and coming back.
 *
 * ## The problem
 *
 * `rememberLazyListState()` lives with the composable that calls it. Opening a film's detail page
 * removes the catalogue from the composition, taking its scroll position with it; going back builds
 * a fresh state at the top. Somebody who scrolled through four hundred titles, opened one, and
 * pressed back had to scroll through them again.
 *
 * That is the single most-repeated action in the app, so the cost is paid constantly.
 *
 * ## Why a plain map
 *
 * The positions are held here, outside the composition, keyed by whatever the caller considers a
 * distinct list. A `rememberSaveable` would survive configuration changes but not removal from the
 * tree, which is exactly the case that matters. Search and category keys make the number of lists
 * open-ended, so each namespace is a small least-recently-used cache. A profile change clears it:
 * positions are private browsing state, not something the next profile should inherit.
 *
 * ## Keys
 *
 * The key has to distinguish lists that are genuinely different. "Films" and "Series" are separate
 * lists in the same composable, and sharing a key between them would restore the film offset onto
 * the series grid — worse than starting at the top, because it looks deliberate.
 */
object RememberedScroll {
    internal const val MAX_ENTRIES_PER_TYPE = 128

    private fun boundedPositions(): MutableMap<String, Pair<Int, Int>> =
        object : LinkedHashMap<String, Pair<Int, Int>>(MAX_ENTRIES_PER_TYPE + 1, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<Int, Int>>?,
            ): Boolean = size > MAX_ENTRIES_PER_TYPE
        }

    private val listPositions = boundedPositions()
    private val gridPositions = boundedPositions()

    internal fun rememberedList(key: String): Pair<Int, Int> = listPositions[key] ?: (0 to 0)

    internal fun rememberedGrid(key: String): Pair<Int, Int> = gridPositions[key] ?: (0 to 0)

    internal fun storeList(key: String, index: Int, offset: Int) {
        listPositions[key] = index to offset
    }

    internal fun storeGrid(key: String, index: Int, offset: Int) {
        gridPositions[key] = index to offset
    }

    /**
     * Forgets everything.
     *
     * Called when the profile changes: the next profile's catalogue is a different set of titles,
     * and restoring a position from somebody else's browsing is meaningless.
     */
    fun clear() {
        listPositions.clear()
        gridPositions.clear()
    }
}

/**
 * A list state that resumes where this [key] was last left.
 *
 * Drop-in for `rememberLazyListState()`. The position is written back on every recomposition rather
 * than through a disposal effect: `onDispose` runs after the state has already been detached in some
 * removal paths, which is precisely the path this exists to survive.
 */
@Composable
fun rememberRestoredListState(key: String): LazyListState {
    val (index, offset) = remember(key) { RememberedScroll.rememberedList(key) }
    // Keyed explicitly. rememberLazyListState only reads its initial values once, so merely changing
    // those values when a search/category key changed reused the previous list's live state.
    val state = remember(key) { LazyListState(index, offset) }
    LaunchedEffect(key, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { position -> RememberedScroll.storeList(key, position.first, position.second) }
    }
    return state
}

/** The grid equivalent. See [rememberRestoredListState]. */
@Composable
fun rememberRestoredGridState(key: String): LazyGridState {
    val (index, offset) = remember(key) { RememberedScroll.rememberedGrid(key) }
    val state = remember(key) { LazyGridState(index, offset) }
    LaunchedEffect(key, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { position -> RememberedScroll.storeGrid(key, position.first, position.second) }
    }
    return state
}
