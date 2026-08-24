package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

/**
 * The provider's catalogue, kept on disk between sessions.
 *
 * Every launch re-downloaded and re-parsed the whole thing — 41,717 items on this machine — before
 * the library could be shown. That is most of the wait a returning user sits through, and none of
 * it buys anything: a provider's catalogue changes over days, not over the minutes between closing
 * the app and opening it again.
 *
 * ## Why a binary format rather than JSON
 *
 * The point is to be faster than re-parsing the provider's own JSON. Writing it back out as JSON
 * and parsing that again would save the network round trip and keep the parse, which is the larger
 * half. This writes the same columns [CompactXtreamCatalog] already stores, in order, so reading is
 * a linear pass with no object allocation per field.
 *
 * ## What is deliberately not stored
 *
 * No URL, no credential, no signed address. The catalogue holds provider ids, titles, category ids
 * and artwork addresses; playback URLs are built in memory at the moment of playing and are never
 * written anywhere. That is the same rule the download manager follows.
 *
 * ## Staleness
 *
 * A cache is used only when it is younger than [MAX_AGE] and was written by this format version and
 * for this account. The account is identified by a salted hash of the credentials rather than the
 * credentials themselves — enough to tell "this is a different subscription, throw it away" without
 * writing anything sensitive to disk.
 */
internal class CatalogDiskCache(
    private val directory: Path = defaultDirectory(),
    private val maxAge: Duration = MAX_AGE,
) {
    /**
     * Reads the cached catalogue for [contentType], or null when there is nothing usable.
     *
     * Null covers every failure — absent, stale, wrong account, wrong version, truncated, corrupt.
     * A cache is an optimisation, so anything that is not plainly valid is discarded and the
     * provider is asked instead; there is no failure here worth surfacing to the user.
     */
    fun read(
        contentType: XtreamContentType,
        accountFingerprint: String,
    ): CachedCatalog? =
        runCatching {
            val file = fileFor(contentType)
            if (!Files.isRegularFile(file)) return null

            val age = Duration.ofMillis(System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis())
            if (age > maxAge) return null

            DataInputStream(Files.newInputStream(file).buffered()).use { input ->
                if (input.readInt() != MAGIC) return null
                if (input.readInt() != FORMAT_VERSION) return null
                if (input.readUTF() != accountFingerprint) return null
                if (input.readUTF() != contentType.name) return null

                val categories =
                    (0 until input.readInt()).map {
                        XtreamCategory(
                            providerId = input.readUTF(),
                            name = input.readUTF(),
                            contentType = contentType,
                        )
                    }

                val itemCount = input.readInt()
                if (itemCount !in 0..MAX_ITEMS) return null
                val catalog = CompactXtreamCatalog(contentType)
                repeat(itemCount) {
                    catalog.add(
                        XtreamCatalogItem(
                            providerId = input.readUTF(),
                            name = input.readUTF(),
                            contentType = contentType,
                            categoryIds = (0 until input.readInt()).map { input.readUTF() },
                            containerExtension = input.readUTF().takeIf(String::isNotEmpty),
                            artworkUrl = input.readUTF().takeIf(String::isNotEmpty),
                            year = input.readInt().takeIf { year -> year != NO_VALUE },
                            rating = input.readDouble().takeIf { rating -> rating >= 0.0 },
                            addedAtEpochSeconds = null,
                            catchUpDays = input.readInt().takeIf { days -> days > 0 },
                        ),
                    )
                }
                CachedCatalog(catalog = catalog, categories = categories)
            }
        }.getOrNull()

    /**
     * Writes [catalog] and [categories] for the next launch.
     *
     * Through a temporary file and an atomic move, so a crash mid-write leaves the previous cache
     * rather than a truncated one. Failure is silent: an unwritable cache costs a slower start, and
     * refusing to run over it would be far worse.
     */
    fun write(
        contentType: XtreamContentType,
        accountFingerprint: String,
        catalog: CompactXtreamCatalog,
        categories: List<XtreamCategory>,
    ) {
        runCatching {
            Files.createDirectories(directory)
            val target = fileFor(contentType)
            val temporary = target.resolveSibling("${target.fileName}.tmp")

            DataOutputStream(Files.newOutputStream(temporary).buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeUTF(accountFingerprint)
                output.writeUTF(contentType.name)

                output.writeInt(categories.size)
                categories.forEach { category ->
                    output.writeUTF(category.providerId)
                    output.writeUTF(category.name)
                }

                output.writeInt(catalog.size)
                repeat(catalog.size) { index ->
                    val item = catalog.itemAt(index)
                    output.writeUTF(item.providerId)
                    output.writeUTF(item.name)
                    output.writeInt(item.categoryIds.size)
                    item.categoryIds.forEach(output::writeUTF)
                    output.writeUTF(item.containerExtension.orEmpty())
                    output.writeUTF(item.artworkUrl.orEmpty())
                    output.writeInt(item.year ?: NO_VALUE)
                    // Negative stands for absent: a real rating is never below zero.
                    output.writeDouble(item.rating ?: -1.0)
                    // Zero stands for "no recorder", which is what absent means for this field.
                    output.writeInt(item.catchUpDays ?: 0)
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Removes every cached catalogue. Used when the account changes or the user signs out. */
    fun clear() {
        runCatching {
            if (!Files.isDirectory(directory)) return
            Files.list(directory).use { stream ->
                stream.filter { path -> path.fileName.toString().endsWith(FILE_SUFFIX) }.toList()
            }.forEach { path -> runCatching { Files.deleteIfExists(path) } }
        }
    }

    private fun fileFor(contentType: XtreamContentType): Path =
        directory.resolve("${contentType.name.lowercase()}$FILE_SUFFIX")

    companion object {
        /**
         * The account a cache belongs to is identified by the repository's own `stableSourceId()`
         * — a salted hash of server and username — rather than by anything defined here.
         *
         * That value already exists to tell one subscription from another, and it never contains
         * the credentials themselves. Defining a second hash for the same question would be two
         * ways to be wrong instead of one.
         */

        /** `BURO` as bytes. A file that does not start with this is not ours. */
        private const val MAGIC = 0x4255524F

        /**
         * Bumped whenever the column layout changes.
         *
         * An older cache is then simply not read, rather than being read as though its fields meant
         * what the current ones mean — which would populate the library with nonsense.
         */
        // 2 added the catch-up window. A version-1 cache is simply not read: its rows end where
        // this one expects another int, and reading past that would take a neighbouring field for
        // a recording window.
        private const val FORMAT_VERSION = 2

        /** Written for "no year". Providers never report one, so it cannot collide. */
        private const val NO_VALUE = Int.MIN_VALUE

        private const val FILE_SUFFIX = ".burocat"

        /**
         * Beyond this the cache is ignored and the provider asked again.
         *
         * A catalogue changes over days: titles are added, rarely removed. Six hours keeps a
         * same-day return instant while making sure yesterday's additions appear without the user
         * having to do anything. The Atualizar listas button forces a refresh regardless.
         */
        val MAX_AGE: Duration = Duration.ofHours(6)

        /** A sanity bound: a real catalogue is tens of thousands, not tens of millions. */
        private const val MAX_ITEMS = 2_000_000

        fun defaultDirectory(): Path =
            Path.of(System.getProperty("user.home"), ".iptvburo", "catalog-cache")
    }
}

/** A catalogue read back from disk, with the categories that belong to it. */
internal data class CachedCatalog(
    val catalog: CompactXtreamCatalog,
    val categories: List<XtreamCategory>,
)
