package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Services shelves surviving a restart.
 *
 * Without this the section re-fetched everything on every launch — one request per service, per
 * kind — and threw the answer away on close. What a service carries changes over days, so the work
 * bought nothing but a wait.
 */
class StreamingShelfDiskCacheTest {
    private val directory: Path = createTempDirectory("buro-shelf-test")

    @AfterTest
    fun cleanUp() {
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { path ->
            runCatching { Files.deleteIfExists(path) }
        }
    }

    private fun shelf(
        providerId: String = "netflix",
        titleCount: Int = 3,
        logoUrl: String? = "https://image.test/$providerId-logo.png",
    ) = TmdbServiceShelf(
        provider =
            StreamingProvider(
                id = providerId,
                displayName = providerId.uppercase(),
                logoUrl = logoUrl,
            ),
        tmdbProviderId = 8,
        titles =
            (1..titleCount).map { index ->
                ExternalTitle(
                    id = ExternalContentId("tmdb", "$index"),
                    title = "Título $index",
                    kind = if (index % 2 == 0) ExternalTitleKind.SERIES else ExternalTitleKind.MOVIE,
                    year = if (index == 1) null else 2020 + index,
                    posterUrl = if (index == 2) null else "https://image.test/$index.jpg",
                )
            },
    )

    @Test
    fun `shelves survive a round trip intact`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        val written = listOf(shelf("netflix"), shelf("prime", titleCount = 5))

        cache.write(TmdbDiscoverKind.MOVIES, "BR", written)
        val read = cache.read(TmdbDiscoverKind.MOVIES, "BR")

        assertEquals(written, read, "a restored shelf must be the one that was stored")
    }

    /**
     * A service with no mark in TMDb's directory, and the "coming to streaming" rail, which belongs
     * to no company at all. An absent logo is stored as an empty string, and has to read back as
     * null: a blank URL is not "no logo" to the artwork loader, it is an address to go and fetch.
     */
    @Test
    fun `a shelf with no logo round trips as null`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        val written = listOf(shelf("coming-soon", logoUrl = null))

        cache.write(TmdbDiscoverKind.MOVIES, "BR", written)
        val read = cache.read(TmdbDiscoverKind.MOVIES, "BR")

        assertNull(read?.single()?.provider?.logoUrl, "an absent mark must not come back as a blank URL")
    }

    /**
     * The "coming to streaming" rail belongs to no service, so it has no provider id.
     *
     * A null is written as the same sentinel the year field uses, and must come back as null rather
     * than as Int.MIN_VALUE — a number the app would then try to refresh the shelf by.
     */
    @Test
    fun `a shelf with no provider id round trips as null`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        val written = listOf(shelf("coming-soon").copy(tmdbProviderId = null))

        cache.write(TmdbDiscoverKind.UPCOMING, "BR", written)
        val read = cache.read(TmdbDiscoverKind.UPCOMING, "BR")

        assertEquals(written, read)
        assertNull(read?.single()?.tmdbProviderId)
    }

    /**
     * Accented titles are the ordinary case here, not an edge one.
     *
     * The catalogue this ships for is Brazilian, and a format that mangled them would corrupt most
     * of what a customer sees while still passing a test written in English.
     */
    @Test
    fun `accented titles come back unchanged`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        val awkward =
            listOf(
                TmdbServiceShelf(
                    provider = StreamingProvider(id = "globoplay", displayName = "Globoplay"),
                    tmdbProviderId = 307,
                    titles =
                        listOf(
                            ExternalTitle(
                                id = ExternalContentId("tmdb", "1"),
                                title = "Coração de Ferro — Edição Única",
                                kind = ExternalTitleKind.MOVIE,
                                year = 2024,
                                posterUrl = null,
                            ),
                        ),
                ),
            )

        cache.write(TmdbDiscoverKind.MOVIES, "BR", awkward)

        assertEquals(
            "Coração de Ferro — Edição Única",
            cache.read(TmdbDiscoverKind.MOVIES, "BR")?.first()?.titles?.first()?.title,
        )
    }

    /**
     * Region is identity, not a filter.
     *
     * Serving BR's shelves to somebody who has set DE would show them a catalogue for a country
     * they are not in, and it would look like the app working rather than like a fault.
     */
    @Test
    fun `shelves stored for one region are not served to another`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        cache.write(TmdbDiscoverKind.MOVIES, "BR", listOf(shelf()))

        assertNull(cache.read(TmdbDiscoverKind.MOVIES, "DE"))
    }

    /** The same for kind: films are not series, and a mix-up here is visible nonsense. */
    @Test
    fun `shelves stored for one kind are not served to another`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        cache.write(TmdbDiscoverKind.MOVIES, "BR", listOf(shelf()))

        assertNull(cache.read(TmdbDiscoverKind.SERIES, "BR"))
    }

    /**
     * A day old is the point at which TMDb is asked again.
     *
     * The file is back-dated rather than waited for: the rule is what matters, and a test that
     * sleeps for a day is a test nobody runs. A negative max age would also pass without proving
     * the timestamp is read at all, so the age is made real instead.
     */
    @Test
    fun `an expired cache is not used`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        cache.write(TmdbDiscoverKind.MOVIES, "BR", listOf(shelf()))

        val file = Files.list(directory).use { stream -> stream.toList() }.first()
        Files.setLastModifiedTime(
            file,
            java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() - Duration.ofDays(2).toMillis(),
            ),
        )

        assertNull(cache.read(TmdbDiscoverKind.MOVIES, "BR"))
    }

    /**
     * A cache written moments ago is used.
     *
     * The other half of the rule, and the one that carries the benefit: without it the disk cache
     * would be written faithfully every day and read never.
     */
    @Test
    fun `a fresh cache is used`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        cache.write(TmdbDiscoverKind.MOVIES, "BR", listOf(shelf()))

        assertTrue(cache.read(TmdbDiscoverKind.MOVIES, "BR")?.isNotEmpty() == true)
    }

    /**
     * An empty answer is a failed fetch, not a catalogue with nothing in it.
     *
     * Caching one would hold the section empty for a whole day over a single bad moment on the
     * network — the user would open the app tomorrow to the same blank page.
     */
    @Test
    fun `an empty result is never stored`() {
        val cache = StreamingShelfDiskCache(directory = directory)

        cache.write(TmdbDiscoverKind.MOVIES, "BR", emptyList())

        assertNull(cache.read(TmdbDiscoverKind.MOVIES, "BR"))
    }

    /**
     * Corruption reads as "no cache", never as a crash.
     *
     * A truncated file is what a power cut during a write leaves behind, and the cost of getting
     * this wrong is an app that will not open until somebody deletes a file by hand.
     */
    @Test
    fun `a truncated file is discarded rather than thrown`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        cache.write(TmdbDiscoverKind.MOVIES, "BR", listOf(shelf(titleCount = 20)))

        val file = Files.list(directory).use { stream -> stream.toList() }.first()
        val bytes = Files.readAllBytes(file)
        Files.write(file, bytes.copyOf(bytes.size / 2))

        assertNull(cache.read(TmdbDiscoverKind.MOVIES, "BR"))
    }

    @Test
    fun `garbage in the cache directory is discarded rather than thrown`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        cache.write(TmdbDiscoverKind.MOVIES, "BR", listOf(shelf()))

        val file = Files.list(directory).use { stream -> stream.toList() }.first()
        Files.write(file, "not a cache at all".toByteArray())

        assertNull(cache.read(TmdbDiscoverKind.MOVIES, "BR"))
    }

    @Test
    fun `clearing removes what was stored`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        cache.write(TmdbDiscoverKind.MOVIES, "BR", listOf(shelf()))

        cache.clear()

        assertNull(cache.read(TmdbDiscoverKind.MOVIES, "BR"))
    }

    /**
     * A missing cache is the first launch, and it must be silent.
     *
     * Reading before anything has ever been written is the ordinary path on a new installation.
     */
    @Test
    fun `an absent cache reads as nothing`() {
        assertNull(StreamingShelfDiskCache(directory = directory).read(TmdbDiscoverKind.MOVIES, "BR"))
    }

    /**
     * Nothing written to disk identifies the user or carries a credential.
     *
     * The shelves are public catalogue data, but the file sits in the user's home directory for a
     * year at a time, and this is the check that keeps it that way as fields are added.
     */
    @Test
    fun `the stored file carries no credential`() {
        val cache = StreamingShelfDiskCache(directory = directory)
        cache.write(TmdbDiscoverKind.MOVIES, "BR", listOf(shelf()))

        val file = Files.list(directory).use { stream -> stream.toList() }.first()
        val contents = String(Files.readAllBytes(file), Charsets.ISO_8859_1).lowercase()

        listOf("api_key", "apikey", "password", "token", "username", "bearer").forEach { secret ->
            assertTrue(secret !in contents, "the shelf cache must never contain '$secret'")
        }
    }
}
