package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Applying a connection a reseller set up, on this machine.
 *
 * The thing that must not go wrong here is the customer's own data. This arrives from outside the
 * machine, so it is added beside what they already had and never in place of it — a remote action
 * that deletes someone's playlists is not one this app performs, and no test after the fact would
 * give those playlists back.
 */
class ProvisioningWiringTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
    private val ui = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")

    private val COLLECTOR = "suspend fun collectProvisionedSource() {"

    private fun collectorBody(): String {
        // substringAfter returns the whole file when its marker is missing, which would leave
        // every check below searching all of DesktopAppState and passing on an unrelated line.
        assertTrue(state.contains(COLLECTOR), "the collector was renamed; this test needs updating")
        return state.substringAfter(COLLECTOR).substringBefore("\n    }")
    }

    @Test
    fun `the app asks at startup`() {
        assertTrue(
            ui.contains("appState.collectProvisionedSource()"),
            "without the call nothing is ever collected and the seller's Apply does nothing",
        )
    }

    @Test
    fun `what the customer already had is never removed`() {
        // The whole safety property of the feature.
        val body = collectorBody()
        assertTrue(body.contains("sourceLibrary.create("), "it adds a source")
        listOf("sourceLibrary.remove", "sourceLibrary.clear", "catalogs = emptyList()", ".forget(").forEach { destructive ->
            assertFalse(
                body.contains(destructive),
                "a remotely applied configuration must never delete the viewer's own sources: $destructive",
            )
        }
    }

    @Test
    fun `the credentials are wiped once they are stored`() {
        assertTrue(
            collectorBody().contains("source.clear()"),
            "they belong in the protected store, not in this process's heap",
        )
        assertTrue(collectorBody().contains("finally"), "wiped whether or not applying succeeded")
    }

    @Test
    fun `the delivery is confirmed only after it is saved`() {
        // Confirming on delivery would leave a customer whose app closed in between with no list
        // and no way to ask for it again.
        val body = collectorBody()
        val saved = body.indexOf(".save(")
        val confirmed = body.indexOf("confirmApplied()")
        assertTrue(saved >= 0 && confirmed >= 0, "both steps exist")
        assertTrue(saved < confirmed, "the save has to come first")
    }

    @Test
    fun `the metadata keys are applied when they are sent`() {
        // The seller can hand over an app that already shows artwork and synopsis, for the same
        // customer who could not set up the list in the first place.
        val body = collectorBody()
        assertTrue(body.contains("setMetadataApiKey("), "the TMDb key")
        assertTrue(body.contains("setCriticScoresApiKey("), "and the OMDb one")
    }

    @Test
    fun `a key that was not sent is left alone rather than cleared`() {
        // A seller replacing a provider address that went down sends the three connection fields
        // and nothing else. Treating absent as empty would wipe a key the viewer set up
        // themselves, and they would have no idea why their artwork disappeared.
        val body = collectorBody()
        // The setters must be reached only from inside a null-guard on the key itself. Checking
        // merely that "metadataKey?.let" appears is not enough: an unconditional
        // setMetadataApiKey(source.metadataKey?.let(::String)) contains that text too, and clears
        // the key on every launch that sends none.
        listOf(
            "source.metadataKey?.let { key -> userStore.setMetadataApiKey(String(key)) }",
            "source.criticsKey?.let { key -> userStore.setCriticScoresApiKey(String(key)) }",
        ).forEach { guarded ->
            assertTrue(body.contains(guarded), "the setter must sit inside the guard: $guarded")
        }
        assertFalse(
            body.contains("userStore.setMetadataApiKey(source."),
            "an unconditional call would clear a key the viewer configured themselves",
        )
        assertFalse(body.contains("userStore.setCriticScoresApiKey(source."))
    }

    @Test
    fun `a failure is reported so the seller can see it`() {
        assertTrue(collectorBody().contains("reportFailure("))
    }

    @Test
    fun `neither the network nor the store runs on the thread that draws`() {
        val body = collectorBody()
        assertTrue(body.contains("withContext(Dispatchers.IO)"), "off the interface thread")
    }

    @Test
    fun `nothing waiting leaves the app exactly as it was`() {
        // The ordinary case, on every machine no operator has configured.
        assertTrue(
            collectorBody().contains("?: return"),
            "an empty answer must return before anything is created",
        )
    }

    @Test
    fun `a failure at startup never stops the app opening`() {
        val body = collectorBody()
        assertTrue(body.contains("catch (failure: Exception)"), "contained")
        assertTrue(
            body.contains("catch (cancellation: CancellationException)") && body.contains("throw cancellation"),
            "but cancellation still propagates, or closing the window waits on the request",
        )
    }
}
