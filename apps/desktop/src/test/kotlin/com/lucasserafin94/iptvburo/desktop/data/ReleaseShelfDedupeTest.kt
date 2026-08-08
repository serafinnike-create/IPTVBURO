package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.shelfDeduplicationKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Lançamentos" shelf must not show the same film twice.
 *
 * Deduplication by normalised title was added once and the shelf still showed pairs. The normaliser
 * strips accents, punctuation, case and a fixed list of quality words — which covers "Duna" against
 * "Duna 4K" — but a provider distinguishes its copies in other ways too: a language marker in
 * brackets, a channel prefix, a trailing tag. Those survive normalisation, so the copies survive
 * the filter.
 *
 * The cases below are the shapes that got through, transcribed from what the shelf displayed.
 * Fixtures are synthetic; the titles are real film names because the failure is about how they are
 * decorated, not about which films they are.
 */
class ReleaseShelfDedupeTest {
    /** What the shelf does. */
    private fun dedupe(names: List<String>): List<String> =
        names.distinctBy { name -> name.shelfDeduplicationKey() }

    /** These the existing normaliser already handles; they are here so a fix cannot regress them. */
    @Test
    fun `accents and case and punctuation already collapse`() {
        val collapsed =
            dedupe(
                listOf(
                    "Authentic Games: No Império Desconectado",
                    "Authentic Games: No Imperio Desconectado",
                    "Noah Kahan: Out of Body",
                    "Noah Kahan: Out Of Body",
                ),
            )

        assertEquals(2, collapsed.size, "was $collapsed")
    }

    @Test
    fun `quality markers already collapse`() {
        assertEquals(1, dedupe(listOf("Enola Holmes 3", "Enola Holmes 3 4K", "Enola Holmes 3 HD")).size)
    }

    /**
     * The shapes that still get through, and the reason the shelf still shows pairs.
     *
     * Each pair is one film. A filter that leaves any of them as two entries is not doing its job.
     */
    @Test
    fun `provider decoration that survives normalisation is still one film`() {
        val pairs =
            listOf(
                "a bracketed language tag" to listOf("Enola Holmes 3", "Enola Holmes 3 [L]"),
                "a bracketed dubbing tag" to listOf("Enola Holmes 3", "Enola Holmes 3 [D]"),
                "a trailing single-letter tag" to listOf("Kyle Larson e o Double", "Kyle Larson e o Double L"),
                "a parenthesised year" to listOf("Noah Kahan: Out of Body", "Noah Kahan: Out of Body (2026)"),
                "a leading channel prefix" to listOf("Enola Holmes 3", "NETFLIX | Enola Holmes 3"),
                "a trailing pipe tag" to listOf("Enola Holmes 3", "Enola Holmes 3 | DUAL"),
            )

        val failures =
            pairs.filter { (_, names) -> dedupe(names).size != 1 }

        assertTrue(
            failures.isEmpty(),
            "These are the same film listed twice, and the shelf shows both:\n" +
                failures.joinToString("\n") { (why, names) ->
                    "  $why: ${names.joinToString(" | ")} -> ${dedupe(names)}"
                },
        )
    }

    /** And the filter must not fuse films that merely look alike. */
    @Test
    fun `different films are not collapsed`() {
        assertEquals(2, dedupe(listOf("Enola Holmes 2", "Enola Holmes 3")).size)
        assertEquals(2, dedupe(listOf("Duna", "Duna: Parte Dois")).size)
        assertEquals(2, dedupe(listOf("Alien", "Aliens")).size)
    }
}
