package com.lucasserafin94.iptvburo.webdav

/**
 * Which files in a share are worth showing.
 *
 * A media share holds more than media: subtitle files, artwork, `.nfo` metadata, sample clips and
 * whatever else a downloader left behind. Listing all of it turns a film folder into a list nobody
 * can read, so the browser shows what can be played and hides the rest.
 *
 * Decided by extension rather than by the server's declared content type. Servers are unreliable
 * about that field — many send `application/octet-stream` for everything — and the extension is
 * what the file actually is.
 */
object WebDavMediaFiles {
    /**
     * Whether [entry] should appear in a browser.
     *
     * Folders always do: a folder is how somebody reaches the files, and refusing to show one
     * because of its name would strand whatever is inside it.
     */
    fun isBrowsable(entry: WebDavEntry): Boolean {
        if (entry.isDirectory) return true
        val name = entry.displayName.lowercase()
        if (SAMPLE.containsMatchIn(name.substringBeforeLast('.', name))) return false
        return name.substringAfterLast('.', "") in PLAYABLE_EXTENSIONS
    }

    /**
     * A sample clip, which sits beside the film and is not the film.
     *
     * Anchored at the *end* of the name, not matched anywhere in it. The first attempt matched
     * "sample" as a whole word wherever it appeared, which also hid *Sample This (2011)* — a real
     * film — because a title may legitimately begin with the word; a test caught it. A downloader's
     * sample is "sample.mkv" or "Duna-sample.mkv", so the marker is always last before the
     * extension.
     */
    private val SAMPLE = Regex("(^|[ ._-])sample$")

    /**
     * What Media3 can be expected to play.
     *
     * Deliberately a list rather than "anything that is not obviously a document": an unknown
     * extension shown and then failing to open is a worse experience than one that was never
     * offered, and this covers what actually appears in film and series libraries.
     */
    private val PLAYABLE_EXTENSIONS =
        setOf(
            "mkv", "mp4", "m4v", "avi", "mov", "wmv", "flv", "webm", "mpg", "mpeg", "m2ts", "ts",
            "vob", "ogv", "3gp", "divx", "rmvb",
            // Audio, for a share used as a music library — GDD 8 territory, and free to allow.
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma",
        )
}
