package com.lucasserafin94.iptvburo.playlist

import com.lucasserafin94.iptvburo.domain.model.PlaylistHeader
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.SequenceInputStream
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.min

/**
 * Streaming M3U parser.
 *
 * The parser never reads the complete input as a String. [parseStreaming] emits one channel at a
 * time and is suitable for batched database imports. The caller owns [input] and remains
 * responsible for closing it.
 */
class M3uParser(
    private val limits: M3uParserLimits = M3uParserLimits(),
) {
    /**
     * Convenience API for small inputs and tests.
     *
     * For large playlists prefer [parseStreaming], which does not accumulate channels.
     */
    fun parse(input: InputStream): M3uParseResult {
        val channels = mutableListOf<ParsedChannel>()
        val summary = parseStreaming(input, channels::add)
        return M3uParseResult(
            header = summary.header,
            channels = channels,
            warnings = summary.warnings,
            bytesRead = summary.bytesRead,
            suppressedWarningCount = summary.suppressedWarningCount,
        )
    }

    fun parseStreaming(
        input: InputStream,
        onChannel: (ParsedChannel) -> Unit,
    ): M3uParseSummary {
        val boundedInput = ByteLimitedInputStream(input, limits.maxBytes)
        val warnings = CappedWarningList(limits.maxWarnings)
        val reader =
            BufferedReader(
                decodedReader(boundedInput, warnings),
                READER_BUFFER_SIZE,
            )
        var playlistHeader = PlaylistHeader()
        var headerSeen = false
        var meaningfulLineSeen = false
        var channelCount = 0
        var lineNumber = 0L
        var pendingEntry: PendingEntry? = null
        val pendingTags = linkedMapOf<String, String>()
        val pendingHeaders = linkedMapOf<String, String>()

        while (true) {
            val rawLine = reader.readLine() ?: break
            lineNumber += 1
            if (rawLine.length > limits.maxLineLength) {
                throw M3uLineLimitExceededException(lineNumber, limits.maxLineLength)
            }

            val line =
                if (lineNumber == 1L) {
                    rawLine.removePrefix(UTF8_BOM).trim()
                } else {
                    rawLine.trim()
                }
            if (line.isEmpty()) continue

            if (line.startsWith(EXTM3U, ignoreCase = true)) {
                if (headerSeen) {
                    warnings +=
                        warning(
                            M3uWarningCode.DUPLICATE_PLAYLIST_HEADER,
                            lineNumber,
                            "A duplicate playlist header was ignored.",
                        )
                    continue
                }
                if (meaningfulLineSeen || channelCount > 0 || pendingEntry != null) {
                    warnings +=
                        warning(
                            M3uWarningCode.LATE_PLAYLIST_HEADER,
                            lineNumber,
                            "The playlist header appeared after playlist entries.",
                        )
                }
                val parsedHeader =
                    parsePlaylistHeader(
                        line = line,
                        lineNumber = lineNumber,
                        warnings = warnings,
                    )
                playlistHeader = parsedHeader
                headerSeen = true
                meaningfulLineSeen = true
                continue
            }

            meaningfulLineSeen = true

            if (line.startsWith(EXTINF, ignoreCase = true)) {
                if (pendingEntry != null) {
                    warnings +=
                        warning(
                            M3uWarningCode.MISSING_CHANNEL_URI,
                            pendingEntry.lineNumber,
                            "Entry metadata was not followed by a stream URI.",
                        )
                    pendingTags.clear()
                    pendingHeaders.clear()
                }
                val parsedEntry =
                    parseExtInf(
                        line = line,
                        lineNumber = lineNumber,
                        warnings = warnings,
                    )
                pendingEntry = parsedEntry
                pendingHeaders.putAll(headersFromAttributes(parsedEntry.attributes))
                continue
            }

            if (line.startsWith(EXTGRP, ignoreCase = true)) {
                pendingTags[EXTGRP_TAG] = line.substringAfter(':', "").trim()
                continue
            }

            if (line.startsWith(EXTVLCOPT, ignoreCase = true)) {
                parseHeaderDirective(
                    payload = line.substringAfter(':', ""),
                    lineNumber = lineNumber,
                    warnings = warnings,
                    destination = pendingHeaders,
                )
                continue
            }

            if (line.startsWith(KODIPROP, ignoreCase = true)) {
                parseKodiProperty(
                    payload = line.substringAfter(':', ""),
                    lineNumber = lineNumber,
                    warnings = warnings,
                    destination = pendingHeaders,
                )
                continue
            }

            if (line.startsWith(EXTHTTP, ignoreCase = true)) {
                parseExtHttp(
                    payload = line.substringAfter(':', ""),
                    lineNumber = lineNumber,
                    warnings = warnings,
                    destination = pendingHeaders,
                )
                continue
            }

            if (line.startsWith("#")) {
                collectTag(line, pendingTags)
                continue
            }

            val parsedStream =
                parseStreamLine(
                    line = line,
                    lineNumber = lineNumber,
                    warnings = warnings,
                )
            if (parsedStream == null) {
                pendingEntry = null
                pendingTags.clear()
                pendingHeaders.clear()
                continue
            }

            if (pendingEntry == null) {
                warnings +=
                    warning(
                        M3uWarningCode.MISSING_EXTINF,
                        lineNumber,
                        "A stream URI had no preceding EXTINF metadata.",
                    )
            }

            val displayName =
                pendingEntry?.displayName?.takeIf(String::isNotBlank)
                    ?: pendingEntry?.attributes?.get(TVG_NAME)?.takeIf(String::isNotBlank)
                    ?: run {
                        warnings +=
                            warning(
                                M3uWarningCode.MISSING_CHANNEL_NAME,
                                lineNumber,
                                "A channel name was missing; a local fallback name was assigned.",
                            )
                        "Channel ${channelCount + 1}"
                    }

            val requestHeaders =
                linkedMapOf<String, String>().apply {
                    putAll(playlistHeader.requestHeaders)
                    putAll(pendingHeaders)
                    putAll(parsedStream.requestHeaders)
                }
            val attributes = pendingEntry?.attributes.orEmpty()
            val groupTitle =
                attributes[GROUP_TITLE]
                    ?.takeIf(String::isNotBlank)
                    ?: pendingTags[EXTGRP_TAG]?.takeIf(String::isNotBlank)
            val logoUri =
                attributes[TVG_LOGO]
                    ?.takeIf(String::isNotBlank)
                    ?: pendingTags[EXTIMG_TAG]?.takeIf(String::isNotBlank)

            channelCount += 1
            if (channelCount > limits.maxChannels) {
                throw M3uChannelLimitExceededException(limits.maxChannels)
            }
            onChannel(
                ParsedChannel(
                    name = displayName,
                    streamUri = parsedStream.uri,
                    durationSeconds = pendingEntry?.durationSeconds,
                    tvgId = attributes[TVG_ID]?.takeIf(String::isNotBlank),
                    tvgName = attributes[TVG_NAME]?.takeIf(String::isNotBlank),
                    logoUri = logoUri,
                    groupTitle = groupTitle,
                    attributes = attributes,
                    tags = pendingTags.toMap(),
                    requestHeaders = requestHeaders,
                ),
            )

            pendingEntry = null
            pendingTags.clear()
            pendingHeaders.clear()
        }

        if (pendingEntry != null) {
            warnings +=
                warning(
                    M3uWarningCode.MISSING_CHANNEL_URI,
                    pendingEntry.lineNumber,
                    "Entry metadata was not followed by a stream URI.",
                )
        }
        if (!headerSeen) {
            warnings.addPriority(
                warning(
                    M3uWarningCode.MISSING_PLAYLIST_HEADER,
                    null,
                    "The input did not contain an EXTM3U playlist header.",
                ),
            )
        }
        if (channelCount == 0) {
            warnings +=
                warning(
                    M3uWarningCode.EMPTY_PLAYLIST,
                    null,
                    "The playlist contained no usable channels.",
                )
        }

        return M3uParseSummary(
            header = playlistHeader,
            channelCount = channelCount,
            warnings = warnings.toList(),
            bytesRead = boundedInput.bytesRead,
            suppressedWarningCount = warnings.suppressedWarningCount,
        )
    }

    private fun decodedReader(
        input: InputStream,
        warnings: CappedWarningList,
    ): Reader {
        val probe = ByteArray(ENCODING_PROBE_SIZE)
        var probeLength = 0
        var reachedEndOfInput = false
        while (probeLength < probe.size) {
            val count = input.read(probe, probeLength, probe.size - probeLength)
            if (count == -1) {
                reachedEndOfInput = true
                break
            }
            if (count == 0) {
                val value = input.read()
                if (value == -1) {
                    reachedEndOfInput = true
                    break
                }
                probe[probeLength] = value.toByte()
                probeLength += 1
            } else {
                probeLength += count
            }
        }
        var remainderInput = input
        if (!reachedEndOfInput && probeLength == probe.size) {
            val lookahead = input.read()
            if (lookahead == -1) {
                reachedEndOfInput = true
            } else {
                remainderInput =
                    SequenceInputStream(
                        ByteArrayInputStream(byteArrayOf(lookahead.toByte())),
                        input,
                    )
            }
        }

        val bom =
            when {
                probe.hasPrefix(probeLength, UTF8_BOM_BYTES) ->
                    BomEncoding(UTF8_BOM_BYTES.size, StandardCharsets.UTF_8)

                probe.hasPrefix(probeLength, UTF16_LE_BOM_BYTES) ->
                    BomEncoding(UTF16_LE_BOM_BYTES.size, StandardCharsets.UTF_16LE)

                probe.hasPrefix(probeLength, UTF16_BE_BOM_BYTES) ->
                    BomEncoding(UTF16_BE_BOM_BYTES.size, StandardCharsets.UTF_16BE)

                else -> null
            }
        if (bom != null) {
            return strictReader(
                input = replay(probe, bom.byteCount, probeLength, remainderInput),
                charset = bom.charset,
            )
        }

        val replayInput = replay(probe, 0, probeLength, remainderInput)
        return if (isStrictUtf8(probe, probeLength, reachedEndOfInput)) {
            strictReader(replayInput, StandardCharsets.UTF_8)
        } else {
            warnings.addPriority(
                warning(
                    code = M3uWarningCode.WINDOWS_1252_FALLBACK,
                    lineNumber = null,
                    message =
                        "The input was not valid UTF-8 and was decoded as Windows-1252.",
                ),
            )
            InputStreamReader(replayInput, WINDOWS_1252)
        }
    }

    private fun strictReader(
        input: InputStream,
        charset: Charset,
    ): Reader =
        InputStreamReader(
            input,
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT),
        )

    private fun replay(
        prefix: ByteArray,
        startIndex: Int,
        endIndex: Int,
        remainder: InputStream,
    ): InputStream =
        SequenceInputStream(
            ByteArrayInputStream(prefix, startIndex, endIndex - startIndex),
            remainder,
        )

    private fun isStrictUtf8(
        bytes: ByteArray,
        length: Int,
        endOfInput: Boolean,
    ): Boolean {
        val decoder =
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes, 0, length)
        val output = CharBuffer.allocate(UTF8_VALIDATION_BUFFER_SIZE)
        while (true) {
            val result = decoder.decode(input, output, endOfInput)
            when {
                result.isError -> return false
                result.isOverflow -> output.clear()
                result.isUnderflow -> return true
            }
        }
    }

    private fun ByteArray.hasPrefix(
        availableLength: Int,
        expected: ByteArray,
    ): Boolean =
        availableLength >= expected.size &&
            expected.indices.all { index -> this[index] == expected[index] }

    private fun parsePlaylistHeader(
        line: String,
        lineNumber: Long,
        warnings: MutableList<M3uWarning>,
    ): PlaylistHeader {
        val payload = line.substring(EXTM3U.length).trim()
        if (payload.isEmpty()) return PlaylistHeader()

        val parsedAttributes = parseAttributes(payload)
        if (parsedAttributes.malformed) {
            warnings +=
                warning(
                    M3uWarningCode.MALFORMED_PLAYLIST_HEADER,
                    lineNumber,
                    "One or more playlist header attributes were malformed.",
                )
        }
        val attributes = parsedAttributes.values
        val tvgShift = attributes[TVG_SHIFT]?.toDoubleOrNull()
        if (attributes.containsKey(TVG_SHIFT) && tvgShift == null) {
            warnings +=
                warning(
                    M3uWarningCode.MALFORMED_PLAYLIST_HEADER,
                    lineNumber,
                    "The playlist time shift was not numeric.",
                )
        }

        val epgUrls =
            sequenceOf(URL_TVG, X_TVG_URL)
                .mapNotNull(attributes::get)
                .flatMap { value -> value.split(',', ';').asSequence() }
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .toList()

        return PlaylistHeader(
            name =
                attributes[PLAYLIST_NAME]?.takeIf(String::isNotBlank)
                    ?: attributes[NAME]?.takeIf(String::isNotBlank),
            epgUrls = epgUrls,
            tvgShiftHours = tvgShift,
            attributes = attributes,
            requestHeaders = headersFromAttributes(attributes),
        )
    }

    private fun parseExtInf(
        line: String,
        lineNumber: Long,
        warnings: MutableList<M3uWarning>,
    ): PendingEntry {
        val payload = line.substringAfter(':', "")
        val commaIndex = findSeparatorOutsideQuotes(payload, ',')
        val descriptor =
            if (commaIndex >= 0) {
                payload.substring(0, commaIndex).trim()
            } else {
                warnings +=
                    warning(
                        M3uWarningCode.MALFORMED_EXTINF,
                        lineNumber,
                        "EXTINF metadata did not contain a title separator.",
                    )
                payload.trim()
            }
        val displayName =
            if (commaIndex >= 0) {
                payload.substring(commaIndex + 1).trim().ifEmpty { null }
            } else {
                null
            }

        val firstWhitespace = descriptor.indexOfFirst(Char::isWhitespace)
        val durationToken =
            if (firstWhitespace >= 0) {
                descriptor.substring(0, firstWhitespace)
            } else {
                descriptor
            }
        val attributePayload =
            if (firstWhitespace >= 0) {
                descriptor.substring(firstWhitespace + 1)
            } else {
                ""
            }
        val duration =
            durationToken
                .takeIf(String::isNotBlank)
                ?.toDoubleOrNull()
                ?.toLong()
        if (durationToken.isBlank() || duration == null) {
            warnings +=
                warning(
                    M3uWarningCode.INVALID_DURATION,
                    lineNumber,
                    "EXTINF duration was missing or invalid.",
                )
        }

        val parsedAttributes = parseAttributes(attributePayload)
        if (parsedAttributes.malformed) {
            warnings +=
                warning(
                    M3uWarningCode.MALFORMED_ATTRIBUTE,
                    lineNumber,
                    "One or more EXTINF attributes were malformed.",
                )
        }

        return PendingEntry(
            lineNumber = lineNumber,
            durationSeconds = duration,
            displayName = displayName,
            attributes = parsedAttributes.values,
        )
    }

    private fun parseStreamLine(
        line: String,
        lineNumber: Long,
        warnings: MutableList<M3uWarning>,
    ): ParsedStream? {
        val pipeIndex = line.indexOf('|')
        val rawUri = (if (pipeIndex >= 0) line.substring(0, pipeIndex) else line).trim()
        val headerPayload =
            if (pipeIndex >= 0) {
                line.substring(pipeIndex + 1)
            } else {
                ""
            }
        val uri =
            try {
                URI(rawUri)
            } catch (_: URISyntaxException) {
                warnings +=
                    warning(
                        M3uWarningCode.INVALID_STREAM_URI,
                        lineNumber,
                        "A malformed stream URI was skipped.",
                    )
                return null
            }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (!uri.isAbsolute || scheme !in SAFE_STREAM_SCHEMES) {
            warnings +=
                warning(
                    M3uWarningCode.UNSAFE_STREAM_URI_SCHEME,
                    lineNumber,
                    "A stream URI with an unsupported or unsafe scheme was skipped.",
                )
            return null
        }

        val headers = linkedMapOf<String, String>()
        if (headerPayload.isNotBlank()) {
            parseEncodedHeaderPairs(
                payload = headerPayload,
                lineNumber = lineNumber,
                warnings = warnings,
                destination = headers,
            )
        }
        return ParsedStream(uri = rawUri, requestHeaders = headers)
    }

    private fun parseHeaderDirective(
        payload: String,
        lineNumber: Long,
        warnings: MutableList<M3uWarning>,
        destination: MutableMap<String, String>,
    ) {
        val separator = payload.indexOf('=')
        if (separator <= 0) {
            warnings +=
                warning(
                    M3uWarningCode.MALFORMED_HEADER,
                    lineNumber,
                    "A request header directive was malformed.",
                )
            return
        }
        val rawName = payload.substring(0, separator).trim()
        val value = payload.substring(separator + 1).trim()
        val canonicalName = canonicalHeaderName(rawName)
        if (canonicalName == null) {
            warnings +=
                warning(
                    M3uWarningCode.UNSUPPORTED_HEADER,
                    lineNumber,
                    "An unsupported request header directive was ignored.",
                )
            return
        }
        destination[canonicalName] = value
    }

    private fun parseKodiProperty(
        payload: String,
        lineNumber: Long,
        warnings: MutableList<M3uWarning>,
        destination: MutableMap<String, String>,
    ) {
        val separator = payload.indexOf('=')
        if (separator <= 0) {
            warnings +=
                warning(
                    M3uWarningCode.MALFORMED_HEADER,
                    lineNumber,
                    "A KODIPROP directive was malformed.",
                )
            return
        }
        val propertyName = payload.substring(0, separator).trim().lowercase(Locale.ROOT)
        val value = payload.substring(separator + 1).trim()
        if (propertyName.endsWith(".stream_headers")) {
            parseEncodedHeaderPairs(value, lineNumber, warnings, destination)
            return
        }
        val canonicalName = canonicalHeaderName(propertyName)
        if (canonicalName != null) {
            destination[canonicalName] = value
            return
        }
        warnings +=
            warning(
                M3uWarningCode.UNSUPPORTED_HEADER,
                lineNumber,
                "An unsupported KODIPROP directive was ignored.",
            )
    }

    private fun parseExtHttp(
        payload: String,
        lineNumber: Long,
        warnings: MutableList<M3uWarning>,
        destination: MutableMap<String, String>,
    ) {
        val matches = JSON_STRING_PAIR.findAll(payload).toList()
        if (!payload.trim().startsWith('{') || !payload.trim().endsWith('}') || matches.isEmpty()) {
            warnings +=
                warning(
                    M3uWarningCode.MALFORMED_HEADER,
                    lineNumber,
                    "An EXTHTTP header object was malformed.",
                )
            return
        }
        matches.forEach { match ->
            val rawName = unescapeJsonString(match.groupValues[1])
            val value = unescapeJsonString(match.groupValues[2])
            val canonicalName = canonicalHeaderName(rawName)
            if (canonicalName == null) {
                warnings +=
                    warning(
                        M3uWarningCode.UNSUPPORTED_HEADER,
                        lineNumber,
                        "An unsupported EXTHTTP request header was ignored.",
                    )
            } else {
                destination[canonicalName] = value
            }
        }
    }

    private fun parseEncodedHeaderPairs(
        payload: String,
        lineNumber: Long,
        warnings: MutableList<M3uWarning>,
        destination: MutableMap<String, String>,
    ) {
        payload.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val separator = pair.indexOf('=')
            if (separator <= 0) {
                warnings +=
                    warning(
                        M3uWarningCode.MALFORMED_HEADER,
                        lineNumber,
                        "A request header pair was malformed.",
                    )
                return@forEach
            }
            val rawName =
                try {
                    percentDecodePreservingPlus(pair.substring(0, separator))
                } catch (_: IllegalArgumentException) {
                    warnings +=
                        warning(
                            M3uWarningCode.MALFORMED_HEADER,
                            lineNumber,
                            "A request header name used invalid encoding.",
                        )
                    return@forEach
                }
            val value =
                try {
                    percentDecodePreservingPlus(pair.substring(separator + 1))
                } catch (_: IllegalArgumentException) {
                    warnings +=
                        warning(
                            M3uWarningCode.MALFORMED_HEADER,
                            lineNumber,
                            "A request header value used invalid encoding.",
                        )
                    return@forEach
                }
            val canonicalName = canonicalHeaderName(rawName)
            if (canonicalName == null) {
                warnings +=
                    warning(
                        M3uWarningCode.UNSUPPORTED_HEADER,
                        lineNumber,
                        "An unsupported request header was ignored.",
                    )
            } else {
                destination[canonicalName] = value
            }
        }
    }

    private fun percentDecodePreservingPlus(value: String): String {
        if ('%' !in value) return value

        val decoded = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '%') {
                decoded.append(value[index])
                index += 1
                continue
            }

            val runBytes = ByteArray((value.length - index + 2) / 3)
            var runLength = 0
            while (index < value.length && value[index] == '%') {
                if (index + 2 >= value.length) {
                    throw IllegalArgumentException("Incomplete percent encoding")
                }
                val high = value[index + 1].digitToIntOrNull(16)
                    ?: throw IllegalArgumentException("Invalid percent encoding")
                val low = value[index + 2].digitToIntOrNull(16)
                    ?: throw IllegalArgumentException("Invalid percent encoding")
                runBytes[runLength] = ((high shl 4) or low).toByte()
                runLength += 1
                index += 3
            }

            val runText =
                try {
                    StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(runBytes, 0, runLength))
                        .toString()
                } catch (error: CharacterCodingException) {
                    throw IllegalArgumentException("Invalid UTF-8 percent encoding", error)
                }
            decoded.append(runText)
        }
        return decoded.toString()
    }

    private fun collectTag(
        line: String,
        destination: MutableMap<String, String>,
    ) {
        val separator = line.indexOf(':')
        if (separator <= 1) return
        val name = line.substring(1, separator).uppercase(Locale.ROOT)
        if (name.startsWith("EXT-X-")) return
        destination[name] = line.substring(separator + 1).trim()
    }

    private fun headersFromAttributes(attributes: Map<String, String>): Map<String, String> =
        buildMap {
            attributes.forEach { (name, value) ->
                canonicalHeaderName(name)?.let { canonicalName ->
                    put(canonicalName, value)
                }
            }
        }

    private fun canonicalHeaderName(rawName: String): String? =
        when (rawName.trim().lowercase(Locale.ROOT)) {
            "user-agent", "http-user-agent" -> "User-Agent"
            "referer", "referrer", "http-referer", "http-referrer" -> "Referer"
            "origin", "http-origin" -> "Origin"
            "cookie", "http-cookie" -> "Cookie"
            "authorization", "http-authorization" -> "Authorization"
            else -> null
        }

    private fun parseAttributes(payload: String): AttributeParseResult {
        if (payload.isBlank()) return AttributeParseResult(emptyMap(), malformed = false)

        val attributes = linkedMapOf<String, String>()
        var index = 0
        var malformed = false

        while (index < payload.length) {
            while (index < payload.length && payload[index].isWhitespace()) index += 1
            if (index >= payload.length) break

            val keyStart = index
            while (
                index < payload.length &&
                    !payload[index].isWhitespace() &&
                    payload[index] != '='
            ) {
                index += 1
            }
            val key = payload.substring(keyStart, index).trim().lowercase(Locale.ROOT)
            while (index < payload.length && payload[index].isWhitespace()) index += 1
            if (key.isEmpty() || index >= payload.length || payload[index] != '=') {
                malformed = true
                while (index < payload.length && !payload[index].isWhitespace()) index += 1
                continue
            }

            index += 1
            while (index < payload.length && payload[index].isWhitespace()) index += 1
            if (index >= payload.length) {
                attributes[key] = ""
                malformed = true
                break
            }

            val value =
                if (payload[index] == '"') {
                    index += 1
                    val builder = StringBuilder()
                    var terminated = false
                    while (index < payload.length) {
                        val char = payload[index]
                        if (char == '\\' && index + 1 < payload.length) {
                            builder.append(payload[index + 1])
                            index += 2
                        } else if (char == '"') {
                            index += 1
                            terminated = true
                            break
                        } else {
                            builder.append(char)
                            index += 1
                        }
                    }
                    if (!terminated) malformed = true
                    builder.toString()
                } else {
                    val valueStart = index
                    while (index < payload.length && !payload[index].isWhitespace()) index += 1
                    payload.substring(valueStart, index)
                }
            attributes[key] = value
        }

        return AttributeParseResult(attributes.toMap(), malformed)
    }

    private fun findSeparatorOutsideQuotes(
        value: String,
        separator: Char,
    ): Int {
        var quoted = false
        var escaped = false
        value.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' && quoted -> escaped = true
                char == '"' -> quoted = !quoted
                char == separator && !quoted -> return index
            }
        }
        return -1
    }

    private fun warning(
        code: M3uWarningCode,
        lineNumber: Long?,
        message: String,
    ) = M3uWarning(code = code, lineNumber = lineNumber, message = message)

    private fun unescapeJsonString(value: String): String =
        value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

    private data class PendingEntry(
        val lineNumber: Long,
        val durationSeconds: Long?,
        val displayName: String?,
        val attributes: Map<String, String>,
    )

    private data class ParsedStream(
        val uri: String,
        val requestHeaders: Map<String, String>,
    )

    private data class AttributeParseResult(
        val values: Map<String, String>,
        val malformed: Boolean,
    )

    private data class BomEncoding(
        val byteCount: Int,
        val charset: Charset,
    )

    private companion object {
        const val READER_BUFFER_SIZE = 8 * 1024
        const val ENCODING_PROBE_SIZE = 1024 * 1024
        const val UTF8_VALIDATION_BUFFER_SIZE = 4 * 1024
        const val UTF8_BOM = "\uFEFF"
        const val EXTM3U = "#EXTM3U"
        const val EXTINF = "#EXTINF:"
        const val EXTGRP = "#EXTGRP:"
        const val EXTVLCOPT = "#EXTVLCOPT:"
        const val KODIPROP = "#KODIPROP:"
        const val EXTHTTP = "#EXTHTTP:"
        const val EXTGRP_TAG = "EXTGRP"
        const val EXTIMG_TAG = "EXTIMG"
        const val TVG_ID = "tvg-id"
        const val TVG_NAME = "tvg-name"
        const val TVG_LOGO = "tvg-logo"
        const val GROUP_TITLE = "group-title"
        const val PLAYLIST_NAME = "playlist-name"
        const val NAME = "name"
        const val URL_TVG = "url-tvg"
        const val X_TVG_URL = "x-tvg-url"
        const val TVG_SHIFT = "tvg-shift"

        val SAFE_STREAM_SCHEMES = setOf("http", "https")
        val WINDOWS_1252: Charset = Charset.forName("windows-1252")
        val UTF8_BOM_BYTES =
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val UTF16_LE_BOM_BYTES =
            byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val UTF16_BE_BOM_BYTES =
            byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val JSON_STRING_PAIR =
            Regex(
                """"((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""",
            )
    }
}

private class CappedWarningList(
    private val maxSize: Int,
) : AbstractMutableList<M3uWarning>() {
    private val delegate =
        ArrayList<M3uWarning>(min(maxSize, INITIAL_WARNING_CAPACITY))

    var suppressedWarningCount: Int = 0
        private set

    override val size: Int
        get() = delegate.size

    override fun get(index: Int): M3uWarning = delegate[index]

    override fun add(
        index: Int,
        element: M3uWarning,
    ) {
        if (delegate.size < maxSize) {
            delegate.add(index, element)
        } else {
            incrementSuppressedCount()
        }
    }

    override fun set(
        index: Int,
        element: M3uWarning,
    ): M3uWarning = delegate.set(index, element)

    override fun removeAt(index: Int): M3uWarning = delegate.removeAt(index)

    fun addPriority(element: M3uWarning) {
        if (delegate.size == maxSize) {
            delegate.removeAt(delegate.lastIndex)
            incrementSuppressedCount()
        }
        delegate.add(0, element)
    }

    private fun incrementSuppressedCount() {
        if (suppressedWarningCount < Int.MAX_VALUE) {
            suppressedWarningCount += 1
        }
    }

    private companion object {
        const val INITIAL_WARNING_CAPACITY = 16
    }
}

private class ByteLimitedInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long,
) : InputStream() {
    var bytesRead: Long = 0
        private set

    override fun read(): Int {
        if (bytesRead >= maxBytes) {
            val overflowByte = delegate.read()
            if (overflowByte == -1) return -1
            throw M3uLimitExceededException(maxBytes)
        }
        val value = delegate.read()
        if (value != -1) bytesRead += 1
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        if (length == 0) return 0
        if (bytesRead >= maxBytes) return readOverflowProbe()

        val remaining = maxBytes - bytesRead
        val allowed = min(length.toLong(), remaining).toInt()
        val count = delegate.read(buffer, offset, allowed)
        if (count > 0) bytesRead += count
        return count
    }

    private fun readOverflowProbe(): Int {
        val overflowByte = delegate.read()
        if (overflowByte == -1) return -1
        throw M3uLimitExceededException(maxBytes)
    }
}
