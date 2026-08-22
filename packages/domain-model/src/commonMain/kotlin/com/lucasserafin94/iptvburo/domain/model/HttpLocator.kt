package com.lucasserafin94.iptvburo.domain.model

/**
 * The parts of an http(s) address that decide a podcast's identity.
 *
 * Deliberately not a general URL type. `java.net.URI` is JVM-only, and the four things this needs —
 * scheme, host, non-default port, normalised path — are a small enough subset to state exactly,
 * which matters because the result is hashed into a key that is written to disk. A fuller parser
 * would be more code to disagree with itself across platforms.
 *
 * User-info and query string are dropped rather than parsed: a feed that rotates an auth token in
 * its query must not change identity between refreshes, which is the whole point of normalising.
 */
internal data class HttpLocator(
    /** Lowercased, and only ever "http" or "https". */
    val scheme: String,
    /** Lowercased, without user-info. */
    val host: String,
    /** Null when the port is absent or is the scheme's default, so both spell the same identity. */
    val port: Int?,
    /** Normalised, always starting with "/" — dot segments resolved as RFC 3986 requires. */
    val path: String,
) {
    companion object {
        /** Returns null when this is not an http(s) address, which callers treat as "hash it raw". */
        fun parse(raw: String): HttpLocator? {
            val trimmed = raw.trim()
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = trimmed.substring(0, schemeEnd).lowercase()
            if (scheme != "http" && scheme != "https") return null

            val afterScheme = trimmed.substring(schemeEnd + 3)
            // The authority ends at the first of these; everything after belongs to path or query.
            val authorityEnd =
                afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
                    .let { if (it < 0) afterScheme.length else it }
            val authority = afterScheme.substring(0, authorityEnd)
            // Credentials are not identity. Dropped before the host is read, not after.
            val hostPort = authority.substringAfterLast('@')
            if (hostPort.isBlank()) return null

            // Split host from port on the last colon, so an IPv6 literal's own colons survive.
            val colon = hostPort.lastIndexOf(':')
            val bracket = hostPort.lastIndexOf(']')
            val host: String
            val explicitPort: Int?
            if (colon > bracket && colon >= 0) {
                host = hostPort.substring(0, colon).lowercase()
                val digits = hostPort.substring(colon + 1)
                // A trailing colon with no digits is the JVM's "no port", not a parse failure.
                explicitPort = if (digits.isEmpty()) null else digits.toIntOrNull() ?: return null
            } else {
                host = hostPort.lowercase()
                explicitPort = null
            }
            if (host.isBlank()) return null

            val defaultPort = if (scheme == "https") 443 else 80
            val port = explicitPort?.takeIf { it != defaultPort }

            val rest = afterScheme.substring(authorityEnd)
            val rawPath = rest.substringBefore('?').substringBefore('#')
            return HttpLocator(scheme, host, port, normalisePath(rawPath))
        }

        /**
         * Resolves "." and ".." the way RFC 3986 does, which is what `URI.normalize` implements.
         *
         * Kept because the identity of a feed reached as `/a/../feed.xml` has to match the one
         * reached as `/feed.xml` — the same document, and a household should not end up with two
         * subscriptions to it.
         */
        private fun normalisePath(rawPath: String): String {
            if (rawPath.isBlank()) return "/"
            val segments = mutableListOf<String>()
            for (segment in rawPath.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." ->
                        // A ".." with nothing above it is dropped rather than escaping the root,
                        // which is what every URL resolver does with it.
                        if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                    else -> segments.add(segment)
                }
            }
            val trailingSlash = rawPath.endsWith("/") && segments.isNotEmpty()
            return "/" + segments.joinToString("/") + if (trailingSlash) "/" else ""
        }
    }
}
