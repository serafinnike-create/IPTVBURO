package com.lucasserafin94.iptvburo.domain.model


/**
 * Conservative local guard used when a provider does not expose dependable age ratings.
 * It blocks only explicit adult labels; it never claims that unlabelled content is child-safe.
 */
object FamilyContentPolicy {
    private val blockedTokens =
        setOf(
            "18+",
            // Both orders, because provider lists carry both and only one was here.
            "+18",
            "adult",
            "adults",
            // The Portuguese was missing while the English was here, so a Brazilian list — where
            // the category is written "Adulto" — passed a Kids profile untouched. ParentalControl
            // had it; this did not, and the two run over the same categories.
            "adulto",
            "adultos",
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
        val words = normalized.split(' ')
        return blockedTokens.any { token ->
            if (token == "18+" || token == "+18") {
                normalized.contains(token)
            } else {
                // The plural too, so a list writing "Eroticos" is caught without every form being
                // spelled out here.
                words.any { word -> word == token || (word.endsWith("s") && word.dropLast(1) == token) }
            }
        }
    }

    /**
     * Categories a Kids profile should not be shown, beyond the explicitly adult ones.
     *
     * Horror and thriller are here because a parent who turns on a Kids profile does not expect it
     * to offer them, and because these are the words real provider lists actually use — in
     * Portuguese, English and Spanish, since one list often mixes all three.
     *
     * Kept apart from [blockedTokens] on purpose. Adult is adult in every context; horror is a
     * genre a grown-up may well want, and the PIN lock is about the first while this is only about
     * the Kids profile. Merging them would lock horror behind the PIN for everybody.
     */
    private val kidsOnlyBlockedTokens =
        setOf(
            "terror",
            "horror",
            "suspense",
            "thriller",
            "gore",
            "slasher",
        )

    fun isAllowedForKids(title: String, categoryNames: Iterable<String?> = emptyList()): Boolean =
        !isExplicitAdultLabel(title) &&
            categoryNames.none(::isExplicitAdultLabel) &&
            !isKidsUnsuitableLabel(title) &&
            categoryNames.none(::isKidsUnsuitableLabel)

    /**
     * Whether this names a genre a Kids profile should not see.
     *
     * Matched on whole words against the same normalised form the adult check uses, so "Terror"
     * and "TERROR | 4K" both match while a film called "Suspense na Escola" in a children's
     * category does not — the check reaches titles as well as categories, and matching a fragment
     * would take out anything containing "terr".
     */
    fun isKidsUnsuitableLabel(value: String?): Boolean {
        val normalized = value.normalizedSafetyWords()
        if (normalized.isBlank()) return false
        val words = normalized.split(' ')
        return kidsOnlyBlockedTokens.any { token ->
            words.any { word ->
                // The plural too, without listing every form: providers write "Terror" and
                // "Terrores", "Thriller" and "Thrillers".
                word == token || (word.endsWith("s") && word.dropLast(1) == token)
            }
        }
    }

    private fun String?.normalizedSafetyWords(): String =
        this
            ?.let(::decomposeForFolding)
            ?.replace(COMBINING_MARKS, "")
            ?.lowercase()
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
