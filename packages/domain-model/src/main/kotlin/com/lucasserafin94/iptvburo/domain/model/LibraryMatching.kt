package com.lucasserafin94.iptvburo.domain.model

/**
 * Deciding whether a title found on a streaming service is the same one the user already has.
 *
 * The whole point is to be conservative. Saying "Disponível na sua biblioteca" about something the
 * playlist does not carry sends the user to a dead end and makes every other claim on the screen
 * suspect; saying nothing merely costs them one extra look. So the bar for asserting a match
 * automatically is deliberately high, and everything below it stays hidden until a human confirms.
 *
 * The canonical failure this exists to prevent is a title reused across remakes:
 * "Duna 1984" and "Duna 2021" share a name, a language and a type, and differ only by year.
 */
enum class MatchStatus {
    /** An external identifier both sides agree on. Nothing else is this strong. */
    CONFIRMED,

    /** Title, year and type all agree. Shown automatically. */
    HIGH_CONFIDENCE,

    /** Plausible but unproven. Never shown automatically; needs a person to say yes. */
    POSSIBLE,

    /** Actively contradicted — a different year, or a film against a series. */
    REJECTED,
    ;

    /** Whether this may claim, without asking anyone, that the user already has the title. */
    val mayClaimAutomatically: Boolean
        get() = this == CONFIRMED || this == HIGH_CONFIDENCE
}

/** Why a match was accepted or refused, so the decision can be shown rather than trusted blindly. */
enum class MatchReason {
    EXTERNAL_ID,
    TITLE_AND_YEAR,
    ORIGINAL_TITLE,
    APPROXIMATE_DURATION,
    SERIES_SEASON_EPISODE,
    FUZZY_TITLE,
    YEAR_CONFLICT,
    TYPE_CONFLICT,
    EPISODE_CONFLICT,
    NO_SIGNAL,
}

data class LibraryMatch(
    val localContentId: String,
    val externalContentId: String,
    val confidence: Double,
    val reasons: List<MatchReason>,
    val status: MatchStatus,
)

/** What the local catalogue knows about one of its own items, in the terms matching needs. */
data class LibraryCandidate(
    val localContentId: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val kind: MatchKind,
    val durationMinutes: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /** Identifiers the provider supplied, keyed by namespace: "tmdb", "imdb". */
    val externalIds: Map<String, String> = emptyMap(),
)

/** The same, for a title described by a discovery service. */
data class ExternalCandidate(
    val externalContentId: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val kind: MatchKind,
    val durationMinutes: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val externalIds: Map<String, String> = emptyMap(),
)

/** A film is never an episode. Keeping them apart is the cheapest rejection there is. */
enum class MatchKind {
    MOVIE,
    SERIES,
    EPISODE,
}

/**
 * Decides whether two descriptions of a title are the same work.
 *
 * Signals are tried strongest first and the first conclusive one wins, so an agreed external id is
 * never overruled by a title that happens to differ in punctuation.
 */
object LibraryMatchingPolicy {
    /**
     * Matches [local] against [external].
     *
     * Returns a [LibraryMatch] in every case, including refusal: the caller decides what to do with
     * a REJECTED result, and hiding the reasons would make a wrong answer impossible to explain.
     */
    fun match(
        local: LibraryCandidate,
        external: ExternalCandidate,
    ): LibraryMatch {
        // A film and a series episode are never the same work, whatever their titles say. Checked
        // before anything else because it is certain and cheap.
        if (conflictingKinds(local.kind, external.kind)) {
            return reject(local, external, MatchReason.TYPE_CONFLICT)
        }

        // Both sides naming the same id is the only signal strong enough to stand alone.
        sharedExternalId(local.externalIds, external.externalIds)?.let {
            return LibraryMatch(
                localContentId = local.localContentId,
                externalContentId = external.externalContentId,
                confidence = 1.0,
                reasons = listOf(MatchReason.EXTERNAL_ID),
                status = MatchStatus.CONFIRMED,
            )
        }

        // An episode has to agree on its numbering, not merely on the series name.
        if (local.kind == MatchKind.EPISODE && external.kind == MatchKind.EPISODE) {
            val sameEpisode =
                local.seasonNumber != null &&
                    local.episodeNumber != null &&
                    local.seasonNumber == external.seasonNumber &&
                    local.episodeNumber == external.episodeNumber
            if (!sameEpisode) {
                return reject(local, external, MatchReason.EPISODE_CONFLICT)
            }
        }

        val titlesAgree =
            titlesMatch(local.title, external.title) ||
                titlesMatch(local.originalTitle, external.originalTitle) ||
                titlesMatch(local.title, external.originalTitle) ||
                titlesMatch(local.originalTitle, external.title)

        if (!titlesAgree) {
            return reject(local, external, MatchReason.NO_SIGNAL)
        }

        // Two different years for the same name is the remake case. Rejected outright rather than
        // demoted: a title that is confidently the wrong film is worse than no answer.
        if (local.year != null && external.year != null && local.year != external.year) {
            return reject(local, external, MatchReason.YEAR_CONFLICT)
        }

        val reasons = mutableListOf<MatchReason>()
        if (local.year != null && local.year == external.year) {
            reasons += MatchReason.TITLE_AND_YEAR
        }
        if (titlesMatch(local.originalTitle, external.originalTitle)) {
            reasons += MatchReason.ORIGINAL_TITLE
        }
        if (local.kind == MatchKind.EPISODE && external.kind == MatchKind.EPISODE) {
            reasons += MatchReason.SERIES_SEASON_EPISODE
        }
        if (durationsAgree(local.durationMinutes, external.durationMinutes)) {
            reasons += MatchReason.APPROXIMATE_DURATION
        }

        // Title and year together is the bar for showing a match without asking. A title alone is
        // not: it is the signal that produces every false positive worth worrying about.
        return if (MatchReason.TITLE_AND_YEAR in reasons) {
            LibraryMatch(
                localContentId = local.localContentId,
                externalContentId = external.externalContentId,
                confidence = if (MatchReason.APPROXIMATE_DURATION in reasons) 0.95 else 0.9,
                reasons = reasons,
                status = MatchStatus.HIGH_CONFIDENCE,
            )
        } else {
            LibraryMatch(
                localContentId = local.localContentId,
                externalContentId = external.externalContentId,
                confidence = if (reasons.isEmpty()) 0.5 else 0.65,
                reasons = reasons.ifEmpty { listOf(MatchReason.FUZZY_TITLE) },
                status = MatchStatus.POSSIBLE,
            )
        }
    }

    /**
     * The best match among [candidates], or null when none may be claimed automatically.
     *
     * Only CONFIRMED and HIGH_CONFIDENCE are returned. A POSSIBLE match is not "the best we have";
     * it is a guess, and the screen must not present a guess as a fact.
     */
    fun bestAutomaticMatch(
        candidates: List<LibraryCandidate>,
        external: ExternalCandidate,
    ): LibraryMatch? =
        candidates
            .map { candidate -> match(candidate, external) }
            .filter { result -> result.status.mayClaimAutomatically }
            .maxByOrNull { result -> result.confidence }

    private fun reject(
        local: LibraryCandidate,
        external: ExternalCandidate,
        reason: MatchReason,
    ): LibraryMatch =
        LibraryMatch(
            localContentId = local.localContentId,
            externalContentId = external.externalContentId,
            confidence = 0.0,
            reasons = listOf(reason),
            status = MatchStatus.REJECTED,
        )

    /** A series and one of its episodes are related but not the same work, so they never match. */
    private fun conflictingKinds(
        local: MatchKind,
        external: MatchKind,
    ): Boolean = local != external

    private fun sharedExternalId(
        local: Map<String, String>,
        external: Map<String, String>,
    ): String? =
        local.entries
            .firstOrNull { (namespace, value) ->
                value.isNotBlank() && external[namespace]?.equals(value, ignoreCase = true) == true
            }?.value

    /**
     * Whether two titles are the same name written differently.
     *
     * Providers punctuate freely — "Duna: Parte Dois" against "Duna Parte 2" — so comparison is on
     * letters and digits alone. This is deliberately not fuzzy: an edit distance would start
     * matching "Duna" to "Luna", and the year check cannot save a comparison that was wrong about
     * which film it was looking at.
     */
    private fun titlesMatch(
        left: String?,
        right: String?,
    ): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.normalisedForMatching() == right.normalisedForMatching()
    }

    /**
     * Runtimes agree when they are within five minutes.
     *
     * Providers round, and a theatrical cut against a streaming cut differs by a minute or two.
     * This only ever strengthens a match that title and year already carried; it never makes one.
     */
    private fun durationsAgree(
        left: Int?,
        right: Int?,
    ): Boolean = left != null && right != null && kotlin.math.abs(left - right) <= 5
}

/** Lower-case, unaccented, letters and digits only: how two titles are compared. */
private fun String.normalisedForMatching(): String =
    lowercase()
        .replace('á', 'a').replace('à', 'a').replace('ã', 'a').replace('â', 'a')
        .replace('é', 'e').replace('ê', 'e')
        .replace('í', 'i')
        .replace('ó', 'o').replace('õ', 'o').replace('ô', 'o')
        .replace('ú', 'u').replace('ü', 'u')
        .replace('ç', 'c')
        .filter { character -> character.isLetterOrDigit() }
