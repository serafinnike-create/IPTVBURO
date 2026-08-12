package com.lucasserafin94.iptvburo.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The loading banner appears only when something is loading.
 *
 * Switching the type filter set `LoadingCatalog` unconditionally, then cleared it a frame later —
 * so moving between films and series with both catalogues already in memory flashed a progress
 * banner for work that never happened. On the favourites screen, where the switch is used most,
 * that read as the app blinking.
 *
 * The cost of getting this wrong is not cosmetic: an indicator that appears when there is no
 * progress teaches the viewer that it means nothing, and the next time it is real they ignore it.
 */
class LoadingIndicatorHonestyTest {
    private val source: String =
        Files.readString(Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt"))

    private val switchBody: String =
        source
            .substringAfter("suspend fun selectXtreamContentType")
            .substringBefore("\n    /**")

    @Test
    fun `switching to an already loaded catalogue does not raise the banner`() {
        assertTrue(
            "alreadyLoaded" in switchBody,
            "the switch no longer distinguishes a cached catalogue from one that must be fetched",
        )
        assertTrue(
            Regex("""if \(!alreadyLoaded\) xtreamStatus = XtreamStatus\.LoadingCatalog""")
                .containsMatchIn(switchBody),
            "LoadingCatalog is set unconditionally again, which flashes the banner on every switch",
        )
    }

    /**
     * The same value decides both the banner and the fetch.
     *
     * Two separate checks would drift: the banner would be shown for a fetch that does not happen,
     * or a fetch would run with no indication at all. One value cannot disagree with itself.
     */
    @Test
    fun `the same condition governs the banner and the fetch`() {
        val fetchBranch = switchBody.substringAfter("runCatching").substringBefore("onSuccess")

        assertTrue(
            "alreadyLoaded" in fetchBranch,
            "the fetch decides for itself whether the catalogue is present, so it can disagree " +
                "with the banner about whether anything is happening",
        )
    }

    /**
     * A real fetch still says so.
     *
     * The failure this guards against is the opposite one: quietly removing the indicator would
     * leave a customer watching a still screen for the seconds a 40,000-item catalogue takes.
     */
    @Test
    fun `a catalogue that must be fetched still shows the banner`() {
        assertTrue(
            "XtreamStatus.LoadingCatalog(contentType)" in switchBody,
            "nothing tells the user a genuine catalogue load is under way",
        )
    }
}
