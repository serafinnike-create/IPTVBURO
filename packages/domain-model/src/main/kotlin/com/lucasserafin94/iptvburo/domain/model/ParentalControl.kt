package com.lucasserafin94.iptvburo.domain.model

/**
 * Which categories are locked behind a PIN, and whether a given one is.
 *
 * The point is a household where a child uses the same machine. It is not a security boundary — a
 * four-digit PIN in a desktop app protects against a curious child, not against someone determined,
 * and pretending otherwise would be worse than being clear about it.
 *
 * Two things are deliberately separate. A **Kids profile** removes adult content from the catalogue
 * entirely, so it is never seen; a **locked category** stays visible and asks for the PIN. The first
 * is for a profile the child uses, the second for the parent's own profile with the child in the
 * room.
 */
data class ParentalLock(
    /** Category ids the user chose to lock. Empty means nothing is locked. */
    val lockedCategoryIds: Set<String> = emptySet(),
    /**
     * Whether categories the provider marks as adult are locked without being listed.
     *
     * On by default once a PIN exists: a provider adds categories over time, and a lock that only
     * covered what was there when it was set would quietly stop covering new ones.
     */
    val lockAdultCategories: Boolean = true,
) {
    val isConfigured: Boolean
        get() = lockedCategoryIds.isNotEmpty() || lockAdultCategories

    /**
     * Whether [categoryId] needs the PIN.
     *
     * [categoryName] is checked against the adult vocabulary only when [lockAdultCategories] is on;
     * an explicitly listed category is locked whatever it is called.
     */
    fun requiresPin(
        categoryId: String?,
        categoryName: String?,
    ): Boolean {
        if (categoryId != null && categoryId in lockedCategoryIds) return true
        return lockAdultCategories && categoryName != null && looksAdult(categoryName)
    }

    private companion object {
        /**
         * Words providers use to name adult categories.
         *
         * Matched on whole words against a normalised name, so "Adulto" locks and "Adultos e
         * Jovens" locks, while a film called "Adult World" in a general category does not — the
         * check is on the category, never on the title.
         */
        val ADULT_WORDS =
            setOf(
                "adulto", "adultos", "adult", "xxx", "porn", "porno", "pornografia",
                "erotico", "erotic", "sexy", "sex", "+18", "18+",
            )

        fun looksAdult(name: String): Boolean {
            val normalised =
                name
                    .lowercase()
                    .replace('á', 'a').replace('ã', 'a').replace('â', 'a')
                    .replace('é', 'e').replace('ê', 'e')
                    .replace('í', 'i')
                    .replace('ó', 'o').replace('õ', 'o')
                    .replace('ú', 'u')
                    .replace('ç', 'c')

            // Split on anything that is not a letter or digit, so "FILMES | ADULTO" and
            // "Canais(Adulto)" both surface the word.
            val words = normalised.split(Regex("[^a-z0-9+]+")).filter(String::isNotBlank)
            return words.any { word ->
                // The plural too, without listing every form: providers write "Adulto" and
                // "Adultos", "Erótico" and "Eróticos", and a list that missed the plural would
                // leave exactly the categories the parent meant to lock wide open.
                word in ADULT_WORDS || (word.endsWith("s") && word.dropLast(1) in ADULT_WORDS)
            }
        }
    }
}

/**
 * A four-digit PIN, stored as a salted hash.
 *
 * Never kept in the clear. It is a weak secret by construction — four digits, ten thousand
 * possibilities — but a user who reuses digits across their life should not have them sitting in a
 * preferences file in plain text.
 */
data class ParentalPin(
    val salt: String,
    val hash: String,
) {
    /** Whether [candidate] is the PIN this was made from. */
    fun matches(candidate: String): Boolean = hashOf(candidate, salt) == hash

    companion object {
        const val LENGTH = 4

        /** Whether [value] could be a PIN at all: exactly four digits. */
        fun isWellFormed(value: String): Boolean =
            value.length == LENGTH && value.all(Char::isDigit)

        /**
         * Creates a PIN from [value], or null when it is not four digits.
         *
         * [salt] is supplied rather than generated here so the domain stays free of a random
         * source; callers pass a fresh random value per PIN.
         */
        fun of(
            value: String,
            salt: String,
        ): ParentalPin? {
            if (!isWellFormed(value)) return null
            return ParentalPin(salt = salt, hash = hashOf(value, salt))
        }

        /**
         * Salted SHA-256.
         *
         * Not a password hash — no work factor — because the input space is four digits and any
         * work factor an app can afford is brute-forced in seconds regardless. The salt is what
         * matters here: it stops the same PIN producing the same stored value across profiles.
         */
        private fun hashOf(
            value: String,
            salt: String,
        ): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(salt.toByteArray(Charsets.UTF_8))
            digest.update(value.toByteArray(Charsets.UTF_8))
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
