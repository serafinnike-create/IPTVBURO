package com.lucasserafin94.iptvburo.xtream

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Pull parser for the very large JSON arrays returned by Xtream panels.
 *
 * Only one provider object exists as a Kotlin object at a time. Unknown fields are skipped at the
 * reader level, so artwork metadata or provider extensions cannot turn a 500k-item import into a
 * second in-memory JSON tree.
 */
internal class XtreamCatalogStreamParser(
    private val contentType: XtreamContentType,
    private val maximumItems: Int,
    private val sanitizeArtwork: (String) -> String?,
) {
    fun parse(
        input: InputStream,
        onItem: (XtreamCatalogItem) -> Unit,
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
                    "The Xtream catalog exceeded the configured item limit.",
                )
            }
            val item = readCatalogObject(reader)
            if (item == null) {
                skipped += 1
            } else {
                onItem(item)
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

                JsonToken.BEGIN_OBJECT -> {
                    // Some panels return {"0": {...}, "1": {...}} instead of an array.
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

                JsonToken.BOOLEAN -> {
                    if (reader.nextBoolean()) {
                        throw invalidCatalog()
                    }
                }

                JsonToken.NULL -> reader.nextNull()
                else -> throw invalidCatalog()
            }
        }

        return XtreamStreamSummary(
            itemCount = emitted,
            skippedItemCount = skipped,
        )
    }

    private fun readCatalogObject(reader: JsonReader): XtreamCatalogItem? {
        var providerId: String? = null
        var name: String? = null
        var title: String? = null
        var categoryId: String? = null
        var categoryIds: List<String> = emptyList()
        var containerExtension: String? = null
        var artworkUrl: String? = null
        var year: Int? = null
        var rating: Double? = null
        var addedAt: Long? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                idField -> providerId = reader.readScalarString()
                "name" -> name = reader.readScalarString()
                "title" -> title = reader.readScalarString()
                "category_id" -> categoryId = reader.readScalarString()
                "category_ids" -> categoryIds = reader.readProviderIds()
                "container_extension" -> containerExtension = reader.readScalarString()
                artworkField -> artworkUrl = reader.readScalarString()
                "year" -> year = reader.readScalarString()?.toIntOrNull()
                "rating_5based" -> rating = reader.readScalarString()?.toDoubleOrNull()
                "rating" -> {
                    val fallback = reader.readScalarString()?.toDoubleOrNull()
                    if (rating == null) rating = fallback
                }
                "added" -> addedAt = reader.readScalarString()?.toLongOrNull()
                "last_modified" -> {
                    val fallback = reader.readScalarString()?.toLongOrNull()
                    if (addedAt == null) addedAt = fallback
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val safeId = providerId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val safeName = (name ?: title)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val mergedCategories =
            buildList {
                categoryId?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
                categoryIds.forEach { value ->
                    value.trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }.distinct()

        return XtreamCatalogItem(
            providerId = safeId.take(MAX_PROVIDER_ID_LENGTH),
            name = safeName.take(MAX_NAME_LENGTH),
            contentType = contentType,
            categoryIds = mergedCategories,
            containerExtension = containerExtension?.sanitizeExtension(),
            artworkUrl = artworkUrl?.let(sanitizeArtwork),
            year = year,
            rating = rating,
            addedAtEpochSeconds = addedAt,
        )
    }

    private fun JsonReader.readProviderIds(): List<String> =
        when (peek()) {
            JsonToken.BEGIN_ARRAY -> buildList {
                beginArray()
                while (hasNext()) {
                    readScalarString()?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
                }
                endArray()
            }
            JsonToken.STRING,
            JsonToken.NUMBER,
            -> readScalarString().orEmpty().parseProviderIdList()
            JsonToken.NULL -> {
                nextNull()
                emptyList()
            }
            else -> {
                skipValue()
                emptyList()
            }
        }

    private fun JsonReader.readScalarString(): String? =
        when (peek()) {
            JsonToken.STRING,
            JsonToken.NUMBER,
            -> nextString()
            JsonToken.BOOLEAN -> nextBoolean().toString()
            JsonToken.NULL -> {
                nextNull()
                null
            }
            else -> {
                skipValue()
                null
            }
        }

    private fun String.parseProviderIdList(): List<String> {
        val normalized = trim().removePrefix("[").removeSuffix("]")
        if (normalized.isEmpty()) return emptyList()
        return normalized
            .split(',')
            .asSequence()
            .map { value -> value.trim().trim('"', '\'') }
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
    }

    private fun String.sanitizeExtension(): String? =
        trim()
            .removePrefix(".")
            .lowercase()
            .takeIf { EXTENSION_PATTERN.matches(it) }

    private val idField: String
        get() = if (contentType == XtreamContentType.SERIES) "series_id" else "stream_id"

    private val artworkField: String
        get() = if (contentType == XtreamContentType.SERIES) "cover" else "stream_icon"

    private fun invalidCatalog(): XtreamClientException =
        XtreamClientException(
            XtreamFailureReason.INVALID_RESPONSE,
            "The Xtream catalog response is not a supported JSON collection.",
        )

    private companion object {
        const val MAX_PROVIDER_ID_LENGTH = 256
        const val MAX_NAME_LENGTH = 512
        val EXTENSION_PATTERN = Regex("[a-z0-9]{1,10}")
    }
}
