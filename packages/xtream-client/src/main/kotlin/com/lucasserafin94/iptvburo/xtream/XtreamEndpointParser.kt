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
    private val ALLOWED_SCHEMES = setOf("http", "https")
    private val KNOWN_ENDPOINT_FILES =
        setOf("get.php", "player_api.php", "xmltv.php", "panel_api.php")
}
