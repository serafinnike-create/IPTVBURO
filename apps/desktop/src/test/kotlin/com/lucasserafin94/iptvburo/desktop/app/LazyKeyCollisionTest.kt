package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A lazy list must not be keyed on a number the provider chose.
 *
 * Compose throws when two items in one lazy layout share a key, and the throw is not contained: it
 * comes out of the measure pass and takes the whole window with it. The app log carried exactly
 * that — an IllegalArgumentException from LazyGridMeasuredItemProvider with no message left behind.
 *
 * `providerId` is the provider's own stream number, and a list that files one stream under two
 * categories sends it twice. Nothing upstream deduplicates by id, and nothing should: the catalogue
 * is the provider's data. So the key carries the position, which cannot collide however the
 * provider numbers its rows.
 *
 * A source scan because the failure lives inside Compose's measure pass, where a unit test cannot
 * reach it. What it costs is that a new lazy list is only covered once it is written the same way.
 */
class LazyKeyCollisionTest {
    private val sources: List<Path> =
        @OptIn(kotlin.io.path.ExperimentalPathApi::class)
        Path.of("src/main/kotlin")
            .walk()
            .filter { path -> path.name.endsWith(".kt") }
            .toList()

    @Test
    fun `no lazy list is keyed on a provider id alone`() {
        // `items(...)` with a key mentioning providerId but no index. `itemsIndexed` is the fixed
        // shape and passes because its key lambda takes the index first.
        val offenders =
            sources.mapNotNull { path ->
                val text = path.readText()
                // Up to the end of the line rather than to the first closing brace: a key such as
                // a contentType-plus-providerId string carries braces of its own, and stopping at
                // the first one made this match nothing at all -- it passed against the very code
                // that crashed. `itemsIndexed` is excluded by name, since its key takes the index.
                val bad =
                    Regex("""(?<!Indexed)\bitems\([^\n]*(?:\n[^\n]*)?key = \{[^\n]*providerId[^\n]*""")
                        .findAll(text)
                        .filterNot { match -> "index" in match.value }
                        .toList()
                if (bad.isEmpty()) null else "${path.name}: ${bad.size}"
            }

        assertTrue(
            offenders.isEmpty(),
            "uma lista lazy voltou a usar so o id do provedor como chave: $offenders",
        )
    }

    /** The scan is worthless if it stopped matching the files it is meant to read. */
    @Test
    fun `the scan actually reaches the screens it guards`() {
        assertTrue(sources.size > 20, "o scan encontrou ${sources.size} ficheiros, algo esta errado")
        val withLazyKeys =
            sources.count { path -> "itemsIndexed(" in path.readText() }
        assertTrue(
            withLazyKeys >= 5,
            "so $withLazyKeys ficheiros usam itemsIndexed; as correcoes desapareceram?",
        )
    }
}
