package com.lucasserafin94.iptvburo.domain.model

import java.text.Normalizer

/**
 * Provider-independent identity for a piece of content.
 *
 * User data — favourites, watch progress, downloads — must survive the user replacing their
 * playlist. Keying it on the provider's numeric id does not achieve that, and fails in two
 * different ways:
 *
 * - **Progress** was keyed on `sourceId`, a hash of server + username. A new list produces a new
 *   hash, so every entry is orphaned. The rows stay on disk and are simply never found again.
 * - **Favourites** were keyed on `contentType:providerId` with no source. That looks like it
 *   survives, but `providerId` is the provider's internal numbering: two unrelated lists reuse the
 *   same numbers for different films. Switching lists therefore did not lose favourites, it
 *   silently marked *the wrong titles* as favourite — worse than losing them.
 *
 * The identity here is derived from what the content actually is: its normalised title plus year.
 * Two providers carrying the same film agree on that; two different films do not collide on it.
 */
@JvmInline
value class ContentIdentity(val key: String) {
    init {
        require(key.isNotBlank()) { "A content identity cannot be blank." }
    }

    companion object {
        /**
         * Builds an identity from a display title and optional year.
         *
         * [kind] separates namespaces so a film and a series sharing a name never collide.
         */
        fun of(
            kind: ContentKind,
            title: String,
            year: Int? = null,
        ): ContentIdentity {
            val slug = slugify(title)
            // A title that normalises to nothing (emoji-only, punctuation-only) would collide with
            // every other such title, so fall back to a stable hash of the raw text instead.
            val stem = slug.ifEmpty { "raw${title.trim().hashCode().toUInt().toString(16)}" }
            return ContentIdentity(
                buildString {
                    append(kind.name.lowercase())
                    append(':')
                    append(stem)
                    if (year != null) {
                        append(':')
                        append(year)
                    }
                },
            )
        }

        /**
         * Reduces a provider title to a comparable stem.
         *
         * Providers decorate titles heavily and inconsistently — `[4K] Movie (2021) DUAL` and
         * `Movie 2021 HD` are the same film. Stripping the decoration is what lets the same title
         * from two different lists resolve to one identity.
         */
        fun slugify(title: String): String {
            val withoutBrackets =
                title
                    .replace(BRACKETED, " ")
                    .replace(PARENTHESISED_YEAR, " ")
            val decomposed = Normalizer.normalize(withoutBrackets, Normalizer.Form.NFD)
            val asciiFolded = COMBINING_MARKS.replace(decomposed, "")
            val lowered = asciiFolded.lowercase()
            val tokens =
                lowered
                    .split(NON_ALPHANUMERIC)
                    .filter { token -> token.isNotEmpty() && token !in NOISE_TOKENS }
            return tokens.joinToString("-")
        }

        /** Year embedded in the title, e.g. `Movie (1998)`. Used when the provider omits `year`. */
        fun yearFromTitle(title: String): Int? =
            PARENTHESISED_YEAR
                .find(title)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf { it in PLAUSIBLE_YEARS }

        private val BRACKETED = Regex("""\[[^]]*]""")
        private val PARENTHESISED_YEAR = Regex("""\((\d{4})\)""")
        private val COMBINING_MARKS = Regex("""\p{Mn}+""")
        private val NON_ALPHANUMERIC = Regex("""[^a-z0-9]+""")
        private val PLAUSIBLE_YEARS = 1_888..2_100

        /**
         * Quality, language and packaging markers. Removed so the same film tagged differently by
         * two providers still resolves to one identity.
         */
        private val NOISE_TOKENS =
            setOf(
                "4k", "uhd", "hd", "sd", "fhd", "hdr", "hdr10", "dolby", "atmos", "vision",
                "1080p", "1080", "720p", "720", "480p", "2160p", "2160",
                "h264", "h265", "hevc", "x264", "x265", "avc", "aac", "ac3", "dts",
                "dub", "dubbed", "dublado", "leg", "legendado", "sub", "subbed", "subtitulado",
                "dual", "multi", "audio", "remux", "bluray", "webrip", "webdl", "web",
                "l", "d", "ptbr", "pt", "br", "en", "es", "lat", "latino", "vo", "vose",
            )
    }
}

/** Namespace for [ContentIdentity]. Keeps a film and a series of the same name distinct. */
enum class ContentKind {
    MOVIE,
    SERIES,
    EPISODE,
    LIVE,
    /** Appended to preserve the persisted ordinal/name order of every existing video kind. */
    UNKNOWN,
}
