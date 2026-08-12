package com.lucasserafin94.iptvburo.desktop.update

import com.google.gson.JsonParser
import com.lucasserafin94.iptvburo.desktop.build.DESKTOP_RELEASE_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

const val DESKTOP_VERSION = DESKTOP_RELEASE_VERSION
internal const val DESKTOP_RELEASE_REPOSITORY = "serafinnike-create/IPTVBURO"
internal const val DESKTOP_RELEASES_URL =
    "https://api.github.com/repos/$DESKTOP_RELEASE_REPOSITORY/releases?per_page=20"

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
    private val releasesUrl: String = DESKTOP_RELEASES_URL,
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
                        // The button is an explicit refresh action. Do not let a local or proxy
                        // cache turn it into a check against yesterday's release list.
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "GitHub responded with ${response.code}" }
                    val releases = JsonParser.parseString(requireNotNull(response.body).string()).asJsonArray
                    // Preview builds are the tester channel and may update to another preview. A
                    // stable build is the customer channel: it must never cross into alpha/beta/rc,
                    // even when that preview has a numerically newer major or minor version.
                    //
                    // Preview MSIs deliberately remain unsigned for now. Keeping the channel
                    // decision here means that exception cannot leak into a stable installation.
                    val runningPreview = isPreviewVersion(currentVersion)
                    val candidates =
                        releases.mapNotNull { element ->
                            val release = element.asJsonObject
                            if (release["draft"]?.asBoolean == true) return@mapNotNull null
                            val tag = release["tag_name"]?.asString?.removePrefix("v") ?: return@mapNotNull null
                            val previewRelease =
                                release["prerelease"]?.asBoolean == true || isPreviewVersion(tag)
                            if (!runningPreview && previewRelease) return@mapNotNull null
                            if (!isNewerVersion(tag, currentVersion)) return@mapNotNull null
                            // A release can carry more than one installer: the versioned artefact
                            // and a legacy name from the packaging task. Picking the first match
                            // would sometimes install an older build than the tag advertises, so
                            // the version-stamped asset is preferred explicitly.
                            val installers =
                                release["assets"]?.asJsonArray
                                    ?.map { it.asJsonObject }
                                    ?.filter { candidate ->
                                        candidate["name"]?.asString?.let { name ->
                                            isWindowsInstallerName(name) &&
                                                (runningPreview || !isUnsignedPreviewInstaller(name))
                                        } == true &&
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
/** The installed executable's file name, used by the relaunch fallbacks. */
internal const val LAUNCHER_EXE = "IPTVBURO.exe"

internal fun writeUpdateScript(
    installer: Path,
    pid: Long,
    launcher: Path?,
    installedProductCode: String? = null,
): Path {
    val script = installer.resolveSibling("apply-update.cmd")
    // Resolved after the install, not before it. The path is read while the OLD build is running,
    // and a fresh install can land the executable somewhere the old one never looked - so the
    // script re-checks the standard location too rather than trusting a single stale answer.
    val relaunch =
        buildString {
            appendLine("rem Give the installer a moment to release the new executable.")
            appendLine("timeout /t 2 /nobreak >nul")
            launcher?.let { path ->
                appendLine("if exist \"${path.toAbsolutePath()}\" (")
                appendLine("  start \"\" \"${path.toAbsolutePath()}\"")
                appendLine("  goto :started")
                appendLine(")")
            }
            val localApp = """%LOCALAPPDATA%\IPTVBURO\$LAUNCHER_EXE"""
            val programFiles = """%PROGRAMFILES%\IPTVBURO\$LAUNCHER_EXE"""
            appendLine("""if exist "$localApp" (""")
            appendLine("""  start "" "$localApp"""")
            appendLine("  goto :started")
            appendLine(")")
            appendLine("""if exist "$programFiles" (""")
            appendLine("""  start "" "$programFiles"""")
            appendLine(")")
            append(":started")
        }
    // Each MSI is generated with a fresh ProductCode, so installing over the existing one can return
    // 1638 ("another version is already installed") and do nothing. Removing the old product fixes
    // that, but only as a fallback: doing it first is what once deleted the app outright. The GUID
    // comes from the registry and is validated before it reaches the shell.
    val code = installedProductCode?.takeIf(PRODUCT_CODE::matches)
    val retryAfterRemoval =
        if (code == null) {
            // Nothing to remove, but the install still failed — so this must not fall through to
            // :done, which would relaunch and delete the script as though the update had worked.
            // A second, visible attempt gives Windows a chance to say what is wrong.
            """
            rem No installed product is registered, so there is nothing to remove.
            rem The install still failed, so retry it visibly rather than pretending it worked.
            msiexec.exe /i "${installer.toAbsolutePath()}" /norestart
            if errorlevel 1 goto :failed
            """.trimIndent()
        } else {
            // Removal is the last resort, and the reinstall after it is retried before giving up.
            //
            // This is the step that deleted a customer's app: the uninstall succeeded, the install
            // that followed did not, and the script then jumped to :failed — which relaunches a
            // build that no longer exists. One failed attempt is not proof the installer cannot
            // work; a second attempt without /passive lets Windows surface whatever it is
            // objecting to instead of failing silently behind a progress bar.
            """
            echo Removendo a versao anterior...
            msiexec.exe /x $code /passive /norestart
            echo Instalando a nova versao...
            msiexec.exe /i "${installer.toAbsolutePath()}" /passive /norestart
            if not errorlevel 1 goto :done
            rem Second attempt, visible, so a blocked install shows the user why.
            msiexec.exe /i "${installer.toAbsolutePath()}" /norestart
            if errorlevel 1 goto :removed_and_failed
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
        echo Atualizando o IPTV BURO...
        msiexec.exe /i "${installer.toAbsolutePath()}" /passive /norestart REINSTALLMODE=amus
        if not errorlevel 1 goto :done
        $retryAfterRemoval
        goto :done

        :failed
        rem The update did not apply and nothing was removed, so the old build is still installed.
        rem The installer and this script are kept so it can be retried by hand.
        $relaunch
        exit /b 1

        :removed_and_failed
        rem The worst case: the old product was removed and the new one would not install. There is
        rem no app to relaunch, so say so plainly and leave the installer where the user can run it.
        rem Silently exiting here is what made the app appear to simply vanish.
        echo.
        echo =====================================================================
        echo  A atualizacao nao pode ser concluida e a versao anterior foi
        echo  removida pelo Windows.
        echo.
        echo  O instalador foi mantido em:
        echo  "${installer.toAbsolutePath()}"
        echo.
        echo  Abra esse arquivo para reinstalar o IPTV BURO.
        echo =====================================================================
        echo.
        rem Opens the folder holding the installer, so the file is one click away rather than a path
        rem the user has to copy out of a console window.
        explorer.exe /select,"${installer.toAbsolutePath()}"
        pause
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

/** Whether [version] belongs to the tester channel rather than the stable customer channel. */
internal fun isPreviewVersion(version: String): Boolean = ParsedVersion.parse(version)?.preRelease != null

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
        /**
         * Parses "2.0", "2.0.1", "v2.0.1" and "0.2.0-alpha.5" alike.
         *
         * The patch number is optional, and that is not cosmetic. This project writes its shipped
         * version with two numbers in older builds — DESKTOP_VERSION was "1.1" and then "2.0" —
         * while the pattern used to demand three. An unparseable *current* version made compareVersions
         * return 1 for every candidate, so the updater offered the running build to itself as an
         * update, repeatedly, and there was no version it would ever consider itself up to date
         * against. A missing patch means zero, which is what "2.0" means.
         */
        fun parse(value: String): ParsedVersion? {
            val match =
                Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?$").matchEntire(value)
                    ?: return null
            return ParsedVersion(
                numbers =
                    listOf(
                        match.groupValues[1].toInt(),
                        match.groupValues[2].toInt(),
                        match.groupValues[3].toIntOrNull() ?: 0,
                    ),
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

/** The explicit marker emitted by preview-release.yml when no Authenticode certificate is present. */
private fun isUnsignedPreviewInstaller(value: String): Boolean =
    value.contains("-unsigned", ignoreCase = true)

private fun requireTrustedDownloadUrl(value: String) {
    val uri = java.net.URI(value)
    require(uri.scheme == "https")
    require(uri.host.equals("github.com", ignoreCase = true))
    require(uri.userInfo == null)
    require(uri.port in setOf(-1, 443))
    require(
        uri.rawPath?.startsWith(
            "/$DESKTOP_RELEASE_REPOSITORY/releases/download/",
            ignoreCase = true,
        ) == true,
    )
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
