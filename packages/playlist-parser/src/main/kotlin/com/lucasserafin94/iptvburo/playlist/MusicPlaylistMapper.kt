package com.lucasserafin94.iptvburo.playlist

import com.lucasserafin94.iptvburo.domain.model.MusicArtist
import com.lucasserafin94.iptvburo.domain.model.MusicGenre
import com.lucasserafin94.iptvburo.domain.model.MusicLibrary
import com.lucasserafin94.iptvburo.domain.model.MusicTrack
import java.util.Locale

/**
 * Reads a parsed M3U as a music playlist.
 *
 * This is a second reading of the same bytes [M3uParser] already produces, not a second parser.
 * A music playlist is an ordinary M3U; only the meaning of the attributes differs — `group-title`
 * carries a genre rather than a provider category, `tvg-logo` is cover art rather than a channel
 * logo, and the display name is usually `Artist - Title` rather than a channel name. Keeping that
 * interpretation here means the video import path is untouched by anything below.
 *
 * Every function is pure. Nothing here reads the disk or the network, so the whole mapping is
 * testable from a string literal.
 */
object MusicPlaylistMapper {
    /**
     * Builds the complete library from parser output.
     *
     * The order of [channels] is preserved in [MusicLibrary.tracks] because a playlist author's
     * ordering is meaningful and is the only ordering an M3U can express. Artists and genres are
     * sorted by [artistsFrom] and [genresFrom].
     */
    fun toLibrary(
        channels: List<ParsedChannel>,
        idPrefix: String = "",
    ): MusicLibrary {
        val tracks = channels.mapIndexed { index, channel -> toTrack(channel, index, idPrefix) }
        return MusicLibrary(
            tracks = tracks,
            artists = artistsFrom(tracks),
            genres = genresFrom(tracks),
        )
    }

    /**
     * Maps one parsed entry to a track.
     *
     * [index] disambiguates the generated id. A music playlist frequently repeats a title across
     * albums and just as frequently omits `tvg-id` altogether, so the position in the playlist is
     * the only identifier that is guaranteed unique.
     */
    fun toTrack(
        channel: ParsedChannel,
        index: Int,
        idPrefix: String = "",
    ): MusicTrack {
        val isRadio = isRadioStation(channel)
        // A station name is not "Artist - Title" even when it contains a dash: splitting
        // "Rádio Cidade - 102.9 FM" would file the station under an artist called "Rádio Cidade".
        // Stations therefore keep their whole name as the title.
        val name = displayNameOf(channel)
        val parsedName = if (isRadio) ArtistTitle(artist = null, title = name) else splitArtistAndTitle(name)
        val genre = channel.groupTitle?.trim()?.takeIf(String::isNotEmpty)

        return MusicTrack(
            id = buildId(idPrefix, channel, index),
            title = parsedName.title,
            artist = parsedName.artist,
            streamUri = channel.streamUri,
            album = albumOf(channel),
            artworkUri = channel.logoUri,
            genre = genre,
            isRadio = isRadio,
            // A station is a continuous stream with no duration. The M3U says so with the
            // conventional -1, which would otherwise surface as a "-1 second" track.
            durationSeconds = channel.durationSeconds?.takeIf { it > 0L },
            requestHeaders = channel.requestHeaders,
        )
    }

    /**
     * Splits a display name into artist and title.
     *
     * Only the FIRST separator splits. "Pink Floyd - Wish You Were Here - Live" is an artist and a
     * title that itself contains a dash, never a three-part name; splitting on the last separator
     * would file it under an artist called "Pink Floyd - Wish You Were Here".
     *
     * With no separator at all the whole string is the title and the artist is unknown. That is the
     * common case for radio stations and for playlists that only ever carry a station name.
     */
    fun splitArtistAndTitle(displayName: String): ArtistTitle {
        val name = displayName.trim()
        if (name.isEmpty()) return ArtistTitle(artist = null, title = "")

        val separator = findSeparator(name) ?: return ArtistTitle(artist = null, title = name)
        val artist = name.substring(0, separator.startIndex).trim()
        val title = name.substring(separator.endIndex).trim()

        // "- Title" and "Artist -" are malformed rather than split: a blank half is worse than no
        // split, because it produces an artist shelf with an empty name.
        if (artist.isEmpty() || title.isEmpty()) return ArtistTitle(artist = null, title = name)
        return ArtistTitle(artist = artist, title = title)
    }

    /**
     * Decides whether an entry is a radio station.
     *
     * Three independent signals, because playlists in the wild use any one of them alone:
     *  - the group says radio ("Rádio", "Radios", "FM Stations");
     *  - the name says radio or carries a broadcast band ("Rádio Cidade", "Antena 1 FM");
     *  - the entry has no track structure at all — no duration and no "Artist - Title" — which is
     *    how a bare station line looks.
     *
     * The band match is deliberately word-bounded. A substring match would classify "Confirm" and
     * "Amsterdam" as stations, and "FM" inside "Confirm" is the exact case that motivated it.
     */
    fun isRadioStation(channel: ParsedChannel): Boolean {
        val name = displayNameOf(channel)
        if (mentionsRadio(channel.groupTitle)) return true
        if (mentionsRadio(name)) return true

        // A positive duration is proof of a finite recording, so it settles the question on its
        // own: no station declares one.
        val hasDuration = (channel.durationSeconds ?: -1L) > 0L
        if (hasDuration) return false

        // No duration, no group and no "Artist - Title" leaves nothing that looks like a track.
        val hasArtist = splitArtistAndTitle(name).artist != null
        return !hasArtist && channel.groupTitle.isNullOrBlank()
    }

    /**
     * Groups tracks by artist, most tracks first, ties broken alphabetically.
     *
     * Case-insensitive grouping: "daft punk" and "Daft Punk" are one artist, and a playlist that
     * mixes the two would otherwise show two half-filled shelves. The first spelling encountered
     * wins as the display name, since it is the one the playlist author chose to lead with.
     *
     * Radio stations are excluded — a station has no artist, and including them would pile every
     * station into one enormous "Unknown Artist" shelf.
     */
    fun artistsFrom(tracks: List<MusicTrack>): List<MusicArtist> {
        val grouped = LinkedHashMap<String, MutableList<MusicTrack>>()
        tracks.asSequence()
            .filterNot(MusicTrack::isRadio)
            .forEach { track ->
                val key = track.artistOrUnknown.lowercase(Locale.ROOT)
                grouped.getOrPut(key) { mutableListOf() }.add(track)
            }

        return grouped.values
            .map { group ->
                MusicArtist(
                    name = group.first().artistOrUnknown,
                    trackCount = group.size,
                    // Borrowed from the first track that has art: an artist shelf with no cover
                    // reads as broken next to shelves that have one.
                    artworkUri = group.firstNotNullOfOrNull(MusicTrack::artworkUri),
                )
            }
            .sortedWith(
                compareByDescending(MusicArtist::trackCount)
                    .thenBy(String.CASE_INSENSITIVE_ORDER, MusicArtist::name),
            )
    }

    /**
     * Derives the genre list from the tracks' `group-title`.
     *
     * Ordered the same way as artists, so the two rails behave alike. Tracks with no group simply
     * do not contribute; an "Unknown" genre row would be a row of unrelated things.
     */
    fun genresFrom(tracks: List<MusicTrack>): List<MusicGenre> {
        val grouped = LinkedHashMap<String, MutableList<MusicTrack>>()
        tracks.forEach { track ->
            val genre = track.genre?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
            grouped.getOrPut(genre.lowercase(Locale.ROOT)) { mutableListOf() }.add(track)
        }

        return grouped.values
            .map { group ->
                MusicGenre(
                    name = group.first().genre.orEmpty(),
                    trackCount = group.size,
                )
            }
            .sortedWith(
                compareByDescending(MusicGenre::trackCount)
                    .thenBy(String.CASE_INSENSITIVE_ORDER, MusicGenre::name),
            )
    }

    /**
     * The artist and title read out of a display name.
     *
     * [artist] is null when the name carried no usable separator, which the caller renders as
     * [MusicTrack.UNKNOWN_ARTIST] rather than inventing its own placeholder.
     */
    data class ArtistTitle(
        val artist: String?,
        val title: String,
    )

    /**
     * `tvg-name` is preferred when the display name is blank.
     *
     * The parser already falls back to a generated "Channel N" name, which is meaningless in a
     * music library; `tvg-name` is a better source when the author supplied one.
     */
    private fun displayNameOf(channel: ParsedChannel): String =
        channel.name.trim().takeIf(String::isNotEmpty)
            ?: channel.tvgName?.trim().orEmpty()

    /**
     * Album, only where the playlist actually states one.
     *
     * Nothing is inferred here. Guessing an album from the group title produced rows titled "Rock"
     * that claimed to be albums, which is worse than showing no album at all.
     */
    private fun albumOf(channel: ParsedChannel): String? =
        ALBUM_ATTRIBUTES.firstNotNullOfOrNull { key ->
            channel.attributes[key]?.trim()?.takeIf(String::isNotEmpty)
        }

    private fun buildId(
        prefix: String,
        channel: ParsedChannel,
        index: Int,
    ): String {
        val local = channel.tvgId?.trim()?.takeIf(String::isNotEmpty) ?: index.toString()
        return if (prefix.isEmpty()) local else "$prefix:$local"
    }

    private fun mentionsRadio(value: String?): Boolean {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return false
        return RADIO_KEYWORDS.containsMatchIn(text)
    }

    private fun findSeparator(name: String): IntRange? {
        val match = SEPARATOR.find(name) ?: return null
        return match.range
    }

    private val IntRange.startIndex: Int
        get() = first

    private val IntRange.endIndex: Int
        get() = last + 1

    /**
     * Separators seen in real playlists: hyphen, en dash and em dash, each surrounded by space.
     *
     * The surrounding space is what makes this safe. Matching a bare hyphen would split
     * "Jay-Z - Encore" into "Jay" and "Z - Encore", and hyphenated artist names are common enough
     * that the space requirement is load-bearing rather than cosmetic.
     */
    private val SEPARATOR = Regex("""\s+[-–—]\s+""")

    /**
     * Word-bounded so "FM" does not match inside "Confirm" and "AM" does not match "Amsterdam".
     * The accented "rádio" is listed explicitly because the playlists this was built for are
     * Portuguese, and `\b` alone does not fold the accent.
     */
    private val RADIO_KEYWORDS =
        Regex(
            """\b(r[aá]dio|radios|r[aá]dios|radio|fm|am)\b""",
            RegexOption.IGNORE_CASE,
        )

    private val ALBUM_ATTRIBUTES = listOf("album", "tvg-album", "group-album")
}
