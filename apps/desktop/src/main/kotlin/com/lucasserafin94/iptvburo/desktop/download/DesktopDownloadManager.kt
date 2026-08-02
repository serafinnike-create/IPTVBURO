package com.lucasserafin94.iptvburo.desktop.download

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

    /** Whether this target may be downloaded at all. */
    fun isDownloadable(target: XtreamPlaybackTarget): Boolean =
        when (target) {
            is XtreamPlaybackTarget.CatalogItem -> target.contentType != XtreamContentType.LIVE
            is XtreamPlaybackTarget.Episode -> true
        }

    fun isDownloaded(contentKey: String): Boolean = Files.exists(fileFor(contentKey))

    fun downloadedFile(contentKey: String): Path? = fileFor(contentKey).takeIf(Files::exists)

    fun delete(contentKey: String): Boolean = Files.deleteIfExists(fileFor(contentKey))

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
        val safe = contentKey.replace(UNSAFE_NAME, "_").take(120)
        val extension = containerExtension?.takeIf { it.matches(SAFE_EXTENSION) } ?: "mp4"
        return rootDirectory.resolve("$safe.$extension")
    }

    private companion object {
        const val BUFFER_BYTES = 1 shl 16
        val UNSAFE_NAME = Regex("""[^A-Za-z0-9._-]""")
        val SAFE_EXTENSION = Regex("""[A-Za-z0-9]{1,5}""")

        fun defaultRootDirectory(): Path =
            Path.of(System.getProperty("user.home"), "Videos", "IPTV BURO")
    }
}

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
