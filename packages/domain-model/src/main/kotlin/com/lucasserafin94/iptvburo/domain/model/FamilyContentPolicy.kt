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
            ?.replace(Regex("\\p{M}+"), "")
            ?.lowercase(Locale.ROOT)
            ?.replace(Regex("[^a-z0-9+]+"), " ")
            ?.trim()
            .orEmpty()
}
