package com.lucasserafin94.iptvburo.desktop

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every guarded loader must clear its Loading status when it is cancelled.
 *
 * The shape that keeps recurring in this file is:
 *
 * ```
 * if (status is Loading) return      // refuses a second attempt
 * status = Loading
 * runCatching { … }.onFailure { error ->
 *     error.rethrowIfCancellation()  // leaves the status on Loading for ever
 *     status = Error(…)
 * }
 * ```
 *
 * A cancelled coroutine — which in Compose means nothing worse than the screen that launched it
 * going away — never reaches the assignment below the rethrow. The status stays Loading, the guard
 * then refuses every retry, and the user is left with a spinner that nothing in the app can clear.
 *
 * It has been found and fixed five separate times: film details, series details, live EPG, playlist
 * import, and the daily home. Each time it was reported as "fica carregando". Rather than wait for
 * a sixth, this reads the source and requires that a Loading status is reset before the rethrow.
 *
 * A source-reading test is a blunt instrument, and it is the right one here: the failure is a
 * missing line in an error path that only runs on cancellation, which is exactly what a behavioural
 * test of a Compose state holder is worst at reaching.
 */
class CancelledLoaderResetTest {
    private val source: String =
        Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
            .readText()

    @Test
    fun `every rethrowIfCancellation is preceded by a status reset`() {
        val lines = source.lines()
        val offenders =
            lines.withIndex().filter { (index, line) ->
                if (!line.contains("error.rethrowIfCancellation()")) {
                    false
                } else {
                    // The reset is expected within the few lines above: close enough to be the
                    // same error path, loose enough to survive a comment being added between them.
                    val preceding = lines.subList(maxOf(0, index - RESET_WINDOW), index)

                    // Unless this rethrow belongs to a superseded request. A loader whose generation
                    // no longer matches owns none of the current state, and clearing a *newer*
                    // request's Loading flag is the same bug in the opposite direction: the screen
                    // stops showing progress for work that is genuinely still running.
                    val supersededBranch = preceding.any { candidate ->
                        SUPERSEDED_GUARD.containsMatchIn(candidate)
                    }

                    // An in-flight *marker* released counts as the reset, the same as a status put
                    // back to rest. `ensureCastPhoto` guards on a set of names rather than on a
                    // status enum, and removing the key before the rethrow leaves nothing stuck —
                    // the next visit is free to ask again, which is the property this test is
                    // actually about. Without this the check reports a correct loader as broken.
                    val markerReleased = preceding.any { candidate ->
                        IN_FLIGHT_RELEASE.containsMatchIn(candidate)
                    }

                    !supersededBranch &&
                        !markerReleased &&
                        preceding.none { candidate -> RESTING_STATE.containsMatchIn(candidate) }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "These loaders rethrow a cancellation without first clearing their Loading status, " +
                "which strands the screen on a spinner that no retry can clear:\n" +
                offenders.joinToString("\n") { (index, line) ->
                    "  DesktopAppState.kt:${index + 1}: ${line.trim()}"
                },
        )
    }

    /**
     * The same trap in its boolean form: `streamingLoading`.
     *
     * `loadStreamingShelves` returns early while the flag is set, so every path out of the coroutine
     * that sets it has to clear it. One did not — the early return taken when the user switches
     * filter mid-load — and the streaming section then stayed empty for the rest of the session,
     * with every later load refused silently by the guard.
     *
     * Checked positionally: the clear must come before the early return that can skip it.
     */
    @Test
    fun `the streaming flag is cleared before any early return`() {
        val lines = source.lines()
        val setAt = lines.indexOfFirst { line -> line.trim() == "streamingLoading = true" }
        assertTrue(setAt >= 0, "streamingLoading = true not found; this test needs updating")

        // The coroutine body: from where the flag is set to the end of that launch block.
        val body = lines.subList(setAt, minOf(lines.size, setAt + BODY_WINDOW))
        val clearedAt = body.indexOfFirst { line -> line.trim() == "streamingLoading = false" }
        val firstEarlyReturn = body.indexOfFirst { line -> line.contains("return@launch") }

        assertTrue(clearedAt > 0, "streamingLoading is never cleared inside the loader")
        assertTrue(
            firstEarlyReturn < 0 || clearedAt < firstEarlyReturn,
            "streamingLoading is cleared after an early return that can skip it — switching filter " +
                "mid-load would leave the flag set and the section permanently empty",
        )
    }

    /**
     * Every guard has a matching reset.
     *
     * Guards and resets are written with the same condition — `if (status is X.Loading)` — so a
     * loader that guards without resetting shows up as an odd count. This is a cheap structural
     * check on top of the positional one above: it catches a guard added in a new loader whose
     * error path was written without the reset at all.
     */
    @Test
    fun `each in-flight guard is paired with a reset`() {
        val conditions = source.lines().filter { line -> GUARD.containsMatchIn(line) }
        val resets = conditions.count { line -> RESTING_STATE.containsMatchIn(line) }
        val guards = conditions.size - resets

        assertTrue(
            guards >= EXPECTED_GUARDS && guards == resets,
            "Found $guards in-flight guards and $resets resets; they must pair up, and at least " +
                "$EXPECTED_GUARDS guards are expected. A guard with no reset strands its screen " +
                "on a spinner; a guard removed altogether should be a deliberate change.\n" +
                conditions.joinToString("\n") { line -> "  ${line.trim()}" },
        )
    }

    private companion object {
        /** Lines above the rethrow that count as the same error path. */
        const val RESET_WINDOW = 8

        /** Lines below `streamingLoading = true` that make up the loader's coroutine body. */
        const val BODY_WINDOW = 60

        /** Film, series, EPG, import, daily home, session connect. */
        const val EXPECTED_GUARDS = 6

        /**
         * The in-flight guard, in either form it takes.
         *
         * Most are a single line; connectXtream's opens a block so it can wipe the credentials
         * before returning. Matching the condition rather than the `return` covers both, and
         * Connecting is included because that is what the session status calls its in-flight
         * state — a different name for the same trap, and the one where being stuck was worst,
         * since the app could then connect to nothing at all.
         */
        val GUARD = Regex("""if \(\w+ is \w+\.(Loading|Connecting)\)""")

        /**
         * A status being put back to rest.
         *
         * Two names, because the resting state depends on the status: a details loader goes back
         * to Idle, while a session that never connected goes back to Disconnected. Both mean "no
         * attempt is in flight", which is the property the guard above depends on.
         */
        val RESTING_STATE = Regex("""=\s*\w+\.(Idle|Disconnected)""")

        /**
         * A generation guard: this failure belongs to a request that has already been replaced.
         *
         * Such a branch must *not* reset anything. The state it would clear belongs to the newer
         * request, and clearing it hides progress for work that is still genuinely running — the
         * same class of bug as the one this test exists to catch, pointing the other way.
         */
        val SUPERSEDED_GUARD = Regex("""requestGeneration\s*!=|generation\s*!=""")

        /**
         * An in-flight marker being released, the set-based equivalent of a status reset.
         *
         * A loader that guards with `if (!inFlight.add(key)) return` is stuck exactly as badly as
         * one guarding on a status, and unstuck by exactly the same act: removing the key. Matching
         * `.remove(` on an in-flight collection recognises that form without weakening the check —
         * a loader that removes nothing before rethrowing is still reported.
         */
        val IN_FLIGHT_RELEASE = Regex("""\w*[Ii]nFlight\w*\.remove\(""")
    }
}
