package com.lucasserafin94.iptvburo.domain.model

/**
 * A user-managed playlist source.
 *
 * Connection details intentionally do not belong to this model. Platform storage is responsible
 * for keeping sensitive source configuration encrypted.
 */
data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long? = null,
    val channelCount: Int = 0,
)

enum class SourceType {
    LOCAL_M3U,
    REMOTE_M3U,
}

data class Category(
    val id: String,
    val sourceId: String,
    val name: String,
    val sortOrder: Int = 0,
)

/**
 * A playable catalog item.
 *
 * [streamUri] and the values in [requestHeaders] are sensitive. The custom [toString] prevents
 * accidental disclosure when the model is passed to a logger or an exception message.
 */
data class Channel(
    val id: String,
    val sourceId: String,
    val name: String,
    val streamUri: String,
    val categoryId: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logoUri: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
) {
    override fun toString(): String =
        "Channel(" +
            "id=$id, " +
            "sourceId=$sourceId, " +
            "name=$name, " +
            "streamUri=<redacted>, " +
            "categoryId=$categoryId, " +
            "tvgId=$tvgId, " +
            "tvgName=$tvgName, " +
            "logoUri=${if (logoUri == null) "null" else "<redacted>"}, " +
            "requestHeaderNames=${requestHeaders.keys.sorted()}" +
            ")"
}
