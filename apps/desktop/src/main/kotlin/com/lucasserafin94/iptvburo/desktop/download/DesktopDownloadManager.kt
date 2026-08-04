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
    private val client: OkHttpClient = OkHttpClient(),
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
            try {
                Files.createDirectories(target.parent)
                val request = Request.Builder().url(uri.toURL()).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext DownloadResult.Failed(FailureReason.REJECTED)
                    }
                    val body = response.body ?: return@withContext DownloadResult.Failed(FailureReason.EMPTY)
                    val total = body.contentLength().takeIf { it > 0L }
                    var read = 0L
                    var cancelledMidStream = false
                    body.byteStream().use { input ->
                        Files.newOutputStream(partial).use { output ->
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
                        return@withContext DownloadResult.Cancelled
                    }
                    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
                    completed = true
                    DownloadResult.Completed(target, read)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (io: IOException) {
                // The exception message can embed the signed URL, so it is never surfaced or logged.
                DownloadResult.Failed(
                    if (io is java.nio.file.FileSystemException) FailureReason.STORAGE else FailureReason.NETWORK,
                )
            } finally {
                // Swept on every exit path, including the early returns for a rejected or empty
                // response and any non-IOException thrown out of the body: each of those used to
                // leave a stray .part file that nothing ever cleaned up.
                if (!completed) runCatching { Files.deleteIfExists(partial) }
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
        val UNSAFE_NAME = Regex("""[^A-Za-z0-9._-]""")
        val SAFE_EXTENSION = Regex("""[A-Za-z0-9]{1,5}""")

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
