package com.lucasserafin94.iptvburo.desktop.playback

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rebuilding VLC's plugin index where the plugins actually live.
 *
 * VLC keeps a `plugins.dat` listing every module it found, so that a launch does not have to open
 * and inspect several hundred DLLs. The index stores each plugin's **absolute path** and its
 * modification time, and rejects any entry whose file no longer matches.
 *
 * That is why generating it at build time did not work. The build wrote the index against
 * `build/generated/appResources/windows/vlc/plugins`, then the installer copied the whole tree to
 * `%LOCALAPPDATA%/IPTVBURO/app/resources/vlc/plugins` with fresh timestamps. Every entry was then
 * stale, VLC logged `stale plugins cache` once per module, and the index shipped in the installer
 * was doing nothing at all. So it is built once here instead, in the installed location, on the
 * first run after an install or update.
 *
 * ## What this is measured to be worth
 *
 * Less than was assumed, and the honest number is recorded here so nobody re-derives the optimism.
 * On a warm filesystem cache the engine reached its control interface in 0.54–0.63 s with a valid
 * index and 0.61 s with none: no measurable difference. The scan is fast once Windows has the
 * plugin directory cached, and that is the common case.
 *
 * It is kept because the cold case — first launch after an install or a reboot, several hundred
 * DLLs read from disk — is the one users meet, and it is not the case that is easy to measure
 * here. The cost is a 1.1 s background task that runs once per install, so a small unproven gain
 * still beats a guaranteed per-launch scan. It is not, on this evidence, the explanation for a
 * film taking a long time to start; that is looked for elsewhere.
 *
 * Failure is deliberately silent and non-fatal: without the index the player still works, it just
 * starts more slowly, and refusing to play a film over a cache file would be far worse.
 */
internal object VlcPluginCache {
    /**
     * Rebuilds the index for the bundled runtime, if that is the one in use.
     *
     * A VLC installed system-wide belongs to the user, not to this app: its index is shared with
     * their own VLC and is not ours to rewrite, so it is left alone even when stale.
     */
    fun ensureFreshForBundledRuntime(): Boolean {
        val executable = findBundledVlcExecutable() ?: return false
        return ensureFresh(executable.parentFile)
    }

    /**
     * Regenerates the index for [vlcDirectory] when it is missing or stale, on the calling thread.
     *
     * Returns true when an index was written. Intended to run off the UI thread, before the first
     * playback — it takes a few seconds and only has to happen once per install.
     */
    fun ensureFresh(vlcDirectory: File): Boolean {
        val generator = vlcDirectory.resolve("vlc-cache-gen.exe")
        val plugins = vlcDirectory.resolve("plugins")
        if (!generator.isFile || !plugins.isDirectory) return false
        if (!isStale(plugins)) return false

        return runCatching {
            val finished =
                ProcessBuilder(generator.absolutePath, plugins.absolutePath)
                    // Nothing here touches media, so there is no MRL to leak; discarded anyway to
                    // keep the app's output free of several hundred lines of module chatter.
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    // Bounded. A generator that hangs must not keep a background thread alive for
                    // the lifetime of the app.
                    .waitFor(GENERATOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            finished && !isStale(plugins)
        }.getOrDefault(false)
    }

    /**
     * The staleness decision, exposed for tests.
     *
     * Running the real generator in a test would mean shipping a Windows executable into the test
     * environment; the decision to run it is the part that was wrong, so that is what is pinned.
     */
    internal fun isStaleForTesting(plugins: File): Boolean = isStale(plugins)

    /**
     * Whether the index is absent, or older than the modules it claims to describe.
     *
     * ## Why the comparison is strict
     *
     * The first version of this allowed a second of slack, on the theory that files written in the
     * same moment by an installer are not evidence of a change. That was wrong, and measurably so:
     * a real install left plugins.dat and the DLLs with timestamps equal to the second, this
     * reported "fresh", and VLC then rejected the index with 363 `stale plugins cache` errors —
     * the exact condition the whole class exists to prevent, passed over by the check meant to
     * catch it.
     *
     * VLC compares more precisely than a second, so anything but "the index is strictly newer than
     * every plugin" is treated as stale. The cost of being wrong in this direction is one 1.1 s
     * background task per launch; the cost of being wrong in the other is a rejected index and a
     * full directory scan on every launch, silently.
     *
     * A directory with no plugins at all is reported fresh: there is nothing to index, and calling
     * the generator on an empty tree would just fail slowly on every launch.
     */
    private fun isStale(plugins: File): Boolean {
        val index = plugins.resolve("plugins.dat")
        if (!index.isFile) return true
        val newestPlugin =
            plugins
                .walkTopDown()
                .filter { file -> file.isFile && file.name.endsWith(".dll", ignoreCase = true) }
                .maxOfOrNull(File::lastModified)
                ?: return false
        return newestPlugin >= index.lastModified()
    }

    private const val GENERATOR_TIMEOUT_SECONDS = 90L
}

/**
 * The VLC runtime shipped inside this app, if present.
 *
 * Separate from [findVlcExecutable] because only this one may have its plugin index rewritten: the
 * bundled copy is ours, a system-wide VLC is the user's.
 */
internal fun findBundledVlcExecutable(): File? {
    val resources = System.getProperty("compose.application.resources.dir")?.let(::File)
    val workingDirectory = File(System.getProperty("user.dir"))
    return listOfNotNull(
        resources?.resolve("vlc/vlc.exe"),
        resources?.resolve("windows/vlc/vlc.exe"),
        workingDirectory.resolve("apps/desktop/build/generated/app-resources/windows/vlc/vlc.exe"),
    ).firstOrNull(File::isFile)
}

/**
 * The VLC the player will actually launch: the bundled runtime first, a system install as fallback.
 *
 * The order matters — the bundled copy is a known version with a known plugin set, while whatever
 * the user has installed may be older than the options this player passes.
 */
internal fun findVlcExecutable(): File? =
    findBundledVlcExecutable()
        ?: listOf(
            File("C:/Program Files/VideoLAN/VLC/vlc.exe"),
            File("C:/Program Files (x86)/VideoLAN/VLC/vlc.exe"),
        ).firstOrNull(File::isFile)
