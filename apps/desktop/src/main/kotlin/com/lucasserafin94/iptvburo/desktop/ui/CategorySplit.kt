package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.xtream.XtreamCategory

/**
 * One category, sorted into the selector it belongs in.
 *
 * The rail this replaces showed every category in one horizontal strip — thirty-odd chips scrolling
 * sideways, mixing "Acao" and "Aventura" with "Netflix" and "Amazon". Those are two different
 * questions ("what kind of film?" and "from which service?") and answering either meant scrolling
 * past the other.
 */
data class CategoryChoice(
    val id: String,
    /** The name with the section prefix already stripped. See [categoryLabel]. */
    val label: String,
    /** The service this category belongs to, when its name names one. */
    val provider: ProviderIdentity?,
)

/**
 * The categories split into services and everything else.
 *
 * A playlist's categories are flat and untyped: "Filmes | Netflix" and "Filmes | Acao" are the same
 * kind of record, and only the name says which is a service and which is a genre. So the split is
 * exactly [providerIdentityFor] — a category whose name carries a recognisable service goes to the
 * provider selector, and everything else is treated as a genre.
 *
 * ## Why the two selectors do not combine
 *
 * Each title belongs to one category, so a title filed under "Filmes | Netflix" is not also under
 * "Filmes | Acao". Asking for Netflix *and* Action would therefore return nothing for most
 * playlists — the intersection is genuinely empty rather than merely small. The screen selects one at
 * a time and clears the other, which is what the data can honestly support; crossing them would need
 * per-title genres from TMDb, which is a different feature.
 */
data class CategorySplit(
    val genres: List<CategoryChoice>,
    val providers: List<CategoryChoice>,
) {
    /** Whether a provider selector is worth drawing at all. */
    val hasProviders: Boolean
        get() = providers.isNotEmpty()

    /** The label for [id], from either group, or null when nothing matches. */
    fun labelFor(id: String?): String? {
        val wanted = id ?: return null
        return (genres + providers).firstOrNull { choice -> choice.id == wanted }?.label
    }

    /** The provider identity for [id], so the closed selector can show the right chip. */
    fun providerFor(id: String?): ProviderIdentity? {
        val wanted = id ?: return null
        return providers.firstOrNull { choice -> choice.id == wanted }?.provider
    }

    /** Whether [id] is one of the provider categories, which decides which selector owns it. */
    fun isProvider(id: String?): Boolean {
        val wanted = id ?: return false
        return providers.any { choice -> choice.id == wanted }
    }

    /**
     * The same split with each service's official mark attached.
     *
     * Applied here rather than inside [splitCategories] so the split stays a pure function of the
     * playlist: the logos arrive asynchronously from TMDb, and a split that reached for them would
     * be rebuilt on a schedule it does not control.
     */
    fun withLogos(logos: Map<String, String>): CategorySplit {
        if (logos.isEmpty() || providers.isEmpty()) return this
        return copy(
            providers =
                providers.map { choice ->
                    choice.copy(provider = choice.provider?.withLogoFrom(logos))
                },
        )
    }
}

/**
 * Splits [categories] into genres and services.
 *
 * Order is preserved within each group: the provider sends its categories in a deliberate order and
 * re-sorting them alphabetically would move "Lançamentos" away from the top, where providers
 * generally put it on purpose.
 *
 * Services are de-duplicated by label rather than by id. A playlist routinely carries "Filmes |
 * Netflix" and "Filmes | Netflix 4K" as separate categories, and two entries both reading "Netflix"
 * in the selector cannot be told apart — so the first one wins and keeps its own id.
 */
fun splitCategories(categories: List<XtreamCategory>): CategorySplit {
    val genres = mutableListOf<CategoryChoice>()
    val providers = mutableListOf<CategoryChoice>()
    val seenProviders = mutableSetOf<String>()

    categories.forEach { category ->
        val label = category.name.categoryLabel()
        val identity = providerIdentityFor(category.name)
        val choice = CategoryChoice(id = category.providerId, label = label, provider = identity)
        if (identity == null) {
            genres += choice
        } else if (seenProviders.add(identity.label)) {
            // Named for the service rather than for the category, so the selector reads "Netflix"
            // and not "Netflix 4K" — the qualifier belongs to the playlist's filing, not to the
            // question being asked.
            providers += choice.copy(label = identity.label)
        }
    }

    return CategorySplit(genres = genres, providers = providers)
}
