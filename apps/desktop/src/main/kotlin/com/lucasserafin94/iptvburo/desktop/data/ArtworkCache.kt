package com.lucasserafin94.iptvburo.desktop.data

import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import java.nio.file.Files
import java.nio.file.Path

/**
 * The artwork Coil keeps on disk, seen from outside Coil.
 *
 * Three things the rest of the app needs and Coil does not offer directly: fetch an image *now* so
 * a later draw is instant, say how much room the cache is taking, and empty it.
 *
 * Everything here is best-effort. A cache is an optimisation, so a failure to warm one poster, or
 * to measure the directory, must never surface as an error — the app simply behaves as it did
 * before the cache existed.
 */
internal object ArtworkCache {
    /**
     * Fetches [url] into the cache without drawing it.
     *
     * `enqueue` rather than `execute`: nothing is waiting for the bitmap, and decoding it into
     * memory only to discard it would cost more than the download. Coil writes the bytes to disk on
     * the way past, which is the whole point.
     */
    fun warm(url: String) {
        val loader = SingletonImageLoader.get(PlatformContextHolder.context ?: return)
        loader.enqueue(
            ImageRequest.Builder(PlatformContextHolder.context ?: return)
                .data(url)
                .build(),
        )
    }

    /**
     * How many bytes the cache directory holds.
     *
     * Measured by walking the directory rather than asked of Coil, because Coil's own accounting is
     * internal and because this is the number somebody can check in Explorer — reporting anything
     * else would be reporting a number the viewer can disprove.
     */
    fun bytesUsed(): Long {
        val directory = directory()
        if (!Files.isDirectory(directory)) return 0
        return runCatching {
            Files.walk(directory).use { paths ->
                paths.filter(Files::isRegularFile).mapToLong { path ->
                    runCatching { Files.size(path) }.getOrDefault(0L)
                }.sum()
            }
        }.getOrDefault(0L)
    }

    /** Deletes everything held. The next draw fetches again, which is the cost of asking for this. */
    fun clear() {
        val directory = directory()
        if (!Files.isDirectory(directory)) return
        runCatching {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    runCatching { Files.deleteIfExists(path) }
                }
            }
        }
    }

    /** Beside the catalogue cache, so everything this app writes is in one place. */
    fun directory(): Path =
        Path.of(System.getProperty("user.home"), ".iptvburo", "artwork-cache")
}

/**
 * Coil needs a PlatformContext to build a request, and on the desktop there is exactly one.
 *
 * Held here rather than threaded through every caller: on this platform the context carries no
 * state worth scoping, and passing it down from the window into a data-layer object would be
 * ceremony around a value that is the same everywhere.
 */
internal object PlatformContextHolder {
    @Volatile
    var context: coil3.PlatformContext? = null
}
