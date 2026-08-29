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

        // Playlists often leave the year field empty and write it into the name instead.
        val localYear = local.year ?: local.title.yearInTitle()

        // Two different years for the same name is the remake case. Rejected outright rather than
        // demoted: a title that is confidently the wrong film is worse than no answer.
        if (localYear != null && external.year != null && localYear != external.year) {
            return reject(local, external, MatchReason.YEAR_CONFLICT)
        }

        val reasons = mutableListOf<MatchReason>()
        if (localYear != null && localYear == external.year) {
            reasons += MatchReason.TITLE_AND_YEAR
        } else if (localYear == null && external.year != null) {
            // The playlist states no year at all. Requiring one would hide most of a real catalogue
            // — Xtream lists frequently carry none — while the risk it guards against is a remake,
            // and a remake would have to *also* be the only copy the user owns for this to mislead.
            // Titles are matched exactly after decoration is stripped, so this is a name agreeing
            // with a name, not a guess.
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

/**
 * Lower-case, unaccented, stripped of the noise a playlist adds: how two titles are compared.
 *
 * A provider's catalogue is not a clean list of names. The same film is "Duna 4K", "Duna [1080p]",
 * "Duna DUBLADO" or "Duna (2021)" depending on who assembled the list, while the discovery
 * catalogue has the bare title. Comparing those literally found almost nothing, which is why films
 * the user plainly owned were not being recognised.
 *
 * Only decoration is removed. Nothing here can turn one film into another: the words stripped are
 * formats, resolutions and language markers, never part of a title.
 */
fun String.normalisedForMatching(): String {
    var working = lowercase()

    // A trailing or embedded year in the name, e.g. "Duna (2021)". Removed from the comparison and
    // read separately by [yearInTitle] — a year is a fact about the title, not part of its name.
    working = working.replace(YEAR_IN_TITLE, " ")

    // Quality, source and language decoration. Bounded by non-letters so a real word is never eaten:
    // "4kids" keeps its "4k", and a film actually called "Dublado" would keep it too.
    working = working.replace(DECORATION, " ")

    return working
        .replace('á', 'a').replace('à', 'a').replace('ã', 'a').replace('â', 'a')
        .replace('é', 'e').replace('ê', 'e')
        .replace('í', 'i')
        .replace('ó', 'o').replace('õ', 'o').replace('ô', 'o')
        .replace('ú', 'u').replace('ü', 'u')
        .replace('ç', 'c')
        .filter { character -> character.isLetterOrDigit() }
}

/** A four-digit year in brackets or standing alone, as playlists write it. */
private val YEAR_IN_TITLE = Regex("""[\[(]?\b(19|20)\d{2}\b[\])]?""")

/**
 * Words a playlist adds that say nothing about which film it is.
 *
 * Deliberately a fixed list rather than a pattern: guessing at "anything in brackets" would eat
 * "Duna (Parte Dois)", and a title is worth more than a tidier rule.
 */
private val DECORATION =
    Regex(
        """\b(4k|uhd|hd|sd|fhd|1080p?|720p?|480p?|2160p?|
           |dublado|dual|legendado|leg|nacional|dub|
           |bluray|blu-ray|webrip|web-dl|webdl|hdrip|dvdrip|remux|
           |imax|extended|remaster(ed|izado)?)\b""".trimMargin().replace("\n", ""),
        RegexOption.IGNORE_CASE,
    )

/** The year written inside a title, when the year field is empty. */
internal fun String.yearInTitle(): Int? =
    YEAR_IN_TITLE.find(this)?.value?.filter(Char::isDigit)?.toIntOrNull()

/**
 * A key for collapsing a provider's several copies of one film into a single shelf entry.
 *
 * Deliberately more aggressive than [normalisedForMatching], and deliberately not used in its
 * place. The two answer different questions:
 *
 * - **Matching** asks "is the film the discovery service is describing the one the user owns?" A
 *   wrong yes sends them to a title they do not have, so it strips only what is provably
 *   decoration and keeps everything that might be part of a name.
 * - **A shelf** asks "have I already shown this?" A wrong yes hides one copy of a film the user can
 *   still reach every other way; a wrong no puts the same poster on screen twice, which is what
 *   this is fixing. The costs are not symmetrical, so the rules are not either.
 *
 * What this removes on top of the shared normaliser, each observed in a real catalogue:
 *
 * - a label on either side of a pipe — `NETFLIX | Enola Holmes 3`, `Enola Holmes 3 | DUAL`
 * - short bracketed tags — `[L]`, `[D]`, `(DUB)` — while leaving longer ones, so
 *   `Duna (Parte Dois)` keeps its subtitle
 * - a trailing one- or two-letter word — `Kyle Larson e o Double L`
 *
 * Numbers are never touched: `Enola Holmes 2` and `Enola Holmes 3` are different films, and a rule
 * that confused them would be far worse than a duplicate.
 */
fun String.shelfDeduplicationKey(): String {
    var working = this

    // Pipes, longest side wins. A provider writes both "NETFLIX | Enola Holmes 3" and
    // "Enola Holmes 3 | DUAL", so which side is the title cannot be decided by position — only one
    // of the two is a label, and the label is the short one. Taking the longer side gets both.
    //
    // Ordering mattered here: stripping a short *prefix* first turned "Enola Holmes 3 | DUAL" into
    // "DUAL", because the title itself is short enough to look like a label.
    if ('|' in working) {
        working = working.split('|').maxByOrNull { part -> part.trim().length }.orEmpty()
    }
    // Short bracketed tags only. Two or three characters is a marker; more may be a subtitle.
    working = working.replace(SHORT_BRACKETED_TAG, " ")
    // Codec and container words. Absent from the shared normaliser because they are not evidence
    // about which film a title is — but on a shelf, "Filme HEVC" and "Filme HD" are one film, and
    // dropping this list while unifying the keys would have reintroduced that duplicate.
    working = working.replace(SHELF_ONLY_DECORATION, " ")
    // A bare quality mark at the end, which is the other way providers write the same thing.
    //
    // `A&E [HD]` collapsed because the tag is bracketed, but `A&E 480` did not — so with two lists
    // merged the grid showed four A&E cards where one belongs. Reported with a screenshot of
    // exactly that.
    //
    // Only at the end, and repeatedly, because "Globo FHD H265" carries two. Anywhere in the
    // string would take the 4K out of a category called "4K Filmes Premiados"; a channel with a
    // number in its actual name — "Canal 4", "Rede 21" — is untouched because those are not
    // quality marks.
    var previous: String? = null
    while (previous != working) {
        previous = working
        working = working.trimEnd().replace(TRAILING_QUALITY, "")
    }
    // A trailing one- or two-letter word, which is how several providers mark a language. After the
    // quality marks, so "Canal HD L" loses both rather than stopping at the language.
    working = working.replace(TRAILING_SHORT_WORD, " ")

    return working.normalisedForMatching()
}

/**
 * `[L]`, `(D)`, `[DUB]`, `[L1]`, `[DV]` — a marker, not a subtitle.
 *
 * Up to four characters and digits allowed, because providers number their language variants:
 * `[L1]` and `[L2]` are the same film with different audio. `Duna (Parte Dois)` is well past this
 * length and keeps its subtitle.
 */
private val SHORT_BRACKETED_TAG = Regex("""[\[(][A-Za-z0-9]{1,4}[\])]""")

/** A one- or two-letter word at the end, after the real title. */
private val TRAILING_SHORT_WORD = Regex("""\s+[A-Za-z]{1,2}\s*$""")

/**
 * A resolution or quality word at the end of a channel name.
 *
 * `A&E`, `A&E 480`, `A&E [HD]` and `A&E [SD]` are one channel offered four ways. The bracketed ones
 * were already collapsed; the bare `480` was not, so merging two lists put four A&E cards on the
 * grid where one belongs.
 *
 * Anchored at the end because that is where a provider writes it, and because anywhere in the
 * string would strip the 4K from a category named "4K Filmes Premiados". A number that is part of
 * the name — "Canal 4", "Rede 21", "Blade Runner 2049" — is not a quality mark and survives.
 */
private val TRAILING_QUALITY =
    Regex("""[\s\-|]+(sd|hd|fhd|uhd|4k|8k|480p?|576p?|720p?|1080p?|2160p?)$""", RegexOption.IGNORE_CASE)

/**
 * Codec, range and audio words, stripped for shelves only.
 *
 * These say nothing about which film a title is, so the matcher has no use for them — but they are
 * exactly how a provider distinguishes its copies, so a shelf must ignore them.
 */
private val SHELF_ONLY_DECORATION =
    Regex("""\b(hevc|h\.?26[45]|x26[45]|av1|dv|hdr10?\+?|atmos|multi|ac3|aac|eac3)\b""", RegexOption.IGNORE_CASE)
