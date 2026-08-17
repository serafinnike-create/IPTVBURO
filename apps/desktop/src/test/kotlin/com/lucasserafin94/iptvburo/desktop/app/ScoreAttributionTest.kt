package com.lucasserafin94.iptvburo.desktop.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A score must be credited to whoever produced it.
 *
 * The ratings panel drew a logo fetched from a constant called `TMDB_MARK_URL`, documented as "TMDb's
 * own mark". It was not TMDb's mark: `/t/p/w92/wwemzKWzjKYJFfCeiB57q3r4Bcm.png` is a **watch
 * provider** logo on TMDb's image CDN, and the file behind it is Netflix's wordmark. The panel
 * therefore showed the Netflix logo beside the words "Nota TMDb" — a TMDb users' average presented
 * as Netflix's verdict.
 *
 * It is an easy mistake to repeat, because the URL is plausible: right host, right CDN, and the app
 * legitimately fetches provider logos from that exact path elsewhere. The mistake is only visible if
 * you open the image. So this test reads the source instead of the rendering: the audience score's
 * mark must not be an image pulled from the provider-logo path.
 */
class ScoreAttributionTest {
    private val workspace =
        File("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
            .also { file -> assertTrue(file.isFile, "Expected to find ${file.path}") }
            .readText()

    /**
     * The specific file that caused it, named so nobody restores it from an old diff.
     *
     * Kept as a fragment of the path rather than the whole URL: the point is that this particular
     * asset is Netflix's, whatever size or prefix it is requested at.
     */
    @Test
    fun `the netflix wordmark is not used as a score mark`() {
        assertFalse(
            workspace.contains(NETFLIX_WORDMARK_ASSET),
            "The audience score is being credited with Netflix's wordmark ($NETFLIX_WORDMARK_ASSET).",
        )
    }

    /**
     * And no other provider logo either.
     *
     * Any hard-coded `image.tmdb.org` path in this file is the same mistake waiting to happen: the
     * provider logos belong to whichever service the *title* streams on, and are looked up per title
     * through the metadata client. A constant one, written into the source, cannot be the mark of the
     * company whose score is on screen.
     */
    @Test
    fun `no image cdn path is hard-coded as a brand mark`() {
        val hardCoded = Regex("""https://image\.tmdb\.org/\S*""").findAll(workspace).map { it.value }.toList()

        assertTrue(
            hardCoded.isEmpty(),
            "A brand mark must not be a hard-coded CDN path; found $hardCoded.",
        )
    }

    /** The audience score still says whose it is — in letters, which cannot become another brand. */
    @Test
    fun `the audience score is labelled TMDb`() {
        assertTrue(
            workspace.contains("\"TMDb\""),
            "The audience score's mark no longer names TMDb.",
        )
    }

    private companion object {
        /** Netflix's wordmark on TMDb's provider-logo path, which is what was being drawn. */
        const val NETFLIX_WORDMARK_ASSET = "wwemzKWzjKYJFfCeiB57q3r4Bcm"
    }
}
