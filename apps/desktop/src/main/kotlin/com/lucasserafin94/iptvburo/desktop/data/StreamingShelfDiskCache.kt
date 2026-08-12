package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * The Services shelves, kept on disk between sessions.
 *
 * Every launch re-fetched them — one request per service, per kind — before the section could show
 * anything, and the result was thrown away when the window closed. None of that work buys anything:
 * what a streaming service is carrying changes over days, not over the minutes between closing the
 * app and opening it again.
 *
 * Modelled on [CatalogDiskCache], deliberately. The two solve the same problem for different data,
 * and a second design here would be a second set of mistakes to find.
 *
 * ## What is deliberately not stored
 *
 * Nothing that identifies the user, and no API key. A shelf holds a service name, TMDb's own numeric
 * ids, titles, years and artwork addresses — all of it public catalogue data that TMDb serves to
 * anyone. The metadata key is a credential and lives only in the build-time configuration.
 *
 * ## Staleness
 *
 * A cache is read only when it is younger than [MAX_AGE], was written by this format version, and
 * belongs to the same region. Region is part of the identity rather than a filter applied on read:
 * shelves for BR and for DE are different answers to the same question, and serving one for the
 * other would quietly show a customer the wrong country's catalogue.
 */
internal class StreamingShelfDiskCache(
    private val directory: Path = defaultDirectory(),
    private val maxAge: Duration = MAX_AGE,
) {
    /**
     * The cached shelves for [kind] in [region], or null when there is nothing usable.
     *
     * Null covers every failure — absent, stale, wrong region, wrong version, truncated, corrupt. A
     * cache is an optimisation, so anything not plainly valid is discarded and TMDb asked instead;
     * no failure here is worth showing the user.
     */
    fun read(
        kind: TmdbDiscoverKind,
        region: String,
    ): List<TmdbServiceShelf>? =
        runCatching {
            val file = fileFor(kind)
            if (!Files.isRegularFile(file)) return null

            val age =
                Duration.ofMillis(
                    System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis(),
                )
            if (age > maxAge) return null

            DataInputStream(Files.newInputStream(file).buffered()).use { input ->
                if (input.readInt() != MAGIC) return null
                if (input.readInt() != FORMAT_VERSION) return null
                if (input.readUTF() != region) return null
                if (input.readUTF() != kind.name) return null

                val shelfCount = input.readInt()
                if (shelfCount !in 0..MAX_SHELVES) return null
                (0 until shelfCount).map {
                    val providerId = input.readUTF()
                    val providerName = input.readUTF()
                    val tmdbProviderId = input.readInt()

                    val titleCount = input.readInt()
                    if (titleCount !in 0..MAX_TITLES_PER_SHELF) return null
                    val titles =
                        (0 until titleCount).map {
                            ExternalTitle(
                                id =
                                    ExternalContentId(
                                        namespace = input.readUTF(),
                                        value = input.readUTF(),
                                    ),
                                title = input.readUTF(),
                                kind = ExternalTitleKind.valueOf(input.readUTF()),
                                year = input.readInt().takeIf { year -> year != NO_VALUE },
                                posterUrl = input.readUTF().takeIf(String::isNotEmpty),
                                // Never cached as true. These rows come from a real catalogue answer;
                                // writing the flag would only create a way for the DEMO badge to
                                // appear on genuine data after a format change.
                                isDemo = false,
                            )
                        }

                    TmdbServiceShelf(
                        provider = StreamingProvider(id = providerId, displayName = providerName),
                        tmdbProviderId = tmdbProviderId,
                        titles = titles,
                    )
                }
            }
        }.getOrNull()

    /**
     * Writes [shelves] for [kind] in [region].
     *
     * Written to a temporary file and moved into place, so a crash or a closed lid mid-write leaves
     * the previous cache intact rather than a half-file that reads as corrupt on the next launch.
     */
    fun write(
        kind: TmdbDiscoverKind,
        region: String,
        shelves: List<TmdbServiceShelf>,
    ) {
        // Nothing is not an answer worth keeping. A failed fetch returns an empty list, and caching
        // that would hold the section empty for a whole day over one bad moment on the network.
        if (shelves.isEmpty()) return

        runCatching {
            Files.createDirectories(directory)
            val target = fileFor(kind)
            val temporary = directory.resolve("${target.fileName}.tmp")

            DataOutputStream(Files.newOutputStream(temporary).buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeUTF(region)
                output.writeUTF(kind.name)

                output.writeInt(shelves.size)
                shelves.forEach { shelf ->
                    output.writeUTF(shelf.provider.id)
                    output.writeUTF(shelf.provider.displayName)
                    output.writeInt(shelf.tmdbProviderId)

                    output.writeInt(shelf.titles.size)
                    shelf.titles.forEach { title ->
                        output.writeUTF(title.id.namespace)
                        output.writeUTF(title.id.value)
                        output.writeUTF(title.title)
                        output.writeUTF(title.kind.name)
                        output.writeInt(title.year ?: NO_VALUE)
                        output.writeUTF(title.posterUrl.orEmpty())
                    }
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Removes every cached shelf. Used when the region changes or the key is replaced. */
    fun clear() {
        runCatching {
            if (!Files.isDirectory(directory)) return
            Files.list(directory).use { stream ->
                stream.filter { path -> path.fileName.toString().endsWith(FILE_SUFFIX) }.toList()
            }.forEach { path -> runCatching { Files.deleteIfExists(path) } }
        }
    }

    private fun fileFor(kind: TmdbDiscoverKind): Path =
        directory.resolve("${kind.name.lowercase()}$FILE_SUFFIX")

    companion object {
        /** `BURO` as bytes. A file that does not start with this is not ours. */
        private const val MAGIC = 0x4255524F

        /**
         * Bumped whenever the column layout changes.
         *
         * An older cache is then simply not read, rather than being read as though its fields meant
         * what the current ones mean — which would fill the section with nonsense.
         */
        private const val FORMAT_VERSION = 1

        /** Written for "no year". TMDb never reports one, so it cannot collide. */
        private const val NO_VALUE = Int.MIN_VALUE

        private const val FILE_SUFFIX = ".buroshelf"

        /**
         * Beyond this the cache is ignored and TMDb asked again.
         *
         * What a service is carrying changes over days. A day keeps every launch after the first
         * instant, while making sure a customer who opens the app tomorrow sees tomorrow's
         * catalogue without having to do anything.
         */
        val MAX_AGE: Duration = Duration.ofDays(1)

        /** Sanity bounds: a real answer is a handful of services with a score of titles each. */
        private const val MAX_SHELVES = 200
        private const val MAX_TITLES_PER_SHELF = 500

        fun defaultDirectory(): Path =
            Path.of(System.getProperty("user.home"), ".iptvburo", "shelf-cache")
    }
}
