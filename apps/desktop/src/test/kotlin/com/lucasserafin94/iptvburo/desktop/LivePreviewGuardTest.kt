package com.lucasserafin94.iptvburo.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * The rules that keep the channel preview from flooding a provider.
 *
 * A preview is a session on the provider, and some providers cap simultaneous connections and cut
 * the account off for exceeding them. Arrowing through a list of four hundred channels must not
 * open four hundred streams — only the one somebody stops on.
 *
 * `DesktopAppState` needs a catalogue, a repository and a live server to construct, which is more
 * scaffolding than this behaviour deserves. What is tested here is the scheduling rule itself,
 * written the same way the state writes it: cancel the pending job, then start a delayed one.
 */
class LivePreviewGuardTest {
    /** The rule as `previewChannel` implements it, isolated from the state that owns it. */
    private class PreviewScheduler(
        private val delayMillis: Long,
        private val scope: kotlinx.coroutines.CoroutineScope,
    ) {
        var opened: MutableList<String> = mutableListOf()
        var enabled: Boolean = false
        var blocked: Boolean = false
        private var job: Job? = null

        fun request(providerId: String?) {
            job?.cancel()
            if (!enabled || providerId == null || blocked) {
                return
            }
            job =
                scope.launch {
                    delay(delayMillis)
                    opened.add(providerId)
                }
        }
    }

    @Test
    fun `arrowing through channels opens only the one the focus rests on`() =
        runTest {
            val scheduler = PreviewScheduler(delayMillis = 1500, scope = this)
            scheduler.enabled = true

            // Twenty channels crossed in under a second, which is an ordinary sweep of a remote.
            listOf("a", "b", "c", "d", "e").forEach { id ->
                scheduler.request(id)
                delay(120)
            }
            // Nothing has opened yet: every request cancelled the one before it.
            assertTrue(scheduler.opened.isEmpty(), "a sweep opened a stream before settling")

            delay(2000)
            assertEquals(listOf("e"), scheduler.opened, "only the channel rested on should open")
        }

    @Test
    fun `disabled means nothing opens at all`() =
        runTest {
            val scheduler = PreviewScheduler(delayMillis = 1500, scope = this)
            // Off by default is the whole safety story: someone who does not know whether their
            // provider caps connections is not exposed to the risk without choosing it.
            scheduler.request("a")
            delay(2000)
            assertTrue(scheduler.opened.isEmpty(), "a disabled preview opened a stream")
        }

    @Test
    fun `a blocking playback keeps the preview shut`() =
        runTest {
            val scheduler = PreviewScheduler(delayMillis = 1500, scope = this)
            scheduler.enabled = true
            // Multiview already holds four sessions; a fifth for a preview would be both a cost and
            // a second audio track playing underneath.
            scheduler.blocked = true
            scheduler.request("a")
            delay(2000)
            assertTrue(scheduler.opened.isEmpty(), "a preview opened under an active playback")
        }

    @Test
    fun `a null selection cancels whatever was pending`() =
        runTest {
            val scheduler = PreviewScheduler(delayMillis = 1500, scope = this)
            scheduler.enabled = true
            scheduler.request("a")
            delay(200)
            // Leaving the list, or selecting a film: there is no channel to preview any more, and
            // the one on its way must not arrive after the screen has changed.
            scheduler.request(null)
            delay(2000)
            assertTrue(scheduler.opened.isEmpty(), "a cancelled preview still opened")
        }

    @Test
    fun `a request that arrives after the delay is not cancelled by a later one`() =
        runTest {
            val scheduler = PreviewScheduler(delayMillis = 1500, scope = this)
            scheduler.enabled = true
            scheduler.request("a")
            delay(2000)
            assertEquals(listOf("a"), scheduler.opened, "the settled channel should have opened")

            // Moving on after a preview has already started: the new one is scheduled, and the
            // old one having opened is not undone by it.
            scheduler.request("b")
            delay(2000)
            assertEquals(
                listOf("a", "b"),
                scheduler.opened,
                "moving on after a preview opened should schedule the next one",
            )
        }
}
