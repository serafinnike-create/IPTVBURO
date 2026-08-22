package com.lucasserafin94.iptvburo.domain.model


/**
 * What an export would disclose, presented to the user before anything is written.
 *
 * GDD 8 section 17 allows M3U export "apenas com autorização e aviso sobre URLs sensíveis" — only
 * with authorisation and a warning about sensitive URLs. That is a hard requirement rather than a
 * courtesy, and this type is what makes it enforceable: [MusicPlaylistExporter.export] takes an
 * explicit acknowledgement, so an export cannot be written without the warning having been raised.
 *
 * The risk is concrete. A playlist entry's URI routinely embeds the subscription credentials of
 * whoever supplied it, so an exported file that looks like a harmless track list can hand over an
 * account to anyone it is sent to.
 */
data class MusicPlaylistExportWarning(
    /** How many entries carry a URI that appears to identify or authenticate the user. */
    val sensitiveUriCount: Int,
    val totalTrackCount: Int,
) {
    /**
     * Whether the user must be warned before this export proceeds.
     *
     * Any sensitive entry at all is enough. There is no threshold below which leaking one
     * credential-bearing URL is acceptable.
     */
    val requiresAcknowledgement: Boolean
        get() = sensitiveUriCount > 0

    companion object {
        /**
         * Query and path markers that indicate a URI carries identity.
         *
         * Matched as substrings against the lowercased URI. Deliberately broad: a false positive
         * costs one extra confirmation dialog, while a false negative leaks an account. Xtream-style
         * paths embed the username and password as path segments rather than query parameters,
         * which is why bare `/live/` and `/movie/` style markers are not relied on alone.
         */
        private val SENSITIVE_MARKERS =
            listOf(
                "username=",
                "password=",
                "token=",
                "auth=",
                "authorization=",
                "signature=",
                "sig=",
                "expires=",
                "hdnts=",
                "hdnea=",
                "session=",
                "sessionid=",
                "apikey=",
                "api_key=",
                "access_token=",
                "x-amz-signature",
                "x-amz-credential",
            )

        /**
         * Whether [uri] looks like it carries credentials or a signature.
         *
         * A `file:` URI is local and carries nothing; anything with userinfo before the host
         * ("scheme://user:pass@host") is sensitive by definition.
         */
        fun isSensitive(uri: String): Boolean {
            val value = uri.trim().lowercase()
            if (value.isEmpty()) return false
            if (value.startsWith("file:")) return false
            // "://user:pass@host" — userinfo is credentials by construction.
            val afterScheme = value.substringAfter("://", "")
            if (afterScheme.isNotEmpty()) {
                val authority = afterScheme.substringBefore('/')
                if ('@' in authority) return true
            }
            return SENSITIVE_MARKERS.any { marker -> marker in value }
        }

        /** Builds the warning for [tracks] without writing anything. */
        fun forTracks(tracks: List<MusicTrack>): MusicPlaylistExportWarning =
            MusicPlaylistExportWarning(
                sensitiveUriCount = tracks.count { isSensitive(it.streamUri) },
                totalTrackCount = tracks.size,
            )
    }
}

/** The outcome of an export request. */
sealed interface MusicPlaylistExportResult {
    /** The M3U text, ready to be written by a caller that owns the disk. */
    data class Written(val content: String, val warning: MusicPlaylistExportWarning) : MusicPlaylistExportResult

    /**
     * The export carried sensitive URIs and was not acknowledged, so nothing was produced.
     *
     * Carrying the warning back means the UI can show precisely what it would have disclosed.
     */
    data class NeedsAcknowledgement(val warning: MusicPlaylistExportWarning) : MusicPlaylistExportResult
}

/**
 * Renders a playlist as M3U text.
 *
 * Produces a string rather than touching a file: the domain module owns no I/O, and keeping the
 * rendering pure means the export format is testable without a temporary directory.
 */
object MusicPlaylistExporter {
    /**
     * Renders [tracks] as an M3U, refusing unless a sensitive export has been acknowledged.
     *
     * [acknowledgedSensitiveUris] must be the user's own answer to the warning. Defaulting it to
     * false means a caller that forgets the dialog gets [MusicPlaylistExportResult.NeedsAcknowledgement]
     * rather than silently writing credentials to a file the user may share.
     */
    fun export(
        playlistName: String,
        tracks: List<MusicTrack>,
        acknowledgedSensitiveUris: Boolean = false,
    ): MusicPlaylistExportResult {
        val warning = MusicPlaylistExportWarning.forTracks(tracks)
        if (warning.requiresAcknowledgement && !acknowledgedSensitiveUris) {
            return MusicPlaylistExportResult.NeedsAcknowledgement(warning)
        }

        val body =
            buildString {
                append("#EXTM3U").append('\n')
                // The playlist's own name, so a re-import can recover it. Newlines are stripped
                // because one would otherwise forge an extra directive line in the file.
                append("#PLAYLIST:").append(sanitise(playlistName)).append('\n')
                tracks.forEach { track ->
                    val duration = track.durationSeconds?.takeIf { it > 0L } ?: -1L
                    append("#EXTINF:").append(duration)
                    track.artworkUri?.let { append(" tvg-logo=\"").append(sanitise(it)).append('"') }
                    track.genre?.let { append(" group-title=\"").append(sanitise(it)).append('"') }
                    append(',')
                    val artist = track.artist?.takeIf(String::isNotBlank)
                    append(if (artist == null) sanitise(track.title) else "${sanitise(artist)} - ${sanitise(track.title)}")
                    append('\n')
                    append(track.streamUri.trim()).append('\n')
                }
            }
        return MusicPlaylistExportResult.Written(content = body, warning = warning)
    }

    /**
     * Removes the characters that would break out of an M3U field.
     *
     * A newline in a title would otherwise become a new directive line, and a quote would end an
     * attribute early — both turn a display name into playlist structure.
     */
    private fun sanitise(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace("\"", "").trim()
}
