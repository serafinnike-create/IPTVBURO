package com.lucasserafin94.iptvburo.domain.model

/**
 * A shareable reference to a title, and the two URL forms it travels as.
 *
 * Sharing a title is not sharing a stream. What crosses the wire here is *which film*, expressed
 * exactly the way [ContentIdentity] expresses it — a normalised title, a kind and a year. The
 * recipient's app resolves that against **their own** playlist. Nothing about the sender's provider
 * is transmitted, and the link is worthless to anyone without a source of their own.
 *
 * That distinction is the whole security model of this feature, so it is enforced here rather than
 * trusted to callers:
 *
 * - the poster is accepted only from a public metadata host ([isPublicArtwork]) — the provider's own
 *   artwork URL sits on the subscriber's server, frequently carries the credentials in its path, and
 *   would leak the account to every recipient of the message;
 * - [ContentIdentity] is a slug, not a locator: it cannot be turned back into a stream URL;
 * - the plot is public catalogue copy, and is length-capped so a share cannot become a data channel.
 *
 * Two forms are produced from one [TitleShareLink]:
 *
 * - [webUrl] is what gets pasted into WhatsApp. It renders a preview with the poster, and it works
 *   for a recipient who does not have the app — they get the page and a download button.
 * - [appUri] is the `iptvburo://` form the installed app is registered for. The web page redirects
 *   to it, which is what makes an installed app open straight onto the title.
 */
data class TitleShareLink(
    val identity: ContentIdentity,
    /** Display title, as the sender sees it. Presentation only; matching runs off [identity]. */
    val title: String,
    val year: Int?,
    /** Public poster URL, or null. Never a provider-hosted image — see [isPublicArtwork]. */
    val artworkUrl: String?,
    /** Public catalogue synopsis, trimmed to [MAX_DESCRIPTION]. */
    val description: String?,
) {
    /** The https link to paste into a message. Renders a preview and works without the app. */
    fun webUrl(baseUrl: String = DEFAULT_BASE_URL): String =
        buildString {
            append(baseUrl.trimEnd('/'))
            append("/t/?")
            append(queryString())
        }

    /** The custom-scheme form the installed app handles. */
    fun appUri(): String = "$APP_SCHEME://title?${queryString()}"

    private fun queryString(): String =
        buildList {
            add("id" to identity.key)
            add("t" to title)
            year?.let { add("y" to it.toString()) }
            artworkUrl?.let { add("img" to it) }
            description?.let { add("d" to it) }
        }.joinToString("&") { (key, value) -> "$key=${encodeComponent(value)}" }

    companion object {
        const val APP_SCHEME = "iptvburo"
        const val DEFAULT_BASE_URL = "https://iptvburo.pages.dev"

        /**
         * Cap on the shared synopsis.
         *
         * Long enough for a real plot paragraph, short enough that the link stays pasteable and that
         * the field cannot be repurposed to smuggle a payload through a share.
         */
        const val MAX_DESCRIPTION = 400

        /**
         * Hosts whose images may be included in a share.
         *
         * An allowlist rather than a denylist, because the thing being excluded — the subscriber's
         * own Xtream server — has no fixed hostname. Anything not positively known to be public
         * metadata is dropped, which fails closed: the recipient sees a preview without a poster,
         * rather than the sender publishing their provider's address.
         */
        private val PUBLIC_ARTWORK_HOSTS =
            setOf(
                "image.tmdb.org",
                "www.themoviedb.org",
                "themoviedb.org",
            )

        /**
         * Builds a link, dropping anything that must not travel.
         *
         * Returns null only when there is no title to share at all. A provider-hosted poster is not
         * an error — it is simply omitted, because a share without a poster is still useful and
         * still safe.
         */
        fun of(
            identity: ContentIdentity,
            title: String,
            year: Int? = null,
            artworkUrl: String? = null,
            description: String? = null,
        ): TitleShareLink? {
            val cleanTitle = title.trim()
            if (cleanTitle.isEmpty()) return null
            return TitleShareLink(
                identity = identity,
                title = cleanTitle,
                year = year,
                artworkUrl = artworkUrl?.trim()?.takeIf { isPublicArtwork(it) },
                description =
                    description
                        ?.replace(WHITESPACE_RUN, " ")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { plot ->
                            if (plot.length <= MAX_DESCRIPTION) {
                                plot
                            } else {
                                plot.take(MAX_DESCRIPTION).trimEnd() + "…"
                            }
                        },
            )
        }

        /**
         * True when [url] is an https image on a known public metadata host.
         *
         * https is required as well as the host: an http URL to the same host would still be a
         * cleartext request the recipient's client makes, and a scheme check is what stops
         * `iptvburo://` or `file://` being smuggled into the poster slot.
         */
        fun isPublicArtwork(url: String): Boolean {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return false
            val afterScheme = trimmed.removePrefix("https://").removePrefix("HTTPS://")
            // Anything before the first '/', '?' or '#' is the authority. Userinfo (`user:pass@`)
            // is rejected outright rather than parsed past: a URL carrying credentials must not be
            // shared even if the host after the '@' is on the allowlist.
            val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
            if ('@' in authority) return false
            val host = authority.substringBefore(':').lowercase()
            return host in PUBLIC_ARTWORK_HOSTS
        }

        /**
         * Reads back a link produced by [webUrl] or [appUri].
         *
         * Accepts either form so one routine serves both the protocol handler and a pasted https
         * link. The same artwork rule is applied on the way in: a link that arrives carrying a
         * non-public image — hand-edited, or built by some future version — has it dropped rather
         * than fetched, so a malicious share cannot make the recipient's app call an arbitrary host.
         */
        fun parse(raw: String): TitleShareLink? {
            val query = raw.substringAfter('?', "").substringBefore('#')
            if (query.isEmpty()) return null
            val fields =
                query
                    .split('&')
                    .mapNotNull { pair ->
                        if ('=' !in pair) return@mapNotNull null
                        val key = pair.substringBefore('=')
                        val value = decodeComponent(pair.substringAfter('='))
                        if (key.isEmpty() || value.isEmpty()) null else key to value
                    }.toMap()

            val identity =
                fields["id"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ContentIdentity(it) }
                    ?: return null
            return of(
                identity = identity,
                title = fields["t"] ?: return null,
                year = fields["y"]?.toIntOrNull(),
                artworkUrl = fields["img"],
                description = fields["d"],
            )
        }

        private val WHITESPACE_RUN = Regex("""\s+""")
    }
}

/**
 * Percent-encodes one query value.
 *
 * Written out rather than delegated to `URLEncoder`, which encodes a space as `+`. That form is
 * correct for a form body and wrong in the path-adjacent query these links use, where a title
 * containing a literal `+` and one containing a space would decode identically.
 */
private fun encodeComponent(value: String): String =
    buildString {
        value.encodeToByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            if (char.isUnreservedInQuery()) {
                append(char)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr 4) and 0xF])
                append(HEX[byte.toInt() and 0xF])
            }
        }
    }

private fun decodeComponent(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char == '%' && index + 2 < value.length -> {
                val hex = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (hex == null) {
                    bytes.add(char.code.toByte())
                    index++
                } else {
                    bytes.add(hex.toByte())
                    index += 3
                }
            }
            // Decoded for tolerance of links written by other tools, even though `encodeComponent`
            // never produces one.
            char == '+' -> {
                bytes.add(' '.code.toByte())
                index++
            }
            else -> {
                char.toString().encodeToByteArray().forEach(bytes::add)
                index++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

private fun Char.isUnreservedInQuery(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-_.~"

private const val HEX = "0123456789ABCDEF"
