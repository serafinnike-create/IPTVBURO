package com.lucasserafin94.iptvburo.playlist

import com.lucasserafin94.iptvburo.domain.model.PlaylistHeader
import java.io.IOException

data class M3uParserLimits @JvmOverloads constructor(
    val maxBytes: Long = DEFAULT_MAX_BYTES,
    val maxChannels: Int = DEFAULT_MAX_CHANNELS,
    val maxLineLength: Int = DEFAULT_MAX_LINE_LENGTH,
    val maxWarnings: Int = DEFAULT_MAX_WARNINGS,
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        require(maxChannels > 0) { "maxChannels must be positive" }
        require(maxLineLength > 0) { "maxLineLength must be positive" }
        require(maxWarnings > 0) { "maxWarnings must be positive" }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 256L * 1024L * 1024L
        const val DEFAULT_MAX_CHANNELS: Int = 500_000
        const val DEFAULT_MAX_LINE_LENGTH: Int = 64 * 1024
        const val DEFAULT_MAX_WARNINGS: Int = 1_000
    }
}

data class M3uParseResult @JvmOverloads constructor(
    val header: PlaylistHeader,
    val channels: List<ParsedChannel>,
    val warnings: List<M3uWarning>,
    val bytesRead: Long,
    val suppressedWarningCount: Int = 0,
)

data class M3uParseSummary @JvmOverloads constructor(
    val header: PlaylistHeader,
    val channelCount: Int,
    val warnings: List<M3uWarning>,
    val bytesRead: Long,
    val suppressedWarningCount: Int = 0,
)

/**
 * Parser output before platform persistence assigns stable source, category, and channel IDs.
 *
 * URI and header values are sensitive and therefore excluded from [toString].
 */
data class ParsedChannel(
    val name: String,
    val streamUri: String,
    val durationSeconds: Long? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logoUri: String? = null,
    val groupTitle: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
    val requestHeaders: Map<String, String> = emptyMap(),
) {
    override fun toString(): String =
        "ParsedChannel(" +
            "name=$name, " +
            "streamUri=<redacted>, " +
            "durationSeconds=$durationSeconds, " +
            "tvgId=$tvgId, " +
            "tvgName=$tvgName, " +
            "logoUri=${if (logoUri == null) "null" else "<redacted>"}, " +
            "groupTitle=$groupTitle, " +
            "attributeNames=${attributes.keys.sorted()}, " +
            "tags=${tags.keys.sorted()}, " +
            "requestHeaderNames=${requestHeaders.keys.sorted()}" +
            ")"
}

data class M3uWarning(
    val code: M3uWarningCode,
    val lineNumber: Long?,
    val message: String,
)

enum class M3uWarningCode {
    MISSING_PLAYLIST_HEADER,
    DUPLICATE_PLAYLIST_HEADER,
    LATE_PLAYLIST_HEADER,
    MALFORMED_PLAYLIST_HEADER,
    MALFORMED_EXTINF,
    INVALID_DURATION,
    MALFORMED_ATTRIBUTE,
    MISSING_CHANNEL_NAME,
    MISSING_CHANNEL_URI,
    MISSING_EXTINF,
    INVALID_STREAM_URI,
    UNSAFE_STREAM_URI_SCHEME,
    MALFORMED_HEADER,
    UNSUPPORTED_HEADER,
    EMPTY_PLAYLIST,
    WINDOWS_1252_FALLBACK,
}

open class M3uParseException(
    message: String,
) : IOException(message)

class M3uLimitExceededException(
    val maxBytes: Long,
) : M3uParseException("M3U input exceeds the configured byte limit ($maxBytes bytes).")

class M3uChannelLimitExceededException(
    val maxChannels: Int,
) : M3uParseException("M3U input exceeds the configured channel limit ($maxChannels channels).")

class M3uLineLimitExceededException(
    val lineNumber: Long,
    val maxLineLength: Int,
) : M3uParseException(
        "M3U line $lineNumber exceeds the configured line length limit ($maxLineLength characters).",
    )
