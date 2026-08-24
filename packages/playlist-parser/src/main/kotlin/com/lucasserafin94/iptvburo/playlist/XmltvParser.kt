package com.lucasserafin94.iptvburo.playlist

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** One programme from an XMLTV guide, tied to a channel by the id the playlist uses. */
data class XmltvProgramme(
    /** Matches a playlist entry's `tvg-id`; that is the only join between the two files. */
    val channelId: String,
    val title: String,
    val description: String?,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
)

/**
 * Reads an XMLTV guide without holding it in memory.
 *
 * These files are big — a week of a few hundred channels runs to tens of megabytes, and several
 * hundred once uncompressed — so the document is never materialised. Each programme is handed to a
 * callback and forgotten, which is the same shape the catalogue parser uses for the same reason.
 *
 * Only what the guide screen shows is read: the channel id, the title, the description and the two
 * times. Everything else in the format — credits, icons, ratings, star ratings, episode numbering —
 * is skipped rather than parsed and discarded, because parsing it would cost time on every element
 * of a file this size.
 */
object XmltvParser {
    /**
     * Parses [input], calling [onProgramme] for each entry that has everything it needs.
     *
     * Gzip is detected from the first two bytes rather than from the file name or the content type:
     * providers serve `.xml.gz` under `.xml` often enough that trusting either produces a parser
     * error on what is actually a valid file.
     *
     * @param maximumProgrammes a ceiling, so a malformed or hostile file cannot fill memory through
     *   the callback. Reached quietly: a truncated guide is better than none.
     * @return how many programmes were handed over.
     */
    fun parse(
        input: InputStream,
        maximumProgrammes: Int = DEFAULT_MAXIMUM_PROGRAMMES,
        onProgramme: (XmltvProgramme) -> Unit,
    ): Int {
        require(maximumProgrammes > 0) { "maximumProgrammes must be positive" }
        val buffered = BufferedInputStream(input, BUFFER_BYTES)
        val decompressed = if (buffered.looksGzipped()) GZIPInputStream(buffered) else buffered

        val reader = newSecureFactory().createXMLStreamReader(decompressed)
        var emitted = 0
        try {
            while (reader.hasNext() && emitted < maximumProgrammes) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) continue
                if (reader.localName != "programme") continue
                reader.readProgramme()?.let { programme ->
                    onProgramme(programme)
                    emitted += 1
                }
            }
        } finally {
            runCatching { reader.close() }
        }
        return emitted
    }

    /**
     * The two attributes and two child elements a programme needs, or null when any is missing.
     *
     * A programme without both times cannot be placed on a schedule, and one without a channel
     * cannot be attached to anything — so an incomplete entry is dropped rather than guessed at.
     * Guides in the wild carry plenty of them.
     */
    private fun XMLStreamReader.readProgramme(): XmltvProgramme? {
        val channelId = getAttributeValue(null, "channel")?.trim().orEmpty()
        val start = parseXmltvTime(getAttributeValue(null, "start"))
        val stop = parseXmltvTime(getAttributeValue(null, "stop"))

        var title: String? = null
        var description: String? = null
        var depth = 1
        while (hasNext() && depth > 0) {
            when (next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth += 1
                    when (localName) {
                        // The first of each wins. A guide repeats these per language, and the
                        // first is the file's own preferred one; picking the last would silently
                        // prefer whichever translation happens to be ordered last.
                        "title" -> if (title == null) title = elementText.trim().takeIf(String::isNotEmpty)
                        "desc" -> if (description == null) description = elementText.trim().takeIf(String::isNotEmpty)
                        else -> Unit
                    }
                    // elementText consumes through the end tag, so the depth it added is already
                    // gone for the two branches above.
                    if (localName == "title" || localName == "desc") depth -= 1
                }
                XMLStreamConstants.END_ELEMENT -> depth -= 1
                else -> Unit
            }
        }

        if (channelId.isEmpty() || title == null || start == null || stop == null) return null
        // A programme that ends before it starts is a broken row, and drawing it would produce a
        // negative duration on the schedule.
        if (stop <= start) return null
        return XmltvProgramme(
            channelId = channelId.take(MAX_FIELD_LENGTH),
            title = title.take(MAX_FIELD_LENGTH),
            description = description?.take(MAX_DESCRIPTION_LENGTH),
            startEpochSeconds = start,
            endEpochSeconds = stop,
        )
    }

    /**
     * XMLTV time: `YYYYMMDDHHMMSS` followed by an optional ` +HHMM` offset.
     *
     * The offset is part of the format and is not optional in practice — a guide without one is
     * being read in whichever zone the reader happens to sit in, which is how a schedule ends up
     * three hours out. When it is absent this assumes UTC and says so here rather than silently
     * using the machine's zone, because a wrong-but-consistent guide is easier to recognise than
     * one that shifts with the reader.
     */
    internal fun parseXmltvTime(value: String?): Long? {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val digits = text.takeWhile(Char::isDigit)
        if (digits.length < 14) return null

        val year = digits.substring(0, 4).toIntOrNull() ?: return null
        val month = digits.substring(4, 6).toIntOrNull() ?: return null
        val day = digits.substring(6, 8).toIntOrNull() ?: return null
        val hour = digits.substring(8, 10).toIntOrNull() ?: return null
        val minute = digits.substring(10, 12).toIntOrNull() ?: return null
        val second = digits.substring(12, 14).toIntOrNull() ?: return null

        val offsetSeconds =
            text.drop(digits.length).trim().takeIf(String::isNotEmpty)?.let { raw ->
                val sign = if (raw.startsWith("-")) -1 else 1
                val body = raw.removePrefix("+").removePrefix("-")
                if (body.length < 4) return@let null
                val offsetHours = body.substring(0, 2).toIntOrNull() ?: return@let null
                val offsetMinutes = body.substring(2, 4).toIntOrNull() ?: return@let null
                sign * (offsetHours * 3600L + offsetMinutes * 60L)
            } ?: 0L

        return runCatching {
            java.time.LocalDateTime
                .of(year, month, day, hour, minute, second)
                .toEpochSecond(java.time.ZoneOffset.UTC) - offsetSeconds
        }.getOrNull()
    }

    /**
     * A factory that will not fetch anything or expand anything.
     *
     * An XMLTV file is downloaded from whatever address a playlist names, so it is untrusted input.
     * External entities are how such a file reads local files or reaches the network on the
     * reader's behalf, and entity expansion is how a few kilobytes becomes gigabytes of memory.
     * Both are refused outright rather than bounded.
     */
    private fun newSecureFactory(): XMLInputFactory =
        XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_COALESCING, true)
        }

    /** The gzip magic number, read without consuming it. */
    private fun BufferedInputStream.looksGzipped(): Boolean {
        mark(2)
        val first = read()
        val second = read()
        reset()
        return first == 0x1f && second == 0x8b
    }

    private const val BUFFER_BYTES = 64 * 1024

    /**
     * Enough for a fortnight of a large provider, and short of what would exhaust memory.
     *
     * A guide of five hundred channels at forty programmes a day for two weeks is about 280,000.
     */
    const val DEFAULT_MAXIMUM_PROGRAMMES = 400_000

    private const val MAX_FIELD_LENGTH = 300
    private const val MAX_DESCRIPTION_LENGTH = 2_000
}
