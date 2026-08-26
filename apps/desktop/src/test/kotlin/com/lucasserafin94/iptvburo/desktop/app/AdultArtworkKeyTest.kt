package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cover art for the one catalogue TMDb answers nothing for.
 *
 * TMDb is asked not to return these titles, and its own guidance says applications should not
 * fetch them, so those rows arrive with no artwork at all. A ThePornDB key fills them.
 *
 * The rule this exists to hold: **the key is the viewer's own and never travels with the app.** An
 * installer is a file anybody unpacks, so a key inside one is published — and the account
 * suspended when somebody abuses it would be the account that issued it, taking every viewer's
 * artwork down at once. The build already refuses to package a TMDb key for exactly this reason.
 */
class AdultArtworkKeyTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val client =
        read("../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/AdultArtworkClient.kt")
    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
    private val settings = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/SettingsDialog.kt")
    private val store = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/user/DesktopUserStore.kt")

    @Test
    fun `no key is shipped with the app`() {
        // The whole safety property. A key here would reach every installer, and the account it
        // belongs to would be the one suspended.
        listOf(client, state, settings, store).forEach { source ->
            assertFalse(
                Regex("""[A-Za-z0-9]{40,}""").containsMatchIn(source),
                "a long literal here is almost certainly a key, and none may be embedded",
            )
        }
    }

    @Test
    fun `without a key nothing is fetched and the app still works`() {
        // Absent is the shipped state: those rows keep the title card they already draw, which is
        // a working screen rather than a broken one.
        assertTrue(client.contains("apiKey?.takeIf(String::isNotBlank) ?: return null"))
        assertTrue(
            state.contains("clean.takeIf(String::isNotBlank)?.let(::AdultArtworkClient)"),
            "no key means no client at all",
        )
    }

    @Test
    fun `the settings screen explains why the key is needed`() {
        // "Paste a key" without a reason is a field people leave empty. It has to say that TMDb
        // does not cover this catalogue and what happens without one.
        assertTrue(settings.contains("ratings.adultKeyBody"))
        assertTrue(settings.contains("ratings.adultKeyTitle"))
    }

    @Test
    fun `the screen links to where the key is issued`() {
        val app = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")
        assertTrue(settings.contains("onOpenAdultKeySite"), "the link is offered")
        assertTrue(
            app.contains("ADULT_METADATA_KEY_URL"),
            "and reaches the browser, or the link does nothing",
        )
        val guide = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/OmdbKeyGuideDialog.kt")
        assertTrue(
            guide.contains("https://theporndb.net/user/api-tokens"),
            "pointing at the token page, not the catalogue",
        )
    }

    @Test
    fun `a key pasted takes effect without a restart`() {
        // Somebody who has just fetched a key expects the covers to appear, not to be told to
        // reopen the app.
        val body =
            state
                .substringAfter("fun updateAdultMetadataApiKey(value: String) {")
                .substringBefore("\n    }")
        assertTrue(body.contains("adultArtworkClient ="), "the client is rebuilt on the spot")
        assertTrue(body.contains("userStore.setAdultMetadataApiKey"), "and the key is stored")
    }

    @Test
    fun `the key never reaches a log line`() {
        assertTrue(
            client.contains("""override fun toString(): String = "AdultArtworkClient(configured=${'$'}isConfigured)""""),
            "a printed client must not carry the key",
        )
    }

    @Test
    fun `only a real address is handed to the image loader`() {
        // The value comes from a third-party response and becomes an image request. Anything that
        // is not http or https is a malformed answer rather than a picture.
        assertTrue(client.contains("fun looksLikeHttpUrl(value: String): Boolean"))
        assertTrue(client.contains("?.takeIf(::looksLikeHttpUrl)"))
    }

    @Test
    fun `a changed response shape costs the artwork, never a crash`() {
        // A details screen that throws because a field was renamed upstream is worse than one
        // without a cover.
        assertTrue(client.contains("runCatching"), "the request is contained")
        assertTrue(
            client.contains("POSTER_FIELDS.firstNotNullOfOrNull"),
            "several known field names are tried rather than one guessed",
        )
    }
}
