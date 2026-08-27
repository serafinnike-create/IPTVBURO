package com.lucasserafin94.iptvburo.domain.model

/**
 * What a connection test measured, and what it means for watching.
 *
 * The point of this is not the numbers. Somebody whose picture keeps freezing does not know whether
 * to blame their Wi-Fi, their provider or the app, and without an answer the only move they have is
 * to ask for help — or assume the app is broken. So every reading here exists to become a sentence
 * a person can act on.
 *
 * Shared across Windows, Android and the television so all three say the same thing about the same
 * connection. A threshold that differs per platform would make the app argue with itself.
 */
object ConnectionDiagnostics {
    /**
     * Download speeds, in megabits per second, that separate one verdict from the next.
     *
     * Chosen against what the app actually plays rather than round numbers: a 1080p stream from a
     * typical provider sits near 8 Mbit/s, 4K near 25, and live television carries no buffer to
     * absorb a dip, so it needs headroom the same file would not.
     */
    const val POOR_DOWNLOAD_MBPS = 10.0
    const val HD_DOWNLOAD_MBPS = 15.0
    const val UHD_DOWNLOAD_MBPS = 30.0

    /** Below this an upload is too thin to sustain the app's own requests while streaming. */
    const val POOR_UPLOAD_MBPS = 5.0

    /**
     * Round-trip times, in milliseconds, past which a stream starts to suffer.
     *
     * Latency does not slow a download, but it delays every seek, every channel change and every
     * recovery from a dropped segment — which is what a viewer experiences as "it keeps freezing"
     * even on a connection whose speed looks fine.
     */
    const val GOOD_PING_MS = 60
    const val POOR_PING_MS = 150

    /** Any sustained loss is a problem; a stream cannot ask for a lost segment twice and keep up. */
    const val POOR_PACKET_LOSS_PERCENT = 2.0

    /** Below this much free memory, playback competes with the rest of the machine. */
    const val LOW_MEMORY_MEGABYTES = 512L

    /** How severe a finding is, so a screen can order and colour them without re-deciding. */
    enum class Severity { GOOD, WARNING, PROBLEM }

    /**
     * One thing the test found, ready to be shown.
     *
     * [detail] carries the measurement and [advice] what it means. Kept apart so a screen can show
     * the number prominently and the sentence underneath, and so a translation changes the words
     * without touching the reading.
     */
    data class Finding(
        val id: String,
        val severity: Severity,
        val detail: String,
        val advice: String? = null,
    )

    /** What a download speed means for watching. Null when the test could not measure it. */
    fun downloadVerdict(mbps: Double?): Severity =
        when {
            mbps == null -> Severity.PROBLEM
            mbps < POOR_DOWNLOAD_MBPS -> Severity.PROBLEM
            mbps < HD_DOWNLOAD_MBPS -> Severity.WARNING
            else -> Severity.GOOD
        }

    fun uploadVerdict(mbps: Double?): Severity =
        when {
            mbps == null -> Severity.WARNING
            mbps < POOR_UPLOAD_MBPS -> Severity.WARNING
            else -> Severity.GOOD
        }

    fun pingVerdict(milliseconds: Int?): Severity =
        when {
            milliseconds == null -> Severity.PROBLEM
            milliseconds > POOR_PING_MS -> Severity.PROBLEM
            milliseconds > GOOD_PING_MS -> Severity.WARNING
            else -> Severity.GOOD
        }

    fun packetLossVerdict(percent: Double?): Severity =
        when {
            percent == null -> Severity.WARNING
            percent >= POOR_PACKET_LOSS_PERCENT -> Severity.PROBLEM
            percent > 0.0 -> Severity.WARNING
            else -> Severity.GOOD
        }

    /**
     * The best quality this connection sustains, as a token a screen turns into a sentence.
     *
     * Deliberately conservative: telling somebody their connection handles 4K when it stutters is
     * worse than telling them nothing, because they will spend the evening blaming the app.
     */
    fun qualityCeiling(mbps: Double?): String =
        when {
            mbps == null -> "unknown"
            mbps < POOR_DOWNLOAD_MBPS -> "unstable"
            mbps < HD_DOWNLOAD_MBPS -> "sd"
            mbps < UHD_DOWNLOAD_MBPS -> "hd"
            else -> "uhd"
        }

    /**
     * The overall verdict: the worst of the parts.
     *
     * Not an average. A connection with excellent speed and 8% packet loss is a connection that
     * freezes, and averaging that into "good" would tell somebody their setup is fine while they
     * watch it stutter.
     */
    fun overall(findings: List<Finding>): Severity =
        when {
            findings.any { it.severity == Severity.PROBLEM } -> Severity.PROBLEM
            findings.any { it.severity == Severity.WARNING } -> Severity.WARNING
            else -> Severity.GOOD
        }

    /**
     * Megabits per second from a transfer, or null when the sample is too small to mean anything.
     *
     * A test that finished in a few milliseconds measured the local buffer rather than the network,
     * and reporting 900 Mbit/s to somebody whose video is freezing destroys the credibility of the
     * whole screen. Better to say nothing than to say something false.
     */
    fun megabitsPerSecond(
        bytes: Long,
        milliseconds: Long,
    ): Double? {
        if (bytes <= 0 || milliseconds < MINIMUM_SAMPLE_MILLIS) return null
        return (bytes * 8.0) / (milliseconds / 1000.0) / 1_000_000.0
    }

    /** Below this a transfer says more about buffering than about the network. */
    const val MINIMUM_SAMPLE_MILLIS = 250L
}
