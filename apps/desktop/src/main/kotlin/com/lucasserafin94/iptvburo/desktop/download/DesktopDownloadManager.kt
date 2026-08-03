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
                    val key = name.substringBeforeLast('.')
                    key to (sidecars[key] ?: StoredDownload(key.toReadableTitle(), null))
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
            val updated = readIndex() + (contentKey to StoredDownload(title, artworkUrl))
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
            val remaining = readIndex() - contentKey
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
            cancelled[contentKey] = flag
            val target = fileFor(contentKey, containerExtension)
            val partial = target.resolveSibling(target.fileName.toString() + ".part")
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
                    body.byteStream().use { input ->
                        Files.newOutputStream(partial).use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                if (flag.get()) {
                                    Files.deleteIfExists(partial)
                                    return@withContext DownloadResult.Cancelled
                                }
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                read += count
                                onProgress(read, total)
                            }
                        }
                    }
                    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
                    DownloadResult.Completed(target, read)
                }
            } catch (cancellation: CancellationException) {
                Files.deleteIfExists(partial)
                throw cancellation
            } catch (io: IOException) {
                Files.deleteIfExists(partial)
                // The exception message can embed the signed URL, so it is never surfaced or logged.
                DownloadResult.Failed(
                    if (io is java.nio.file.FileSystemException) FailureReason.STORAGE else FailureReason.NETWORK,
                )
            } finally {
                cancelled.remove(contentKey)
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

/** How a completed download should be presented in the library. */
data class StoredDownload(
    val title: String,
    val artworkUrl: String?,
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
