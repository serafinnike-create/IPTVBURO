package com.lucasserafin94.iptvburo.domain.model

/**
 * Where to send a user for a title on a given service.
 *
 * The discovery catalogue does not supply per-service destinations — TMDb states plainly that it
 * returns no deep links — so the app has to build them. That makes this a **maintained guess**:
 * these are the services' own public search and browse URLs, and a service that reorganises its
 * site will break its entry here without breaking anything else.
 *
 * Because it is a guess, it degrades rather than fails. Every lookup ends somewhere useful:
 *
 * 1. the service's own search for the title, when the pattern is known and still works;
 * 2. the service's homepage, when it is not;
 * 3. the catalogue's own fallback page, when the service is unknown entirely.
 *
 * What it never does is fabricate a *title* URL — `netflix.com/title/12345` with an id the app
 * invented would 404 on a real company's site. Searching for the name is honest about being a
 * search, and lands the user in the right place with one click.
 *
 * No deep links into native apps. A custom scheme that resolves to nothing shows an OS error the
 * user cannot act on, and there is no way to test the installed-app case from here.
 */
object ProviderDeepLinks {
    /**
     * A destination for [title] on [providerId], or null when nothing better than the catalogue's
     * own fallback is known.
     *
     * [providerId] is the app's own slug, not TMDb's numeric id: the numbers are JustWatch's and
     * would tie this table to their identifiers.
     */
    fun searchUrlFor(
        providerId: String,
        title: String,
    ): String? {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null
        val pattern = SEARCH_PATTERNS[StreamingProvider.normaliseId(providerId)] ?: return null
        return pattern + encodeQuery(cleanTitle)
    }

    /** The service's homepage, used when no search pattern is known. */
    fun homepageFor(providerId: String): String? = HOMEPAGES[StreamingProvider.normaliseId(providerId)]

    /**
     * The best destination available for [title] on [providerId].
     *
     * Falls back through search, then homepage, then [catalogueFallbackUrl] — the discovery
     * catalogue's own page for the title, which always exists even when nothing here matches.
     */
    fun bestTargetFor(
        providerId: String,
        title: String,
        catalogueFallbackUrl: String? = null,
    ): ExternalLaunchTarget? {
        val destination =
            searchUrlFor(providerId, title)
                ?: homepageFor(providerId)
                ?: catalogueFallbackUrl
                ?: return null
        return ExternalLaunchTarget(webUrl = destination, providerId = providerId)
    }

    /**
     * Percent-encodes a title for a query string.
     *
     * Written out rather than delegated to a URL encoder so this file stays free of platform
     * dependencies. Space becomes `%20` rather than `+`: it is correct in a path or a query, while
     * `+` is only correct in a query and reads as a literal plus elsewhere.
     */
    private fun encodeQuery(value: String): String =
        buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val character = byte.toInt().toChar()
                if (character.isLetterOrDigit() && byte.toInt() in 0..127 || character in UNRESERVED) {
                    append(character)
                } else {
                    append('%').append("%02X".format(byte))
                }
            }
        }

    private const val UNRESERVED = "-_.~"

    /**
     * Search URLs, by the app's own provider slug.
     *
     * Kept small on purpose. Every entry is a promise the app is making about someone else's site,
     * and an entry that silently starts 404ing is worse than no entry — the homepage fallback at
     * least always works.
     */
    private val SEARCH_PATTERNS: Map<String, String> =
        mapOf(
            "netflix" to "https://www.netflix.com/search?q=",
            "prime-video" to "https://www.primevideo.com/search/ref=atv_nb_sr?phrase=",
            "disney-plus" to "https://www.disneyplus.com/search?q=",
            "apple-tv" to "https://tv.apple.com/search?term=",
            "google-play" to "https://play.google.com/store/search?c=movies&q=",
            "hbo-max" to "https://play.max.com/search?q=",
            "globoplay" to "https://globoplay.globo.com/busca/?q=",
            "paramount-plus" to "https://www.paramountplus.com/search/?q=",
        )

    private val HOMEPAGES: Map<String, String> =
        mapOf(
            "netflix" to "https://www.netflix.com/",
            "prime-video" to "https://www.primevideo.com/",
            "disney-plus" to "https://www.disneyplus.com/",
            "apple-tv" to "https://tv.apple.com/",
            "google-play" to "https://play.google.com/store/movies",
            "hbo-max" to "https://play.max.com/",
            "globoplay" to "https://globoplay.globo.com/",
            "paramount-plus" to "https://www.paramountplus.com/",
        )
}
