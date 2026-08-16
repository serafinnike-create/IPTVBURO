package com.lucasserafin94.iptvburo.domain.model

/**
 * Domain contracts for the "Assinaturas" area: telling the user *where* a title can be watched.
 *
 * The whole area is a signpost, never a source. It answers "this film is on a service you already
 * pay for", "it can be rented", "it is in your own list", and then hands off to the official app or
 * site. Nothing here resolves, decrypts or plays a protected stream, and no type in this file
 * carries a playable URL for anything other than the user's own library.
 *
 * This file is pure domain: no Compose, no I/O, no HTTP, no provider hostnames. The one behaviour
 * that lives here is [BestOfferPolicy], because ranking offers is a product rule rather than a
 * rendering detail, and it has to be testable without a network.
 */

/**
 * A place a title can be watched.
 *
 * Deliberately just an id and a name. No logo, no icon, no brand colour and no artwork: the
 * repository is public, and shipping third-party marks would be a licensing problem the product
 * does not need. The UI layer decides how to present a provider from its [displayName] alone.
 *
 * [id] is a neutral, stable identifier used for equality, persistence and matching against the
 * user's selected services. It is lowercased and trimmed so a catalogue writing "Provider" and a
 * preference writing "provider" refer to one service rather than two.
 */
data class StreamingProvider(
    val id: String,
    val displayName: String,
    /**
     * The service's own mark, when one is known.
     *
     * Shown at the product owner's explicit instruction; the rule that kept providers text-only was
     * reversed deliberately. Null is ordinary and must degrade to the name alone rather than to a
     * gap — the name is what identifies the service, and the logo only makes it quicker to spot.
     */
    val logoUrl: String? = null,
) {
    init {
        require(id.isNotBlank()) { "A streaming provider needs an id." }
        require(displayName.isNotBlank()) { "A streaming provider needs a display name." }
    }

    companion object {
        /** Normalises an id so casing and stray whitespace never split one service into two. */
        fun normaliseId(id: String): String = id.trim().lowercase()

        /** Builds a provider with a normalised [id]. */
        fun of(
            id: String,
            displayName: String,
            logoUrl: String? = null,
        ): StreamingProvider =
            StreamingProvider(
                id = normaliseId(id),
                displayName = displayName.trim(),
                logoUrl = logoUrl?.takeIf(String::isNotBlank),
            )
    }

    /** The user's own playlist, represented as a provider so it can appear in one ranked list. */
    val isUserLibrary: Boolean
        get() = id == USER_LIBRARY_PROVIDER_ID
}

/**
 * The provider id reserved for the user's own playlist.
 *
 * The user's library is modelled as a provider so that "where can I watch this" produces a single
 * ranked list instead of one list plus a special case the UI has to remember to render first.
 */
const val USER_LIBRARY_PROVIDER_ID: String = "user-library"

/**
 * How a title is available from a provider.
 *
 * These six are the complete set. There is intentionally no "free" type that would let the user's
 * own library be described as a price of zero — see [OfferType.USER_LIBRARY].
 */
enum class OfferType {
    /**
     * The title is already in the user's own playlist.
     *
     * This is an *origin*, not a price. It must never be modelled as a zero-cost offer: the product
     * does not give content away, and a "free" badge on the user's own list would claim exactly
     * that. [StreamingOffer] enforces the distinction by refusing to carry a price on this type.
     */
    USER_LIBRARY,

    /** Included with a subscription to the provider. */
    SUBSCRIPTION,

    /** Legally free to watch, funded by advertising. No subscription required. */
    FREE_WITH_ADS,

    /** Available to rent for a period. */
    RENT,

    /** Available to buy outright. */
    BUY,

    /**
     * Known to the catalogue, but not currently watchable anywhere it can see.
     *
     * Kept as a real type rather than an empty list so the UI can say "not available right now"
     * instead of rendering a blank panel that looks like a failure.
     */
    UNAVAILABLE,
    ;

    /** Whether an offer of this type may carry a price. Only rentals and purchases cost money. */
    val supportsPrice: Boolean
        get() = this == RENT || this == BUY
}

/**
 * A price in minor units, paired with its currency.
 *
 * Minor units (cents) rather than a floating point amount: money compared with `Double` produces
 * the classic 4.99-is-not-4.99 fault, and the cheapest-rental rule in [BestOfferPolicy] depends on
 * comparisons being exact.
 *
 * Prices are only ever compared within one currency — see [BestOfferPolicy] for why cross-currency
 * comparison is refused rather than guessed at.
 */
data class Price(
    val amountMinor: Long,
    val currency: String,
) : Comparable<Price> {
    init {
        require(amountMinor >= 0) { "A price cannot be negative." }
        require(currency.isNotBlank()) { "A price needs a currency." }
    }

    /** Uppercased ISO-4217-style code, so "eur" and "EUR" compare as one currency. */
    val currencyCode: String
        get() = currency.trim().uppercase()

    /**
     * Orders by amount. Only meaningful within a single currency; callers must group by
     * [currencyCode] first, which is what [BestOfferPolicy] does.
     */
    override fun compareTo(other: Price): Int = amountMinor.compareTo(other.amountMinor)
}

/**
 * One way to watch a title: a provider plus the terms.
 *
 * The invariants are enforced here rather than trusted to callers, because an adapter mapping a
 * third-party catalogue is exactly where a rental price would leak onto a subscription entry, or a
 * zero price onto the user's own library.
 */
data class StreamingOffer(
    val provider: StreamingProvider,
    val type: OfferType,
    val price: Price? = null,
    /**
     * Where to send the user to watch this. Null when the catalogue gave no destination.
     *
     * For everything except [OfferType.USER_LIBRARY] this is an official app or web destination,
     * never a stream.
     */
    val launchTarget: ExternalLaunchTarget? = null,
    /** Free-text quality note as reported by the catalogue, e.g. "HD". Never inferred. */
    val quality: String? = null,
) {
    init {
        if (!type.supportsPrice) {
            require(price == null) {
                "An offer of type $type cannot carry a price. The user's own library is an origin, " +
                    "not a free offer, and subscription or ad-funded access has no per-title price."
            }
        }
        if (type == OfferType.USER_LIBRARY) {
            require(provider.isUserLibrary) {
                "A USER_LIBRARY offer must come from the reserved user library provider."
            }
        }
        require(!(type != OfferType.USER_LIBRARY && provider.isUserLibrary)) {
            "The user library provider can only back a USER_LIBRARY offer."
        }
    }

    /** True when watching this costs the user money at the point of use. */
    val isPaidPerTitle: Boolean
        get() = type.supportsPrice
}

/**
 * An identifier for a title in an external catalogue.
 *
 * Namespaced by [namespace] so ids from different catalogues cannot collide, and kept separate from
 * [ContentIdentity] on purpose: [ContentIdentity] is what the app derives from title and year to key
 * the *user's own* data, while this is a foreign key into someone else's numbering. Conflating them
 * is how a catalogue change would orphan favourites and progress.
 */
data class ExternalContentId(
    val namespace: String,
    val value: String,
) {
    init {
        require(namespace.isNotBlank()) { "An external content id needs a namespace." }
        require(value.isNotBlank()) { "An external content id needs a value." }
    }

    /** Stable string form, safe to persist. */
    val key: String
        get() = "$namespace:$value"
}

/** Whether an external title is a film or a series. Mirrors the app's own [ContentKind] split. */
enum class ExternalTitleKind {
    MOVIE,
    SERIES,
}

/**
 * A title as returned by a discovery search: enough to render a card.
 *
 * [posterUrl] is the *film's own* artwork, which discovery catalogues serve for display and which
 * the app already renders throughout the library. It is deliberately not a service's brand logo:
 * those are the companies' own marks, carry no such permission, and are never fetched.
 */
data class ExternalTitle(
    val id: ExternalContentId,
    val title: String,
    val kind: ExternalTitleKind,
    val year: Int? = null,
    /**
     * The full release date as an ISO string, when the catalogue knows one.
     *
     * Alongside [year] rather than replacing it: a year is what a card shows and what identity
     * matching uses, while this is what an upcoming title needs — "22/08/2026" is the whole point
     * of a coming-soon shelf, and a bare year says nothing about whether it is next week or in
     * eleven months. Kept as text because that is how the catalogue supplies it, and a date that
     * cannot be parsed must degrade to "no date" rather than take a shelf down.
     */
    val releaseDate: String? = null,
    /**
     * The film's poster, when the catalogue supplied one.
     *
     * Null is ordinary and must render as a card without an image rather than as a broken one — a
     * grey box with a missing-image icon reads as a fault in the app.
     */
    val posterUrl: String? = null,
    /**
     * Marks synthetic data so it can never be mistaken for a real catalogue result.
     *
     * The fixture provider sets this on everything it returns, and the UI is expected to label such
     * rows rather than present them as genuine availability.
     */
    val isDemo: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "An external title needs a title." }
    }

    /**
     * The app's own identity for this title, so a discovery result can be lined up against the
     * user's library without persisting the foreign catalogue's id as the key.
     */
    val contentIdentity: ContentIdentity
        get() =
            ContentIdentity.of(
                kind =
                    when (kind) {
                        ExternalTitleKind.MOVIE -> ContentKind.MOVIE
                        ExternalTitleKind.SERIES -> ContentKind.SERIES
                    },
                title = title,
                year = year,
            )
}

/**
 * The detail view of an external title.
 *
 * [offers] is what the availability panel renders. It may be empty, and an empty list is a valid
 * answer meaning "nothing known", not an error.
 */
data class ExternalTitleDetails(
    val title: ExternalTitle,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val offers: List<StreamingOffer> = emptyList(),
) {
    init {
        require(runtimeMinutes == null || runtimeMinutes > 0) { "A runtime, when present, must be positive." }
    }
}

/**
 * Where to send the user so they can watch a title on someone else's service.
 *
 * Both addresses are optional because platforms differ: a desktop build has a web URL and no app to
 * deep-link into, a TV build may have the reverse. At least one must be present, since a launch
 * target that goes nowhere is not a launch target — which is why `ExternalContentLauncher` never has
 * to handle a wholly empty target.
 *
 * This type only describes the destination. Deciding whether it is *safe* to open — refusing raw
 * streams, credential-bearing URLs and odd schemes — is `ExternalContentLauncher` in
 * `ExternalContentLaunch.kt`, so the domain stays free of platform intent handling.
 */
data class ExternalLaunchTarget(
    /** Official web destination. */
    val webUrl: String? = null,
    /** Platform-specific deep link into the official app, when one is known. */
    val appDeepLink: String? = null,
    /**
     * The provider this destination belongs to, when the caller knows it.
     *
     * Optional, because the offer that carries a target already names its provider — requiring it
     * here would only duplicate that. It exists for the cases where a target travels on its own.
     */
    val providerId: String? = null,
) {
    init {
        require(!webUrl.isNullOrBlank() || !appDeepLink.isNullOrBlank()) {
            "A launch target needs at least a web URL or an app deep link."
        }
    }

    /**
     * Redacted: a launch target can carry query parameters that identify the user or the session,
     * and those must not reach a log. Matches the redaction rule used across the domain.
     */
    override fun toString(): String =
        "ExternalLaunchTarget(providerId=${providerId ?: "none"}, " +
            "webUrl=${if (webUrl.isNullOrBlank()) "none" else "<redacted>"}, " +
            "appDeepLink=${if (appDeepLink.isNullOrBlank()) "none" else "<redacted>"})"
}

/**
 * Which services the user says they pay for, and in which currency they want prices.
 *
 * This is a user statement, not a verified entitlement. The app has no way to check whether someone
 * really subscribes to a service and does not try: the preference exists so ranking can put "you
 * already have this" above "you would have to subscribe", which is useful even when it is wrong.
 */
data class UserStreamingPreference(
    val subscribedProviderIds: Set<String> = emptySet(),
    /**
     * Currency the user's prices are quoted in.
     *
     * Rentals in another currency are still shown, just never compared against this one — see
     * [BestOfferPolicy].
     */
    val preferredCurrency: String? = null,
    /** When set, offers requiring a purchase or rental are ranked below free options only. */
    val hideUnavailable: Boolean = false,
) {
    private val normalisedIds: Set<String> = subscribedProviderIds.map(StreamingProvider::normaliseId).toSet()

    /** Whether the user says they subscribe to [provider]. Case-insensitive by construction. */
    fun subscribesTo(provider: StreamingProvider): Boolean = provider.id in normalisedIds

    companion object {
        /** No stated subscriptions. Every subscription offer ranks as "would need to subscribe". */
        val NONE: UserStreamingPreference = UserStreamingPreference()
    }
}

/**
 * Reads an external catalogue of titles and their availability.
 *
 * Intentionally an interface with no real implementation in this phase. Binding it to a concrete
 * catalogue means choosing a vendor, holding an API key and making network calls, none of which
 * belong in `domain-model` and none of which are in scope here.
 *
 * Implementations must not return playable stream URLs for third-party services. The contract is
 * discovery and hand-off only.
 */
interface StreamingDiscoveryProvider {
    /** Titles matching [query]. An unmatched query returns an empty list rather than throwing. */
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): List<ExternalTitle>

    /** Full detail for [id], or null when the catalogue does not know it. */
    suspend fun details(id: ExternalContentId): ExternalTitleDetails?

    /**
     * Where [id] can be watched right now.
     *
     * An empty list means "nothing known", which callers must handle as a normal outcome.
     */
    suspend fun offers(id: ExternalContentId): List<StreamingOffer>

    /** Titles announced but not yet released. Empty when the catalogue offers no such data. */
    suspend fun upcoming(limit: Int = DEFAULT_SEARCH_LIMIT): List<ExternalTitle>

    companion object {
        /** Bounded so a search cannot return an unrenderable result set. */
        const val DEFAULT_SEARCH_LIMIT: Int = 20
    }
}
