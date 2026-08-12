package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The walkthrough for obtaining a TMDb key, in every language the app ships.
 *
 * The settings screen used to offer one link, straight to `themoviedb.org/settings/api` — a page
 * that cannot be reached without an account and cannot be used without knowing that "Developer" is
 * the right answer to a form asking for an application URL. Anyone who had never registered a key
 * stopped there, which is the state the app's own metadata features start from.
 */
class TmdbGuideStringsTest {
    private fun steps(strings: DesktopStrings) =
        listOf(
            strings.tmdbGuide.tmdbStep1Title to strings.tmdbGuide.tmdbStep1Body,
            strings.tmdbGuide.tmdbStep2Title to strings.tmdbGuide.tmdbStep2Body,
            strings.tmdbGuide.tmdbStep3Title to strings.tmdbGuide.tmdbStep3Body,
            strings.tmdbGuide.tmdbStep4Title to strings.tmdbGuide.tmdbStep4Body,
            strings.tmdbGuide.tmdbStep5Title to strings.tmdbGuide.tmdbStep5Body,
            strings.tmdbGuide.tmdbStep6Title to strings.tmdbGuide.tmdbStep6Body,
        )

    @Test
    fun `every language carries all six steps`() {
        DesktopLanguage.entries.forEach { language ->
            val strings = DesktopStrings.of(language)

            steps(strings).forEachIndexed { index, (title, body) ->
                assertTrue(title.isNotBlank(), "$language step ${index + 1} has no title")
                assertTrue(body.isNotBlank(), "$language step ${index + 1} has no body")
            }
        }
    }

    /**
     * The steps are distinct from one another.
     *
     * A copy-paste that left two steps identical would produce a guide that reads as six steps and
     * teaches four — and every emptiness check above would still pass.
     */
    @Test
    fun `no two steps say the same thing`() {
        DesktopLanguage.entries.forEach { language ->
            val bodies = steps(DesktopStrings.of(language)).map { (_, body) -> body }

            assertTrue(
                bodies.toSet().size == bodies.size,
                "$language repeats a step: $bodies",
            )
        }
    }

    /**
     * The answers that trip people up are spelled out.
     *
     * "Developer" rather than "Commercial" is the choice that decides whether the request is
     * approved, and the key's name on the page is what somebody is hunting for. A guide that
     * described the journey without naming these two would leave the user exactly where they were.
     */
    @Test
    fun `the guide names the developer option and the key itself`() {
        DesktopLanguage.entries.forEach { language ->
            val strings = DesktopStrings.of(language)
            val everything = steps(strings).joinToString(" ") { (title, body) -> "$title $body" }

            assertTrue(
                "Developer" in everything,
                "$language never tells the user to choose Developer over Commercial",
            )
            assertTrue(
                "v3" in everything,
                "$language never names the field the key is copied from",
            )
            assertTrue(
                "themoviedb.org" in everything,
                "$language never says where to go",
            )
        }
    }

    /**
     * The entry point is present and reads as a question, not a label.
     *
     * It sits beside a link that already goes to TMDb, so if it did not distinguish itself the two
     * would look like the same control twice.
     */
    @Test
    fun `every language has a way in and a way out`() {
        DesktopLanguage.entries.forEach { language ->
            val strings = DesktopStrings.of(language)

            listOf(
                "tmdbGuideButton" to strings.tmdbGuide.tmdbGuideButton,
                "tmdbGuideTitle" to strings.tmdbGuide.tmdbGuideTitle,
                "tmdbGuideSubtitle" to strings.tmdbGuide.tmdbGuideSubtitle,
                "tmdbGuideOpenSignup" to strings.tmdbGuide.tmdbGuideOpenSignup,
                "tmdbGuideOpenApiPage" to strings.tmdbGuide.tmdbGuideOpenApiPage,
            ).forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "$language is missing $name")
            }

            assertTrue(
                strings.tmdbGuide.tmdbGuideButton != strings.metadataKeyHint,
                "$language uses the same text for the guide and the direct link",
            )
        }
    }

    /**
     * Nothing in the guide promises the key is anything but the customer's own.
     *
     * The key is stored on their machine and sent only to TMDb; the last step says so, and this
     * keeps that promise from being edited away.
     */
    @Test
    fun `the final step says where the key is kept`() {
        DesktopLanguage.entries.forEach { language ->
            val body = DesktopStrings.of(language).tmdbGuide.tmdbStep6Body.lowercase()

            // Each language's own word for the machine. Spanish says "ordenador" where Portuguese
            // says "computador", and a list that assumed the two were spelled alike failed the
            // moment Spanish was added — correctly, because the promise was what was being checked.
            assertTrue(
                listOf("computador", "computer", "ordenador", "rechner").any { word -> word in body },
                "$language does not tell the user the key stays on their machine: $body",
            )
        }
    }
}
