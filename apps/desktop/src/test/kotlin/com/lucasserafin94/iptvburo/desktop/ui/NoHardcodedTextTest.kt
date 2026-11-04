package com.lucasserafin94.iptvburo.desktop.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * User-facing wording lives in the translation tables, not in the code that draws it.
 *
 * The app offers five languages, and `EveryLanguageCompleteTest` checks that each one is complete —
 * but it reads `DesktopStrings` and only that, so a Portuguese sentence written straight into a
 * screen was invisible to it. Ninety-three of them had accumulated: failure messages, loading stages,
 * the player's own labels. Somebody running the app in English met Portuguese at exactly the moments
 * that matter most, and nothing in the suite noticed.
 *
 * This reads the source instead. It is a blunt instrument and the right one: what is being checked is
 * the *absence* of something, in files that compile and pass every behavioural test regardless.
 */
class NoHardcodedTextTest {
    private val sourceRoot = File("src/main/kotlin/com/lucasserafin94/iptvburo/desktop")

    /**
     * Files whose remaining literals are known and not yet moved.
     *
     * Every entry is a promise rather than an excuse. Shrinking this list is the work; growing it
     * needs a reason written down beside the name, and the second test below makes that deliberate.
     */
    private val pending = emptySet<String>()

    // WebServiceStrings.kt is a table like PlayerText.kt: every language side by side, kept out of
    // DesktopStrings so one small feature does not grow each of its five constructors.
    private val TRANSLATION_TABLES = setOf("DesktopStrings.kt", "PlayerText.kt", "WebServiceStrings.kt", "SyncStrings.kt", "SetupServiceStrings.kt")

    /**
     * Words that mark a literal as Portuguese *prose*.
     *
     * Deliberately narrow, and tuned against what a first attempt got wrong. Matching "com" flagged
     * every preferences path — `com/lucasserafin94/iptvburo/user-v1` — and matching a bare accent
     * flagged "comédia" inside a category rule and "Português (Brasil)" in the language picker, which
     * is a language's own name and must never be translated.
     *
     * Prose is what needs translating, so the test looks for prose: a sentence carries a verb or a
     * function word, and an identifier does not.
     */
    private val portuguese =
        Regex(
            """\b(não|nao|você|voce|foi|será|sera|está|esta|pode|precisa|tente|verifique|""" +
                """nenhum|nenhuma|possível|possivel|aplicativo|arquivo|servidor|catálogo|catalogo)\b""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * One-word Portuguese labels, which the sentence test above cannot see.
     *
     * It needs a space and six characters, so a bare "Filme" or "Série" written straight into a
     * screen passed while the same sentence spoken in full would have failed. Found exactly that
     * way in the search results: every other label on the screen was translated and these three
     * were not, in a file the suite called clean.
     */
    private val portugueseSingleWord =
        Regex(""""(filme|filmes|série|séries|ao vivo)"""", RegexOption.IGNORE_CASE)

    /**
     * Files whose quoted words match provider category names instead of being shown.
     *
     * `CategoryLabel` and `CategoryBadge` compare lowercase words against what the provider called
     * a category — "filme", "ao vivo" — to pick an icon or a clean name. Those words are data keys,
     * not display text, and translating them would break the match. Kept apart from [pending]
     * because they are a permanent shape, not work waiting to happen.
     */
    private val keywordFiles =
        setOf(
            "CategoryLabel.kt",
            "CategoryBadge.kt",
        )

    @Test
    fun `no screen holds its own Portuguese wording`() {
        val offenders = mutableListOf<String>()

        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // The two translation tables. PlayerText carries the player's words in every language,
            // and FilmDropAndTextTest fails if any language is missing one.
            .filterNot { it.name in TRANSLATION_TABLES || it.name in pending || it.name in keywordFiles }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    // Comments explain the code and may quote what the user sees; they are not shown.
                    if (trimmed.startsWith("*") || trimmed.startsWith("//")) return@forEachIndexed
                    // A log line is read by whoever is debugging, not by a user.
                    if (trimmed.contains("println(")) return@forEachIndexed

                    Regex("\"([^\"\\\$\n]{6,})\"").findAll(line).forEach { match ->
                        val literal = match.groupValues[1]
                        // A path, a URL or a single word is an identifier, not a sentence.
                        val looksLikeProse =
                            literal.contains(' ') &&
                                !literal.startsWith("http") &&
                                !literal.contains('/')
                        if (looksLikeProse && portuguese.containsMatchIn(literal)) {
                            offenders += "${file.name}:${index + 1}  \"$literal\""
                        }
                    }
                    // The same defect one word long: the sentence test needs a space and six
                    // characters, and "Filme" has neither.
                    portugueseSingleWord.find(line)?.let { match ->
                        offenders += "${file.name}:${index + 1}  ${match.value}"
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "User-facing text must live in DesktopStrings so every language has it:\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * And the list of exemptions does not quietly grow.
     *
     * A pending list that can be extended without anyone noticing is not a plan, it is a place to
     * hide things. This fails as soon as the count rises, which forces the addition to be argued for.
     */
    @Test
    fun `the pending list only shrinks`() {
        assertTrue(
            pending.size <= PENDING_CEILING,
            "The pending list has grown to ${pending.size}. Move the wording instead of adding to it.",
        )
    }

    private companion object {
        /**
         * What it was when the list was written. Lower it as files are cleared; never raise it.
         *
         * Zero since the demo catalogue moved to the test source set: it was the last entry, and a
         * file only tests read never needed translating in the first place.
         */
        const val PENDING_CEILING = 0
    }
}
