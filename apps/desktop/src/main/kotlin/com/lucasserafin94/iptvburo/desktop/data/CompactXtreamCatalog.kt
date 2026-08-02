package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.nio.charset.StandardCharsets

/** Maps the provider's content type onto the identity namespace. */
internal fun XtreamContentType.toContentKind(): ContentKind =
    when (this) {
        XtreamContentType.LIVE -> ContentKind.LIVE
        XtreamContentType.MOVIE -> ContentKind.MOVIE
        XtreamContentType.SERIES -> ContentKind.SERIES
    }

/**
 * Session-only, columnar catalog index for the desktop application.
 *
 * Keeping one domain object and multiple backing collections per row is very expensive at provider
 * scale. This index stores the artwork column in a compact UTF-8 arena and reconstructs domain
 * objects only for the current small page.
 */
internal class CompactXtreamCatalog(
    private val contentType: XtreamContentType,
) {
    private val providerIds = ArrayList<String>()
    private val names = ArrayList<String>()
    private val encodedCategoryIds = ArrayList<String?>()
    private val containerExtensions = ArrayList<String?>()
    private var artworkOffsets = IntArray(INITIAL_CAPACITY)
    private var artworkLengths = IntArray(INITIAL_CAPACITY)
    private var artworkBytes = ByteArray(INITIAL_ARTWORK_CAPACITY)
    private var artworkByteCount = 0
    private var years = IntArray(INITIAL_CAPACITY)
    private var hasYear = BooleanArray(INITIAL_CAPACITY)
    private var ratings = DoubleArray(INITIAL_CAPACITY)
    private var hasRating = BooleanArray(INITIAL_CAPACITY)

    val size: Int
        get() = providerIds.size

    fun add(item: XtreamCatalogItem) {
        require(item.contentType == contentType) { "Catalog content type mismatch." }
        ensurePrimitiveCapacity(size + 1)
        val index = size
        providerIds += item.providerId
        names += item.name
        encodedCategoryIds += item.categoryIds.encodeCategoryIds()
        containerExtensions += item.containerExtension
        storeArtwork(index, item.artworkUrl)
        item.year?.let { value ->
            years[index] = value
            hasYear[index] = true
        }
        item.rating?.let { value ->
            ratings[index] = value
            hasRating[index] = true
        }
    }

    fun matches(
        index: Int,
        categoryId: String?,
        normalizedQuery: String,
        releaseYear: Int? = null,
        allowedIdentities: Set<ContentIdentity>? = null,
    ): Boolean {
        require(index in 0 until size)
        val matchesCategory =
            categoryId == null ||
                encodedCategoryIds[index]?.contains("$CATEGORY_SEPARATOR$categoryId$CATEGORY_SEPARATOR") == true
        val matchesYear = releaseYear == null || hasYear[index] && years[index] == releaseYear
        // Matched on content identity rather than provider id. Provider ids are per-list numbering,
        // so filtering favourites by them showed the wrong titles once the user changed list.
        val matchesLibrary = allowedIdentities == null || identityAt(index) in allowedIdentities
        return matchesCategory && matchesYear && matchesLibrary &&
            (normalizedQuery.isEmpty() || names[index].contains(normalizedQuery, ignoreCase = true))
    }

    /** Identity of the row, derived from the title and year the provider supplied. */
    fun identityAt(index: Int): ContentIdentity {
        require(index in 0 until size)
        val name = names[index]
        return ContentIdentity.of(
            kind = contentType.toContentKind(),
            title = name,
            year = years[index].takeIf { hasYear[index] } ?: ContentIdentity.yearFromTitle(name),
        )
    }

    fun itemAt(index: Int): XtreamCatalogItem {
        require(index in 0 until size)
        return XtreamCatalogItem(
            providerId = providerIds[index],
            name = names[index],
            contentType = contentType,
            categoryIds = encodedCategoryIds[index].decodeCategoryIds(),
            containerExtension = containerExtensions[index],
            artworkUrl = artworkAt(index),
            year = years[index].takeIf { hasYear[index] },
            rating = ratings[index].takeIf { hasRating[index] },
            addedAtEpochSeconds = null,
        )
    }

    fun itemByProviderId(providerId: String): XtreamCatalogItem? {
        val index = providerIds.indexOf(providerId)
        return index.takeIf { it >= 0 }?.let(::itemAt)
    }

    private fun ensurePrimitiveCapacity(required: Int) {
        if (required <= years.size) return
        val nextCapacity = maxOf(required, years.size.coerceAtLeast(1) * 2)
        years = years.copyOf(nextCapacity)
        hasYear = hasYear.copyOf(nextCapacity)
        ratings = ratings.copyOf(nextCapacity)
        hasRating = hasRating.copyOf(nextCapacity)
        artworkOffsets = artworkOffsets.copyOf(nextCapacity)
        artworkLengths = artworkLengths.copyOf(nextCapacity)
    }

    private fun storeArtwork(index: Int, artworkUrl: String?) {
        val encoded =
            artworkUrl
                ?.takeIf(String::isNotBlank)
                ?.toByteArray(StandardCharsets.UTF_8)
                ?.takeIf { it.size <= MAX_ARTWORK_URL_BYTES }
                ?: return
        ensureArtworkCapacity(artworkByteCount + encoded.size)
        encoded.copyInto(artworkBytes, destinationOffset = artworkByteCount)
        artworkOffsets[index] = artworkByteCount
        artworkLengths[index] = encoded.size
        artworkByteCount += encoded.size
    }

    private fun artworkAt(index: Int): String? {
        val length = artworkLengths[index]
        if (length == 0) return null
        return String(
            artworkBytes,
            artworkOffsets[index],
            length,
            StandardCharsets.UTF_8,
        )
    }

    private fun ensureArtworkCapacity(required: Int) {
        if (required <= artworkBytes.size) return
        var nextCapacity = artworkBytes.size.coerceAtLeast(1)
        while (nextCapacity < required) {
            nextCapacity = maxOf(required, nextCapacity * 2)
        }
        artworkBytes = artworkBytes.copyOf(nextCapacity)
    }

    private fun List<String>.encodeCategoryIds(): String? =
        takeIf(List<String>::isNotEmpty)
            ?.joinToString(
                separator = CATEGORY_SEPARATOR.toString(),
                prefix = CATEGORY_SEPARATOR.toString(),
                postfix = CATEGORY_SEPARATOR.toString(),
            )

    private fun String?.decodeCategoryIds(): List<String> =
        this
            ?.trim(CATEGORY_SEPARATOR)
            ?.takeIf(String::isNotEmpty)
            ?.split(CATEGORY_SEPARATOR)
            .orEmpty()

    private companion object {
        const val INITIAL_CAPACITY = 1_024
        const val INITIAL_ARTWORK_CAPACITY = 64 * 1_024
        const val MAX_ARTWORK_URL_BYTES = 16 * 1_024
        const val CATEGORY_SEPARATOR = '\u001F'
    }
}
