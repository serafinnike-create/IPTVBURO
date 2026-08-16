package com.lucasserafin94.iptvburo.metadata

import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.ProviderDeepLinks
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider

/**
 * Turning TMDb's watch-provider response into the offers the app ranks and renders.
 *
 * Kept apart from `TmdbClient` so the mapping can be tested without a server, and apart from the
 * domain so the domain stays free of any particular catalogue's shape.
 *
 * Two things this mapping deliberately does not do:
 *
 * - **It never produces a price.** TMDb returns none, in any bucket. A rental becomes
 *   [OfferType.RENT] with `price = null`, which [com.lucasserafin94.iptvburo.domain.model.BestOfferPolicy]
 *   already handles: the offer is listed but no "cheapest" claim is made about it. Filling in a
 *   plausible number would be inventing a fact the user could act on.
 * - **It never invents a title URL.** Destinations come from [ProviderDeepLinks], which searches the
 *   service for the name and falls back to the homepage, then to TMDb's own page. A fabricated
 *   `/title/12345` would 404 on a real company's site.
 */
object TmdbStreamingDiscovery {
    /**
     * The offers for one title, from one region's listing.
     *
     * Empty when nothing is known. The caller must not read that as "unavailable everywhere" — see
     * [com.lucasserafin94.iptvburo.metadata.TmdbClient.watchProviders], which returns null for
     * "we cannot say".
     */
    fun offersFrom(
        listing: TmdbWatchProviders,
        title: String,
    ): List<StreamingOffer> {
        val fallback = listing.tmdbWatchPageUrl

        // Order within a type does not matter here — BestOfferPolicy ranks the whole list — but the
        // buckets are read in a fixed order so the same response always maps identically.
        return buildList {
            listing.subscription.forEach { entry -> add(offer(entry, OfferType.SUBSCRIPTION, title, fallback)) }
            // TMDb's "ads" is genuinely ad-funded. Its "free" only promises no payment, which is a
            // weaker claim; both map here because the app has no third label, and promising ads on
            // something free is the less harmful of the two errors — the user is not charged either
            // way, and an unexpected absence of adverts is not a complaint.
            listing.withAds.forEach { entry -> add(offer(entry, OfferType.FREE_WITH_ADS, title, fallback)) }
            listing.free.forEach { entry -> add(offer(entry, OfferType.FREE_WITH_ADS, title, fallback)) }
            listing.rent.forEach { entry -> add(offer(entry, OfferType.RENT, title, fallback)) }
            listing.buy.forEach { entry -> add(offer(entry, OfferType.BUY, title, fallback)) }
        }.distinctBy { streamingOffer -> streamingOffer.provider.id to streamingOffer.type }
    }

    private fun offer(
        entry: TmdbWatchProvider,
        type: OfferType,
        title: String,
        fallbackUrl: String?,
    ): StreamingOffer {
        val provider = StreamingProvider.of(slugFor(entry.name), entry.name, entry.logoUrl)
        return StreamingOffer(
            provider = provider,
            type = type,
            // Never set. TMDb has no price to give, for any bucket.
            price = null,
            launchTarget = ProviderDeepLinks.bestTargetFor(provider.id, title, fallbackUrl),
        )
    }

    /**
     * The app's own slug for a service name.
     *
     * Derived from the display name rather than TMDb's numeric `provider_id`: the numbers are
     * JustWatch's identifiers, and keying our own table on them would tie the deep-link map to a
     * third party's numbering. A name-derived slug is stable enough — these brands rename rarely —
     * and readable in a stored preference, which the numbers would not be.
     */
    fun slugFor(providerName: String): String =
        providerName
            .trim()
            .lowercase()
            .replace("+", "-plus")
            .map { character -> if (character.isLetterOrDigit()) character else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
}
