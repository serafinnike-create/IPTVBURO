package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The JVM will not load a class whose constructor takes more than 254 arguments.
 *
 * This is not a theoretical limit here. DesktopStrings grew past it once: the code compiled, the
 * tests that existed passed, the MSI built, and the installed app died at class load with
 * `ClassFormatError: Too many arguments in method signature` — 151 MB resident instead of 550 MB,
 * no window, no message. A shipped app that would not start.
 *
 * The rule that came out of it is that new strings go into a grouped class such as SettingsStrings
 * rather than onto DesktopStrings itself. This test enforces that rule instead of trusting anyone
 * to remember it, and fails while there is still headroom rather than at the cliff edge.
 */
class StringsConstructorLimitTest {
    /**
     * Counted through Java reflection, not Kotlin's.
     *
     * kotlin-reflect is not on the runtime classpath and adding a megabyte of library to count
     * parameters would be a poor trade. Java's view is also the more faithful one: the limit is a
     * JVM rule about the descriptor, which is exactly what this reads.
     */
    private fun constructorParameterCount(type: Class<*>): Int =
        type.declaredConstructors.maxOf { constructor -> constructor.parameterCount }

    @Test
    fun `DesktopStrings stays clear of the JVM constructor limit`() {
        val parameters = constructorParameterCount(DesktopStrings::class.java)

        assertTrue(
            parameters <= SAFE_CEILING,
            "DesktopStrings has $parameters constructor parameters. The JVM refuses to load a " +
                "class above $JVM_LIMIT, and crossing it shipped an app that would not start. " +
                "Put new strings in SettingsStrings or another grouped class instead of adding " +
                "them here.",
        )
    }

    /** Every grouped class is subject to the same ceiling; none is a place to hide the problem. */
    @Test
    fun `SettingsStrings stays clear of the JVM constructor limit`() {
        val parameters = constructorParameterCount(SettingsStrings::class.java)

        assertTrue(parameters <= SAFE_CEILING, "SettingsStrings has $parameters constructor parameters.")
    }

    /**
     * The failure was at class load, so the only conclusive check is loading it.
     *
     * Every language is built, because they are separate call sites and a limit crossed by one is
     * crossed by all of them.
     */
    @Test
    fun `every language actually constructs`() {
        DesktopLanguage.entries.forEach { language ->
            val built = DesktopStrings.of(language)
            assertTrue(
                built.settingsText.firstRunTitle.isNotBlank(),
                "$language built but is missing first-run copy",
            )
        }
    }

    private companion object {
        const val JVM_LIMIT = 254

        /**
         * Deliberately below the real limit.
         *
         * A test that only fails at 255 fails on the commit that breaks the build, which is too
         * late to be useful; this leaves room to notice and regroup first.
         */
        const val SAFE_CEILING = 240
    }
}

