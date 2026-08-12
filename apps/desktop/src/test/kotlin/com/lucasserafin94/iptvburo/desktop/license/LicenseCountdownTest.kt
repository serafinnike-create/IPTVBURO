package com.lucasserafin94.iptvburo.desktop.license

import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason
import com.lucasserafin94.iptvburo.domain.model.LicenseDecision
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The countdown shown in the header.
 *
 * Somebody who finds out their trial ended by discovering the app locked has been ambushed. The
 * arithmetic behind "7 dias" is small but it is the difference between an ending that was expected
 * and one that was not, so it is pinned here — particularly the rounding, where an off-by-one shows
 * "0 dias" beside an app that still works.
 */
class LicenseCountdownTest {

    private fun allowed(remaining: Duration?, isTrial: Boolean) =
        LicenseStatus(
            decision = LicenseDecision.Allowed(remaining = remaining, isTrial = isTrial),
            deviceId = "FP86-XARB-9JZW",
        )

    @Test
    fun `a full trial reports seven days`() {
        assertEquals(7L, allowed(Duration.ofDays(7), isTrial = true).daysRemaining)
    }

    /**
     * Part of a day counts as a day.
     *
     * Rounded up, because a licence with eleven hours left has a day remaining, not zero — and
     * "0 dias" next to a working app reads as a fault rather than as a warning.
     */
    @Test
    fun `a partial day rounds up`() {
        assertEquals(1L, allowed(Duration.ofHours(11), isTrial = true).daysRemaining)
        assertEquals(1L, allowed(Duration.ofMinutes(5), isTrial = true).daysRemaining)
        assertEquals(2L, allowed(Duration.ofHours(25), isTrial = true).daysRemaining)
    }

    @Test
    fun `an exact number of days does not gain one`() {
        // ofDays(2) is exactly 48 hours: it must be 2, not 3.
        assertEquals(2L, allowed(Duration.ofDays(2), isTrial = true).daysRemaining)
        assertEquals(30L, allowed(Duration.ofDays(30), isTrial = false).daysRemaining)
    }

    @Test
    fun `a two year licence counts in days`() {
        // 730 days rather than "2 years": a number somebody can act on, all the way down.
        assertEquals(730L, allowed(Duration.ofDays(730), isTrial = false).daysRemaining)
        assertEquals(359L, allowed(Duration.ofDays(359), isTrial = false).daysRemaining)
    }

    @Test
    fun `an elapsed duration is zero rather than negative`() {
        // Between the licence lapsing and the next check, the remaining time is negative. Showing
        // "-1 dias" would be worse than saying nothing.
        assertEquals(0L, allowed(Duration.ofHours(-5), isTrial = true).daysRemaining)
    }

    @Test
    fun `a licence with no end date reports nothing rather than zero`() {
        assertNull(allowed(remaining = null, isTrial = false).daysRemaining)
    }

    @Test
    fun `a blocked licence has no countdown`() {
        val blocked = LicenseStatus(
            decision = LicenseDecision.Blocked(LicenseBlockReason.TRIAL_ENDED),
            deviceId = "FP86-XARB-9JZW",
        )

        assertNull(blocked.daysRemaining)
        assertNull(blocked.remaining)
        assertEquals(LicenseBlockReason.TRIAL_ENDED, blocked.blockReason)
    }

    @Test
    fun `a trial is distinguishable from a paid licence`() {
        assertTrue(allowed(Duration.ofDays(7), isTrial = true).isTrial)
        assertTrue(!allowed(Duration.ofDays(730), isTrial = false).isTrial)
    }

    /**
     * A paid licence is silent until its final month.
     *
     * A permanent countdown on something already paid for is nagging, and for twenty-three of the
     * twenty-four months it carries no information. Thirty days is enough warning to renew without
     * being hurried.
     */
    @Test
    fun `the chip hides a paid licence with time to spare`() {
        val source = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/LicenseChip.kt")
            .readText()

        assertTrue(
            source.contains("PAID_WARNING_DAYS = 30L"),
            "a paid licence should stay quiet until its last month",
        )
        assertTrue(
            source.contains("if (!status.isTrial && days > PAID_WARNING_DAYS) return"),
            "the early return is what keeps the header clear",
        )
    }

    @Test
    fun `a trial always shows, however long is left`() {
        val source = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/LicenseChip.kt")
            .readText()

        // The hiding rule must be conditional on *not* being a trial. A trial that only appeared in
        // its final days would be the ambush this exists to prevent.
        assertTrue(source.contains("!status.isTrial &&"), "a trial must never be hidden")
    }

    @Test
    fun `the countdown is translated into every language`() {
        val strings = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/DesktopStrings.kt")
            .readText()

        // Derived from the enum, not a literal four.
        //
        // This said "four" and counted multiples of four, so adding Spanish broke it — for the
        // wrong reason. A test that has to be edited every time a language is added is a test that
        // gets edited without being read.
        val languages = com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage.entries.size

        for (key in listOf("trialDaysLeft", "licenseDaysLeft", "licenseLastDay", "trialLastDay")) {
            val uses = Regex("""\b$key = "[^"]+"""").findAll(strings).count()
            // A multiple rather than exactly one per language: `licenseDaysLeft` exists in both
            // LicenseStrings and SettingsStrings, so it legitimately appears twice per language.
            // What matters is that none is missing — which a count not divisible by the number of
            // languages would reveal.
            assertTrue(
                uses > 0 && uses % languages == 0,
                "$key appears $uses times across $languages languages; one is missing",
            )
        }
    }

    @Test
    fun `the day count strings carry a placeholder`() {
        val strings = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/DesktopStrings.kt")
            .readText()

        // Formatted with String.format, so a translation that dropped the %d would print a sentence
        // with no number in it — and would do so only in that one language.
        Regex("""(trialDaysLeft|licenseDaysLeft) = "([^"]+)"""").findAll(strings).forEach { match ->
            assertTrue(
                match.groupValues[2].contains("%d"),
                "${match.groupValues[1]} is missing its %d: ${match.groupValues[2]}",
            )
        }
    }
}
