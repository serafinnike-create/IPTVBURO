package com.lucasserafin94.iptvburo.xtream

/**
 * How fast a download is going, measured over a short trailing window.
 *
 * A catalogue of tens of thousands of titles takes long enough that a screen saying only what it
 * is doing cannot tell the viewer whether it is working or stuck. A rate answers that on its own,
 * and it is the figure someone uses to decide whether to keep waiting or to blame their network.
 *
 * Deliberately a trailing window rather than an average over the whole transfer. An average is
 * dominated by however the download started and stops reacting to what is happening now — a
 * connection that stalls halfway would keep reporting the healthy figure it earned in its first
 * seconds, which is precisely the case the viewer needs to see.
 *
 * Not thread-safe. Feed it from the one thread that reads the body.
 */
class DownloadRate(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    private val samples = ArrayDeque<Sample>()
    private var total = 0L

    /** Records [byteCount] bytes as having arrived at [atMillis]. */
    fun record(byteCount: Int, atMillis: Long) {
        if (byteCount <= 0) return
        total += byteCount
        samples.addLast(Sample(atMillis, byteCount.toLong()))
        trimTo(atMillis)
    }

    /**
     * Bytes per second over the trailing window, or null when there is not enough to say.
     *
     * Null rather than zero, and the distinction matters on screen: zero reads as "stopped", which
     * is a claim this cannot make. Too few samples, or samples too close together in time, mean
     * the honest answer is silence.
     */
    fun bytesPerSecond(atMillis: Long): Long? {
        trimTo(atMillis)
        if (samples.size < MINIMUM_SAMPLES) return null
        val elapsed = atMillis - samples.first().atMillis
        if (elapsed < MINIMUM_ELAPSED_MILLIS) return null
        val windowBytes = samples.sumOf(Sample::byteCount)
        return windowBytes * MILLIS_PER_SECOND / elapsed
    }

    /** Everything counted so far, for callers that want a total rather than a rate. */
    fun totalBytes(): Long = total

    private fun trimTo(atMillis: Long) {
        val oldest = atMillis - windowMillis
        while (samples.size > MINIMUM_SAMPLES && samples.first().atMillis < oldest) {
            samples.removeFirst()
        }
    }

    private data class Sample(val atMillis: Long, val byteCount: Long)

    companion object {
        /**
         * Long enough to survive one slow block, short enough to still describe now.
         *
         * Two seconds of history: a single stalled read does not erase the reading, and a
         * connection that degrades is reflected within about that long rather than being averaged
         * away.
         */
        const val DEFAULT_WINDOW_MILLIS = 2_000L

        /** Below this the elapsed time is too small to divide by without inventing a figure. */
        private const val MINIMUM_ELAPSED_MILLIS = 250L

        /** One sample is a size, not a rate. */
        private const val MINIMUM_SAMPLES = 2

        private const val MILLIS_PER_SECOND = 1_000L
    }
}
