package com.lucasserafin94.iptvburo.webdav

/**
 * One entry in a WebDAV directory: a folder to walk into, or a file to play.
 *
 * [href] is the path the server returned, relative to the host. Deliberately not a full URL: the
 * address that reaches the server carries the subscriber's credentials, and this type is passed
 * around, logged and held in lists. The credential is added back only at the moment a request is
 * made, which is the same rule the rest of this app follows for playback URLs.
 */
data class WebDavEntry(
    val href: String,
    val displayName: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val contentType: String? = null,
) {
    override fun toString(): String =
        "WebDavEntry(displayName=$displayName, isDirectory=$isDirectory, href=<redacted>)"
}

/**
 * Reads a `PROPFIND` response into entries.
 *
 * ## Why a hand-written reader rather than an XML library
 *
 * The response is a `D:multistatus` document with one `D:response` per entry, and the three things
 * this app needs from each — the href, the display name, and whether it is a collection — are
 * always plain text inside known tags. A full XML parser would pull a dependency into a package
 * that otherwise needs none, and would still need this much code to map the result.
 *
 * Namespace prefixes are the reason this is not a simple substring search: servers use `D:`,
 * `d:`, `lp1:` or no prefix at all for the same elements, and a reader that assumed one of them
 * would work against one server and silently return nothing against the next.
 */
object WebDavListing {
    /**
     * Every entry in [xml], excluding the collection being listed.
     *
     * A `PROPFIND` on a folder returns that folder as its own first entry. Keeping it would show a
     * directory containing itself, and walking into it would never terminate — so the entry whose
     * href matches the requested path is dropped.
     */
    fun parse(xml: String, requestedPath: String? = null): List<WebDavEntry> {
        val entries =
            RESPONSE.findAll(xml).mapNotNull { match ->
                val body = match.groupValues[1]
                val href = tagText(body, "href")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val decodedHref = decodePath(href)
                // `resourcetype` holding a `collection` element is what marks a folder. Some servers
                // send `<D:collection/>`, others `<D:collection></D:collection>`; both are matched.
                val isDirectory = COLLECTION.containsMatchIn(body)
                val name =
                    tagText(body, "displayname")?.takeIf(String::isNotBlank)
                        ?: decodedHref.trimEnd('/').substringAfterLast('/')
                WebDavEntry(
                    href = decodedHref,
                    displayName = decodeXmlEntities(name),
                    isDirectory = isDirectory,
                    sizeBytes = tagText(body, "getcontentlength")?.trim()?.toLongOrNull(),
                    contentType = tagText(body, "getcontenttype")?.trim()?.takeIf(String::isNotBlank),
                )
            }.toList()

        val self = requestedPath?.let(::normalisePath)
        return entries.filterNot { entry -> self != null && normalisePath(entry.href) == self }
    }

    /** Trailing slashes and case are not meaningful when comparing a folder against itself. */
    private fun normalisePath(path: String): String = path.trimEnd('/').lowercase()

    /**
     * The text of the first `tag` in [body], whatever namespace prefix the server used.
     *
     * Compiled per tag and cached: this runs once per element per entry, and a directory of several
     * thousand files would otherwise rebuild the same handful of patterns thousands of times.
     */
    private fun tagText(body: String, tag: String): String? =
        TAG_PATTERNS.getValue(tag).find(body)?.groupValues?.get(1)

    /**
     * Percent-decoding, because servers return hrefs escaped.
     *
     * A file called "Duna (2021).mkv" arrives as "Duna%20%282021%29.mkv", and a path used without
     * decoding would be escaped twice on the next request and 404.
     */
    private fun decodePath(href: String): String =
        runCatching {
            java.net.URLDecoder.decode(href, Charsets.UTF_8)
        }.getOrDefault(href)

    /** The five entities XML defines. A display name may legitimately contain any of them. */
    private fun decodeXmlEntities(value: String): String =
        value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            // Ampersand last, so "&amp;lt;" decodes to "&lt;" rather than to "<".
            .replace("&amp;", "&")

    private val RESPONSE =
        Regex("<[^>]*:?response[^>]*>(.*?)</[^>]*:?response>", RegexOption.DOT_MATCHES_ALL)

    private val COLLECTION = Regex("<[^>]*:?collection[^>]*/?>")

    private val TAG_PATTERNS: Map<String, Regex> =
        listOf("href", "displayname", "getcontentlength", "getcontenttype").associateWith { tag ->
            Regex("<[^>]*:?$tag[^>]*>(.*?)</[^>]*:?$tag>", RegexOption.DOT_MATCHES_ALL)
        }
}
