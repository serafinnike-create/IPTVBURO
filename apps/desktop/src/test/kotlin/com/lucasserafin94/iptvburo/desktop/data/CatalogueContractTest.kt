package com.lucasserafin94.iptvburo.desktop.data

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The seam a second protocol arrives through.
 *
 * The app spoke to a concrete class named after one protocol, so a Stalker subscription had
 * nowhere to land — even though its client had been written and tested long before, and the
 * television had shipped Stalker for longer still.
 *
 * What these guard is the extraction staying honest: an interface whose defaults disagree with the
 * class implementing it would compile and then hand callers a different page size depending on
 * which type they happened to hold.
 */
class CatalogueContractTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val contract =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/CatalogueRepository.kt")
    private val session =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/SessionXtreamRepository.kt")
    private val switching =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/SwitchingCatalogueRepository.kt")

    @Test
    fun `the existing repository implements the contract`() {
        assertTrue(
            session.contains(") : CatalogueRepository {"),
            "without this the interface is decoration and nothing can be swapped for it",
        )
    }

    @Test
    fun `every method on the contract is actually implemented`() {
        // A method declared and not overridden fails to compile, so this is really about the
        // reverse: a method quietly dropped from the contract while the class keeps it, which
        // leaves a capability the app can only reach by naming the concrete type again.
        val declared =
            Regex("""^    fun (\w+)\(""", RegexOption.MULTILINE)
                .findAll(contract)
                .map { it.groupValues[1] }
                .toSet()
        val implemented =
            Regex("""^    override fun (\w+)\(""", RegexOption.MULTILINE)
                .findAll(session)
                .map { it.groupValues[1] }
                .toSet()
        assertEquals(emptySet(), declared - implemented, "declared but not implemented")
        assertEquals(23, declared.size, "the contract covers what the app actually uses")
    }

    /**
     * The switcher must forward every method, not inherit a default.
     *
     * A method with a default body on the interface and no override here compiles perfectly and
     * then answers "nothing" for ever. That shipped: the diagnostics screen reported "could not
     * measure the speed" on a working connection, because the two probes were added to the
     * interface and the session repository and never to the switcher in between. There was no
     * error anywhere — the default simply returned null.
     */
    @Test
    fun `the switcher forwards every method rather than inheriting a default`() {
        val declared =
            Regex("""^    fun (\w+)\(""", RegexOption.MULTILINE)
                .findAll(contract)
                .map { it.groupValues[1] }
                .toSet()
        val forwarded =
            Regex("""^    override fun (\w+)\(""", RegexOption.MULTILINE)
                .findAll(switching)
                .map { it.groupValues[1] }
                .toSet()

        assertEquals(
            emptySet(),
            declared - forwarded,
            "estes metodos caem no valor por defeito em vez de chegarem a fonte activa",
        )
    }

    @Test
    fun `the defaults agree with the implementation they came from`() {
        // Kotlin forbids defaults on an overriding function, so these values had to move to the
        // interface — which means the constants now live in two places. A silent disagreement
        // would give one page size to a caller holding the interface and another to a caller
        // holding the class.
        val pageSize =
            Regex("""const val DEFAULT_PAGE_SIZE = (\d+)""").find(session)?.groupValues?.get(1)
        val searchLimit =
            Regex("""const val DEFAULT_SEARCH_LIMIT = (\d+)""").find(session)?.groupValues?.get(1)
        assertTrue(pageSize != null && searchLimit != null, "the class still declares both")
        assertTrue(
            contract.contains("const val CATALOGUE_PAGE_SIZE = $pageSize"),
            "the contract's page size has drifted from the repository's",
        )
        assertTrue(
            contract.contains("const val CATALOGUE_SEARCH_LIMIT = $searchLimit"),
            "the contract's search limit has drifted from the repository's",
        )
    }

    @Test
    fun `the playback address is still built late, never stored`() {
        // The one promise on this interface that is about safety rather than shape: the URL
        // carries the account's credentials, so a second implementation must not be tempted to
        // cache it.
        assertTrue(contract.contains("fun buildConfirmedPlaybackUri("))
        assertTrue(
            contract.contains("never stored"),
            "the obligation has to be written where the next implementer will read it",
        )
    }
}
