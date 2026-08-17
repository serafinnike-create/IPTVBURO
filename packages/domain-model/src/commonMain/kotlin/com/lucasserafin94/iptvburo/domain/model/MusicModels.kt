package com.lucasserafin94.iptvburo.domain.model

/**
 * A playable entry in the music library: either a track or a radio station.
 *
 * Radio stations are not modelled as a separate type. An M3U presents a station exactly as it
 * presents a track — one `#EXTINF` line and one URI — and the only real difference is that the
 * station never ends. Splitting the type would force every consumer to handle two shapes for what
 * the player treats identically, so the difference is carried by [isRadio] instead.
 *
 * [streamUri] and [artworkUri] are sensitive: a playlist URI usually embeds the subscription
 * credentials of whoever supplied it. The custom [toString] keeps them out of logs and crash
 * reports, matching [Channel].
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val streamUri: String,
    val album: String? = null,
    val artworkUri: String? = null,
    val genre: String? = null,
    val isRadio: Boolean = false,
    val durationSeconds: Long? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
) {
    /**
     * The artist to show when the entry never carried one.
     *
     * Callers that group or sort need a non-null key, and every one of them would otherwise invent
     * its own placeholder. Keeping [artist] nullable preserves the "we genuinely do not know" case
     * for anyone who wants to render it differently.
     */
    val artistOrUnknown: String
        get() = artist ?: UNKNOWN_ARTIST

    override fun toString(): String =
        "MusicTrack(" +
            "id=$id, " +
            "title=$title, " +
            "artist=$artist, " +
            "streamUri=<redacted>, " +
            "album=$album, " +
            "artworkUri=${if (artworkUri == null) "null" else "<redacted>"}, " +
            "genre=$genre, " +
            "isRadio=$isRadio, " +
            "durationSeconds=$durationSeconds, " +
            "requestHeaderNames=${requestHeaders.keys.sorted()}" +
            ")"

    companion object {
        /**
         * Deliberately not a translated string. This is a grouping key that has to stay stable
         * across a language change; the UI is free to swap it for a localised label at render time.
         */
        const val UNKNOWN_ARTIST: String = "Unknown Artist"
    }
}

/**
 * An artist shelf, derived from the tracks rather than from any provider metadata.
 *
 * There is no artist entity in an M3U, so this exists only as an aggregate produced by
 * `MusicLibrary.artistsFrom`. [artworkUri] is borrowed from the artist's first track that has one,
 * because a shelf with no cover looks broken next to shelves that have them.
 */
data class MusicArtist(
    val name: String,
    val trackCount: Int,
    val artworkUri: String? = null,
) {
    override fun toString(): String =
        "MusicArtist(" +
            "name=$name, " +
            "trackCount=$trackCount, " +
            "artworkUri=${if (artworkUri == null) "null" else "<redacted>"}" +
            ")"
}

/**
 * A genre row, derived from `group-title`.
 *
 * Music playlists overwhelmingly use `group-title` as a genre label ("Rock", "MPB", "Rádio"),
 * which is a different meaning from the video library, where the same attribute is a provider
 * category. The music library therefore reads it as a genre and does not share the video
 * [Category] type, so a change to one cannot silently reshape the other.
 */
data class MusicGenre(
    val name: String,
    val trackCount: Int,
)

/**
 * The whole music library, in the shape the screens consume.
 *
 * Kept separate from the video catalogue on purpose: the music playlist is optional and supplied
 * independently, and nothing here may reach the existing [Channel] path.
 */
data class MusicLibrary(
    val tracks: List<MusicTrack> = emptyList(),
    val artists: List<MusicArtist> = emptyList(),
    val genres: List<MusicGenre> = emptyList(),
) {
    /** Stations belong on their own shelf; the UI almost never wants them mixed into tracks. */
    val radioStations: List<MusicTrack>
        get() = tracks.filter(MusicTrack::isRadio)

    val songs: List<MusicTrack>
        get() = tracks.filterNot(MusicTrack::isRadio)

    val isEmpty: Boolean
        get() = tracks.isEmpty()

    companion object {
        val EMPTY: MusicLibrary = MusicLibrary()
    }
}
