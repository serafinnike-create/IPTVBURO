package com.lucasserafin94.iptvburo.desktop.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The loading bar must never sit still while the app is working.
 *
 * Reported as "barra de carregamento fica parada em 80%". It did: `loadDailyHome` announced three
 * fixed milestones — 0.75, 0.88, 0.96 — and the jump from the first to the second spanned the entire
 * catalogue download, which on a forty-thousand-title list is tens of seconds. The figure was
 * accurate and useless: a bar parked on a number is indistinguishable from a crashed app, and the
 * user cannot tell whether waiting is worth it.
 *
 * Two properties fix that, and both are asserted here because each is useless alone:
 *
 *  - the catalogue download reports as it runs, so there is something to move;
 *  - when nothing has moved for a moment, the screen stops claiming a percentage and shows an
 *    animated sweep instead — which is honest about not knowing rather than lying about being stuck.
 *
 * Read from the source rather than rendered, because what is under test is a timing behaviour over
 * seconds: a composition test would have to sleep through the very stall it is checking for, and
 * would then assert on an animation frame rather than on the rule.
 */
class SplashNeverFrozenTest {
    private fun read(path: String) =
        File(path).also { assertTrue(it.isFile, "Expected to find $path") }.readText()

    private val splash =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/SplashScreen.kt")

    private val state =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")

    private val repository =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/SessionXtreamRepository.kt")

    /** The catalogue download reports while it runs, rather than only before and after. */
    @Test
    fun `the catalogue download reports progress as it parses`() {
        assertTrue(
            repository.contains("CATALOG_PROGRESS_ITEM_INTERVAL"),
            "The catalogue parse reports nothing while it runs; the bar has nothing to move on.",
        )
        assertTrue(
            state.contains("onCatalogueItems"),
            "loadDailyHome does not receive item progress from the catalogue load.",
        )
    }

    /**
     * And it is throttled.
     *
     * An event per item over 41,698 items costs more than the parse it describes, which would trade
     * a frozen bar for a slower start — a worse deal than the defect.
     */
    @Test
    fun `progress reporting is throttled`() {
        assertTrue(
            repository.contains("seen % CATALOG_PROGRESS_ITEM_INTERVAL == 0"),
            "Progress is reported per item, which costs more than the work it reports on.",
        )
    }

    /** The splash knows when the bar last moved. */
    @Test
    fun `the splash tracks when progress last changed`() {
        assertTrue(
            splash.contains("beatAtMillis"),
            "The splash cannot tell a stalled bar from a moving one.",
        )
        assertTrue(
            state.contains("var startupBeatAt"),
            "Nothing records when progress last moved.",
        )
    }

    /**
     * And stops showing a percentage once it stalls.
     *
     * This is the reported defect stated as a rule: no frozen number. The sweep animation above the
     * bar carries on regardless, so the screen still reads as working.
     */
    @Test
    fun `a stalled bar stops claiming a percentage`() {
        assertTrue(
            splash.contains("STALL_AFTER_MILLIS"),
            "There is no stall threshold; the bar can sit on a figure indefinitely.",
        )
        val percentBlock = splash.substringAfter("detail.isNotBlank() ->").substringBefore("},")
        assertTrue(
            percentBlock.contains("stalled ->"),
            "The percentage is printed regardless of whether it is still moving.",
        )
    }

    /**
     * The stall check re-evaluates on a timer.
     *
     * Keyed on the progress value alone it would never fire during the stall it exists to detect —
     * the case that matters is exactly the one where no new value arrives.
     */
    @Test
    fun `the stall check does not depend on progress arriving`() {
        val effect = splash.substringAfter("LaunchedEffect(beatAtMillis)").substringBefore("}")

        assertTrue(
            effect.contains("delay(STALL_AFTER_MILLIS)"),
            "The stall is detected by waiting, not by a value that will never arrive.",
        )
    }
}
