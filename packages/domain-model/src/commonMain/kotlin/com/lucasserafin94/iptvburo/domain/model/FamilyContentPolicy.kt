package com.lucasserafin94.iptvburo.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative local guard used when a provider does not expose dependable age ratings.
 * It blocks only explicit adult labels; it never claims that unlabelled content is child-safe.
 */
object FamilyContentPolicy {
    private val blockedTokens =
        setOf(
            "18+",
            "adult",
            "adults",
            "erotic",
            "erotico",
            "hentai",
            "porn",
            "porno",
            "pornografia",
            "sex",
            "sexo",
            "xxx",
        )

    fun isExplicitAdultLabel(value: String?): Boolean {
        val normalized = value.normalizedSafetyWords()
        if (normalized.isBlank()) return false
        return blockedTokens.any { token ->
            token == "18+" && normalized.contains("18+") ||
                token != "18+" && normalized.split(' ').any { word -> word == token }
        }
    }

    fun isAllowedForKids(title: String, categoryNames: Iterable<String?> = emptyList()): Boolean =
        !isExplicitAdultLabel(title) && categoryNames.none(::isExplicitAdultLabel)

    private fun String?.normalizedSafetyWords(): String =
        this
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            ?.replace(COMBINING_MARKS, "")
            ?.lowercase(Locale.ROOT)
            ?.replace(NON_WORD_RUNS, " ")
            ?.trim()
            .orEmpty()

    /**
     * Compiled once, not per call.
     *
     * `isAllowedForKids` runs for the title *and* every category of every row, and the catalogue
     * paging loop calls it for each of tens of thousands of items whenever a Kids profile browses.
     * Compiling two patterns inside that call meant tens of thousands of Regex constructions per
     * page turn — and a page is turned on every keystroke in the search box.
     */
    private val COMBINING_MARKS = Regex("""\p{M}+""")

    private val NON_WORD_RUNS = Regex("[^a-z0-9+]+")
}
