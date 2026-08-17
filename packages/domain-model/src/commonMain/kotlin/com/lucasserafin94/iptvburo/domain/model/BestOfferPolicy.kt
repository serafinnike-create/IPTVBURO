package com.lucasserafin94.iptvburo.domain.model

/**
 * Why an offer landed where it did in the ranking.
 *
 * The ranking exists to be explained, not merely obeyed. A user shown "watch on X" and no reason
 * has to take the app's word for it; a user shown "you already have this in your list" or "cheapest
 * rental" can check the claim. Carrying the reason in the result also means the UI never has to
 * re-derive it from the ordering and get it subtly wrong.
 */
enum class OfferReason {
    /** Already in the user's own playlist. Nothing outranks this. */
    IN_YOUR_LIBRARY,

    /** Included with a service the user says they subscribe to. */
    INCLUDED_IN_YOUR_SUBSCRIPTION,

    /** Legally free, funded by advertising. No payment and no subscription. */
    FREE_WITH_ADS,

    /** The cheapest rental among those quoted in a comparable currency. */
    CHEAPEST_RENTAL,

    /** A rental, but not the cheapest one available. Still shown. */
    RENTAL,

    /** Available to buy outright. */
    PURCHASE,

    /** On a service the user has not said they subscribe to; watching means subscribing first. */
    REQUIRES_NEW_SUBSCRIPTION,

    /** Known to the catalogue but not watchable anywhere it can see. */
    NOT_AVAILABLE,
}

/**
 * One offer plus the explanation for its position.
 *
 * [rank] is the tier the offer fell into. Offers can share a rank — two rentals at different prices
 * sit in different tiers, but two purchases sit in the same one — so [rank] is an ordering key, not
 * a unique position.
 */
data class RankedOffer(
    val offer: StreamingOffer,
    val reason: OfferReason,
    val rank: Int,
) {
    /** Convenience for the UI: the provider behind this offer. */
    val provider: StreamingProvider
        get() = offer.provider
}

/**
 * The outcome of ranking a title's offers.
 *
 * Always defined, including for a title with no offers at all: [best] is then null and [all] is
 * empty. Callers get a value to render rather than an exception to catch, because "we found nowhere
 * to watch this" is an ordinary answer from a discovery catalogue, not a fault.
 */
data class OfferRanking(
    val all: List<RankedOffer>,
) {
    /** The recommended offer, or null when there are none. */
    val best: RankedOffer?
        get() = all.firstOrNull()

    /** Every offer other than the recommended one, in ranked order. Never hidden from the user. */
    val alternatives: List<RankedOffer>
        get() = all.drop(1)

    val isEmpty: Boolean
        get() = all.isEmpty()

    companion object {
        /** The defined result for a title with no known offers. */
        val EMPTY: OfferRanking = OfferRanking(emptyList())
    }
}

/**
 * Orders the ways a title can be watched, cheapest and most-already-paid-for first.
 *
 * The order is a product rule from the "Assinaturas" area:
 *
 * 1. the user's own library — they already have the file;
 * 2. a subscription they have told us they pay for — no new spend;
 * 3. legally free with ads — no spend, but with a cost in attention;
 * 4. rental, cheapest first;
 * 5. purchase;
 * 6. a subscription they do not have — real money, recurring;
 * 7. nothing available.
 *
 * Two rules matter more than the order itself:
 *
 * - **Nothing is hidden.** Every valid offer passed in comes back out. The policy sorts and
 *   explains; it never filters. A user who wants to buy rather than rent, or who subscribes to a
 *   service the app does not know about, must still be able to see that option.
 * - **The user's library is not a free offer.** It ranks first and carries no price, because the
 *   product does not give content away — the user already owns what is in their own list.
 *
 * Pure and total: no clock, no store, no I/O, and no input produces an exception.
 */
object BestOfferPolicy {
    /**
     * Ranks [offers] for a user with the stated [preference].
     *
     * Ordering within a tier is stable: offers that tie fall back to provider display name, so the
     * same catalogue response always renders in the same order rather than depending on the order
     * the adapter happened to emit.
     */
    fun rank(
        offers: List<StreamingOffer>,
        preference: UserStreamingPreference = UserStreamingPreference.NONE,
    ): OfferRanking {
        if (offers.isEmpty()) return OfferRanking.EMPTY

        val cheapestRental = cheapestRentalIn(offers, preference)

        val ranked =
            offers.map { offer ->
                val reason = reasonFor(offer, preference, cheapestRental)
                RankedOffer(offer = offer, reason = reason, rank = rankOf(reason))
            }

        return OfferRanking(
            ranked.sortedWith(
                compareBy<RankedOffer> { it.rank }
                    // Within a tier of rentals or purchases, the cheaper one reads first.
                    .thenBy { it.offer.price?.amountMinor ?: Long.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.provider.displayName },
            ),
        )
    }

    /**
     * Convenience for callers that only need the recommendation.
     *
     * Null when there is nothing to recommend, mirroring [OfferRanking.best].
     */
    fun best(
        offers: List<StreamingOffer>,
        preference: UserStreamingPreference = UserStreamingPreference.NONE,
    ): RankedOffer? = rank(offers, preference).best

    /**
     * The cheapest rental, or null when none can be compared.
     *
     * Rentals without a price are excluded: an unpriced rental cannot be claimed to be the cheapest.
     *
     * Currency is grouped, never converted. The app holds no exchange rates, and inventing one to
     * declare a EUR rental cheaper than a BRL rental would be a fabricated comparison shown to the
     * user as a fact. When the user has stated a [UserStreamingPreference.preferredCurrency], only
     * that currency competes; otherwise the largest group of same-currency rentals does, which is
     * the best available guess at "the currency this user is being quoted in".
     */
    private fun cheapestRentalIn(
        offers: List<StreamingOffer>,
        preference: UserStreamingPreference,
    ): StreamingOffer? {
        val pricedRentals =
            offers.filter { offer ->
                offer.type == OfferType.RENT && offer.price != null
            }
        if (pricedRentals.isEmpty()) return null

        val byCurrency = pricedRentals.groupBy { requireNotNull(it.price).currencyCode }

        val preferred = preference.preferredCurrency?.trim()?.uppercase()?.takeIf(String::isNotBlank)
        val competing =
            when {
                preferred != null -> byCurrency[preferred] ?: return null
                byCurrency.size == 1 -> byCurrency.values.first()
                // Ambiguous: several currencies and no stated preference. Pick the largest group so
                // a single stray foreign-currency quote cannot make itself "cheapest", and break a
                // tie by currency code so the choice is deterministic.
                else ->
                    byCurrency
                        .entries
                        .sortedWith(compareByDescending<Map.Entry<String, List<StreamingOffer>>> { it.value.size }.thenBy { it.key })
                        .first()
                        .value
            }

        return competing.minWithOrNull(
            compareBy<StreamingOffer> { requireNotNull(it.price).amountMinor }
                // Deterministic when two providers quote the same price.
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.provider.displayName },
        )
    }

    /** Classifies a single offer. The reason determines the tier via [rankOf]. */
    private fun reasonFor(
        offer: StreamingOffer,
        preference: UserStreamingPreference,
        cheapestRental: StreamingOffer?,
    ): OfferReason =
        when (offer.type) {
            OfferType.USER_LIBRARY -> OfferReason.IN_YOUR_LIBRARY

            OfferType.SUBSCRIPTION ->
                if (preference.subscribesTo(offer.provider)) {
                    OfferReason.INCLUDED_IN_YOUR_SUBSCRIPTION
                } else {
                    // The distinction the whole preference exists for: a service the user does not
                    // pay for costs a new recurring subscription, so it ranks below every one-off
                    // payment rather than alongside a subscription they already have.
                    OfferReason.REQUIRES_NEW_SUBSCRIPTION
                }

            OfferType.FREE_WITH_ADS -> OfferReason.FREE_WITH_ADS

            OfferType.RENT ->
                // Identity, not price equality: two providers quoting the same amount must not both
                // be labelled "cheapest".
                if (cheapestRental != null && offer === cheapestRental) {
                    OfferReason.CHEAPEST_RENTAL
                } else {
                    OfferReason.RENTAL
                }

            OfferType.BUY -> OfferReason.PURCHASE

            OfferType.UNAVAILABLE -> OfferReason.NOT_AVAILABLE
        }

    /**
     * The tier for a reason. Lower sorts first.
     *
     * Gaps are left between tiers so a later phase can insert a category without renumbering the
     * ones already persisted or asserted against.
     */
    private fun rankOf(reason: OfferReason): Int =
        when (reason) {
            OfferReason.IN_YOUR_LIBRARY -> 0
            OfferReason.INCLUDED_IN_YOUR_SUBSCRIPTION -> 10
            OfferReason.FREE_WITH_ADS -> 20
            OfferReason.CHEAPEST_RENTAL -> 30
            OfferReason.RENTAL -> 40
            OfferReason.PURCHASE -> 50
            OfferReason.REQUIRES_NEW_SUBSCRIPTION -> 60
            OfferReason.NOT_AVAILABLE -> 70
        }
}
