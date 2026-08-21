package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.download.formatBytes
import com.lucasserafin94.iptvburo.desktop.download.formatDuration
import com.lucasserafin94.iptvburo.desktop.download.formatRate
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Numbers shown to a person must not change shape with the machine's regional settings.
 *
 * `%d` and `%f` follow the default locale's *digits*, not merely its decimal separator. On Windows
 * set to Egypt, Iran, Bangladesh or Myanmar the playback clock rendered as `١:٠٥:٠٩` — Arabic-Indic
 * numerals inside an interface with no Arabic translation, on a build whose text was Portuguese.
 *
 * Hex is deliberately excluded: `%x` is ASCII in every locale, so digests and percent-encoding were
 * never at risk, and asserting otherwise would be inventing a bug to guard.
 */
class DisplayNumbersLocaleTest {
    private val exotic =
        listOf("ar-EG", "fa-IR", "bn-BD", "my-MM", "de-DE", "en-US", "pt-BR")
            .map(Locale::forLanguageTag)

    private fun <T> underEachLocale(block: () -> T): List<T> {
        val original = Locale.getDefault()
        return try {
            exotic.map { locale ->
                Locale.setDefault(locale)
                block()
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `sizes rates and durations read the same everywhere`() {
        val sizes = underEachLocale { formatBytes(1_572_864L) }
        assertEquals(1, sizes.distinct().size, "formatBytes drifted with the locale: $sizes")
        assertEquals("1,5 MB", sizes.first())

        val rates = underEachLocale { formatRate(4L * 1024 * 1024) }
        assertEquals(1, rates.distinct().size, "formatRate drifted with the locale: $rates")

        val durations = underEachLocale { formatDuration(3_725L) }
        assertEquals(1, durations.distinct().size, "formatDuration drifted with the locale: $durations")
    }

    @Test
    fun `every ASCII digit survives the exotic locales`() {
        // The failure is visual rather than arithmetic, so this looks at the characters themselves.
        underEachLocale { formatBytes(1_572_864L) }.forEach { rendered ->
            assertTrue(
                rendered.none { it.isDigit() && it !in '0'..'9' },
                "Non-ASCII digits reached the interface: $rendered",
            )
        }
    }

    /**
     * The clocks live in composables that need a running player to reach, so this reads the source.
     *
     * Weaker than calling them, and deliberately so: the alternative is a rendering harness whose
     * own fakes would be what the assertion actually tested.
     */
    @Test
    fun `the playback clocks name the display locale`() {
        val files =
            listOf(
                "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt",
                "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt",
                "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/ContinueWatchingWorkspace.kt",
            )
        files.forEach { relative ->
            val source = Path.of(relative).readText()
            val clockCalls = Regex(""""%0?2?d?:%02d(:%02d)?"\.format\(([^)]*)\)""").findAll(source)
            clockCalls.forEach { match ->
                assertTrue(
                    match.groupValues[2].startsWith("DISPLAY_LOCALE"),
                    "A clock in $relative formats without DISPLAY_LOCALE: ${match.value}",
                )
            }
        }
    }
}
