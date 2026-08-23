package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Disponível nesta lista" answers which of this actor's titles the user owns.
 *
 * It used to answer something else. The provider only exposes a cast inside per-film details, so
 * finding an actor meant one network request per film — impossible against forty thousand, so it
 * stopped after four hundred. Always the same four hundred, taken from the top of the catalogue in
 * catalogue order, so the section listed whatever happened to be in them: under one percent of the
 * library, chosen by position rather than by the person. Reported as an actor's page showing random
 * films, which is what it was.
 *
 * TMDb has already named everything the person appears in. Turning that into "which of these do I
 * own" is a lookup against the catalogue held in memory, and costs no requests at all.
 */
class ActorLibraryMatchTest {
    private val state: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    private val repository: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/SessionXtreamRepository.kt").readText()

    @Test
    fun `the per-film cast sweep is gone`() {
        assertFalse(
            repository.contains("fun findByCastMember("),
            "A sweep that costs one request per film can only ever be capped, and a cap makes the " +
                "answer 'the first four hundred rows' rather than 'this actor's work'.",
        )
        assertFalse(
            repository.contains("MAX_CAST_LOOKUPS"),
            "The bound existed only for that sweep; leaving it invites the sweep back.",
        )
    }

    @Test
    fun `the library is matched against the credits`() {
        assertTrue(
            repository.contains("fun findByTitles("),
            "The catalogue is in memory, so this is a set lookup rather than a request per row.",
        )
        assertTrue(
            state.contains("xtreamRepository.findByTitles(creditNames, MAX_FILMOGRAPHY_ITEMS)"),
            "The person page should ask which of the credits it owns.",
        )
    }

    @Test
    fun `the same normalisation both sides`() {
        // "Tropa de Elite 4K [DUB]" and "Tropa de Elite" have to be one title, and the shared
        // helper is what ServiceTitleIndex already uses — two spellings of "normalise" would match
        // nothing and look like an empty library.
        assertTrue(
            state.contains("credit.title.normalisedForMatching()"),
            "The credits are normalised before the lookup.",
        )
        assertTrue(
            repository.contains("catalog.nameAt(index).normalisedForMatching() in normalisedTitles"),
            "And the library rows with the same function.",
        )
    }

    @Test
    fun `a row is only built once it matches`() {
        // itemAt allocates an item; nameAt reads a field. This runs over the whole catalogue, and
        // building forty thousand objects to discard them was measured at 31ms against 10ms
        // elsewhere in this file.
        // Bounded by the function, not by "return matches" — there is an early return above both
        // calls, and slicing at it cut the body before the thing being asserted. The first version
        // of this test did exactly that and failed against correct code.
        val body = repository.substringAfter("fun findByTitles(").substringBefore("\n    fun ")
        val nameAt = body.indexOf("nameAt(index)")
        val itemAt = body.indexOf("itemAt(index)")
        assertTrue(nameAt > 0 && itemAt > 0, "Both calls should be in the body.")
        assertTrue(nameAt < itemAt, "Check the name first; build the item only for a match.")
    }

    @Test
    fun `an empty credit list asks nothing`() {
        // With no credits there is nothing to match, and asking would walk the catalogue for a
        // guaranteed empty answer.
        assertTrue(
            state.contains("if (creditNames.isEmpty()) {"),
            "No credits means no lookup.",
        )
    }
}
