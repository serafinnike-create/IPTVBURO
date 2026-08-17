package com.lucasserafin94.iptvburo.data.cache

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Whether an artwork URL is safe to write to disk.
 *
 * The danger this guards against is real: anything cached outlives the playlist it came from, so an
 * address carrying the subscriber's username and password would leave a credential on disk long
 * after the source was deleted. That is why the image loader's disk cache was disabled outright,
 * and why re-enabling it needs this rule rather than a flag.
 *
 * The test is for the credential itself, matching what `XtreamClient.sanitizeArtworkUrl` applies
 * when the catalogue is imported:
 *
 * - **userinfo** (`https://user:pass@host/…`) — the plainest way to carry one;
 * - **any query string** — where a signed or token-bearing URL puts it;
 * - **the provider's own authenticated paths** (`/live/`, `/movie/`, `/series/`), which are built
 *   as `/movie/<username>/<password>/<id>` and are the shape that made this a concern at all.
 *
 * An ordinary Xtream poster — `http://host/images/abc.jpg` — carries no credential and is kept.
 * A local file stays acceptable: it is the app's own copy.
 *
 * Lives here, rather than beside the one caller that first needed it, because the reminder store and
 * the artwork cache must apply the *same* rule. Two copies would be two places to get it wrong, and
 * the one that drifted would be the one writing a password to disk.
 */
fun isStorableArtwork(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_ARTWORK_URL_LENGTH) return false
    if (trimmed.startsWith("file://")) return true
    val parsed = trimmed.toHttpUrlOrNull() ?: return false
    if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
    if (parsed.query != null) return false
    // Case-insensitive: a provider answering with `/Movie/` would otherwise walk straight past a
    // check that only knows the lowercase spelling.
    return parsed.pathSegments.none { segment -> segment.lowercase() in CREDENTIAL_BEARING_PATHS }
}

/** Xtream's authenticated endpoints, whose paths are `/<kind>/<username>/<password>/<id>`. */
private val CREDENTIAL_BEARING_PATHS = setOf("live", "movie", "series")

/** Longer than any real poster address; a value past this is not one worth keeping. */
private const val MAX_ARTWORK_URL_LENGTH = 2_048
