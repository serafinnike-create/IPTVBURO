package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.MusicLibrary
import com.lucasserafin94.iptvburo.domain.model.MusicTrack
import com.lucasserafin94.iptvburo.playlist.MusicPlaylistParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the user's optional music M3U from disk.
 *
 * Deliberately not part of the video import path: this produces a [MusicLibrary] and touches
 * nothing the catalogue uses, so a music playlist that fails to load leaves the rest of the app
 * exactly as it was.
 */
class MusicLibraryLoader(
    private val parser: MusicPlaylistParser = MusicPlaylistParser(),
) {
    /**
     * Parses [path], or returns null when it cannot be read.
     *
     * Null rather than an exception because every caller treats a missing or unreadable music
     * playlist the same way — the Músicas section simply does not appear. The file is the user's
     * own and may have been moved or deleted since they chose it, which is not an error worth
     * interrupting them over.
     */
    fun load(path: Path): MusicLibrary? =
        runCatching {
            if (!Files.isRegularFile(path)) return null
            Files.newInputStream(path).use { input ->
                parser.parse(input, idPrefix = MUSIC_ID_PREFIX).library
            }
        }.getOrNull()

    private companion object {
        /**
         * Namespaces music track ids so they can never collide with a video content key, which is
         * what play counts and playback identities are stored against.
         */
        const val MUSIC_ID_PREFIX = "music"
    }
}

/**
 * The music home, arranged into the two shelves the screen shows.
 *
 * Held as a type rather than computed in the composable because "most played" depends on stored
 * counts, and recomputing an ordering on every recomposition is how the video home's shelves ended
 * up being rebuilt on every frame.
 */
data class MusicHomeShelves(
    val newReleases: List<MusicTrack>,
    val mostPlayed: List<MusicTrack>,
)

/**
 * Builds the home shelves from a library and the user's play counts.
 *
 * "New releases" is the head of the playlist in the author's own order. An M3U carries no release
 * date, so the only honest reading of "new" is "what the person who made this list put first" —
 * inventing a date from the file would be a guess presented as fact.
 *
 * "Most played" is omitted entirely when nothing has been played yet, rather than shown as an
 * arbitrary slice: a shelf claiming to rank plays that never happened is worse than no shelf.
 */
fun musicHomeShelves(
    library: MusicLibrary,
    playCounts: Map<String, Int>,
    shelfSize: Int = MUSIC_SHELF_SIZE,
): MusicHomeShelves {
    val songs = library.songs
    val mostPlayed =
        songs
            .mapNotNull { track -> playCounts[track.id]?.let { count -> track to count } }
            .sortedWith(
                compareByDescending<Pair<MusicTrack, Int>> { it.second }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.title },
            ).take(shelfSize)
            .map { it.first }

    return MusicHomeShelves(
        newReleases = songs.take(shelfSize),
        mostPlayed = mostPlayed,
    )
}

/** Titles per music shelf. Matches the video home's rails so the two read as one product. */
const val MUSIC_SHELF_SIZE: Int = 18
