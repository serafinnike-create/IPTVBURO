package com.lucasserafin94.iptvburo.domain.model

/**
 * Metadata declared on the `#EXTM3U` line.
 *
 * URLs and header values are deliberately omitted from [toString] because either may contain
 * credentials.
 */
data class PlaylistHeader(
    val name: String? = null,
    val epgUrls: List<String> = emptyList(),
    val tvgShiftHours: Double? = null,
    val attributes: Map<String, String> = emptyMap(),
    val requestHeaders: Map<String, String> = emptyMap(),
) {
    override fun toString(): String =
        "PlaylistHeader(" +
            "name=$name, " +
            "epgUrlCount=${epgUrls.size}, " +
            "tvgShiftHours=$tvgShiftHours, " +
            "attributeNames=${attributes.keys.sorted()}, " +
            "requestHeaderNames=${requestHeaders.keys.sorted()}" +
            ")"
}
