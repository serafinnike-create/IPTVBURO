package com.lucasserafin94.iptvburo.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * User-facing wording lives in the `strings.xml` tables of every `values` locale directory, not
 * in the code that draws it.
 *
 * The app offers five languages and every `strings.xml` is complete — but a resource table only
 * covers what is referenced from it, so a Portuguese sentence written straight into a screen was
 * invisible to every check. Several had accumulated: the details pages' loading and error lines,
 * the Watch button, the credits block, the home rails' editorial labels. Somebody running the app
 * in English met Portuguese at exactly the moments that matter most, and nothing in the suite
 * noticed.
 *
 * This reads the source instead, the same blunt instrument the desktop module's
 * `NoHardcodedTextTest` uses: what is being checked is the *absence* of something, in files that
 * compile and pass every behavioural test regardless.
 */
class NoHardcodedTextTest {
    private val sourceRoot = File("src/main/kotlin")

    /**
     * Files whose remaining Portuguese literals are known and justified.
     *
     * Every entry is a promise rather than an excuse. Shrinking this list is the work; growing it
     * needs a reason written down beside the name, and the second test below makes that deliberate.
     */
    private val pending =
        setOf(
            // `HomeLabels.Fallback` is the Portuguese wording used by tests and previews, which
            // have no resources to resolve; the app itself always passes labels already resolved
            // from `strings.xml` in the user's language. The fallback is what keeps the pure
            // catalog builder testable, so translating it would be churn, not coverage.
            "RealHomeCatalog.kt",
            // The language picker runs *before* any locale is applied, so string resources would
            // resolve to the device language — exactly what the user is there to change. Each
            // line is deliberately repeated in every supported language instead.
            "LanguageSelectionScreen.kt",
        )

    /**
     * Words that mark a literal as Portuguese *prose*.
     *
     * Deliberately narrow, and tuned against what a first attempt got wrong on the desktop:
     * matching "com" flagged every preferences path, and matching a bare accent flagged "comédia"
     * inside a category rule and "Português (Brasil)" in the language picker, which is a
     * language's own name and must never be translated.
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
     * screen passed while the same sentence spoken in full would have failed — exactly how the
     * home rails' editorial labels stayed Portuguese.
     */
    private val portugueseSingleWord =
        Regex(""""(filme|filmes|série|séries|ao vivo)"""", RegexOption.IGNORE_CASE)

    /**
     * Files whose quoted words match provider category names instead of being shown.
     *
     * `CategoryBadge` compares lowercase words against what the provider called a category —
     * "filme", "ao vivo" — to pick an icon. Those words are data keys, not display text, and
     * translating them would break the match. Kept apart from [pending] because they are a
     * permanent shape, not work waiting to happen.
     */
    private val keywordFiles =
        setOf(
            "CategoryBadge.kt",
        )

    @Test
    fun `no screen holds its own Portuguese wording`() {
        val offenders = mutableListOf<String>()

        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name in pending || it.name in keywordFiles }
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
            "User-facing text must live in res/values*/strings.xml so every language has it:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * And the list of exemptions does not quietly grow.
     *
     * A pending list that can be extended without anyone noticing is not a plan, it is a place to
     * hide things. This fails as soon as the count rises, which forces the addition to be argued
     * for.
     */
    @Test
    fun `the pending list only shrinks`() {
        assertTrue(
            "The pending list has grown to ${pending.size}. Move the wording instead of adding to it.",
            pending.size <= PENDING_CEILING,
        )
    }

    private companion object {
        /** What it was when the list was written. Lower it as files are cleared; never raise it. */
        const val PENDING_CEILING = 2
    }
}
