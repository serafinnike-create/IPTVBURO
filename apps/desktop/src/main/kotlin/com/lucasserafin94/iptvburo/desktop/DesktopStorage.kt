package com.lucasserafin94.iptvburo.desktop

import java.nio.file.Path
import java.util.prefs.Preferences

/**
 * Where the desktop app keeps what it stores: preference nodes, the per-user data folder and the
 * home-folder caches.
 *
 * Every default goes through here so a test run can be pointed somewhere else as a whole. Before
 * this, each store defaulted straight to the real user profile, and the test suite — which builds
 * a full app state without passing every store — wrote into it: a machine that had only ever run
 * the tests opened the app to eight saved lists called "localhost". The test task sets
 * `iptvburo.storage.root`; the app never does, so for a real user nothing changes.
 */
object DesktopStorage {
    private val testRoot: String? =
        System.getProperty("iptvburo.storage.root")?.trim()?.takeIf(String::isNotEmpty)

    /** A preferences node at [path], under the test root when one is set. */
    fun preferences(path: String): Preferences =
        Preferences.userRoot().node(if (testRoot != null) "$testRoot/$path" else path)

    /** The per-user data folder: `%LOCALAPPDATA%\lucasserafin94\IPTVBURO`. */
    fun localData(): Path {
        testRoot?.let { root -> return Path.of(System.getProperty("java.io.tmpdir"), root, "local") }
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
        val base = localAppData?.let(Path::of) ?: Path.of(System.getProperty("user.home"), "AppData", "Local")
        return base.resolve("lucasserafin94").resolve("IPTVBURO")
    }

    /** `~/.iptvburo/<parts>`: caches, logs and the licence file. */
    fun homeData(vararg parts: String): Path {
        val base =
            testRoot?.let { root -> Path.of(System.getProperty("java.io.tmpdir"), root, "home") }
                ?: Path.of(System.getProperty("user.home"), ".iptvburo")
        return parts.fold(base) { path, part -> path.resolve(part) }
    }
}
