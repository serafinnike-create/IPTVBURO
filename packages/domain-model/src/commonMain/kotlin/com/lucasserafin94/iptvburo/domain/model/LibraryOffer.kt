package com.lucasserafin94.iptvburo.domain.model

/**
 * Finding a title from a streaming catalogue in the user's own playlist.
 *
 * This is the part of "where can I watch this" that only BURO can answer. Every other row on the
 * screen says "subscribe, rent or buy"; this one says "you already have it, press play" — and it is
 * the reason the feature is worth having rather than a copy of a comparison site.
 *
 * It is also the row most damaging to get wrong. A false "you have this" sends the user to a title
 * that is not there, and after that they have no reason to trust the rows that are correct. So the
 * bar is [LibraryMatchingPolicy]'s: only CONFIRMED or HIGH_CONFIDENCE produces an offer, and
 * anything weaker produces nothing at all rather than a maybe.
 */
data class LibraryLookup(
    val offer: StreamingOffer,
    /** Which local item to open. The caller resolves this to a playable target at press time. */
    val localContentId: String,
    val match: LibraryMatch,
)

object LibraryOfferPolicy {
    /** The provider standing for the user's own playlist. */
    val USER_LIBRARY_PROVIDER: StreamingProvider =
        // The product's own name, not "your list": the row carries the BURO mark beside it, and a
        // generic label next to a logo reads as a mismatch.
        StreamingProvider.of(USER_LIBRARY_PROVIDER_ID, "IPTV BURO")

    /**
     * The user's own copy of [external], if they have one.
     *
     * Null when nothing in [library] matches confidently enough to claim. Returning null rather
     * than a low-confidence guess is the whole safety property here: the screen has no way to
     * express "probably", and a caller given a maybe would render it as a fact.
     *
     * The offer carries no price and no launch target. It is [OfferType.USER_LIBRARY] — an origin,
     * not a purchase — and it opens inside the app rather than through the external launcher, so
     * there is nothing to hand to a browser.
     */
    fun findInLibrary(
        external: ExternalCandidate,
        library: List<LibraryCandidate>,
    ): LibraryLookup? {
        val match = LibraryMatchingPolicy.bestAutomaticMatch(library, external) ?: return null
        return LibraryLookup(
            offer = StreamingOffer(provider = USER_LIBRARY_PROVIDER, type = OfferType.USER_LIBRARY),
            localContentId = match.localContentId,
            match = match,
        )
    }

    /**
     * [offers] with the user's own copy added at the front, when they have one.
     *
     * The position is not what makes it first — [BestOfferPolicy] does that. Prepending only keeps
     * the input tidy for callers that skip ranking.
     */
    fun withLibraryOffer(
        offers: List<StreamingOffer>,
        external: ExternalCandidate,
        library: List<LibraryCandidate>,
    ): List<StreamingOffer> {
        val found = findInLibrary(external, library) ?: return offers
        // A catalogue should never be describing the user's own playlist, but if one ever did, two
        // "in your list" rows would appear. Cheap to prevent, confusing to debug later.
        val withoutDuplicate = offers.filterNot { offer -> offer.type == OfferType.USER_LIBRARY }
        return listOf(found.offer) + withoutDuplicate
    }
}

/**
 * An [ExternalTitle] described in the terms matching needs.
 *
 * The external id is carried through under its own namespace, so a catalogue and a playlist that
 * both know a title's TMDb id match on that rather than on their spelling of the name.
 */
fun ExternalTitle.asExternalCandidate(): ExternalCandidate =
    ExternalCandidate(
        externalContentId = id.key,
        title = title,
        year = year,
        kind =
            when (kind) {
                ExternalTitleKind.SERIES -> MatchKind.SERIES
                ExternalTitleKind.MOVIE -> MatchKind.MOVIE
            },
        externalIds = mapOf(id.namespace to id.value),
    )

/**
 * A [Channel] described in the terms matching needs.
 *
 * Kept as an extension rather than a field on [Channel] so the catalogue model stays free of
 * discovery concerns. The year comes from the provider when it supplied one; a title with no year
 * can still match on an external id, but never on its name alone — see [LibraryMatchingPolicy].
 */
fun Channel.asLibraryCandidate(): LibraryCandidate =
    LibraryCandidate(
        localContentId = id,
        title = name,
        year = year,
        kind =
            when (contentType) {
                CatalogContentType.SERIES -> MatchKind.SERIES
                CatalogContentType.EPISODE -> MatchKind.EPISODE
                // LIVE and UNKNOWN fall here. A live channel is not a work and should be filtered
                // out by the caller; treating it as a film is harmless because the title of a
                // channel will not match a film's, and the kind check rejects it against a series.
                CatalogContentType.MOVIE, CatalogContentType.LIVE, CatalogContentType.UNKNOWN -> MatchKind.MOVIE
            },
    )
