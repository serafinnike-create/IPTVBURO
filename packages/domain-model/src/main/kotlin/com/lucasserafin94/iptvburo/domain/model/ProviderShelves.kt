package com.lucasserafin94.iptvburo.domain.model

/**
 * Grouping titles by the service that carries them — GDD 9, the "browse by service" entry point.
 *
 * [BestOfferPolicy] answers "where can I watch *this film*". This answers the other direction:
 * "what is on *this service*". Both read the same [StreamingOffer] list, which is deliberate — one
 * catalogue response feeds both views, so a title can never appear on a shelf that its own offers
 * do not support.
 *
 * Pure and total: no clock, no I/O, no ordering that depends on the order an adapter happened to
 * emit. It lives here rather than in a composable because "which shelves exist and what is on them"
 * is a product rule that has to be assertable without rendering anything.
 */

/**
 * One provider's shelf: the service, and the titles it carries.
 *
 * [titles] holds whole [ExternalTitleDetails] rather than ids so the row can render a title and
 * hand the same object back to the existing offers screen without a second lookup that could miss.
 *
 * A shelf is never empty. [streamingShelves] drops a provider with nothing on it rather than
 * emitting a heading over blank space, which reads as a fault rather than as an absence.
 */
data class ProviderShelf(
    val provider: StreamingProvider,
    val titles: List<ExternalTitleDetails>,
) {
    init {
        require(titles.isNotEmpty()) {
            "A provider shelf with no titles is a heading over blank space. Omit the shelf instead."
        }
    }

    /**
     * Whether every title on this shelf is synthetic.
     *
     * The UI badges each title individually; this exists so a shelf built entirely from fixture data
     * can be marked as a whole. It is a fact derived from the titles, never a flag someone sets, so
     * there is no way to present invented listings as real by declaring them so.
     */
    val isDemo: Boolean
        get() = titles.all { it.title.isDemo }

    val size: Int
        get() = titles.size
}

/**
 * Builds one shelf per provider from [titles].
 *
 * A title carried by three services appears on three shelves. That is the point of the view: the
 * user is asking what is on Netflix, and a film that is also on two other services is still on
 * Netflix. Deduplicating to a "primary" service would mean inventing a ranking this function has no
 * basis for — [BestOfferPolicy] ranks *offers for one title*, which is a different question.
 *
 * Rules that matter more than the grouping itself:
 *
 * - **The user's own library is not a service.** It is excluded by default via
 *   [includeUserLibrary]: it already has its own screen, and a "Sua lista" rail among Netflix and
 *   Prime would be a worse duplicate of it. The parameter exists because a later phase may want a
 *   combined view, not because the default is in doubt.
 * - **Availability is what the catalogue said.** A provider only gets a shelf from offers that
 *   actually name it. [OfferType.UNAVAILABLE] is excluded by default through
 *   [includeUnavailable]: an unavailable offer means "known, but not watchable here", and putting
 *   that title on the service's shelf would claim availability the catalogue explicitly denied.
 * - **One shelf per service.** Two offers from the same provider for the same title — a rental and
 *   a purchase, say — put the title on that shelf once, not twice.
 *
 * Ordering is fully determined so the same catalogue response always renders identically:
 * shelves by descending size, then by provider display name; titles within a shelf by name, then by
 * their stable external id to break a tie between two identically-named entries.
 *
 * @param titles the catalogue entries to arrange. May be empty, which yields no shelves.
 * @param includeUserLibrary whether the reserved user-library provider gets a shelf of its own.
 * @param includeUnavailable whether [OfferType.UNAVAILABLE] offers place a title on a shelf.
 * @param maxTitlesPerShelf caps a rail so one enormous provider cannot produce an unrenderable row.
 * @return shelves in display order. Never contains an empty shelf.
 */
fun streamingShelves(
    titles: List<ExternalTitleDetails>,
    includeUserLibrary: Boolean = false,
    includeUnavailable: Boolean = false,
    maxTitlesPerShelf: Int = DEFAULT_SHELF_SIZE,
): List<ProviderShelf> {
    require(maxTitlesPerShelf > 0) { "A shelf has to hold at least one title." }
    if (titles.isEmpty()) return emptyList()

    // Keyed by normalised provider id so a catalogue writing "Netflix" in one entry and "netflix"
    // in another produces one shelf rather than two half-full ones. The display name is taken from
    // the first offer that named the provider, so the casing the catalogue chose is preserved.
    val byProvider = LinkedHashMap<String, MutableProviderShelf>()

    titles.forEach { details ->
        details.offers.forEach offers@{ offer ->
            if (!includeUnavailable && offer.type == OfferType.UNAVAILABLE) return@offers
            if (!includeUserLibrary && offer.provider.isUserLibrary) return@offers

            val bucket =
                byProvider.getOrPut(offer.provider.id) { MutableProviderShelf(offer.provider) }
            // A set of ids, not a list: a provider offering the same film to rent *and* to buy
            // still carries it once.
            bucket.add(details)
        }
    }

    return byProvider.values
        .map { bucket -> bucket.toShelf(maxTitlesPerShelf) }
        .sortedWith(
            // Biggest shelf first: the service with most to show is the one worth scrolling. Name
            // breaks the tie so the order never depends on catalogue iteration order.
            compareByDescending<ProviderShelf> { it.size }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.provider.displayName },
        )
}

/**
 * The shelves for one provider only, by id.
 *
 * A convenience for "show me everything on this service" without the caller having to filter the
 * full result and handle the empty case itself. Null when the provider carries nothing.
 */
fun streamingShelfFor(
    titles: List<ExternalTitleDetails>,
    providerId: String,
    includeUnavailable: Boolean = false,
    maxTitlesPerShelf: Int = DEFAULT_SHELF_SIZE,
): ProviderShelf? {
    val wanted = StreamingProvider.normaliseId(providerId)
    return streamingShelves(
        titles = titles,
        // The caller named a provider explicitly; if that provider is the user's library, honouring
        // the request is more useful than silently returning null for a service that does carry
        // titles.
        includeUserLibrary = true,
        includeUnavailable = includeUnavailable,
        maxTitlesPerShelf = maxTitlesPerShelf,
    ).firstOrNull { it.provider.id == wanted }
}

/**
 * Titles per shelf.
 *
 * Matches the music and video rails so the three read as one product, and bounds a rail that a real
 * catalogue could otherwise fill with hundreds of entries.
 */
const val DEFAULT_SHELF_SIZE: Int = 18

/** Accumulator for one provider while grouping. Not part of the public contract. */
private class MutableProviderShelf(
    val provider: StreamingProvider,
) {
    private val seen = LinkedHashSet<String>()
    private val titles = ArrayList<ExternalTitleDetails>()

    fun add(details: ExternalTitleDetails) {
        if (seen.add(details.title.id.key)) titles.add(details)
    }

    fun toShelf(limit: Int): ProviderShelf =
        ProviderShelf(
            provider = provider,
            titles =
                titles
                    .sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER, ExternalTitleDetails::sortKey)
                            // Two films sharing a name still need a stable order between them.
                            .thenBy { it.title.id.key },
                    ).take(limit),
        )
}

/** The name a shelf sorts by. Extracted so the comparator reads as one thing. */
private val ExternalTitleDetails.sortKey: String
    get() = title.title
