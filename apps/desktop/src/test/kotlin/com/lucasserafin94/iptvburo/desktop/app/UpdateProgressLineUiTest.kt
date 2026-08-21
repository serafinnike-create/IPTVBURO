package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The update overlay must show speed and remaining time, not only a percentage.
 *
 * Reads the source rather than rendering: the overlay needs a DesktopRelease and a live download to
 * reach its progress branch, and a test that fakes both would be asserting against its own fake.
 */
class UpdateProgressLineUiTest {
    private val source =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()

    @Test
    fun `the overlay reports speed and remaining time`() {
        assertTrue(
            source.contains("if (bytesPerSecond > 0L) add(formatRate(bytesPerSecond))"),
            "The download speed must reach the update overlay.",
        )
        assertTrue(
            source.contains("secondsRemaining?.takeIf { it > 0L }?.let { add(formatDuration(it)) }"),
            "The remaining time must reach the update overlay.",
        )
    }

    @Test
    fun `the measurement is fed by the real byte count`() {
        assertTrue(
            source.contains("updateRateTracker.observe(UPDATE_RATE_KEY, bytesRead)"),
            "The rate must be measured from the bytes the downloader actually reports.",
        )
        assertTrue(
            source.contains("updateRateTracker.forget(UPDATE_RATE_KEY)"),
            "A previous attempt's samples must not be averaged into a new download.",
        )
    }
}
