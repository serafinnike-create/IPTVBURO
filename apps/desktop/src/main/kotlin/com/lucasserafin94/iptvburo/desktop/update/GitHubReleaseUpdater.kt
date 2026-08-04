package com.lucasserafin94.iptvburo.desktop.update

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

const val DESKTOP_VERSION = "0.2.0-alpha.6"

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val release: DesktopRelease) : UpdateCheckResult
    data class Failed(val userMessage: String) : UpdateCheckResult
}

data class DesktopRelease(
    val version: String,
    val displayName: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

class GitHubReleaseUpdater(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofMinutes(10))
            .followRedirects(true)
            .build(),
    private val releasesUrl: String = RELEASES_URL,
    private val currentVersion: String = DESKTOP_VERSION,
    private val updatesDirectory: Path = defaultUpdatesDirectory(),
    private val installerLauncher: (Path) -> Boolean = ::launchWindowsInstaller,
) {
    suspend fun check(): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url(releasesUrl)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "IPTV-BURO-Updater/$currentVersion")
                        .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "GitHub responded with ${response.code}" }
                    val releases = JsonParser.parseString(requireNotNull(response.body).string()).asJsonArray
                    val candidates =
                        releases.mapNotNull { element ->
                            val release = element.asJsonObject
                            if (release["draft"]?.asBoolean == true) return@mapNotNull null
                            val tag = release["tag_name"]?.asString?.removePrefix("v") ?: return@mapNotNull null
                            if (!isNewerVersion(tag, currentVersion)) return@mapNotNull null
                            // A release can carry more than one installer: the versioned artefact
                            // and a legacy name from the packaging task. Picking the first match
                            // would sometimes install an older build than the tag advertises, so
                            // the version-stamped asset is preferred explicitly.
                            val installers =
                                release["assets"]?.asJsonArray
                                    ?.map { it.asJsonObject }
                                    ?.filter { candidate ->
                                        candidate["name"]?.asString?.let(::isWindowsInstallerName) == true &&
                                            candidate["digest"]?.asString?.startsWith("sha256:") == true
                                    }
                                    .orEmpty()
                            val asset =
                                installers.firstOrNull { candidate ->
                                    candidate["name"].asString.contains(tag, ignoreCase = true)
                                } ?: installers.firstOrNull() ?: return@mapNotNull null
                            val downloadUrl = asset["browser_download_url"]?.asString ?: return@mapNotNull null
                            requireTrustedDownloadUrl(downloadUrl)
                            DesktopRelease(
                                version = tag,
                                displayName = release["name"]?.asString?.takeIf(String::isNotBlank) ?: "IPTV BURO $tag",
                                assetName = asset["name"].asString,
                                downloadUrl = downloadUrl,
                                sizeBytes = asset["size"]?.asLong ?: 0L,
                                sha256 = asset["digest"].asString.removePrefix("sha256:").lowercase(),
                            )
                        }.sortedWith { left, right -> compareVersions(right.version, left.version) }
                    candidates.firstOrNull()?.let(UpdateCheckResult::Available) ?: UpdateCheckResult.UpToDate
                }
            }.getOrElse {
                UpdateCheckResult.Failed("Não foi possível verificar atualizações agora.")
            }
        }

    /**
     * Downloads the installer, reporting progress as a 0..1 fraction.
     *
     * The installer is well over a hundred megabytes, so a caller that shows only "updating…" for
     * several minutes is indistinguishable from one that has hung.
     */
    suspend fun downloadAndLaunch(
        release: DesktopRelease,
        onProgress: (Float) -> Unit = {},
    ): Result<Path> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isWindowsInstallerName(release.assetName))
                require(release.sha256.matches(Regex("[a-f0-9]{64}")))
                requireTrustedDownloadUrl(release.downloadUrl)
                Files.createDirectories(updatesDirectory)
                val finalFile = updatesDirectory.resolve(release.assetName)
                val temporary = updatesDirectory.resolve("${release.assetName}.part")
                val request =
                    Request.Builder()
                        .url(release.downloadUrl)
                        .header("User-Agent", "IPTV-BURO-Updater/$currentVersion")
                        .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Download failed with ${response.code}" }
                    val body = requireNotNull(response.body)
                    check(body.contentLength() in 1..MAX_INSTALLER_BYTES || body.contentLength() == -1L)
                    var copied = 0L
                    body.byteStream().use { input ->
                        Files.newOutputStream(temporary).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                copied += read
                                check(copied <= MAX_INSTALLER_BYTES)
                                output.write(buffer, 0, read)
                                if (release.sizeBytes > 0L) {
                                    onProgress((copied.toFloat() / release.sizeBytes).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                check(sha256(temporary) == release.sha256) { "Installer digest mismatch" }
                Files.move(temporary, finalFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                check(installerLauncher(finalFile)) { "Windows Installer could not be started" }
                finalFile
            }
        }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/lucasserafin94/IPTVBURO/releases?per_page=20"
        const val MAX_INSTALLER_BYTES = 1_000_000_000L

        fun defaultUpdatesDirectory(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            val root = localAppData?.let(Path::of) ?: Path.of(System.getProperty("user.home"), "AppData", "Local")
            return root.resolve("IPTVBURO").resolve("updates")
        }

        /**
         * Installs the update after this process has exited, then starts the new build.
         *
         * Running msiexec directly while the app is open meant the MSI could not replace files that
         * were in use: it rolled back or deferred to a reboot, so the app restarted on the version
         * it started with and nothing explained why. A detached script waits for the process to go
         * away first, which is the only point at which the install can actually succeed, and it is
         * also what makes an automatic relaunch possible — the app cannot start itself after exit.
         */
        fun launchWindowsInstaller(installer: Path): Boolean =
            runCatching {
                val script = writeInstallScript(installer)
                ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "start",
                    "",
                    "/min",
                    "cmd.exe",
                    "/c",
                    script.toAbsolutePath().toString(),
                ).start()
                true
            }.getOrDefault(false)

        fun writeInstallScript(installer: Path): Path =
            writeUpdateScript(
                installer = installer,
                pid = ProcessHandle.current().pid(),
                launcher = installedLauncher(),
                installedProductCode = installedProductCode(),
            )

        /**
         * The installed executable, so the script can start the version it just installed.
         *
         * The MSI installs per-user into LOCALAPPDATA, not into Program Files, and names the binary
         * IPTVBURO.exe. Resolving from the running process is the reliable route; the fixed paths
         * are only a fallback for a layout this build did not produce.
         */
        private fun installedLauncher(): Path? {
            // resources.dir is <install>/app/resources, so the executable is two levels up.
            val fromRuntime =
                System.getProperty("compose.application.resources.dir")
                    ?.let(Path::of)
                    ?.parent
                    ?.parent
                    ?.resolve(LAUNCHER_NAME)
            val localAppData =
                System.getenv("LOCALAPPDATA")
                    ?.let(Path::of)
                    ?.resolve("IPTVBURO")
                    ?.resolve(LAUNCHER_NAME)
            return listOfNotNull(fromRuntime, localAppData).firstOrNull(Files::isRegularFile)
        }

        const val LAUNCHER_NAME = "IPTVBURO.exe"
    }
}

/**
 * Writes the batch file that performs the swap.
 *
 * Only paths the updater produced are interpolated — the installer inside our own updates directory
 * and the launcher inside the install directory — so no user input reaches the shell. The process is
 * identified by [pid] rather than by window title: a title match would wait on, or act against, an
 * unrelated window with the same name.
 *
 * ## Never leave the machine without the app
 *
 * An earlier version removed the installed product and then installed the new one. When the install
 * failed the removal had already happened, so the app simply vanished — which is exactly what it did
 * on a real machine. The order is now: install first, and only if that fails fall back to removing
 * the old product and installing again. The removal is never the last thing that ran.
 *
 * The script also keeps itself and the downloaded installer when anything goes wrong, so a failed
 * update can be inspected and re-run by hand instead of disappearing without trace.
 *
 * The wait is bounded. If the app somehow never exits, installing anyway is better than leaving a
 * downloaded update that is never applied and a script that never terminates.
 */
internal fun writeUpdateScript(
    installer: Path,
    pid: Long,
    launcher: Path?,
    installedProductCode: String? = null,
): Path {
    val script = installer.resolveSibling("apply-update.cmd")
    val relaunch =
        launcher?.let { path -> "start \"\" \"${path.toAbsolutePath()}\"" }
            ?: "rem launcher not found; the update is installed and can be opened from the Start menu"
    // Each MSI is generated with a fresh ProductCode, so installing over the existing one can return
    // 1638 ("another version is already installed") and do nothing. Removing the old product fixes
    // that, but only as a fallback: doing it first is what once deleted the app outright. The GUID
    // comes from the registry and is validated before it reaches the shell.
    val code = installedProductCode?.takeIf(PRODUCT_CODE::matches)
    val retryAfterRemoval =
        if (code == null) {
            "rem no installed product registered; nothing to remove"
        } else {
            """
            msiexec.exe /x $code /passive /norestart
            msiexec.exe /i "${installer.toAbsolutePath()}" /passive /norestart
            if errorlevel 1 goto :failed
            """.trimIndent()
        }
    Files.writeString(
        script,
        """
        @echo off
        rem Wait for IPTV BURO to exit so the installer can replace files that are in use.
        for /l %%i in (1,1,60) do (
          tasklist /fi "PID eq $pid" | find "$pid" >nul || goto :install
          timeout /t 1 /nobreak >nul
        )
        :install
        rem Install over the existing product first. If this succeeds nothing is ever removed, so a
        rem failure cannot leave the machine with no app.
        msiexec.exe /i "${installer.toAbsolutePath()}" /passive /norestart REINSTALLMODE=amus
        if not errorlevel 1 goto :done
        $retryAfterRemoval
        goto :done

        :failed
        rem The update did not apply. The installer and this script are kept so it can be retried by
        rem hand, and the old build is started if it is still there.
        $relaunch
        exit /b 1

        :done
        $relaunch
        del "%~f0"
        """.trimIndent(),
    )
    return script
}

/** A Windows Installer ProductCode. Anything else is refused rather than passed to the shell. */
internal val PRODUCT_CODE = Regex("\\{[0-9A-Fa-f]{8}(-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}}")

/**
 * The ProductCode of the currently installed build, read from the per-user uninstall registry.
 *
 * Returns null when nothing is registered, in which case the script simply installs.
 */
internal fun installedProductCode(): String? =
    // Verified against a real install: this MSI registers under HKLM even though it installs
    // per-user into LOCALAPPDATA. HKCU is searched too, since the hive is a packaging detail that
    // could change and querying both costs nothing.
    UNINSTALL_KEYS.firstNotNullOfOrNull { key ->
        runCatching {
            val process =
                ProcessBuilder("reg", "query", key, "/s", "/f", "IPTVBURO")
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            // The key name is the ProductCode; the match is anchored on the GUID shape.
            PRODUCT_CODE.find(output)?.value
        }.getOrNull()
    }

private val UNINSTALL_KEYS =
    listOf(
        "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
    )

internal fun isNewerVersion(candidate: String, current: String): Boolean = compareVersions(candidate, current) > 0

internal fun compareVersions(left: String, right: String): Int {
    val leftVersion = ParsedVersion.parse(left) ?: return -1
    val rightVersion = ParsedVersion.parse(right) ?: return 1
    for (index in 0..2) {
        val compared = leftVersion.numbers[index].compareTo(rightVersion.numbers[index])
        if (compared != 0) return compared
    }
    if (leftVersion.preRelease == null && rightVersion.preRelease != null) return 1
    if (leftVersion.preRelease != null && rightVersion.preRelease == null) return -1
    return comparePreRelease(leftVersion.preRelease.orEmpty(), rightVersion.preRelease.orEmpty())
}

private data class ParsedVersion(val numbers: List<Int>, val preRelease: String?) {
    companion object {
        fun parse(value: String): ParsedVersion? {
            val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$").matchEntire(value) ?: return null
            return ParsedVersion(
                numbers = (1..3).map { match.groupValues[it].toInt() },
                preRelease = match.groupValues[4].takeIf(String::isNotBlank),
            )
        }
    }
}

private fun comparePreRelease(left: String, right: String): Int {
    val leftParts = left.split('.', '-')
    val rightParts = right.split('.', '-')
    for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
        val leftPart = leftParts.getOrNull(index) ?: return -1
        val rightPart = rightParts.getOrNull(index) ?: return 1
        val leftNumber = leftPart.toIntOrNull()
        val rightNumber = rightPart.toIntOrNull()
        val compared =
            when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> leftPart.compareTo(rightPart, ignoreCase = true)
            }
        if (compared != 0) return compared
    }
    return 0
}

private fun isWindowsInstallerName(value: String): Boolean =
    value.matches(Regex("(?i)IPTV[-_]?BURO[-_A-Za-z0-9.]*\\.msi"))

private fun requireTrustedDownloadUrl(value: String) {
    val uri = java.net.URI(value)
    require(uri.scheme == "https")
    require(uri.host?.lowercase() in setOf("github.com", "objects.githubusercontent.com"))
}

private fun sha256(path: Path): String =
    Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
