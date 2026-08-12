package com.lucasserafin94.iptvburo.ui.security

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cleartext is permitted for the user's providers and denied for the app's own services.
 *
 * The app has to allow plain HTTP: IPTV providers serve playlists, Xtream APIs and streams over it,
 * and refusing them would refuse most of what users own. But the blanket
 * `usesCleartextTraffic="true"` that permission used to be also covered the licence server and
 * TMDb, where it buys nothing and costs a downgrade path — an attacker answering DNS on a hostile
 * network could read and rewrite a licence check that was supposed to be authenticated.
 *
 * Read from the resource because that is where the rule lives; there is no runtime API that reports
 * it back, and a regression here is invisible until someone is attacked.
 */
class NetworkSecurityConfigTest {
    private val config: String by lazy {
        String(
            Files.readAllBytes(Path.of("src/main/res/xml/network_security_config.xml")),
            Charsets.UTF_8,
        )
    }

    private val manifest: String by lazy {
        String(Files.readAllBytes(Path.of("src/main/AndroidManifest.xml")), Charsets.UTF_8)
    }

    @Test
    fun `the manifest points at the network security config`() {
        assertTrue(
            "without this attribute the whole file is ignored and every host allows cleartext",
            """android:networkSecurityConfig="@xml/network_security_config"""" in manifest,
        )
    }

    /** The user's own providers must keep working, including the plain-HTTP ones. */
    @Test
    fun `provider traffic may still use cleartext`() {
        assertTrue(
            "a base-config denying cleartext would break most real playlists",
            Regex("""<base-config\s+cleartextTrafficPermitted="true"""").containsMatchIn(config),
        )
    }

    /** The services the app itself talks to are a known list, so they can be held to HTTPS. */
    @Test
    fun `the app's own services refuse cleartext`() {
        val secured =
            Regex("""<domain-config\s+cleartextTrafficPermitted="false">(.*?)</domain-config>""", RegexOption.DOT_MATCHES_ALL)
                .find(config)
                ?.groupValues
                ?.get(1)
                .orEmpty()

        assertFalse("no domain-config denying cleartext was found", secured.isBlank())

        listOf(
            // The licence server decides whether the app runs, and carries a device identity and a
            // purchase token: the single most damaging request to leave downgradable.
            "iptvburo.workers.dev",
            // Serves the shared-title page and, once verified, the App Links target.
            "iptvburo.pages.dev",
            // An intercepted metadata call leaks viewing interests and carries the user's API key.
            "themoviedb.org",
        ).forEach { host ->
            assertTrue(
                "$host must be pinned to HTTPS, or its traffic can be downgraded",
                host in secured,
            )
        }
    }
}
