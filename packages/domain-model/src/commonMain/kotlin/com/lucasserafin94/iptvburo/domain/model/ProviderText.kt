package com.lucasserafin94.iptvburo.domain.model

/**
 * Whether a provider's text arrived with its accents already destroyed.
 *
 * Some providers store their catalogue in a single-byte encoding and convert it badly on the way
 * out, so every accented letter reaches us as a question mark: "O tenente Marion Cobretti est? no
 * centro de uma s?rie de assassinatos", "carteiras de motoristas ca?adas por viola??es de tr?nsito".
 *
 * This is not a decoding fault on our side and cannot be undone by decoding differently — the bytes
 * that carried the letters are gone before we ever see them. What is left is the choice between
 * showing a paragraph the viewer has to decipher and showing nothing.
 *
 * Only the paragraph is affected. Titles come through the same path and are clean on the lists this
 * was found on, so the film is still named correctly; it is the synopsis that arrives damaged.
 */
object ProviderText {
    /**
     * A question mark standing where an accent used to be.
     *
     * Two shapes, because the damage takes two. Inside a word — `s?rie`, `tra?do`, `exterm?nio` —
     * and at the end of one that a lower-case word follows: `est? sendo`, `est? no`. A sentence
     * genuinely ending in a question is followed by a capital or nothing, so "Quem matou o pai
     * dele? Ninguém sabe." scores nothing.
     *
     * Matching only inside a word was not enough: "Um escritor que acredita que est? sendo tra?do"
     * scored one and went through, which is how a damaged synopsis reached the banner after the
     * first attempt at this.
     */
    private val ACCENT_LOST = Regex("""\p{L}\?(?=\p{L})|\p{L}\?(?= \p{Ll})""")

    /**
     * How many such marks before the text is judged unusable.
     *
     * Two rather than one. A single hit can be a typo in an otherwise readable paragraph, and
     * discarding a good synopsis is its own harm; by two the pattern is the encoding, not the
     * writer.
     */
    private const val DAMAGE_THRESHOLD = 2

    /** Whether [text] carries enough lost accents to be worth hiding. */
    fun isDamaged(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return ACCENT_LOST.findAll(text).take(DAMAGE_THRESHOLD).count() >= DAMAGE_THRESHOLD
    }

    /**
     * [text], or null when it arrived damaged.
     *
     * Null so the caller falls back to whatever it already shows when a provider sends no
     * description at all — a path that exists and reads properly, rather than a new kind of error
     * message about encodings, which would tell the viewer nothing they can act on.
     */
    fun usableOrNull(text: String?): String? = text?.takeUnless { isDamaged(it) }

    /**
     * The words a provider's broken encoding turns into question marks, and what they should be.
     *
     * A short label cannot be hidden the way a paragraph can: a fact line reading
     * "Lançamento 2022-08-02 • Thriller, A??o" is ugly, but dropping the genre outright leaves a
     * line with a hole in it and tells the viewer even less. These are the handful of category
     * names an IPTV catalogue actually carries, so the damage is repairable rather than merely
     * detectable.
     *
     * Deliberately a fixed list and not a guess. Restoring accents by rule would put them in words
     * the provider spelled correctly, and a wrong accent is worse than a question mark: one reads
     * as a broken feed, the other as the app not knowing the language.
     */
    private val KNOWN_LABELS =
        listOf(
            "A??o" to "Ação",
            "A?ão" to "Ação",
            "Aç?o" to "Ação",
            "Fic??o cient?fica" to "Ficção científica",
            "Fic??o Cient?fica" to "Ficção Científica",
            "Fic??o" to "Ficção",
            "Anima??o" to "Animação",
            "Com?dia" to "Comédia",
            "Fam?lia" to "Família",
            "Fantas?a" to "Fantasia",
            "Hist?ria" to "História",
            "M?sica" to "Música",
            "Myst?rio" to "Mistério",
            "Mist?rio" to "Mistério",
            "Rom?ntico" to "Romântico",
            "Document?rio" to "Documentário",
            "Guerra e Pol?tica" to "Guerra e Política",
            "Cinema TV" to "Cinema TV",
        )

    /**
     * A short label with its accents put back, where they are known.
     *
     * For genres, countries and the like — anything printed as a fact rather than read as prose.
     * Words this does not recognise are returned untouched: a question mark is honest about a
     * provider's encoding, and inventing a spelling is not.
     */
    fun repairLabel(text: String?): String? {
        val value = text?.takeIf(String::isNotBlank) ?: return text
        if (!value.contains('?')) return value
        return KNOWN_LABELS.fold(value) { repaired, (broken, correct) ->
            repaired.replace(broken, correct)
        }
    }
}
