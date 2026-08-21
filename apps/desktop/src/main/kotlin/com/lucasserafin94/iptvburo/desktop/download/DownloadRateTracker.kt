package com.lucasserafin94.iptvburo.desktop.download

import java.util.concurrent.ConcurrentHashMap

/**
 * How fast a download is actually going.
 *
 * The naive answer — total bytes divided by total elapsed — is wrong in the way that matters: it
 * keeps reporting a healthy average long after a transfer has stalled, so the one moment the user
 * most needs the truth is the moment it lies. This measures a recent window instead, so a stall
 * shows up within seconds.
 *
 * Smoothed rather than instantaneous. Raw samples over a fraction of a second swing wildly with
 * buffer sizes and make the number unreadable; an exponential average settles quickly and still
 * reacts to a real change.
 *
 * Safe to call from any thread — downloads run concurrently, each on its own coroutine.
 */
class DownloadRateTracker(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Sample(
        val atMillis: Long,
        val bytes: Long,
        val rate: Double,
    )

    private val samples = ConcurrentHashMap<String, Sample>()

    /**
     * Records that [key] has now read [totalBytesRead], returning the current rate in bytes/second.
     *
     * Zero until there is enough of a gap to divide by — the first callback arrives immediately and
     * a rate computed over a millisecond would read as gigabytes per second.
     */
    fun observe(
        key: String,
        totalBytesRead: Long,
    ): Long {
        val now = clock()
        val previous = samples[key]

        if (previous == null) {
            samples[key] = Sample(now, totalBytesRead, 0.0)
            return 0L
        }

        val elapsedMillis = now - previous.atMillis
        // Below the sampling floor the division is dominated by timer noise, so the previous
        // answer is kept rather than replaced with a wilder one.
        if (elapsedMillis < MIN_SAMPLE_MILLIS) return previous.rate.toLong()

        val deltaBytes = totalBytesRead - previous.bytes
        // A restarted or rewound transfer would otherwise produce a negative rate.
        val instant = if (deltaBytes > 0) deltaBytes * 1000.0 / elapsedMillis else 0.0

        // The first real measurement is taken as-is; smoothing from zero would show a rate far
        // below the truth for the first several seconds of every download.
        val smoothed =
            if (previous.rate <= 0.0) instant else previous.rate * (1 - SMOOTHING) + instant * SMOOTHING

        samples[key] = Sample(now, totalBytesRead, smoothed)
        return smoothed.toLong()
    }

    /** Forgets [key], so a later download of the same title starts from a clean measurement. */
    fun forget(key: String) {
        samples.remove(key)
    }

    private companion object {
        const val MIN_SAMPLE_MILLIS = 500L

        /** Weight of the newest sample. Higher reacts faster; lower reads more steadily. */
        const val SMOOTHING = 0.3
    }
}

/**
 * A byte count as a person reads it.
 *
 * Binary units (1024) because that is what file managers on every desktop platform show, and a size
 * that disagrees with Explorer reads as a bug in the app.
 */
fun formatBytes(bytes: Long): String =
    when {
        bytes < 0 -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.0f KB".format(DISPLAY_LOCALE, bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(DISPLAY_LOCALE, bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(DISPLAY_LOCALE, bytes / (1024.0 * 1024 * 1024))
    }

/**
 * The locale every user-facing number is formatted in.
 *
 * Pinned to the app's own audience rather than left to the JVM's default, which is whatever Windows
 * is set to and made the same build render "1.0 MB" on one machine and "1,0 MB" on another.
 * Portuguese is the primary language here and uses a comma.
 *
 * It is not only the separator. `%d` and `%f` follow the locale's *digits* too, so on a system set
 * to Egypt, Iran, Bangladesh or Myanmar the playback clock rendered as `١:٠٥:٠٩` — Arabic-Indic
 * numerals inside an interface that has no Arabic translation. Shared rather than private so every
 * formatter that shows a number to a person can use the same one; hex digests are unaffected,
 * because `%x` is ASCII in any locale.
 */
internal val DISPLAY_LOCALE: java.util.Locale = java.util.Locale.forLanguageTag("pt-BR")

/** A transfer rate, as a person reads it. */
fun formatRate(bytesPerSecond: Long): String =
    if (bytesPerSecond <= 0) "—" else "${formatBytes(bytesPerSecond)}/s"

/**
 * A duration as a person reads it.
 *
 * Deliberately coarse past an hour: "2 h 14 min" is as much precision as anyone acts on, and a
 * seconds figure that ticks on an hour-long estimate only draws attention to how rough it is.
 */
fun formatDuration(seconds: Long): String =
    when {
        seconds < 0 -> "—"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }
