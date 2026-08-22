package com.lucasserafin94.iptvburo.domain.model

/**
 * How a catalogue is arranged and narrowed while browsing — the rules behind the filter bar.
 *
 * Pure and total: no clock, no I/O, no dependence on the order an adapter happened to emit. It lives
 * here rather than in a composable because "which genres exist" and "what does this filter select"
 * are product rules that have to be assertable without rendering anything, and because the desktop
 * and Android must not answer them differently.
 */

/**
 * How much room each entry gets in a catalogue grid.
 *
 * Mirrors the desktop's `CatalogLayout` so a household sees the same three choices on both. The ids
 * are stable strings rather than ordinals because they are persisted, and a reordered enum must not
 * silently change what someone chose.
 */
enum class CatalogueLayout(val id: String) {
    /** Large artwork with the title underneath. The default, and how a catalogue is browsed. */
    POSTER("poster"),

    /** Smaller artwork, more per row: for scanning a long category rather than admiring it. */
    COMPACT("compact"),

    /** One row per title, artwork small and the name given room. Best where names carry meaning. */
    LIST("list"),
    ;

    companion object {
        fun fromId(id: String?): CatalogueLayout = entries.firstOrNull { it.id == id } ?: POSTER
    }
}

/**
 * The order a catalogue grid is presented in.
 *
 * [PROVIDER] preserves whatever the source sent. It is the default because a provider's own order
 * often carries editorial meaning — new arrivals first, say — that any sort of ours would destroy.
 */
enum class CatalogueSort(val id: String) {
    PROVIDER("provider"),
    TITLE_ASC("title-asc"),
    TITLE_DESC("title-desc"),
    YEAR_DESC("year-desc"),
    YEAR_ASC("year-asc"),
    RATING_DESC("rating-desc"),
    ;

    companion object {
        fun fromId(id: String?): CatalogueSort = entries.firstOrNull { it.id == id } ?: PROVIDER
    }
}

/**
 * One item as the browsing rules see it.
 *
 * Deliberately not the full catalogue entry: filtering and sorting need four fields, and depending
 * on more would tie this to whichever screen happened to be calling.
 */
data class BrowsableItem(
    val id: String,
    val title: String,
    val genre: String?,
    val year: Int?,
    val rating: Double?,
)

/**
 * What the user has narrowed the catalogue to.
 *
 * Every field is optional and an unset field means "no restriction", so the empty selection is the
 * whole catalogue rather than nothing — the state a screen opens in.
 */
data class CatalogueFilter(
    val genre: String? = null,
    val year: Int? = null,
    val sort: CatalogueSort = CatalogueSort.PROVIDER,
) {
    val isActive: Boolean
        get() = genre != null || year != null || sort != CatalogueSort.PROVIDER
}

/**
 * The genres present in [items], in display order.
 *
 * Derived from the items rather than from a fixed list: a provider's genres are its own, and a
 * hard-coded set would be wrong for every catalogue but one. Split on the separators providers
 * actually use, because a single "Ação, Aventura" string is two genres to a person reading it.
 *
 * Case is normalised for grouping but the first spelling seen is what gets shown, so a catalogue
 * mixing "Ação" and "AÇÃO" produces one entry rather than two.
 */
fun availableGenres(items: List<BrowsableItem>): List<String> {
    val byKey = LinkedHashMap<String, String>()
    items.forEach { item ->
        item.genre.orEmpty().split(',', '/', '|', ';').forEach { part ->
            val clean = part.trim()
            if (clean.isNotEmpty()) byKey.getOrPut(clean.lowercase()) { clean }
        }
    }
    return byKey.values.sortedBy { it.lowercase() }
}

/**
 * The years present in [items], most recent first.
 *
 * Newest first because a catalogue is usually browsed for what is new; an ascending list would put
 * 1954 at the top of a picker whose first entries are the ones most likely to be wanted.
 */
fun availableYears(items: List<BrowsableItem>): List<Int> =
    items.mapNotNull(BrowsableItem::year).distinct().sortedDescending()

/**
 * [items] narrowed by [filter] and arranged by its sort.
 *
 * Matching a genre is a containment test on the item's whole genre string rather than equality,
 * because providers pack several genres into one field. It is deliberately case-insensitive: a
 * catalogue that spells the same genre two ways must not hide half of it behind a filter.
 *
 * Ordering is fully determined — every sort falls back to the title, and then to the id — so the
 * same catalogue always renders in the same order. Without that tie-break, two identically titled
 * entries could swap places between recompositions and the grid would appear to shuffle itself.
 */
fun applyCatalogueFilter(
    items: List<BrowsableItem>,
    filter: CatalogueFilter,
): List<BrowsableItem> {
    val genreKey = filter.genre?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
    val matching =
        items.filter { item ->
            val genreMatches =
                genreKey == null || item.genre?.lowercase()?.contains(genreKey) == true
            val yearMatches = filter.year == null || item.year == filter.year
            genreMatches && yearMatches
        }

    val byTitle = compareBy<BrowsableItem> { it.title.lowercase() }.thenBy(BrowsableItem::id)
    return when (filter.sort) {
        // Untouched: the provider's own order is the answer, and sorting it would discard it.
        CatalogueSort.PROVIDER -> matching
        CatalogueSort.TITLE_ASC -> matching.sortedWith(byTitle)
        CatalogueSort.TITLE_DESC ->
            matching.sortedWith(
                compareByDescending<BrowsableItem> { it.title.lowercase() }.thenBy(BrowsableItem::id),
            )
        // Undated entries sort last rather than counting as year zero, which would put them above
        // everything in an ascending sort and make the catalogue look broken.
        CatalogueSort.YEAR_DESC ->
            matching.sortedWith(
                compareByDescending<BrowsableItem> { it.year ?: Int.MIN_VALUE }.then(byTitle),
            )
        CatalogueSort.YEAR_ASC ->
            matching.sortedWith(compareBy<BrowsableItem> { it.year ?: Int.MAX_VALUE }.then(byTitle))
        CatalogueSort.RATING_DESC ->
            matching.sortedWith(
                compareByDescending<BrowsableItem> { it.rating ?: Double.NEGATIVE_INFINITY }
                    .then(byTitle),
            )
    }
}
