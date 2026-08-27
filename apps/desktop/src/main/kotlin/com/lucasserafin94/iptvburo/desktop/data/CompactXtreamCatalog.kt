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

    /**
     * Days of catch-up per channel; zero means none, which is also the default.
     *
     * A plain IntArray rather than a nullable column: zero and absent mean the same thing here — a
     * channel keeping no recording — so a parallel `has` array would carry no extra information and
     * cost a bit per row across forty thousand.
     */
    private var catchUpDays = IntArray(INITIAL_CAPACITY)

    /**
     * Content key to row, built on first lookup rather than on insert.
     *
     * Empty until something asks, which is the common case: most sessions never open the history
     * screen, and deriving an identity per row costs a full object construction. See
     * [indexOfContentKey] for why the lookup could not stay a scan.
     */
    private val contentKeyIndex = HashMap<String, Int>()

    /** How much of the catalogue [contentKeyIndex] covers, so a later arrival extends it. */
    private var indexedUpTo = 0

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
        // Fall back to a year written into the title. Providers frequently leave the `year` field
        // empty while naming the item "Movie (1998)", and without this those films were invisible
        // to the release-year filter — or, worse, indistinguishable from an actual new release.
        val resolvedYear = item.year ?: ContentIdentity.yearFromTitle(item.name)
        resolvedYear?.let { value ->
            years[index] = value
            hasYear[index] = true
        }
        item.rating?.let { value ->
            ratings[index] = value
            hasRating[index] = true
        }
        // Dropped here once, which made every channel look as though it kept no recording: the
        // catalogue is rebuilt from these columns, so a field the columns do not hold is a field
        // the app never sees again.
        catchUpDays[index] = item.catchUpDays ?: 0
    }

    fun matches(
        index: Int,
        categoryId: String?,
        normalizedQuery: String,
        releaseYear: Int? = null,
        minimumRating: Double? = null,
        allowedIdentities: Set<ContentIdentity>? = null,
    ): Boolean {
        require(index in 0 until size)

        // Cheapest test first, and each one returns rather than being folded into a single
        // expression at the end.
        //
        // Every test used to be computed as a `val` before any of them was consulted, so all five
        // ran for all 41,698 rows on every page turn — including `identityAt`, which builds a whole
        // XtreamCatalogItem to read one field off it. That is the expensive call this class was
        // written to avoid: 31 ms against 10 ms over the catalogue, per the note on `nameAt`, and a
        // page is turned on every keystroke in the search box. It ran even when the category test
        // above it had already excluded the row.
        //
        // Ordered by cost: three array reads, then a substring scan of the name, then the object
        // build. The last is reached only by rows that have survived everything else, and only when
        // the favourites filter is actually in play.
        if (categoryId != null &&
            encodedCategoryIds[index]?.contains("$CATEGORY_SEPARATOR$categoryId$CATEGORY_SEPARATOR") != true
        ) {
            return false
        }
        if (releaseYear != null && !(hasYear[index] && years[index] == releaseYear)) return false
        // An unrated title is excluded once a minimum is asked for. Treating "no rating" as good
        // enough would fill a "4+ stars" filter with titles that were never scored at all.
        if (minimumRating != null && !(hasRating[index] && ratings[index] >= minimumRating)) return false
        if (normalizedQuery.isNotEmpty() &&
            !names[index].contains(normalizedQuery, ignoreCase = true)
        ) {
            return false
        }
        // Matched on content identity rather than provider id. Provider ids are per-list numbering,
        // so filtering favourites by them showed the wrong titles once the user changed list.
        return allowedIdentities == null || identityAt(index) in allowedIdentities
    }

    /**
     * Identity of the row.
     *
     * Deliberately built from [itemAt] rather than from the columns directly. `append` already
     * resolves a missing year out of the title, so reading `years[index]` here produced an identity
     * carrying a year while `XtreamCatalogItem.contentIdentity()` — which sees the provider's raw
     * null — produced one without. Favouriting stored the second form and the favourites filter
     * searched for the first, so a favourited film never appeared in Favourites.
     */
    fun identityAt(index: Int): ContentIdentity = itemAt(index).contentIdentity()

    /**
     * One column of one row, without building the row.
     *
     * Paging asks two questions of every match — is it allowed for a Kids profile, is its category
     * locked — and both were answered by constructing a full [XtreamCatalogItem] and reading two
     * fields off it. On a catalogue of this size that is the bulk of the work in turning a page,
     * for objects immediately discarded.
     *
     * Measured over 41,698 rows: 31 ms building each row against 10 ms reading the two columns.
     * A page is turned on every keystroke in the catalogue's search box.
     */
    fun nameAt(index: Int): String {
        require(index in 0 until size)
        return names[index]
    }

    /**
     * The provider's own id for the row, without building the row.
     *
     * Same reasoning as [nameAt]: the Serviço filter tests one string per row over tens of thousands
     * of them, and `itemAt` would allocate a whole item to read a single field.
     */
    fun providerIdAt(index: Int): String {
        require(index in 0 until size)
        return providerIds[index]
    }

    fun categoryIdsAt(index: Int): List<String> {
        require(index in 0 until size)
        return encodedCategoryIds[index].decodeCategoryIds()
    }

    /**
     * Covers this provider hands out for thousands of titles at once, so [itemAt] can drop them.
     *
     * Held here rather than filtered by each screen because every reader goes through [itemAt];
     * the previous attempt corrected the screens one at a time and missed the catalogue grid, which
     * was the one in the screenshot. The arena itself is untouched, so [artworkUrls] still reports
     * what the provider actually sent and the count stays honest.
     */
    private var placeholderArtwork: Set<String> = emptySet()

    fun markPlaceholderArtwork(urls: Set<String>) {
        placeholderArtwork = urls
    }

    fun itemAt(index: Int): XtreamCatalogItem {
        require(index in 0 until size)
        return XtreamCatalogItem(
            providerId = providerIds[index],
            name = names[index],
            contentType = contentType,
            categoryIds = encodedCategoryIds[index].decodeCategoryIds(),
            containerExtension = containerExtensions[index],
            artworkUrl = usableArtworkAt(index),
            year = years[index].takeIf { hasYear[index] },
            rating = ratings[index].takeIf { hasRating[index] },
            addedAtEpochSeconds = null,
            catchUpDays = catchUpDays[index].takeIf { days -> days > 0 },
        )
    }

    fun itemByProviderId(providerId: String): XtreamCatalogItem? {
        val index = providerIds.indexOf(providerId)
        return index.takeIf { it >= 0 }?.let(::itemAt)
    }

    /**
     * The row whose content identity is [contentKey], or -1.
     *
     * Backed by an index built on first use, because the obvious implementation is quadratic in a
     * place that shows: identities are not stored — [identityAt] constructs a whole
     * `XtreamCatalogItem` per row to derive one — so a linear scan over a 41,698-item catalogue
     * built 41,698 objects per lookup. The history screen does two hundred of those, and its search
     * box re-derives the list on every keystroke.
     *
     * Measured on a 41,698-item catalogue, 200 lookups — one history screen:
     *
     * ```
     * linear scan (old):      4238 ms
     * indexed, first call:      56 ms
     * indexed, repeat:           0 ms
     * ```
     *
     * The map is deliberately not maintained by [add]: catalogues are filled once at load and read
     * for the rest of the session, so paying per insert would be the wrong trade. Instead the index
     * records how far it was built, and a later arrival extends it rather than invalidating it.
     */
    fun indexOfContentKey(contentKey: String): Int {
        contentKeyIndex[contentKey]?.let { return it }
        // Only the rows added since the index was last extended. First call covers everything.
        while (indexedUpTo < size) {
            val key = identityAt(indexedUpTo).key
            // putIfAbsent semantics: two rows can share an identity — the same film listed twice by
            // the provider — and the first is the one every other part of the app resolves to.
            if (key !in contentKeyIndex) contentKeyIndex[key] = indexedUpTo
            indexedUpTo += 1
        }
        return contentKeyIndex[contentKey] ?: -1
    }

    private fun ensurePrimitiveCapacity(required: Int) {
        if (required <= years.size) return
        val nextCapacity = maxOf(required, years.size.coerceAtLeast(1) * 2)
        years = years.copyOf(nextCapacity)
        hasYear = hasYear.copyOf(nextCapacity)
        ratings = ratings.copyOf(nextCapacity)
        hasRating = hasRating.copyOf(nextCapacity)
        catchUpDays = catchUpDays.copyOf(nextCapacity)
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

    /**
     * Every artwork address in this catalogue, in order.
     *
     * Reads the artwork column directly rather than walking [itemAt]: rebuilding a domain object
     * per row costs about 31 ms over forty thousand of them, and none of the other fields are
     * wanted here. Lazy, so a caller that stops early pays only for what it read.
     */
    fun artworkUrls(): Sequence<String?> = (0 until size).asSequence().map(::artworkAt)

    /**
     * The cover for a row, or null when the provider's cover is one of its placeholders.
     *
     * Null rather than the placeholder address so the reader falls through to what it already does
     * for a title with no cover — the readable title card, the TMDb lookup, the adult source.
     */
    private fun usableArtworkAt(index: Int): String? =
        artworkAt(index)?.takeIf { url -> url.trim() !in placeholderArtwork }

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
