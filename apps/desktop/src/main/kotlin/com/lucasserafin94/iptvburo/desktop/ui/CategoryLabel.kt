package com.lucasserafin94.iptvburo.desktop.ui

/**
 * The part of a provider category name that actually distinguishes it.
 *
 * Providers namespace their categories by section — `Filmes | Lançamentos`, `SÉRIES - Netflix`,
 * `CANAIS: Esportes`. Inside the Films tab every chip then began with the same word, so a rail of
 * twenty categories spent most of its width repeating what the tab already said and the names were
 * pushed off the right edge.
 *
 * Only a leading section prefix is dropped, and only when something is left afterwards: a category
 * genuinely called `Filmes` keeps its name.
 */
fun String.categoryLabel(): String {
    var label = trim()

    var strippedBySeparator = false
    val separator = label.indexOfFirst { it in SEPARATORS }
    if (separator > 0) {
        val head = label.take(separator).trim()
        val tail = label.drop(separator + 1).trim()
        if (tail.any(Char::isLetterOrDigit) && head.normalisedForSection() in SECTION_WORDS) {
            label = tail
            strippedBySeparator = true
        }
    }

    // Some providers use no separator at all, only a prefix: "FILMES LANÇAMENTOS".
    //
    // Only when the separator pass found nothing to remove. "Canais | Filmes e Séries" names one
    // category whose own name begins with a section word, and running both passes over it left
    // "e Séries" — a chip that reads as a fragment because it is one.
    val firstSpace = if (strippedBySeparator) -1 else label.indexOf(' ')
    if (firstSpace > 0) {
        val head = label.take(firstSpace).normalisedForSection()
        val tail = label.drop(firstSpace + 1).trim()
        // The tail must carry a word. "Filmes |" is a malformed name from the provider, not a
        // prefix plus a category, and stripping it would leave a chip labelled "|".
        if (tail.any(Char::isLetterOrDigit) && head in SECTION_WORDS) {
            label = tail
        }
    }

    return label.ifBlank { trim() }
}

/** Lower-cased and stripped of the accents and punctuation providers are inconsistent about. */
private fun String.normalisedForSection(): String =
    lowercase()
        .replace('á', 'a').replace('ã', 'a').replace('â', 'a')
        .replace('é', 'e').replace('ê', 'e')
        .replace('í', 'i')
        .replace('ó', 'o').replace('õ', 'o').replace('ô', 'o')
        .replace('ú', 'u')
        .replace('ç', 'c')
        .trim { !it.isLetterOrDigit() }

private val SEPARATORS = charArrayOf('|', ':', '»', '›', '/')

private val SECTION_WORDS =
    setOf(
        "filme", "filmes", "movie", "movies", "vod",
        "serie", "series", "serie s", "tv shows", "novelas",
        "canal", "canais", "channel", "channels", "live", "ao vivo", "aovivo",
    )
