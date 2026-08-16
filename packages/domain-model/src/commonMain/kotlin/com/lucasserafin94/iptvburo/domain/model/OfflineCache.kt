package com.lucasserafin94.iptvburo.domain.model

/**
 * How much of the library the viewer wants kept on this machine.
 *
 * ## What the setting actually buys
 *
 * Posters, backdrops and cast photographs are fetched from the network every time they are drawn.
 * On a list of forty thousand titles that is the difference between a wall of artwork appearing at
 * once and one that fills in row by row as you scroll. Keeping them on disk turns the second into
 * the first, permanently.
 *
 * ## Why the viewer chooses the size
 *
 * Because the cost is theirs. A full library of artwork is measured in gigabytes, and somebody on a
 * small laptop drive has every right to say no — while somebody with a large disk and a slow
 * connection gets the most from saying yes. A default guess would be wrong for both.
 *
 * Zero is a real answer and must keep working exactly as the app does today: nothing stored, every
 * image fetched when needed.
 */
@JvmInline
value class CacheBudget(val bytes: Long) {
    init {
        require(bytes >= 0) { "A cache budget cannot be negative." }
    }

    /** The size as whole gigabytes, which is the unit the setting is expressed in. */
    val gigabytes: Int get() = (bytes / BYTES_PER_GB).toInt()

    /** Whether anything is kept at all. Zero means the app behaves as it did before this existed. */
    val isEnabled: Boolean get() = bytes > 0

    companion object {
        const val BYTES_PER_GB: Long = 1_073_741_824L

        /** The ceiling the product asked for. Past this the setting stops being a choice anyone makes. */
        const val MAX_GIGABYTES: Int = 64

        /**
         * What a fresh install gets before anybody chooses.
         *
         * Not zero, and not the maximum. Two gigabytes holds the artwork of a large list without
         * being a number anyone would notice on a modern disk, so the app is faster out of the box
         * for somebody who never opens the setting — and the first-run screen still asks, so the
         * choice is offered rather than assumed.
         */
        const val DEFAULT_GIGABYTES: Int = 2

        val DEFAULT: CacheBudget = ofGigabytes(DEFAULT_GIGABYTES)
        val DISABLED: CacheBudget = CacheBudget(0)

        /** Clamped rather than rejected: a slider cannot produce a value this should refuse. */
        fun ofGigabytes(gigabytes: Int): CacheBudget =
            CacheBudget(gigabytes.coerceIn(0, MAX_GIGABYTES) * BYTES_PER_GB)
    }
}

/**
 * How far along the first fill is.
 *
 * The first fill is the slow one: nothing is on disk, so every poster in the library is a request.
 * It runs in the background and the app stays usable throughout — a progress bar that blocks the
 * product for twenty minutes would be a worse answer than no cache at all.
 */
data class CacheFillProgress(
    val done: Int = 0,
    val total: Int = 0,
    val bytesWritten: Long = 0L,
    val state: CacheFillState = CacheFillState.IDLE,
) {
    /**
     * From 0.0 to 1.0, or null when there is nothing to measure yet.
     *
     * Null rather than zero while the total is unknown, so the screen can show a bar that moves
     * rather than one stuck at the left — the two mean different things and look identical.
     */
    val fraction: Float?
        get() = if (total <= 0) null else (done.toFloat() / total).coerceIn(0f, 1f)

    /**
     * The same position as a whole percentage, or null while there is nothing to measure.
     *
     * Rounded down rather than to nearest, so the bar never reads "100%" while images are still
     * being fetched — finishing is what [CacheFillState.COMPLETE] says, and a percentage that
     * arrives there early is the one thing a progress figure must not do.
     */
    val percent: Int?
        get() = fraction?.let { value -> (value * 100).toInt().coerceIn(0, 100) }

    val isRunning: Boolean get() = state == CacheFillState.RUNNING

    /** Whether the fill stopped part-way and has something left to do. */
    val isResumable: Boolean get() = state == CacheFillState.PAUSED && (total <= 0 || done < total)
}

enum class CacheFillState {
    /** Nothing in flight. Either never started or finished. */
    IDLE,
    RUNNING,

    /** Stopped by the viewer, and resumable: what is already on disk stays there. */
    PAUSED,

    /** Everything the budget allows is stored. */
    COMPLETE,
}

/**
 * Decides what the fill should do next.
 *
 * Pure, so the awkward cases — a budget lowered mid-fill, a library larger than the budget — can be
 * settled here rather than inside a coroutine that is hard to reason about.
 */
object CachePolicy {
    /**
     * Whether another item may be written.
     *
     * The budget is a ceiling on what is kept, not a promise to keep everything: a library whose
     * artwork exceeds the chosen size fills to that size and stops, which is the honest behaviour
     * and the one the setting's wording has to match.
     */
    fun canWrite(
        budget: CacheBudget,
        bytesUsed: Long,
        nextItemBytes: Long,
    ): Boolean {
        if (!budget.isEnabled) return false
        if (nextItemBytes <= 0) return false
        return bytesUsed + nextItemBytes <= budget.bytes
    }

    /**
     * How much has to be freed for a new budget to hold.
     *
     * Zero when the budget grew or already fits. Lowering the setting has to take effect at once —
     * somebody who reduces it to free space and finds nothing freed has been ignored.
     */
    fun bytesToFree(
        budget: CacheBudget,
        bytesUsed: Long,
    ): Long = (bytesUsed - budget.bytes).coerceAtLeast(0)

    /**
     * A rough size for a library's artwork, for the estimate shown before anybody commits.
     *
     * Deliberately approximate and named so. A poster at the width this app requests is around
     * 80 KB and a backdrop rather more; the number exists so the first-run screen can say "about
     * 3 GB" instead of asking somebody to choose blind, and it must never be presented as exact.
     */
    fun estimatedBytesFor(titleCount: Int): Long =
        titleCount.coerceAtLeast(0).toLong() * APPROXIMATE_BYTES_PER_TITLE

    /** One poster, plus a share of the backdrops and cast photographs a title pulls in. */
    private const val APPROXIMATE_BYTES_PER_TITLE = 110_000L
}
