package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.metadata.AdultArtworkClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Covers fetched for a grid of adult titles, one lookup per title however often it is drawn.
 *
 * The details screen could ask for one title at a time and hold the answer in a single slot. A grid
 * cannot: it draws a hundred cards, rebuilds them on every scroll, and asks again for the same
 * title each time it comes back into view. Without a cache that is a request per card per scroll,
 * against a service the viewer pays for with their own key.
 *
 * So this remembers, including the misses. A title the source has never heard of is the common case
 * on a provider's own naming, and asking again on every scroll would spend the whole request budget
 * on titles that will never resolve.
 *
 * @param maximumInFlight how many lookups may run at once. Small on purpose: the grid asks for
 *   everything visible at once, and letting a hundred requests go at a service that rate-limits
 *   would get the whole page refused rather than answered slowly.
 */
class AdultArtworkShelf(
    private val client: AdultArtworkClient,
    private val scope: CoroutineScope,
    maximumInFlight: Int = DEFAULT_IN_FLIGHT,
    private val onFound: () -> Unit = {},
) {
    /**
     * What each title resolved to: an address, or absent-and-known-absent.
     *
     * A `ConcurrentHashMap` rather than Compose state, because it is written from lookups running
     * off the main thread. Recomposition is driven by [onFound] instead, one signal per answer.
     */
    private val found = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /** Titles being asked about right now, so a scroll cannot start the same lookup twice. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val permits = Semaphore(maximumInFlight.coerceAtLeast(1))

    /**
     * The cover for [title] if one is already known, starting a lookup when it is not.
     *
     * Returns null on the first call for a title and the address on a later one, once the answer
     * has arrived and [onFound] has prompted a redraw. Safe to call from a composable — it is
     * cheap and idempotent for a title already asked about.
     */
    fun posterFor(title: String): String? {
        val wanted = title.trim()
        if (wanted.isEmpty()) return null
        found[wanted]?.let { return it }
        if (wanted in missing) return null

        // `add` returns false when another card already started this one, which is the common case
        // in a grid where several cards carry the same title at different qualities.
        if (!inFlight.add(wanted)) return null
        scope.launch {
            try {
                permits.withPermit {
                    val poster =
                        runCatching { withContext(Dispatchers.IO) { client.posterFor(wanted) } }
                            .getOrNull()
                    if (poster != null) {
                        found[wanted] = poster
                        onFound()
                    } else {
                        // Remembered as a miss. A provider's own naming resolves rarely, and asking
                        // again on every scroll would spend the budget on titles that never will.
                        missing += wanted
                    }
                }
            } finally {
                inFlight -= wanted
            }
        }
        return null
    }

    /** How many titles have been asked about, for the settings screen to report. */
    fun lookupCount(): Int = found.size + missing.size

    private companion object {
        /**
         * Four at a time.
         *
         * The grid asks for everything visible at once — a hundred cards on a wide window — and a
         * hundred simultaneous requests to a service that rate-limits gets the page refused rather
         * than answered slowly. Four keeps a screenful arriving within a few seconds while leaving
         * the connection usable for the video the viewer is actually watching.
         */
        const val DEFAULT_IN_FLIGHT = 4
    }
}
