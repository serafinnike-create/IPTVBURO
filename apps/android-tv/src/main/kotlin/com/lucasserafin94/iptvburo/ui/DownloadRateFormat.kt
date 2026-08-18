package com.lucasserafin94.iptvburo.ui

import java.util.Locale

/**
 * A download rate as a viewer reads it, or null when there is nothing honest to show.
 *
 * Null rather than `0 KB/s`, and the difference is the whole point of showing this at all: a zero
 * reads as "stopped", and between two requests, or before enough has arrived to divide by, the app
 * does not know that. Saying nothing is the honest answer, and the caller omits the line.
 *
 * Formatted through the viewer's locale so the decimal separator is theirs — `1,4 MB` in Portuguese
 * and German, `1.4 MB` in English. The unit names are the same word everywhere they appear in this
 * app, so they are not translated.
 */
fun formatDownloadRate(bytesPerSecond: Long?, locale: Locale = Locale.getDefault()): String? {
    if (bytesPerSecond == null || bytesPerSecond <= 0) return null
    return when {
        // Below a kilobyte a second, a decimal is noise: the interesting fact is that it is slow.
        bytesPerSecond < BYTES_PER_KIB -> String.format(locale, "%d B", bytesPerSecond)
        bytesPerSecond < BYTES_PER_MIB ->
            String.format(locale, "%.1f KB", bytesPerSecond.toDouble() / BYTES_PER_KIB)
        else ->
            String.format(locale, "%.1f MB", bytesPerSecond.toDouble() / BYTES_PER_MIB)
    }
}

private const val BYTES_PER_KIB = 1_024L
private const val BYTES_PER_MIB = 1_024L * 1_024L
