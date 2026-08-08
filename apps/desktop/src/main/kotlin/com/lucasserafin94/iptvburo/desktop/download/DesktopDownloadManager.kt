package com.lucasserafin94.iptvburo.desktop.download

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Offline copies of VOD items.
 *
 * ## Divergence from GDD 6, by explicit owner decision
 *
 * `docs/GDD_6_BURO_OFFLINE_VAULT.md` gates the download button behind six conditions, including
 * "a autorização permite uso offline", and forbids turning the app into a generic downloader. The
 * owner decided to ship unrestricted VOD download instead. This file is that decision.
 *
 * The constraints kept below are the ones that are not stylistic — removing them would break the
 * app or leak secrets rather than merely widen the feature:
 *
 * - **Live is refused.** A live stream has no end, so a download would grow until the disk fills.
 * - **No URL or credential is written to disk.** The signed URL is resolved in memory per request
 *   and never stored, logged, or included in the file name. This is the same rule the rest of the
 *   app already follows.
 * - **No decryption.** Protected streams are stored exactly as received; the app does not attempt
 *   to strip protection, which would not work anyway.
 */
class DesktopDownloadManager(
    private val rootDirectory: Path = defaultRootDirectory(),
    private val client: OkHttpClient = defaultClient(),
) {
    private val cancelled = ConcurrentHashMap<String, AtomicBoolean>()
    private val gson = Gson()

    /** Whether this target may be downloaded at all. */
    fun isDownloadable(target: XtreamPlaybackTarget): Boolean =
        when (target) {
            is XtreamPlaybackTarget.CatalogItem -> target.contentType != XtreamContentType.LIVE
            is XtreamPlaybackTarget.Episode -> true
        }

    fun isDownloaded(contentKey: String): Boolean = downloadedFile(contentKey) != null

    /**
     * Removes chunks left by downloads that never finished, once they are old enough to be dead.
     *
     * Nothing else sweeps them: a transfer killed by closing the app, or by a provider that stopped
     * sending, leaves its `.part` on disk for ever. One user had 430 MB of an episode that was
     * showing as 100% complete and could not be played, because the file it needed was never
     * written.
     *
     * ## Why there is an age limit
     *
     * This used to delete every `.part` unconditionally at startup, and it destroyed a download the
     * user was still waiting for: they had a 106 MB episode in flight, the app was restarted, and
     * the transfer's own file was swept out from under it. A quarter of an hour without a single
     * byte written is the difference between "abandoned" and "still going" — an active download
     * touches its file constantly, so anything stale by that much is genuinely dead.
     *
     * Erring towards keeping: a stray chunk costs disk space the user can see and delete, while a
     * wrongly swept one costs them the download and gives no clue why.
     */
    fun discardInterruptedDownloads(): Int {
        if (!Files.isDirectory(rootDirectory)) return 0
        val deadline = System.currentTimeMillis() - STALE_PART_MILLIS
        return runCatching {
            Files.list(rootDirectory).use { stream ->
                stream
                    .filter { path -> Files.isRegularFile(path) }
                    .filter { path -> path.fileName.toString().endsWith(".part") }
                    .filter { path ->
                        runCatching { Files.getLastModifiedTime(path).toMillis() < deadline }
                            .getOrDefault(false)
                    }.toList()
            }.count { path -> runCatching { Files.deleteIfExists(path) }.getOrDefault(false) }
        }.getOrDefault(0)
    }

    /**
     * Completed downloads on disk, keyed by content key.
     *
     * Lets the library rebuild its list after a restart. The key is recovered from the file name,
     * which is why the name is derived from the key rather than from the stream URL. Title and
     * artwork come from the sidecar written at download time; without it the list could only show
     * the sanitised key, which is how `movie_supergirl_2026` ended up on screen.
     */
    fun storedDownloads(): Map<String, StoredDownload> {
        if (!Files.isDirectory(rootDirectory)) return emptyMap()
        val sidecars = readIndex()
        return Files.list(rootDirectory).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) }
                .map { path -> path.fileName.toString() }
                // `.part` files are interrupted downloads, not stored copies.
                // The index itself is bookkeeping, not a download.
                .filter { name -> !name.endsWith(".part") && name != INDEX_FILE }
                .toList()
                .associate { name ->
                    val storedName = name.substringBeforeLast('.')
                    // A file with no sidecar is still a finished download: the copy is what proves
                    // it, not the bookkeeping beside it. Transfers interrupted between the move and
                    // the sidecar write left files that were complete and unusable.
                    val sidecar = sidecars[storedName]
                    // The original key, not the sanitised file name: the app looks downloads up by
                    // the same content key it uses for playback and favourites, and for an episode
                    // those differ ("series:x|s1e3" against "series_x_s1e3").
                    val key = sidecar?.contentKey?.takeIf(String::isNotBlank) ?: storedName
                    key to (sidecar ?: StoredDownload(storedName.toReadableTitle(), null, key))
                }
        }
    }

    /**
     * Records how a stored download should be presented.
     *
     * Only the poster URL is kept, never the stream URL: artwork is public and unauthenticated,
     * while the media address carries the account credentials.
     */
    fun remember(
        contentKey: String,
        title: String,
        artworkUrl: String?,
    ) {
        runCatching {
            Files.createDirectories(rootDirectory)
            // Indexed by the sanitised name, which is what storedDownloads recovers from the file
            // on disk. Storing the raw key meant the two never matched for anything containing a
            // ':' or '|' - every episode - so the library fell back to the mangled key as a title.
            val updated =
                readIndex() + (safeName(contentKey) to StoredDownload(title, artworkUrl, contentKey))
            Files.writeString(rootDirectory.resolve(INDEX_FILE), gson.toJson(updated))
        }
    }

    private fun readIndex(): Map<String, StoredDownload> {
        val file = rootDirectory.resolve(INDEX_FILE)
        if (!Files.isRegularFile(file)) return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, StoredDownload>>() {}.type
            gson.fromJson<Map<String, StoredDownload>>(Files.readString(file), type).orEmpty()
        }.getOrDefault(emptyMap())
    }

    /**
     * The stored copy for [contentKey], whatever container it was saved in.
     *
     * Matching on the key rather than assuming `.mp4` is what makes an episode downloaded as `.mkv`
     * findable: the extension comes from the provider and is not known here.
     */
    fun downloadedFile(contentKey: String): Path? {
        if (!Files.isDirectory(rootDirectory)) return null
        val safe = safeName(contentKey)
        return Files.list(rootDirectory).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) }
                .filter { path ->
                    val name = path.fileName.toString()
                    !name.endsWith(".part") && name.substringBeforeLast('.') == safe
                }
                .findFirst()
                .orElse(null)
        }
    }

    fun delete(contentKey: String): Boolean {
        val removed = downloadedFile(contentKey)?.let(Files::deleteIfExists) ?: false
        runCatching {
            val remaining = readIndex() - safeName(contentKey)
            Files.writeString(rootDirectory.resolve(INDEX_FILE), gson.toJson(remaining))
        }
        return removed
    }

    fun cancel(contentKey: String) {
        cancelled[contentKey]?.set(true)
    }

    /**
     * Streams [uri] to disk, reporting progress in bytes.
     *
     * Writes to a `.part` file and moves it into place only on success, so an interrupted download
     * never leaves a truncated file that later looks like a complete one.
     */
    suspend fun download(
        contentKey: String,
        displayName: String,
        uri: URI,
        containerExtension: String?,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    ): DownloadResult =
        withContext(Dispatchers.IO) {
            val flag = AtomicBoolean(false)
            // Claimed atomically. Two downloads of the same key share one `.part` path and used to
            // interleave their writes into it, producing a file that was neither copy; worse, the
            // second registration replaced the first's cancel flag and the first's `finally` then
            // removed the second's, so Cancel silently stopped working for both.
            if (cancelled.putIfAbsent(contentKey, flag) != null) {
                return@withContext DownloadResult.Failed(FailureReason.ALREADY_RUNNING)
            }
            val target = fileFor(contentKey, containerExtension)
            val partial = target.resolveSibling(target.fileName.toString() + ".part")
            var completed = false

            /**
             * Whether the chunk on disk is a usable prefix that a later attempt can resume from.
             *
             * Set only where what was written is genuinely the start of the file: a cancellation
             * and a dropped connection. A rejected or empty response leaves nothing worth keeping.
             */
            var keepPartial = false
            try {
                Files.createDirectories(target.parent)

                // What a previous attempt already wrote, if anything.
                //
                // A 600 MB episode over a domestic line takes long enough that an interruption is
                // ordinary rather than exceptional — closing the app, a provider that drops the
                // connection, a laptop that sleeps. Starting again from zero every time made a
                // large download on an unreliable line effectively impossible to finish.
                val alreadyHave =
                    runCatching { if (Files.exists(partial)) Files.size(partial) else 0L }
                        .getOrDefault(0L)

                val request =
                    Request.Builder()
                        .url(uri.toURL())
                        .apply {
                            // Asks the server to send from where the chunk ends. A server that does
                            // not support ranges simply ignores this and answers 200 with the whole
                            // file, which the branch below handles by starting over.
                            if (alreadyHave > 0L) header("Range", "bytes=$alreadyHave-")
                        }.build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext DownloadResult.Failed(FailureReason.REJECTED)
                    }
                    // 206 means the server honoured the range and is sending the remainder; 200
                    // means it ignored it and is sending everything. Getting this backwards would
                    // append a whole second copy onto the chunk and produce a corrupt file, so the
                    // append decision follows the status code rather than what was asked for.
                    val resuming = alreadyHave > 0L && response.code == HTTP_PARTIAL_CONTENT

                    // OkHttp 5's body is non-null, so the old `?: Failed(EMPTY)` never fired and
                    // EMPTY was unreachable. The condition it was meant to catch is real, though —
                    // a provider answering 200 with nothing in it — and is checked after the
                    // transfer instead, where "nothing arrived" is a fact rather than a guess.
                    val body = response.body
                    // The whole file's size, not this response's: on a resumed transfer the body
                    // carries only the remainder, and reporting that as the total would show the
                    // bar restarting at zero for a download that is most of the way done.
                    val total =
                        body.contentLength().takeIf { it > 0L }?.let { length ->
                            if (resuming) length + alreadyHave else length
                        }
                    var read = if (resuming) alreadyHave else 0L
                    var cancelledMidStream = false
                    body.byteStream().use { input ->
                        val sink =
                            if (resuming) {
                                Files.newOutputStream(partial, StandardOpenOption.APPEND)
                            } else {
                                // Not appending: the server sent the whole file, so anything
                                // already in the chunk is a prefix of what is arriving now.
                                Files.newOutputStream(partial)
                            }
                        sink.use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                if (flag.get()) {
                                    // Flagged rather than deleted here, so the single sweep in
                                    // `finally` owns removal of the chunk on every exit path
                                    // instead of each branch remembering to do it for itself.
                                    cancelledMidStream = true
                                    return@use
                                }
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                read += count
                                onProgress(read, total)
                            }
                        }
                    }
                    if (cancelledMidStream) {
                        // Everything written is a valid prefix, so pressing download again picks up
                        // from here rather than starting the transfer over.
                        keepPartial = read > 0L
                        return@withContext DownloadResult.Cancelled
                    }
                    // A 200 that carried nothing. Reported as a failure rather than moved into
                    // place: an empty file passes every later check — it exists, so the library
                    // lists it as downloaded and offers to play it — and the user gets a title
                    // that opens to a black screen with no explanation. The `.part` is swept by
                    // the `finally` below, since `completed` is still false here.
                    if (read == 0L) {
                        return@withContext DownloadResult.Failed(FailureReason.EMPTY)
                    }
                    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
                    completed = true
                    DownloadResult.Completed(target, read)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (io: IOException) {
                // A dropped connection mid-transfer: what arrived is still the start of the file,
                // so it is kept and the next attempt resumes. A storage error is different — the
                // chunk may be truncated or unwritable — and that one is swept.
                keepPartial = io !is java.nio.file.FileSystemException &&
                    runCatching { Files.exists(partial) && Files.size(partial) > 0L }.getOrDefault(false)
                // The exception message can embed the signed URL, so it is never surfaced or logged.
                DownloadResult.Failed(
                    if (io is java.nio.file.FileSystemException) FailureReason.STORAGE else FailureReason.NETWORK,
                )
            } finally {
                // Kept when it can still be resumed, swept when it cannot.
                //
                // This deleted the chunk on every unsuccessful exit, which is right for a rejected
                // or empty response — those leave a file that is not a prefix of anything — and
                // wrong for the two ordinary interruptions: a cancellation and a dropped
                // connection. Both leave a valid prefix, and throwing it away is what made a large
                // download on an unreliable line impossible to finish.
                if (!completed && !keepPartial) runCatching { Files.deleteIfExists(partial) }
                // Only if it is still ours, so a later download of the same key keeps its own flag.
                cancelled.remove(contentKey, flag)
            }
        }

    /**
     * File name derived from the content key, not from the stream URL.
     *
     * Keeps signed URLs and credentials out of the filesystem, and keeps the name stable so a
     * download can be recognised again after the playlist is replaced.
     */
    private fun fileFor(
        contentKey: String,
        containerExtension: String? = null,
    ): Path {
        val extension = containerExtension?.takeIf { it.matches(SAFE_EXTENSION) } ?: "mp4"
        return rootDirectory.resolve("${safeName(contentKey)}.$extension")
    }

    private fun safeName(contentKey: String): String =
        contentKey.replace(UNSAFE_NAME, "_").take(120)

    private companion object {
        const val BUFFER_BYTES = 1 shl 16
        const val INDEX_FILE = "library.json"

        /**
         * 206 Partial Content: the server honoured the Range header and is sending the remainder.
         *
         * A plain 200 to a ranged request means it ignored the header and is sending the whole file
         * again — appending in that case would concatenate a second copy onto the chunk and produce
         * a file that is corrupt in a way nothing later checks for.
         */
        const val HTTP_PARTIAL_CONTENT = 206

        /**
         * How long a `.part` must go untouched before it counts as abandoned.
         *
         * An active transfer writes to its file continuously, so fifteen minutes of silence means
         * the process that owned it is gone. Long enough to survive a restart while a download is
         * running, which is the case that lost a user 106 MB mid-transfer.
         */
        const val STALE_PART_MILLIS = 15 * 60 * 1000L
        val UNSAFE_NAME = Regex("""[^A-Za-z0-9._-]""")
        val SAFE_EXTENSION = Regex("""[A-Za-z0-9]{1,5}""")

        /**
         * The client used for downloads.
         *
         * A read timeout is the whole point. OkHttp's default is none, so a provider that stops
         * sending mid-file left the transfer hanging for ever: the bar sat at whatever fraction it
         * had reached - 100% when the announced length was wrong - with a .part file on disk, no
         * error, and no way to tell a finished download from an abandoned one.
         *
         * Generous, because a large file over a slow line legitimately pauses between chunks; it
         * only has to be shorter than "for ever".
         */
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .readTimeout(java.time.Duration.ofSeconds(60))
                .retryOnConnectionFailure(true)
                .build()

        fun defaultRootDirectory(): Path =
            Path.of(System.getProperty("user.home"), "Videos", "IPTV BURO")
    }
}

/**
 * How a completed download should be presented in the library.
 *
 * [contentKey] is the app's own key, kept because the file name is a sanitised form of it and the
 * two differ for anything containing ':' or '|' — every episode. Null for entries written before
 * this field existed, where the sanitised name is the best available answer.
 */
data class StoredDownload(
    val title: String,
    val artworkUrl: String?,
    val contentKey: String? = null,
)

sealed interface DownloadResult {
    data class Completed(val file: Path, val bytes: Long) : DownloadResult

    data object Cancelled : DownloadResult

    data class Failed(val reason: FailureReason) : DownloadResult
}

enum class FailureReason {
    NETWORK,
    STORAGE,
    REJECTED,
    EMPTY,

    /**
     * The same content key is already downloading.
     *
     * Distinct from the other reasons because nothing went wrong: the first download is still
     * running and owns the `.part` file, so the caller must leave its state alone.
     */
    ALREADY_RUNNING,
}

/**
 * Best-effort title from a sanitised content key, used when the real title is not in memory.
 *
 * A key looks like `movie_the_godfather_1972`: the kind prefix and the trailing year are structure,
 * not part of the name, and the separators were `:` and `-` before the filesystem-safe rewrite.
 * The stored title is preferred whenever the app still has it; this only has to be good enough for
 * a copy downloaded in an earlier session.
 */
internal fun String.toReadableTitle(): String {
    val withoutKind = KIND_PREFIX.replace(this, "")
    val withoutYear = TRAILING_YEAR.replace(withoutKind, "")
    val words =
        withoutYear
            .split('_', '-')
            .filter(String::isNotBlank)
            .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }
    return words.ifBlank { this }
}

private val KIND_PREFIX = Regex("^(movie|series|episode|live)_")
private val TRAILING_YEAR = Regex("""_(18|19|20|21)\d{2}$""")
