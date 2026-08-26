package com.lucasserafin94.iptvburo.desktop.data

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A Stalker portal answering the questions the app asks of any subscription.
 *
 * The client for this has existed and passed its tests for a long time, and the television has
 * shipped Stalker for longer; the desktop had nowhere to plug it in. What these guard is the two
 * places where a portal differs from Xtream in a way that matters.
 *
 * The first is the playback command. A Stalker item carries no address — it carries an opaque
 * command the portal exchanges for a short-lived, single-use URL — so writing that command down
 * would leave a durable handle on somebody's subscription in a file.
 *
 * The second is what a portal simply does not have. Episodes and a programme guide are absent from
 * this API, and inventing either produces a screen of titles that refuse to play.
 */
class StalkerCatalogueRepositoryTest {
    private val source =
        Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/StalkerCatalogueRepository.kt")
            .readText()

    @Test
    fun `it satisfies the same contract as the Xtream repository`() {
        assertTrue(
            source.contains(") : CatalogueRepository {"),
            "otherwise it cannot be put where the app expects a subscription",
        )
    }

    @Test
    fun `the portal command is never written to disk`() {
        // The whole reason this class holds a map rather than storing items whole: the command is
        // the portal's handle on a stream, and a copy in a cache file outlives the session.
        assertTrue(source.contains("private val commands = mutableMapOf<String, String>()"))
        assertFalse(
            source.contains("CatalogDiskCache") || source.contains("Files.write"),
            "nothing here may reach the disk cache",
        )
    }

    @Test
    fun `the playable address is resolved at the moment of playback`() {
        // Fetching it earlier yields a URL that is usually dead by the time somebody presses play,
        // and stores a live credential in the meantime.
        val body =
            source.substringAfter("override fun buildConfirmedPlaybackUri(").substringBefore("\n    }")
        assertTrue(body.contains("client.resolvePlaybackUrl("), "resolved here, not at load time")
    }

    @Test
    fun `neither the credentials nor a command reach the printed form`() {
        assertTrue(
            source.contains("""override fun toString(): String = "StalkerCatalogueRepository(open=${'$'}{credentials != null})""""),
            "a printed repository must not carry the portal address, the MAC or a token",
        )
    }

    @Test
    fun `the subscription id carries no MAC address`() {
        // It reaches preferences, and a MAC written there is a device identifier at rest.
        val body =
            source.substringAfter("sourceId = \"stalker:\"").substringBefore("\n")
        assertTrue(body.contains("hashCode()"), "the id is derived, never the raw MAC")
    }

    @Test
    fun `what the portal does not have is reported as absent, not invented`() {
        // A series with fabricated episodes is a screen full of titles that will not play, which
        // is worse than one that is visibly empty.
        assertTrue(source.contains("episodes = emptyList()"), "no invented episode tree")
        assertTrue(source.contains("programs = emptyList<XtreamEpgProgram>()"), "no invented guide")
    }

    @Test
    fun `a target the portal cannot address is refused rather than approximated`() {
        // A plausible-looking URL that cannot play is worse than a button that says it cannot.
        val body =
            source.substringAfter("override fun buildConfirmedPlaybackUri(").substringBefore("\n    }")
        assertTrue(body.contains("does not address episodes individually"))
        assertTrue(body.contains("does not offer catch-up"))
    }

    @Test
    fun `a portal that never stops paging cannot spin forever`() {
        // Portals have been seen repeating their last page indefinitely, which would leave the
        // splash counting up until the app was killed.
        assertTrue(source.contains("pageNumber <= MAX_PAGES"))
        assertTrue(source.contains("if (page.items.isEmpty()) break"), "an empty page ends the walk")
    }

    @Test
    fun `a Kids profile is not shown an item filed nowhere`() {
        // The one mode meant to stop adult categories has to fail closed: an item with no known
        // category is exactly what an unfiled adult title looks like.
        val body =
            source.substringAfter("override fun isAllowedForBrowsing(").substringBefore("\n    }")
        assertTrue(
            body.contains("allowed.isNotEmpty()"),
            "an unfiled item must not pass Kids mode",
        )
    }
}
