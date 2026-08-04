package com.lucasserafin94.iptvburo.playlist

import com.lucasserafin94.iptvburo.domain.model.MusicLibrary
import com.lucasserafin94.iptvburo.domain.model.PlaylistHeader
import java.io.InputStream

/**
 * The parsed music playlist, plus everything the caller needs to report on the import.
 *
 * The warnings are the ordinary [M3uParser] warnings, unchanged. A malformed line in a music
 * playlist fails exactly as it does in a video playlist, and the UI can surface the same message.
 */
data class MusicPlaylistResult(
    val header: PlaylistHeader,
    val library: MusicLibrary,
    val warnings: List<M3uWarning>,
    val bytesRead: Long,
    val suppressedWarningCount: Int = 0,
)

/**
 * Parses an M3U as a music playlist.
 *
 * A thin composition of [M3uParser] and [MusicPlaylistMapper], not a separate parser. All the M3U
 * handling the video path relies on — encoding detection, size limits, request headers, malformed
 * line recovery — applies here unchanged, because it is literally the same code.
 *
 * The music library is a second, optional library. Nothing in this file writes to the video
 * catalogue, and a music import that fails leaves the video library exactly as it was.
 */
class MusicPlaylistParser(
    private val parser: M3uParser = M3uParser(),
) {
    /**
     * The caller owns [input] and remains responsible for closing it, matching [M3uParser.parse].
     *
     * Unlike the video path there is no streaming variant. A music library is grouped by artist and
     * by genre, and both groupings need the whole playlist in hand before they can be ordered, so
     * there is nothing to be gained by emitting tracks one at a time.
     */
    fun parse(
        input: InputStream,
        idPrefix: String = "",
    ): MusicPlaylistResult {
        val result = parser.parse(input)
        return MusicPlaylistResult(
            header = result.header,
            library = MusicPlaylistMapper.toLibrary(result.channels, idPrefix),
            warnings = result.warnings,
            bytesRead = result.bytesRead,
            suppressedWarningCount = result.suppressedWarningCount,
        )
    }
}
