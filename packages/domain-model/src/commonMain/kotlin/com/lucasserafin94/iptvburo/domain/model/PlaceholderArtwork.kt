package com.lucasserafin94.iptvburo.domain.model

/**
 * Recognises the one cover a provider hands out for thousands of titles at once.
 *
 * Adult catalogues in particular ship a single generic card — a red "XXX ADULT" stamp — as the
 * artwork for every row. Technically each item has a cover, so nothing downstream ever reaches its
 * fallback, and the grid draws the same picture hundreds of times. A viewer cannot tell one title
 * from another by looking, which is what artwork is for.
 *
 * ## Why counting is the right test
 *
 * Measured on a real 42,000-title list: 52,201 covers, 30,301 of them distinct, and **one** address
 * used 10,353 times. The next most repeated appeared six times. There is no middle ground to get
 * wrong — a real cover belongs to one film, or to a handful when a provider carries the same title
 * at several qualities.
 *
 * So this does not try to recognise a picture, or match a list of known addresses that would go
 * stale the moment a provider changed theirs. It counts, on the catalogue actually loaded, and a
 * cover shared by more titles than any real one could be is treated as no cover at all.
 */
object PlaceholderArtwork {
    /**
     * How many titles must share one address before it stops counting as artwork.
     *
     * Well above what duplication explains: a provider listing the same film in 4K, HD and SD, in
     * two dubbings, across three category prefixes still reaches only a dozen or so. Well below the
     * ten thousand a real placeholder reaches. The gap between the two is three orders of
     * magnitude, so the exact number here is not delicate.
     */
    const val SHARED_COVER_THRESHOLD = 25

    /**
     * The addresses in [artworkUrls] that are placeholders rather than covers.
     *
     * Computed once per catalogue load and handed to the screens, rather than asked per card: the
     * grid draws hundreds of cards and would otherwise count the whole catalogue for each one.
     */
    fun detect(artworkUrls: Sequence<String?>): Set<String> {
        val counts = mutableMapOf<String, Int>()
        artworkUrls.forEach { url ->
            val trimmed = url?.trim().orEmpty()
            if (trimmed.isNotEmpty()) counts[trimmed] = (counts[trimmed] ?: 0) + 1
        }
        return counts
            .asSequence()
            .filter { (_, count) -> count >= SHARED_COVER_THRESHOLD }
            .map { (url, _) -> url }
            .toSet()
    }
}
