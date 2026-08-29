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
     * A question mark between two letters, which is where an accent used to be.
     *
     * Real punctuation does not sit inside a word: "quê?" and "O que é isto?" end a sentence and
     * are followed by a space or nothing. `est?` and `s?rie` cannot be anything but damage.
     *
     * Deliberately narrow. A synopsis that genuinely asks a question — "Quem matou o pai dele?" —
     * has to survive, and a rule that counted every question mark would throw it away.
     */
    private val ACCENT_LOST = Regex("""[\p{L}]\?(?=[\p{L}])""")

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
}
