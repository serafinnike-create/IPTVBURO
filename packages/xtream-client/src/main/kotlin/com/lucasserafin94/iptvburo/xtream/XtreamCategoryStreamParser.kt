package com.lucasserafin94.iptvburo.xtream

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Pull parser for the category list, matching [XtreamCatalogStreamParser].
 *
 * Categories used to go through the buffered `request()` path, which reads the whole body into a
 * `ByteArray`, decodes that into a `String` and then builds a full `JsonElement` tree — three
 * copies of the same response alive at once, under a 512 MiB ceiling. On a large provider that is a
 * very large allocation on the IO dispatcher during startup, which is what made the window freeze;
 * when it ran out of heap the resulting error was reported as an incompatible catalogue.
 *
 * The catalogue itself was already streamed for exactly these reasons. This does the same for the
 * one request that was left behind.
 */
internal class XtreamCategoryStreamParser(
    private val contentType: XtreamContentType,
    private val maximumItems: Int,
) {
    fun parse(
        input: InputStream,
        onItem: (XtreamCategory) -> Unit,
    ): XtreamStreamSummary {
        require(maximumItems > 0) { "maximumItems must be positive" }
        val decoder =
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val reader = JsonReader(InputStreamReader(input, decoder))
        reader.setStrictness(Strictness.STRICT)

        var emitted = 0
        var skipped = 0
        var visited = 0

        fun consumeEntry() {
            visited += 1
            if (visited > maximumItems) {
                throw XtreamClientException(
                    XtreamFailureReason.RESPONSE_TOO_LARGE,
                    "The Xtream category list exceeded the configured item limit.",
                )
            }
            val category = readCategoryObject(reader)
            if (category == null) {
                skipped += 1
            } else {
                onItem(category)
                emitted += 1
            }
        }

        reader.use {
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            consumeEntry()
                        } else {
                            reader.skipValue()
                            skipped += 1
                        }
                    }
                    reader.endArray()
                }

                // Same panel quirk the catalogue parser handles: an object keyed by index rather
                // than an array.
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName()
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            consumeEntry()
                        } else {
                            reader.skipValue()
                            skipped += 1
                        }
                    }
                    reader.endObject()
                }

                // `false` is how some panels say "nothing here". An empty category list is a
                // perfectly ordinary answer, so it is not an error.
                JsonToken.BOOLEAN -> {
                    if (reader.nextBoolean()) throw invalidCategories()
                }

                JsonToken.NULL -> reader.nextNull()
                else -> throw invalidCategories()
            }
        }

        return XtreamStreamSummary(itemCount = emitted, skippedItemCount = skipped)
    }

    /** Null when the object lacks an id or a name, which is what the buffered version skipped too. */
    private fun readCategoryObject(reader: JsonReader): XtreamCategory? {
        var providerId: String? = null
        var name: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "category_id" -> providerId = reader.readScalarOrNull()
                "category_name" -> name = reader.readScalarOrNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (providerId.isNullOrBlank() || name.isNullOrBlank()) return null
        if (providerId.length > MAXIMUM_FIELD_LENGTH || name.length > MAXIMUM_FIELD_LENGTH) return null
        return XtreamCategory(providerId, name, contentType)
    }

    /**
     * A string or a number as text, or null for anything else.
     *
     * Panels are inconsistent about whether `category_id` is `"12"` or `12`, and reading it as a
     * string only would throw on the numeric form.
     */
    private fun JsonReader.readScalarOrNull(): String? =
        when (peek()) {
            JsonToken.STRING -> nextString()
            JsonToken.NUMBER -> nextString()
            JsonToken.NULL -> {
                nextNull()
                null
            }
            else -> {
                skipValue()
                null
            }
        }

    private fun invalidCategories(): XtreamClientException =
        XtreamClientException(
            XtreamFailureReason.INVALID_RESPONSE,
            "The Xtream category response is not a supported JSON collection.",
        )

    private companion object {
        /** Generous for a category name, and short enough that a hostile field cannot grow. */
        const val MAXIMUM_FIELD_LENGTH = 512
    }
}
