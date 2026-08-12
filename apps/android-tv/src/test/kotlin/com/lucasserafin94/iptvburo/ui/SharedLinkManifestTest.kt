package com.lucasserafin94.iptvburo.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest declarations a shared link depends on.
 *
 * Read from the file rather than exercised through the framework: the behaviour these encode only
 * appears on a device, and the failure they guard against is silent. Each was found by tapping a
 * real link on a real phone, and each would pass every other test in this suite while being broken.
 */
class SharedLinkManifestTest {
    // readAllBytes rather than readString: the latter is API 26+ and this module's unit tests
    // compile against a lower level, where the call does not resolve at all.
    private val manifest: String by lazy {
        String(Files.readAllBytes(Path.of("src/main/AndroidManifest.xml")), Charsets.UTF_8)
    }

    /**
     * The bug this exists for, found on a device.
     *
     * Without `singleTask`, Android answers a VIEW intent by stacking a *new* MainActivity — the
     * task reached three of them — and a new activity builds a new, empty MainViewModel with no
     * profile and no catalogue. The link then resolves against nothing and the app sits on the home
     * screen, while `onNewIntent` is never called because there is nothing to deliver an intent to.
     */
    @Test
    fun `the activity is singleTask so a link reaches the running app`() {
        assertTrue(
            "MainActivity must be singleTask, or a shared link opens a second empty copy of the app",
            """android:launchMode="singleTask"""" in manifest,
        )
    }

    @Test
    fun `the private scheme is registered`() {
        assertTrue(
            "the iptvburo:// scheme is what the web page redirects to",
            """android:scheme="iptvburo"""" in manifest && """android:host="title"""" in manifest,
        )
    }

    /**
     * The https filter carries autoVerify and the custom-scheme filter does not.
     *
     * They must stay separate declarations: autoVerify on a filter with a non-web scheme makes
     * Android skip domain verification for the whole activity, which would silently stop the https
     * link ever opening the app directly.
     */
    @Test
    fun `the web link is declared for verification against the real host`() {
        assertTrue(
            "App Links need autoVerify or the link always opens in a browser",
            """android:autoVerify="true"""" in manifest,
        )
        assertTrue(
            "the verified host must be the one the app builds links for",
            """android:host="iptvburo.pages.dev"""" in manifest,
        )
        assertTrue(
            "only the /t/ path is claimed, so the rest of the site still opens in a browser",
            """android:pathPrefix="/t/"""" in manifest,
        )
    }
}
