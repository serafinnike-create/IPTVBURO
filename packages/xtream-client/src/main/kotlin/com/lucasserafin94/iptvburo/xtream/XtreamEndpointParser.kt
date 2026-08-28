package com.lucasserafin94.iptvburo.xtream

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object XtreamEndpointParser {
    fun parse(rawValue: String): XtreamEndpoint {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            throw XtreamClientException(
                XtreamFailureReason.INVALID_SERVER,
                "The server address is empty.",
            )
        }

        // A scheme that was meant and mistyped is refused, not quietly treated as a hostname.
        //
        // `http:7/buro.ac` — one key missed on the second slash — has no `//`, so it used to fall
        // through to the bare-host branch and become `https://http:7/buro.ac`: host `http`, port 7.
        // That parses, so the address was accepted and the connection failed later saying the
        // address was invalid, on an address the viewer had typed correctly but for one character.
        //
        // Anything before a colon that looks like a scheme has to be one. A bare host cannot
        // contain a colon except before a port, and a port is digits.
        if (!SCHEME_PATTERN.containsMatchIn(trimmed) && MISTYPED_SCHEME_PATTERN.containsMatchIn(trimmed)) {
            throw XtreamClientException(
                XtreamFailureReason.INVALID_SERVER_SCHEME,
                "The server address scheme is malformed.",
            )
        }

        val withScheme =
            if (SCHEME_PATTERN.containsMatchIn(trimmed)) {
                trimmed
            } else {
                "https://$trimmed"
            }
        val parsed =
            withScheme.toHttpUrlOrNull()
                ?: throw XtreamClientException(
                    XtreamFailureReason.INVALID_SERVER,
                    "The server address is invalid.",
                )
        if (parsed.scheme !in ALLOWED_SCHEMES || parsed.host.isBlank()) {
            throw XtreamClientException(
                XtreamFailureReason.INVALID_SERVER,
                "Only HTTP and HTTPS Xtream servers are supported.",
            )
        }

        val pathSegments =
            parsed.pathSegments
                .filterNot(String::isBlank)
                .toMutableList()
        if (pathSegments.lastOrNull()?.lowercase() in KNOWN_ENDPOINT_FILES) {
            pathSegments.removeAt(pathSegments.lastIndex)
        }

        val builder =
            parsed.newBuilder()
                .username("")
                .password("")
                .query(null)
                .fragment(null)
                .encodedPath("/")
        pathSegments
            .filter(String::isNotBlank)
            .forEach(builder::addPathSegment)

        return XtreamEndpoint(builder.build())
    }

    private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    /**
     * A known scheme name followed by anything other than `//`.
     *
     * Matched against the scheme names themselves rather than "letters then a colon", because a
     * host and a scheme look alike before the colon and only the host can carry a port.
     * `buro.ac:8080` is untouched; `http:7/buro.ac` and `http:/buro.ac` are caught.
     */
    private val MISTYPED_SCHEME_PATTERN = Regex("^(?:https?|ftp|file):(?!//)", RegexOption.IGNORE_CASE)
    private val ALLOWED_SCHEMES = setOf("http", "https")
    private val KNOWN_ENDPOINT_FILES =
        setOf("get.php", "player_api.php", "xmltv.php", "panel_api.php")
}
