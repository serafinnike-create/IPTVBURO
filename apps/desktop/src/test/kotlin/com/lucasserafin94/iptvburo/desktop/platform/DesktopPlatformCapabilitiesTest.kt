package com.lucasserafin94.iptvburo.desktop.platform

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlatformCapabilitiesTest {
    /**
     * The manifest is the single source of truth for what the UI offers.
     *
     * Each flag here is a deliberate release decision, asserted so that flipping one is a visible
     * change to this test rather than a silent change to what customers can see.
     */
    @Test
    fun `packaged preview contract matches what has actually shipped`() {
        val capabilities = DesktopPlatformCapabilities.current

        // Released. Each was built, tested, and then invisible because this file said otherwise —
        // the failure this manifest exists to prevent, working in the wrong direction. Downloads
        // were reported as "you removed it"; they had never been switched on.
        assertTrue(capabilities.multiviewSupported)
        assertTrue(capabilities.audioSupported)
        assertTrue(capabilities.offlineSupported)
    }

    /**
     * Downloading a film is released; the rest of the offline story is not.
     *
     * Queueing a whole season and fetching in the background are separate features that do not
     * exist, and the sub-flags are what keep them from being claimed by the one that does.
     */
    @Test
    fun `unbuilt offline features stay off`() {
        val manifest = Path
            .of("../../packages/platform-capabilities/windows-preview.json")
            .readText()

        assertTrue(Regex(""""backgroundJobs"\s*:\s*false""").containsMatchIn(manifest))
        assertTrue(Regex(""""seasonQueue"\s*:\s*false""").containsMatchIn(manifest))
        assertTrue(Regex(""""smartDownloads"\s*:\s*false""").containsMatchIn(manifest))
    }

    @Test
    fun `missing and malformed capability fields fail closed`() {
        val malformed = DesktopPlatformCapabilities.parse("""{"offline":{"supported":"yes"}}""")
        val invalid = DesktopPlatformCapabilities.parse("not-json")

        assertFalse(malformed.offlineSupported)
        assertFalse(malformed.multiviewSupported)
        assertFalse(malformed.audioSupported)
        assertFalse(invalid.offlineSupported)
        assertFalse(invalid.multiviewSupported)
        assertFalse(invalid.audioSupported)
    }
}
