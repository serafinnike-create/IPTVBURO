package com.lucasserafin94.iptvburo.domain.model

/**
 * Several subscriptions shown as one catalogue.
 *
 * Somebody who buys a second list to fill the gaps in the first ends up switching between them to
 * find out which has the film they want — which is work the app should be doing. With this on, the
 * two arrive as one library: everything from both, each title once.
 *
 * ## Why the biggest list wins
 *
 * When a title exists in more than one subscription, one copy has to be chosen, and the largest
 * catalogue is the best available proxy for the better one: a provider carrying forty thousand
 * films is likelier to hold the good stream than one carrying ten. It is a heuristic, not a truth,
 * but it is stable and explicable — the alternative, choosing per title, would make the same film
 * play from a different provider on different days for no reason the viewer can see.
 *
 * The smaller lists are not ignored: they contribute everything the larger one does not have, which
 * is the whole point of owning them.
 *
 * ## A source that is down does not take the others with it
 *
 * A subscription that has expired or whose server is unreachable is named, and the rest still load.
 * The opposite — one dead list blanking a working library — would be far worse than the problem
 * this solves.
 */
object MergedSources {
    /**
     * How many subscriptions may be merged at once.
     *
     * Ten because that is what was asked for, and because the work is proportional to the total
     * number of titles: ten large lists is several hundred thousand rows to compare, which is
     * already the outer edge of what a television can hold in memory.
     */
    const val MAXIMUM_SOURCES = 10

    /** One subscription's contribution, before merging. */
    data class Contribution<T>(
        val sourceId: String,
        val label: String,
        val items: List<T>,
        /**
         * Why this source contributed nothing, or null when it worked.
         *
         * Carried rather than thrown: the viewer needs to know which of their lists is down, and an
         * exception would replace that with a failure of the whole load.
         */
        val failure: String? = null,
    )

    /** What the merge produced, and what went wrong on the way. */
    data class Merged<T>(
        val items: List<T>,
        /** Sources that failed, in the order they were tried, so the screen can name them. */
        val failed: List<Contribution<T>>,
        /** How many titles each source contributed after duplicates were removed. */
        val contributed: Map<String, Int>,
    ) {
        val hasFailures: Boolean
            get() = failed.isNotEmpty()
    }

    /**
     * Merges [contributions] into one catalogue, largest source first.
     *
     * @param key the identity two copies of the same title share. Callers pass the same normaliser
     *   the shelves already use, so "Duna 4K [DUB]" and "Duna" are recognised as one film.
     */
    fun <T> merge(
        contributions: List<Contribution<T>>,
        key: (T) -> String,
    ): Merged<T> {
        val (worked, failed) = contributions.partition { it.failure == null }

        // Largest first, so its copy of a shared title is the one kept. Ties broken by source id so
        // the result is the same on every load rather than depending on which finished first.
        val ordered =
            worked.sortedWith(
                compareByDescending<Contribution<T>> { it.items.size }.thenBy { it.sourceId },
            )

        val seen = mutableSetOf<String>()
        val items = mutableListOf<T>()
        val contributed = mutableMapOf<String, Int>()

        ordered.forEach { source ->
            var added = 0
            source.items.forEach { item ->
                val identity = key(item)
                // A blank key cannot be compared to anything, so the item is kept rather than
                // dropped: losing a title is worse than showing it twice.
                if (identity.isBlank() || seen.add(identity)) {
                    items += item
                    added += 1
                }
            }
            contributed[source.sourceId] = added
        }

        return Merged(items = items, failed = failed, contributed = contributed)
    }

    /**
     * Whether merging is worth doing at all.
     *
     * One working source merges to itself, so the whole pass can be skipped — which matters on a
     * television, where walking forty thousand rows for no reason is a visible pause.
     */
    fun <T> isWorthMerging(contributions: List<Contribution<T>>): Boolean =
        contributions.count { it.failure == null } > 1

    /**
     * The sources to attempt, capped at [MAXIMUM_SOURCES].
     *
     * Largest first, so if the cap does bite it keeps the lists most likely to hold what somebody
     * is looking for.
     */
    fun <T> withinLimit(contributions: List<Contribution<T>>): List<Contribution<T>> =
        if (contributions.size <= MAXIMUM_SOURCES) {
            contributions
        } else {
            contributions.sortedByDescending { it.items.size }.take(MAXIMUM_SOURCES)
        }
}
