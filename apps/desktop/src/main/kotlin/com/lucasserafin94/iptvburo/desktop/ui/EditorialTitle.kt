package com.lucasserafin94.iptvburo.desktop.ui

/**
 * Cleans provider decoration out of a title for display.
 *
 * Providers append quality, codec and language markers directly to the name, so the catalogue was
 * showing entries like `Uma Carta à Minha Juventude 4K [DV][HDR]`. At card width the decoration ate
 * the part the user actually reads and pushed the real title into an ellipsis.
 *
 * Two rules, deliberately conservative:
 *
 * - bracketed groups are dropped only when short, so `Movie [Director's Cut]` survives while
 *   `[DV]`, `[HDR]`, `[L]` do not;
 * - bare markers are dropped only from the **end** of the title, because a word like `4K` in the
 *   middle is far more likely to belong to the work than to be a tag.
 */
fun String.editorialTitle(): String {
    var result = replace(BRACKETED_TAG, " ").replace(WHITESPACE, " ").trim()

    // Trailing markers are stripped repeatedly: providers stack them, e.g. "Movie 4K DUAL HDR".
    var changed = true
    while (changed) {
        changed = false
        val trimmed = result.trimEnd(' ', '-', '·', '|', '.')
        val lastWord = trimmed.substringAfterLast(' ', "")
        if (lastWord.isNotEmpty() && lastWord.lowercase() in TRAILING_MARKERS) {
            result = trimmed.substringBeforeLast(' ', trimmed).trim()
            changed = true
        } else {
            result = trimmed
        }
    }
    return result.ifBlank { trim() }
}

private val BRACKETED_TAG = Regex("""\s*\[[^]]{1,12}]\s*""")
private val WHITESPACE = Regex("""\s+""")

/**
 * Markers that are decoration when they end a title.
 *
 * Kept narrow on purpose. Anything ambiguous — a year, a number, a roman numeral — is left alone,
 * since removing part of a real title is a worse failure than leaving a tag visible.
 */
private val TRAILING_MARKERS =
    setOf(
        "4k", "uhd", "hd", "fhd", "sd", "hdr", "hdr10", "dv", "sdr",
        "h264", "h265", "hevc", "x264", "x265", "avc", "aac", "ac3", "dts", "atmos",
        "dub", "dubbed", "dublado", "leg", "legendado", "sub", "subbed",
        "dual", "multi", "remux", "bluray", "webrip", "webdl", "web", "cam",
        "l", "d", "s", "ptbr", "pt", "br", "en", "es", "lat", "latino",
        "1080p", "1080", "720p", "720", "480p", "2160p", "2160",
    )
