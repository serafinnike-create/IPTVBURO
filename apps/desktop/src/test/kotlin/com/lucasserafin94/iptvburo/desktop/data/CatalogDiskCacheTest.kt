package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.deleteRecursively
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catalogue kept between launches.
 *
 * A cache that returns the wrong data is far worse than no cache: it would fill the library with
 * another account's titles, or with fields shifted by one because the format changed underneath.
 * Every rejection path is therefore tested as carefully as the happy one.
 *
 * Fixtures are synthetic. Nothing here reads a real playlist or contacts a provider.
 */
class CatalogDiskCacheTest {
    private val account = "xtream-someone"

    private fun <T> withCache(block: (CatalogDiskCache, Path) -> T): T {
        val directory = Files.createTempDirectory("iptvburo-cache")
        return try {
            block(CatalogDiskCache(directory), directory)
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            directory.deleteRecursively()
        }
    }

    private fun catalogue(size: Int): CompactXtreamCatalog =
        CompactXtreamCatalog(XtreamContentType.MOVIE).apply {
            repeat(size) { index ->
                add(
                    XtreamCatalogItem(
                        providerId = "id-$index",
                        name = "Synthetic title $index",
                        contentType = XtreamContentType.MOVIE,
                        categoryIds = listOf("cat-${index % 40}", "cat-all"),
                        containerExtension = if (index % 3 == 0) "mkv" else "mp4",
                        artworkUrl = if (index % 5 == 0) null else "https://images.invalid/$index.jpg",
                        year = if (index % 7 == 0) null else 2000 + (index % 26),
                        rating = if (index % 4 == 0) null else (index % 10) / 2.0,
                        addedAtEpochSeconds = null,
                    ),
                )
            }
        }

    private val categories =
        listOf(
            XtreamCategory(providerId = "cat-1", name = "Ação", contentType = XtreamContentType.MOVIE),
            XtreamCategory(providerId = "cat-2", name = "Comédia", contentType = XtreamContentType.MOVIE),
        )

    /** Every field must survive the round trip, including the ones that are absent. */
    @Test
    fun `a catalogue round-trips exactly`() {
        withCache { cache, _ ->
            val original = catalogue(200)
            cache.write(XtreamContentType.MOVIE, account, original, categories)

            val restored = assertNotNull(cache.read(XtreamContentType.MOVIE, account))

            assertEquals(original.size, restored.catalog.size)
            assertEquals(categories, restored.categories)
            repeat(original.size) { index ->
                val before = original.itemAt(index)
                val after = restored.catalog.itemAt(index)
                assertEquals(before.providerId, after.providerId, "providerId at $index")
                assertEquals(before.name, after.name, "name at $index")
                assertEquals(before.categoryIds, after.categoryIds, "categoryIds at $index")
                assertEquals(before.containerExtension, after.containerExtension, "container at $index")
                assertEquals(before.artworkUrl, after.artworkUrl, "artwork at $index")
                assertEquals(before.year, after.year, "year at $index")
                assertEquals(before.rating, after.rating, "rating at $index")
            }
        }
    }

    /** Accented titles are the norm in this catalogue, not an edge case. */
    @Test
    fun `accented titles survive`() {
        withCache { cache, _ ->
            val catalog =
                CompactXtreamCatalog(XtreamContentType.MOVIE).apply {
                    add(
                        XtreamCatalogItem(
                            providerId = "1",
                            name = "O Poderoso Chefão · Coração Selvagem",
                            contentType = XtreamContentType.MOVIE,
                            categoryIds = listOf("cat-ação"),
                            containerExtension = "mp4",
                            artworkUrl = null,
                            year = 1972,
                            rating = null,
                            addedAtEpochSeconds = null,
                        ),
                    )
                }
            cache.write(XtreamContentType.MOVIE, account, catalog, emptyList())

            val restored = assertNotNull(cache.read(XtreamContentType.MOVIE, account))

            assertEquals("O Poderoso Chefão · Coração Selvagem", restored.catalog.itemAt(0).name)
            assertEquals(listOf("cat-ação"), restored.catalog.itemAt(0).categoryIds)
        }
    }

    /**
     * A different subscription must never read this one's catalogue.
     *
     * The worst thing this cache could do is show a household member titles from an account they do
     * not have, so the account is checked before anything is trusted.
     */
    @Test
    fun `another account cannot read the cache`() {
        withCache { cache, _ ->
            cache.write(XtreamContentType.MOVIE, account, catalogue(10), categories)

            val other = "xtream-someone-else"

            assertNull(cache.read(XtreamContentType.MOVIE, other))
        }
    }

    /** Content types do not share a file, and must not read each other's. */
    @Test
    fun `content types are kept apart`() {
        withCache { cache, _ ->
            cache.write(XtreamContentType.MOVIE, account, catalogue(10), categories)

            assertNull(cache.read(XtreamContentType.SERIES, account))
            assertNotNull(cache.read(XtreamContentType.MOVIE, account))
        }
    }

    /** Old enough and it is ignored: yesterday's additions have to appear on their own. */
    @Test
    fun `a stale cache is ignored`() {
        val directory = Files.createTempDirectory("iptvburo-cache-stale")
        try {
            val cache = CatalogDiskCache(directory, maxAge = Duration.ofHours(6))
            cache.write(XtreamContentType.MOVIE, account, catalogue(10), categories)

            val file = directory.resolve("movie.burocat")
            file.toFile().setLastModified(System.currentTimeMillis() - Duration.ofHours(7).toMillis())

            assertNull(cache.read(XtreamContentType.MOVIE, account), "seven hours old must not be used")
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            directory.deleteRecursively()
        }
    }

    /** A truncated or corrupt file must read as absent rather than as a half catalogue. */
    @Test
    fun `a corrupt cache is ignored`() {
        withCache { cache, directory ->
            cache.write(XtreamContentType.MOVIE, account, catalogue(50), categories)
            val file = directory.resolve("movie.burocat")

            // Cut it in half: the header survives, the items do not.
            val bytes = Files.readAllBytes(file)
            Files.write(file, bytes.copyOf(bytes.size / 2))

            assertNull(cache.read(XtreamContentType.MOVIE, account))
        }
    }

    /** A file that is not ours at all is rejected on its first four bytes. */
    @Test
    fun `a foreign file is ignored`() {
        withCache { cache, directory ->
            Files.createDirectories(directory)
            Files.writeString(directory.resolve("movie.burocat"), "this is not a catalogue")

            assertNull(cache.read(XtreamContentType.MOVIE, account))
        }
    }

    /** Nothing cached is the ordinary first-run case, and answers null without complaint. */
    @Test
    fun `an absent cache reads as null`() {
        withCache { cache, _ ->
            assertNull(cache.read(XtreamContentType.MOVIE, account))
        }
    }

    /** Clearing removes every content type, which is what signing out has to do. */
    @Test
    fun `clear removes everything`() {
        withCache { cache, _ ->
            cache.write(XtreamContentType.MOVIE, account, catalogue(5), categories)
            cache.write(XtreamContentType.SERIES, account, CompactXtreamCatalog(XtreamContentType.SERIES), emptyList())

            cache.clear()

            assertNull(cache.read(XtreamContentType.MOVIE, account))
            assertNull(cache.read(XtreamContentType.SERIES, account))
        }
    }

    /**
     * The whole point, stated as a bound.
     *
     * A catalogue this size took seconds to fetch and parse from the provider. Reading it back has
     * to be fast enough that a returning user does not notice it at all — otherwise the cache has
     * moved the wait rather than removed it.
     */
    @Test
    fun `a full catalogue reads back quickly`() {
        withCache { cache, _ ->
            val big = catalogue(41_717)
            cache.write(XtreamContentType.MOVIE, account, big, categories)

            var restored: CachedCatalog? = null
            val elapsed = measureTimeMillis { restored = cache.read(XtreamContentType.MOVIE, account) }

            assertEquals(41_717, assertNotNull(restored).catalog.size)
            assertTrue(
                elapsed < 3_000,
                "reading a 41,717-item catalogue took ${elapsed}ms; this runs before the library appears",
            )
            println("[cache] 41,717 items read back in ${elapsed}ms")
        }
    }
}
