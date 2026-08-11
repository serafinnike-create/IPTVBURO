package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every language the app offers is actually translated.
 *
 * A missing translation does not fail to compile — the field simply holds another language's text —
 * so the only thing that catches it is a check that reads every string of every language. Adding
 * Spanish is what made this worth writing: 370 fields is far too many to eyeball, and the two tests
 * that did fail were failing for reasons unrelated to the translation itself.
 */
class EveryLanguageCompleteTest {
    /**
     * Words that belong to exactly one language, used to spot text left behind from another.
     *
     * Deliberately narrow. Spanish and Portuguese share most of their vocabulary, so a broad list
     * would flag correct translations — "música", "catálogo" and "favoritos" are identical in both.
     * These are forms that exist in one and not the other.
     */
    private val portugueseOnly =
        listOf("você", "não ", "ção", "ções", "aplicativo", "arquivo", "usuário", "assistir")

    private val spanishOnly = listOf("¿", "¡", "ñ", "usted", "ordenador", "pulsa ")

    /**
     * Every string in a language, including the nested holders.
     *
     * Java reflection rather than Kotlin's. `kotlin-reflect` is not on the test classpath, and
     * adding a dependency so one test can read property names would be a poor trade — the getters
     * are ordinary Java methods and carry everything needed here.
     *
     * Read reflectively rather than named one by one: 370 fields would go stale on the first
     * addition, and a check nobody updates passes for the wrong reason.
     */
    private fun everyString(holder: Any): List<String> =
        buildList {
            holder.javaClass.methods
                .filter { method -> method.parameterCount == 0 && method.name.startsWith("get") }
                .sortedBy { method -> method.name }
                .forEach { method ->
                    when (val value = runCatching { method.invoke(holder) }.getOrNull()) {
                        is String -> add(value)
                        is SettingsStrings -> addAll(everyString(value))
                        is LicenseStrings -> addAll(everyString(value))
                        is TmdbGuideStrings -> addAll(everyString(value))
                        else -> Unit
                    }
                }
        }

    @Test
    fun `no language is left holding another language's text`() {
        DesktopLanguage.entries.forEach { language ->
            val values = everyString(DesktopStrings.of(language)).map(String::lowercase)

            val foreign =
                when (language) {
                    DesktopLanguage.SPANISH ->
                        values.filter { value -> portugueseOnly.any { word -> word in value } }
                    DesktopLanguage.PORTUGUESE_BRAZIL ->
                        values.filter { value -> spanishOnly.any { word -> word in value } }
                    else -> emptyList()
                }

            assertTrue(
                foreign.isEmpty(),
                "$language holds text from another language:\n" +
                    foreign.take(8).joinToString("\n") { value -> "  $value" },
            )
        }
    }

    /**
     * Nothing is blank in any language.
     *
     * A field left empty renders as a gap where a label should be, and only in the language nobody
     * on the project reads.
     */
    @Test
    fun `the check actually reads the strings`() {
        val count = everyString(DesktopStrings.of(DesktopLanguage.SPANISH)).size
        assertTrue(count > 300, "only $count strings were read; the reflection is not finding them")
    }

    @Test
    fun `no language has an empty string`() {
        DesktopLanguage.entries.forEach { language ->
            val blanks = everyString(DesktopStrings.of(language)).count(String::isBlank)

            assertTrue(blanks == 0, "$language has $blanks blank strings")
        }
    }

    /**
     * Every language carries roughly the same amount of text.
     *
     * A block copied from another language and half-translated still has the right field count; the
     * total length is what reveals it. Translations legitimately differ by some margin — German runs
     * long, English short — so the bound is generous and only catches something badly wrong.
     */
    @Test
    fun `no language is conspicuously shorter than the rest`() {
        val totals =
            DesktopLanguage.entries.associateWith { language ->
                everyString(DesktopStrings.of(language)).sumOf(String::length)
            }
        val median = totals.values.sorted()[totals.size / 2]

        totals.forEach { (language, total) ->
            assertTrue(
                total > median / 2,
                "$language totals $total characters against a median of $median — likely incomplete",
            )
        }
    }

    /**
     * Format placeholders survive translation.
     *
     * A dropped `%d` prints a sentence with no number in it, and does so only in the one language
     * that lost it — the kind of fault that reaches a customer before it reaches a developer.
     */
    @Test
    fun `placeholder counts match across languages`() {
        val reference = DesktopStrings.of(DesktopLanguage.PORTUGUESE_BRAZIL)
        val referencePlaceholders = everyString(reference).sumOf { value -> value.split("%d").size - 1 }

        DesktopLanguage.entries.forEach { language ->
            val count = everyString(DesktopStrings.of(language)).sumOf { it.split("%d").size - 1 }

            assertTrue(
                count == referencePlaceholders,
                "$language has $count %d placeholders against $referencePlaceholders in Portuguese",
            )
        }
    }
}
