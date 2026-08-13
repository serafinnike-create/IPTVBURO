package com.lucasserafin94.iptvburo.xtream

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Whatever the provider actually sent the subscriber, turned into the three fields the app needs.
 *
 * Providers hand out the same subscription in several shapes, and which one a person receives is an
 * accident of who sold it to them:
 *
 * - a **playlist URL** — `http://host:port/get.php?username=U&password=P&type=m3u_plus`;
 * - the **short form** some panels prefer — `http://host:port/playlist/U/P/m3u_plus`;
 * - an **API URL** — `http://host:port/player_api.php?username=U&password=P`;
 * - **three separate fields**, host, username and password, typed by hand;
 * - a URL with the credentials in the **userinfo** — `http://U:P@host:port/`.
 *
 * Every one of these describes the same Xtream account. Making the user work out which is which,
 * and retype credentials they already hold in a link, is asking them to do the parsing.
 *
 * Nothing here contacts the network: this is a pure reading of text the user pasted. Whether the
 * account works is [XtreamClient]'s answer, not this one's.
 */
data class XtreamSubscriptionLink(
    /** The server, with the endpoint file and credentials stripped. */
    val endpoint: XtreamEndpoint,
    val username: String,
    val password: String,
) {
    /**
     * Never prints the credentials. A pasted link is exactly the sort of value that ends up in a
     * log line or a crash report while someone is debugging a connection problem.
     */
    override fun toString(): String = "XtreamSubscriptionLink(endpoint=$endpoint, credentials=<redacted>)"
}

object XtreamSubscriptionParser {
    /**
     * Reads a pasted link, or null when it carries no credentials.
     *
     * Null is not a failure: it means "this is only a server address", which is the ordinary case
     * when someone pastes a host and types the username and password themselves. The caller keeps
     * whatever the user typed in that situation.
     */
    fun parse(rawValue: String): XtreamSubscriptionLink? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null

        val withScheme =
            if (SCHEME_PATTERN.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.scheme !in ALLOWED_SCHEMES || url.host.isBlank()) return null

        val fromQuery = url.queryParameter("username") to url.queryParameter("password")
        val fromUserInfo = url.username.ifBlank { null } to url.password.ifBlank { null }
        val fromPath = credentialsFromPath(url.pathSegments)

        val (username, password) =
            listOf(fromQuery, fromUserInfo, fromPath)
                .firstOrNull { (user, secret) -> !user.isNullOrBlank() && !secret.isNullOrBlank() }
                ?: return null

        // Re-parsed through the ordinary parser rather than trimmed here, so a link and a typed
        // host end up at exactly the same endpoint. Two ways of reaching the same server that
        // disagreed by a trailing path would be a bug nobody could see.
        val endpoint = runCatching { XtreamEndpointParser.parse(withScheme) }.getOrNull() ?: return null

        return XtreamSubscriptionLink(
            endpoint = endpoint,
            username = requireNotNull(username),
            password = requireNotNull(password),
        )
    }

    /**
     * The `/playlist/USER/PASS/m3u_plus` shape, and the `/live/USER/PASS/…` stream URLs.
     *
     * Only read when the segment before them says so. Two arbitrary path segments are not
     * credentials, and guessing they might be would turn a plain server address into a login with
     * nonsense in it.
     */
    private fun credentialsFromPath(segments: List<String>): Pair<String?, String?> {
        val clean = segments.filterNot(String::isBlank)
        val marker = clean.indexOfFirst { segment -> segment.lowercase() in CREDENTIAL_PATH_MARKERS }
        if (marker < 0 || clean.size < marker + 3) return null to null
        return clean[marker + 1] to clean[marker + 2]
    }

    private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** Path segments that announce "the next two segments are a username and a password". */
    private val CREDENTIAL_PATH_MARKERS = setOf("playlist", "live", "movie", "series")
}
