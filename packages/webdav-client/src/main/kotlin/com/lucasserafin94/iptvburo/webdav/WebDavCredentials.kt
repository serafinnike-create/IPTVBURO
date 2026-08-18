package com.lucasserafin94.iptvburo.webdav

import okhttp3.HttpUrl

/**
 * What is needed to reach a share, and nothing that identifies the person using it.
 *
 * ## Why this type prints nothing
 *
 * A Real-Debrid or TorBox WebDAV password is the account. Anything that reaches a log, a crash
 * report or a `toString()` in a list is a credential leaked, so [toString] states the shape and
 * never the values — the same rule `XtreamImportRequest` and `SourceConfig` already follow.
 *
 * The address is redacted too, not only the password: a share URL often contains the account id,
 * and on some services the URL alone is enough to enumerate what somebody has.
 */
data class WebDavCredentials(
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    init {
        require(displayName.isNotBlank()) { "A share needs a name to be shown under." }
        require(baseUrl.isNotBlank()) { "A share needs an address." }
    }

    /**
     * [url] with the credentials in it, for a player that cannot carry a header.
     *
     * Media3 can send an `Authorization` header and is given one; this exists for the cases that
     * cannot — a cast target, an external player — and is produced per playback rather than stored.
     *
     * Percent-encoded, because a password may legitimately contain `@`, `:` or `/`, and an
     * unencoded one would either break the URL or silently point somewhere else.
     */
    fun authorise(url: HttpUrl): String =
        url.newBuilder()
            .username(username)
            .password(password)
            .build()
            .toString()

    override fun toString(): String =
        "WebDavCredentials(displayName=$displayName, baseUrl=<redacted>, " +
            "username=<redacted>, password=<redacted>)"
}
