package com.lucasserafin94.iptvburo.domain.model

/**
 * Cleaning up the names in a music playlist.
 *
 * ## The problem
 *
 * An M3U's display name is whatever the person who made it happened to have. In practice that means
 * filenames: `01 - Pink Floyd - Time.mp3`, `Pink_Floyd_-_Time`, `[320kbps] Time (Official Video)`.
 * The parser's artist/title split is correct for a clean `Artist - Title`, and produces nonsense for
 * any of those — an artist called "01", a title ending in ".mp3", a track filed under "[320kbps]".
 *
 * ## Why this is separate from the parser
 *
 * The parser must read what the file says. This decides what the file *meant*, which is a guess, and
 * guesses belong somewhere they can be shown to the user and undone. Every function here is pure and
 * returns a proposal; nothing is applied without the user seeing it.
 *
 * ## What is deliberately not attempted
 *
 * No title casing. "REM" is not "Rem", "k.d. lang" is not "K.D. Lang", and a rule that fixes shouty
 * filenames breaks band names that are shouty on purpose. Capitalisation is left exactly as found.
 */
object MusicTidying {

    /**
     * A cleaner reading of one track's name, or null when nothing would change.
     *
     * Null rather than an unchanged copy so a caller can count how many tracks a tidy would actually
     * touch — "412 faixas, 38 alteradas" is a decision somebody can make, while "412 propostas" is
     * not.
     */
    fun proposalFor(track: MusicTrack): MusicTidyProposal? {
        // A radio station's name is not "Artist - Title" even when it contains a dash, and its
        // "title" is the station. Splitting "Rádio Cidade - 102.9 FM" invents an artist.
        if (track.isRadio) return null

        val cleanedTitle = tidyTitle(track.title)
        val recovered = recoverArtist(cleanedTitle, track.artist)

        val title = recovered.title
        val artist = recovered.artist ?: track.artist

        if (title == track.title && artist == track.artist) return null
        return MusicTidyProposal(trackId = track.id, title = title, artist = artist)
    }

    /** Every change a tidy would make to [tracks], in playlist order. */
    fun proposalsFor(tracks: List<MusicTrack>): List<MusicTidyProposal> =
        tracks.mapNotNull(::proposalFor)

    /**
     * Strips the decoration a filename carries and a title does not.
     *
     * Ordered deliberately: the extension goes first, because `Time.mp3` must not have its `.mp3`
     * mistaken for part of a bracketed tag once other things have moved around.
     */
    fun tidyTitle(raw: String): String {
        var working = raw.trim()

        working = working.removeSuffix(EXTENSION_PATTERN.find(working)?.value.orEmpty())
        working = working.replace(UNDERSCORES, " ")

        // Noise before the track number, and the number checked afterwards.
        //
        // The order matters and is not obvious: "[320kbps] 02. Money" has its track number in the
        // middle, not at the start, so a leading-number rule applied first sees "[320kbps]" and does
        // nothing. Removing the bracketed tag first brings the number to the front where the rule
        // can find it. Repeated spaces are collapsed in between so "  02. " still matches.
        working = working.replace(BRACKETED_NOISE, " ")
        working = working.replace(TRAILING_NOISE, "")
        working = working.replace(REPEATED_SPACES, " ").trim()
        working = working.replace(LEADING_TRACK_NUMBER, "")
        working = working.replace(REPEATED_SPACES, " ")

        return working.trim().trim('-', '–', '—', '·').trim()
            // Never return nothing. A name made entirely of decoration — "[320kbps].mp3" — is
            // useless, but an empty row is worse: it cannot be found, selected, or corrected.
            .ifEmpty { raw.trim() }
    }

    /**
     * Pulls an artist out of a title that still contains one.
     *
     * Only when the track has no artist already. A playlist that says `artist="Pink Floyd"` and
     * `title="Pink Floyd - Time"` is common, and the fix there is to shorten the title — but
     * overwriting a stated artist with something scraped out of a title would lose real information
     * in exchange for a guess.
     */
    fun recoverArtist(title: String, existingArtist: String?): ArtistAndTitle {
        if (!existingArtist.isNullOrBlank()) {
            // The redundant-prefix case: title begins with the artist it already has.
            val trimmed = title.trim()
            for (separator in SEPARATORS) {
                val marker = existingArtist + separator
                if (trimmed.startsWith(marker, ignoreCase = true)) {
                    val remainder = trimmed.drop(marker.length).trim()
                    if (remainder.isNotEmpty()) return ArtistAndTitle(existingArtist, remainder)
                }
            }
            return ArtistAndTitle(existingArtist, trimmed)
        }

        // Only the first separator splits. "Pink Floyd - Wish You Were Here - Live" is an artist and
        // a title that contains a dash, never a three-part name.
        for (separator in SEPARATORS) {
            val at = title.indexOf(separator)
            if (at <= 0) continue
            val artist = title.take(at).trim()
            val remainder = title.drop(at + separator.length).trim()
            if (artist.isNotEmpty() && remainder.isNotEmpty()) {
                return ArtistAndTitle(artist, remainder)
            }
        }

        return ArtistAndTitle(null, title)
    }

    /**
     * Groups tracks that are the same recording under different names.
     *
     * Matched on artist and title after tidying and case folding, which catches the ordinary
     * duplicate — the same song added twice from two sources with different filenames. It does not
     * try to detect that a live version and a studio version are "the same", because they are not,
     * and a tool that quietly removes one is a tool nobody can trust with the other.
     *
     * Groups of one are omitted: the caller wants duplicates, not an index.
     */
    fun duplicateGroups(tracks: List<MusicTrack>): List<List<MusicTrack>> =
        tracks
            .filterNot(MusicTrack::isRadio)
            .groupBy { track ->
                val proposal = proposalFor(track)
                val artist = (proposal?.artist ?: track.artist).orEmpty().lowercase().trim()
                val title = (proposal?.title ?: track.title).lowercase().trim()
                artist to title
            }
            .values
            .filter { group -> group.size > 1 }
            .toList()

    /**
     * Tracks whose stream address is identical to another's.
     *
     * A stronger signal than a matching name: two entries pointing at one URL are the same entry
     * twice, whatever they are called. Kept separate from [duplicateGroups] because this one is
     * certain and the other is a judgement.
     */
    fun sameAddressGroups(tracks: List<MusicTrack>): List<List<MusicTrack>> =
        tracks
            // A missing address is not evidence that two rows are the same. Grouping blank values
            // made every malformed row look like one enormous duplicate set.
            .filter { track -> track.streamUri.isNotBlank() }
            .groupBy { track -> track.streamUri.trim() }
            .values
            .filter { group -> group.size > 1 }
            .toList()

    // Spaces are evidence that punctuation separates artist from title. An unspaced hyphen is
    // commonly part of a real name (AC-DC, Blink-182, COVID-19) and must not invent an artist.
    private val SEPARATORS = listOf(" - ", " – ", " — ", " · ")

    /** A file extension at the very end. Audio only: a title may legitimately end in ".com". */
    private val EXTENSION_PATTERN =
        Regex("""\.(mp3|m4a|aac|flac|ogg|opus|wav|wma|mp4|mkv|webm)$""", RegexOption.IGNORE_CASE)

    /** Underscores where spaces belong, the signature of a filename. */
    private val UNDERSCORES = Regex("_+")

    /**
     * A track number at the start: "01 ", "01. ", "01 - ", "(01)".
     *
     * Bounded to four digits and required to be followed by a separator or space, so a song called
     * "1979" or "99 Luftballons" keeps its name.
     */
    private val LEADING_TRACK_NUMBER = Regex("""^\(?\d{1,4}\)?\s*[-.–—)]\s*""")

    /**
     * Bracketed noise: bitrates, "official video", "hd", "lyrics".
     *
     * A deliberately short list of things that are never part of a song's name. Brackets in general
     * are left alone, because "(Live at Pompeii)" and "(feat. Someone)" are part of the title.
     */
    private val BRACKETED_NOISE =
        Regex(
            """[\[(]\s*(\d{2,4}\s*kbps|official\s*(music\s*)?video|official\s*audio|lyrics?|hd|hq|4k|full\s*hd|audio|video oficial|clipe oficial)\s*[\])]""",
            RegexOption.IGNORE_CASE,
        )

    /** The same noise without brackets, when it trails the name. */
    private val TRAILING_NOISE =
        Regex(
            """\s*[-–—|]\s*(official\s*(music\s*)?video|official\s*audio|lyrics?|hd|hq|4k)\s*$""",
            RegexOption.IGNORE_CASE,
        )

    private val REPEATED_SPACES = Regex("""\s{2,}""")
}

/** A proposed correction to one track. Applied only after the user has seen it. */
data class MusicTidyProposal(
    val trackId: String,
    val title: String,
    val artist: String?,
)

/** The result of separating a display name into its two parts. */
data class ArtistAndTitle(
    val artist: String?,
    val title: String,
)
