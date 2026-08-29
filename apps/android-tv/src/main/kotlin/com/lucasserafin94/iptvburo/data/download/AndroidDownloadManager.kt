package com.lucasserafin94.iptvburo.data.download

import android.content.Context
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Offline copies of VOD items, mirroring `DesktopDownloadManager` on Windows.
 *
 * ## Divergence from GDD 6, by explicit owner decision
 *
 * `docs/GDD_6_BURO_OFFLINE_VAULT.md` gates the download button behind six conditions, including
 * "a autorização permite uso offline". `docs/adr/ADR-008-UNRESTRICTED-VOD-DOWNLOAD.md` records the
 * owner's decision to ship unrestricted VOD download instead. This file is the Android half of that
 * decision, and it keeps exactly the three constraints the ADR calls non-stylistic:
 *
 * - **Live is refused.** A live stream has no end, so a download would grow until storage fills.
 * - **No URL or credential is written to disk.** The signed URL is resolved in memory per request
 *   and never stored, logged, or included in the file name. This is the same rule the rest of the
 *   app already follows, and it is why [download] swallows the exception message on failure.
 * - **No decryption.** Protected streams are stored exactly as received; the app does not attempt
 *   to strip protection, which would not work anyway.
 *
 * The context is resolved through a [Provider] rather than held directly, because the storage
 * directory is only needed once a download actually starts — construction must stay free of
 * platform work so the view model remains testable without an Android runtime.
 */
@Singleton
class AndroidDownloadManager @Inject constructor(
    @param:ApplicationContext private val contextProvider: Provider<Context>,
    private val client: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val cancelled = ConcurrentHashMap<String, AtomicBoolean>()

    /**
     * App-private external storage, so an offline copy is removed with the app and never lands in
     * the shared media store where another app could read it.
     */
    private val rootDirectory: File
        get() {
            val context = contextProvider.get()
            val parent = context.getExternalFilesDir(null) ?: context.filesDir
            return File(parent, VAULT_DIRECTORY)
        }

    /** Whether this content type may be downloaded at all. */
    fun isDownloadable(contentType: CatalogContentType): Boolean =
        when (contentType) {
            CatalogContentType.MOVIE,
            CatalogContentType.SERIES,
            CatalogContentType.EPISODE,
            -> true

            CatalogContentType.LIVE,
            CatalogContentType.UNKNOWN,
            -> false
        }

    fun isDownloaded(contentKey: String): Boolean = downloadedFile(contentKey) != null

    /** The stored copy, whatever container the provider used, or null when nothing is stored. */
    fun downloadedFile(contentKey: String): File? {
        val prefix = safeStem(contentKey) + "."
        return rootDirectory
            .listFiles()
            ?.firstOrNull { file ->
                file.isFile && file.name.startsWith(prefix) && !file.name.endsWith(PART_SUFFIX)
            }
    }

    @Synchronized
    fun delete(contentKey: String): Boolean {
        val deleted = downloadedFile(contentKey)?.delete() == true
        forget(contentKey)
        return deleted
    }

    /**
     * Persists only catalogue identity and a display title after the bytes are complete.
     * Stream URLs, headers and credentials are deliberately absent.
     */
    @Synchronized
    fun rememberCompleted(
        contentKey: String,
        title: String,
        sourceId: String,
        providerItemId: String?,
        contentType: CatalogContentType,
    ) {
        val preferences = metadataPreferences()
        val known = preferences.getStringSet(METADATA_KEYS, emptySet()).orEmpty().toMutableSet()
        known += contentKey
        preferences.edit()
            .putStringSet(METADATA_KEYS, known)
            .putString(metaKey(METADATA_TITLE, contentKey), title.take(240))
            .putString(metaKey(METADATA_SOURCE, contentKey), sourceId)
            .putString(metaKey(METADATA_PROVIDER_ITEM, contentKey), providerItemId)
            .putString(metaKey(METADATA_CONTENT_TYPE, contentKey), contentType.name)
            .apply()
    }

    /** Completed entries that still have a real file on disk. */
    @Synchronized
    fun storedDownloads(): List<StoredDownload> {
        val preferences = metadataPreferences()
        return preferences.getStringSet(METADATA_KEYS, emptySet()).orEmpty().mapNotNull { contentKey ->
            val file = downloadedFile(contentKey) ?: return@mapNotNull null
            StoredDownload(
                contentKey = contentKey,
                title = preferences.getString(metaKey(METADATA_TITLE, contentKey), null) ?: contentKey,
                sourceId = preferences.getString(metaKey(METADATA_SOURCE, contentKey), null).orEmpty(),
                providerItemId = preferences.getString(metaKey(METADATA_PROVIDER_ITEM, contentKey), null),
                contentType =
                    runCatching {
                        CatalogContentType.valueOf(
                            preferences.getString(metaKey(METADATA_CONTENT_TYPE, contentKey), null).orEmpty(),
                        )
                    }.getOrDefault(CatalogContentType.MOVIE),
                file = file,
            )
        }
    }

    fun cancel(contentKey: String) {
        cancelled[contentKey]?.set(true)
    }

    /**
     * Streams [url] to storage, reporting progress in bytes.
     *
     * Writes to a `.part` file and renames it into place only on success, so an interrupted
     * download never leaves a truncated file that later looks like a complete one.
     */
    suspend fun download(
        contentKey: String,
        url: String,
        containerExtension: String?,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    ): DownloadResult =
        withContext(ioDispatcher) {
            val flag = AtomicBoolean(false)
            cancelled[contentKey] = flag
            val target = fileFor(contentKey, containerExtension)
            val partial = File(target.parentFile, target.name + PART_SUFFIX)
            try {
                target.parentFile?.mkdirs()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext DownloadResult.Failed(FailureReason.REJECTED)
                    }
                    val body = response.body
                    val total = body.contentLength().takeIf { it > 0L }
                    var read = 0L
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                if (flag.get()) {
                                    partial.delete()
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
                    // Re-checked after the loop: cancellation landing right as the last chunk hits
                    // EOF would otherwise exit the loop via `break` without the flag ever being
                    // seen, and the file below gets finalized despite the caller believing it was
                    // cancelled.
                    if (flag.get()) {
                        partial.delete()
                        return@withContext DownloadResult.Cancelled
                    }
                    // A 200 carrying no body would otherwise be filed as a complete offline copy
                    // that plays back as nothing.
                    if (read == 0L) {
                        partial.delete()
                        return@withContext DownloadResult.Failed(FailureReason.EMPTY)
                    }
                    // An older copy in a different container would otherwise survive alongside the
                    // new one and be picked up first by downloadedFile.
                    downloadedFile(contentKey)?.delete()
                    if (!partial.renameTo(target)) {
                        partial.delete()
                        return@withContext DownloadResult.Failed(FailureReason.STORAGE)
                    }
                    DownloadResult.Completed(target, read)
                }
            } catch (cancellation: CancellationException) {
                partial.delete()
                throw cancellation
            } catch (io: IOException) {
                partial.delete()
                // The exception message can embed the signed URL, so it is never surfaced or logged.
                DownloadResult.Failed(
                    if (rootDirectory.usableSpace == 0L) FailureReason.STORAGE else FailureReason.NETWORK,
                )
            } catch (malformed: IllegalArgumentException) {
                // OkHttp rejects a URL it cannot parse. The message quotes the URL, so it is dropped.
                partial.delete()
                DownloadResult.Failed(FailureReason.REJECTED)
            } finally {
                cancelled.remove(contentKey)
            }
        }

    /**
     * File name derived from the content key, not from the stream URL.
     *
     * Keeps signed URLs and credentials out of storage, and keeps the name stable so a download can
     * be recognised again after the playlist is replaced.
     */
    private fun fileFor(
        contentKey: String,
        containerExtension: String?,
    ): File {
        val extension = containerExtension?.takeIf { it.matches(SAFE_EXTENSION) } ?: "mp4"
        return File(rootDirectory, "${safeStem(contentKey)}.$extension")
    }

    private fun safeStem(contentKey: String): String = contentKey.replace(UNSAFE_NAME, "_").take(120)

    private fun metadataPreferences() =
        contextProvider.get().getSharedPreferences(METADATA_PREFERENCES, Context.MODE_PRIVATE)

    private fun metaKey(prefix: String, contentKey: String): String = "$prefix$contentKey"

    private fun forget(contentKey: String) {
        val preferences = metadataPreferences()
        val known = preferences.getStringSet(METADATA_KEYS, emptySet()).orEmpty().toMutableSet()
        known -= contentKey
        preferences.edit()
            .putStringSet(METADATA_KEYS, known)
            .remove(metaKey(METADATA_TITLE, contentKey))
            .remove(metaKey(METADATA_SOURCE, contentKey))
            .remove(metaKey(METADATA_PROVIDER_ITEM, contentKey))
            .remove(metaKey(METADATA_CONTENT_TYPE, contentKey))
            .apply()
    }

    private companion object {
        const val BUFFER_BYTES = 1 shl 16
        const val PART_SUFFIX = ".part"
        const val VAULT_DIRECTORY = "offline-vault"
        const val METADATA_PREFERENCES = "offline-vault-index"
        const val METADATA_KEYS = "completed.keys"
        const val METADATA_TITLE = "title:"
        const val METADATA_SOURCE = "source:"
        const val METADATA_PROVIDER_ITEM = "provider-item:"
        const val METADATA_CONTENT_TYPE = "content-type:"
        val UNSAFE_NAME = Regex("""[^A-Za-z0-9._-]""")
        val SAFE_EXTENSION = Regex("""[A-Za-z0-9]{1,5}""")
    }
}

data class StoredDownload(
    val contentKey: String,
    val title: String,
    val sourceId: String,
    val providerItemId: String?,
    val contentType: CatalogContentType,
    val file: File,
)

sealed interface DownloadResult {
    data class Completed(val file: File, val bytes: Long) : DownloadResult

    data object Cancelled : DownloadResult

    data class Failed(val reason: FailureReason) : DownloadResult
}

enum class FailureReason {
    NETWORK,
    STORAGE,
    REJECTED,
    EMPTY,
}
