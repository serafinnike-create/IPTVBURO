package com.lucasserafin94.iptvburo.stalker

/**
 * Credentials for a Stalker/Ministra portal.
 *
 * Unlike Xtream, a Stalker portal authenticates the **device**, not a person: the MAC address is
 * the credential. Some resellers additionally gate the portal behind a login, which is why
 * [username] and [password] exist but are optional.
 *
 * [toString] is redacted because the MAC alone grants access to the whole subscription.
 */
data class StalkerCredentials(
    val portalUrl: String,
    val macAddress: String,
    val username: String? = null,
    val password: String? = null,
) {
    override fun toString(): String = "StalkerCredentials(portalUrl=<redacted>, mac=<redacted>)"
}

/** Session returned by a successful handshake. */
data class StalkerSession(
    val token: String,
    val expiresAtEpochMillis: Long,
) {
    override fun toString(): String = "StalkerSession(token=<redacted>)"
}

/** Account state reported by the portal after `get_main_info`. */
data class StalkerAccount(
    val authenticated: Boolean,
    val expiryDate: String?,
    val tariffPlan: String?,
    val blocked: Boolean,
)

enum class StalkerContentType {
    LIVE,
    MOVIE,
    SERIES,
}

data class StalkerCategory(
    val providerId: String,
    val name: String,
    val contentType: StalkerContentType,
)

data class StalkerCatalogItem(
    val providerId: String,
    val name: String,
    val contentType: StalkerContentType,
    val categoryId: String?,
    val artworkUrl: String?,
    val year: Int?,
    val rating: Double?,
    /**
     * Opaque portal command for this item, e.g. `ffmpeg http://localhost/ch/123_`.
     *
     * Held rather than resolved eagerly: turning it into a playable URL costs a `create_link`
     * round trip and yields a short-lived, single-use token, so it is only resolved when the user
     * actually presses play.
     */
    val command: String?,
) {
    override fun toString(): String =
        "StalkerCatalogItem(providerId=$providerId, name=$name, contentType=$contentType, " +
            "categoryId=$categoryId, artworkUrl=${if (artworkUrl == null) "null" else "<redacted>"}, " +
            "year=$year, rating=$rating, command=<redacted>)"
}

data class StalkerPage(
    val items: List<StalkerCatalogItem>,
    val totalItems: Int,
)

enum class StalkerFailureReason {
    /** Portal reachable but rejected the MAC. Almost always a wrong or unregistered MAC. */
    UNAUTHORISED,

    /** The subscription exists but the portal marked it blocked or expired. */
    BLOCKED,

    /** Network, DNS or TLS failure. */
    NETWORK,

    /** Portal answered with something this client cannot parse. */
    MALFORMED,
}

class StalkerClientException(
    val reason: StalkerFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Never includes the portal URL, MAC or token. */
    override fun toString(): String = "StalkerClientException(reason=$reason)"
}
