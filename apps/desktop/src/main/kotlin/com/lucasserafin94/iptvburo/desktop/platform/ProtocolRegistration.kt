package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Registers `iptvburo://` with Windows, so a shared link opens this app.
 *
 * Done by the app rather than by the installer, for a reason that is specific to how this product
 * is packaged: the MSI is `perUserInstall`, and Compose Desktop's jpackage DSL exposes no
 * protocol-handler option at all. Writing the keys from the running app puts them in `HKCU`, which
 * is the hive a per-user install belongs in and the only one writable without elevation.
 *
 * The registration is idempotent and cheap, and it is refreshed on every launch on purpose: the
 * command it stores is an absolute path to the launcher, and an upgrade or a move would otherwise
 * leave the key pointing at an executable that is no longer there.
 *
 * A development run registers nothing. [launcherPath] only resolves under a packaged app, where
 * `jpackage.app-path` is set, so running from Gradle cannot hijack the protocol on a machine that
 * also has the real app installed.
 */
object ProtocolRegistration {
    /**
     * Writes the handler keys, returning what happened.
     *
     * Never throws. A machine policy that forbids writing to HKCU is a reason for share links not
     * to open the app; it is not a reason for the app to fail to start.
     */
    fun ensureRegistered(): Result {
        if (!isWindows()) return Result.NotApplicable
        val launcher = launcherPath() ?: return Result.NotPackaged
        val command = "\"$launcher\" \"%1\""
        val root = "HKCU\\SOFTWARE\\Classes\\${TitleShareLink.APP_SCHEME}"

        // `URL Protocol` is the marker that tells the shell this class is a protocol handler rather
        // than a file association. Without it the scheme is registered and never invoked.
        val written =
            registryAdd(root, name = null, value = "URL:IPTV BURO Protocol") &&
                registryAdd(root, name = "URL Protocol", value = "") &&
                registryAdd("$root\\DefaultIcon", name = null, value = "\"$launcher\",0") &&
                registryAdd("$root\\shell\\open\\command", name = null, value = command)

        return if (written) Result.Registered else Result.Failed
    }

    enum class Result {
        Registered,

        /** Not Windows. macOS and Linux declare their handlers in the bundle, not at runtime. */
        NotApplicable,

        /** Running from Gradle rather than an installed app, so there is no launcher to point at. */
        NotPackaged,

        /** The registry refused the write. Share links will open the website instead. */
        Failed,
    }

    /**
     * Absolute path of the installed launcher, or null when not running from a packaged app.
     *
     * `jpackage.app-path` is set by the launcher jpackage generates, so its presence is also the
     * test for whether this is a real installation.
     */
    private fun launcherPath(): Path? =
        System.getProperty("jpackage.app-path")
            ?.takeIf { it.isNotBlank() }
            ?.let(::Path)
            ?.takeIf { Files.isRegularFile(it) }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /**
     * One `reg add`, with the value passed as a separate argument rather than interpolated into a
     * command line. `reg` is invoked directly, not through `cmd`, so a path containing a space or a
     * quote cannot become another command.
     */
    private fun registryAdd(
        key: String,
        name: String?,
        value: String,
    ): Boolean =
        runCatching {
            val command =
                buildList {
                    add("reg")
                    add("add")
                    add(key)
                    if (name != null) {
                        add("/v")
                        add(name)
                    } else {
                        add("/ve")
                    }
                    add("/t")
                    add("REG_SZ")
                    add("/d")
                    add(value)
                    // Overwrite without the interactive confirmation, which would otherwise block
                    // on a process that has no console to answer it.
                    add("/f")
                }
            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
            // Drained before waiting. `reg` writes little, but a process whose output is never read
            // can block on a full pipe, and this runs during startup.
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0
        }.getOrDefault(false)
}
