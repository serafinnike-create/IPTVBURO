package com.lucasserafin94.iptvburo.desktop.update

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class UpdateScriptTest {
    private fun <T> withDirectory(block: (Path) -> T): T {
        val directory = Files.createTempDirectory("iptvburo-update")
        return try {
            block(directory)
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            directory.deleteRecursively()
        }
    }

    /**
     * The bug this exists for: msiexec ran while the app was still open, so the MSI could not
     * replace files in use and the app came back on the old version.
     */
    @Test
    fun `waits for the process to exit before running msiexec`() {
        withDirectory { directory ->
            val installer = directory.resolve("IPTVBURO-0.3.0.msi")
            val script = writeUpdateScript(installer, pid = 4321, launcher = null)
            val body = Files.readString(script)

            val waitAt = body.indexOf("PID eq 4321")
            val installAt = body.indexOf("msiexec.exe")
            assertTrue(waitAt in 0..<installAt, "the wait must precede the install")
        }
    }

    @Test
    fun `relaunches the installed app when the launcher is known`() {
        withDirectory { directory ->
            val installer = directory.resolve("IPTVBURO-0.3.0.msi")
            val launcher = directory.resolve("IPTV BURO.exe")
            val script = writeUpdateScript(installer, pid = 1, launcher = launcher)
            val body = Files.readString(script)

            val installAt = body.indexOf("msiexec.exe")
            val startAt = body.indexOf("start \"\" \"${launcher.toAbsolutePath()}\"")
            assertTrue(startAt > installAt, "the relaunch must follow the install")
        }
    }

    /** Without a launcher the update must still install rather than doing nothing. */
    @Test
    /**
     * The launcher path is read while the OLD build is running, so a fresh install can put the
     * executable somewhere that answer never covered. When it is unknown the script falls back to
     * the standard install locations rather than giving up - a user who updates and is left staring
     * at a closed app has, from their side, simply lost the application.
     */
    fun `relaunches from the standard locations when the launcher path is unknown`() {
        withDirectory { directory ->
            val installer = directory.resolve("IPTVBURO-0.3.0.msi")
            val body = Files.readString(writeUpdateScript(installer, pid = 1, launcher = null))

            assertContains(body, "msiexec.exe")
            assertContains(body, "%LOCALAPPDATA%")
            assertTrue(body.contains("start \"\" \""), "it must still try to reopen the app")
        }
    }

    @Test
    fun `passes norestart so the machine is never rebooted behind the user`() {
        withDirectory { directory ->
            val body =
                Files.readString(
                    writeUpdateScript(directory.resolve("a.msi"), pid = 1, launcher = null),
                )

            assertContains(body, "/norestart")
        }
    }

    /**
     * The bug that deleted the app off a real machine: the script removed the installed product and
     * then installed. When the install failed, the removal had already happened and nothing was
     * left. Installing first means a failure can never take the existing app with it.
     */
    @Test
    fun `installs before it ever removes anything`() {
        withDirectory { directory ->
            val code = "{A49CCA56-12E0-3ACF-81D3-649F5B7460D5}"
            val body =
                Files.readString(
                    writeUpdateScript(
                        installer = directory.resolve("a.msi"),
                        pid = 1,
                        launcher = null,
                        installedProductCode = code,
                    ),
                )

            val installAt = body.indexOf("msiexec.exe /i")
            val removeAt = body.indexOf("/x $code")
            assertTrue(installAt >= 0, "the script must install")
            assertTrue(removeAt > installAt, "removal must only happen after an install attempt")
        }
    }

    /** The removal is a fallback, so it must sit behind the check for a failed install. */
    @Test
    fun `removal is guarded by the install having failed`() {
        withDirectory { directory ->
            val code = "{A49CCA56-12E0-3ACF-81D3-649F5B7460D5}"
            val body =
                Files.readString(
                    writeUpdateScript(
                        installer = directory.resolve("a.msi"),
                        pid = 1,
                        launcher = null,
                        installedProductCode = code,
                    ),
                )

            val skipAt = body.indexOf("if not errorlevel 1 goto :done")
            val removeAt = body.indexOf("/x $code")
            assertTrue(skipAt in 0..<removeAt, "a successful install must skip the removal")
        }
    }

    /**
     * The case that deleted a customer's app.
     *
     * The old product was removed, the install that followed failed, and the script jumped to
     * :failed — which relaunches a build that no longer exists. The window was started minimised,
     * so nothing was on screen either: from the outside the app simply vanished.
     *
     * After a removal has happened, failure must reach its own label, tell the user plainly, and
     * leave the installer somewhere they can run it — never the silent path.
     */
    @Test
    fun `a failure after removal reports itself instead of vanishing`() {
        withDirectory { directory ->
            val code = "{A49CCA56-12E0-3ACF-81D3-649F5B7460D5}"
            val body =
                Files.readString(
                    writeUpdateScript(
                        installer = directory.resolve("a.msi"),
                        pid = 1,
                        launcher = null,
                        installedProductCode = code,
                    ),
                )

            assertTrue(":removed_and_failed" in body, "the post-removal failure needs its own path")
            // It must not fall into :failed, whose whole job is relaunching the old build.
            val removeAt = body.indexOf("/x $code")
            val recoveryAt = body.indexOf("goto :removed_and_failed")
            assertTrue(removeAt in 0..<recoveryAt, "the recovery path belongs after the removal")
            // The user is told where the installer is, and it is not deleted.
            val label = body.indexOf(":removed_and_failed")
            val deleteAt = body.indexOf("del \"%~f0\"")
            assertTrue(label in 0..<deleteAt, "the recovery path must not reach the delete")
            assertTrue("pause" in body.substring(label), "the message must stay on screen")
        }
    }

    /** One failed install is not proof it cannot work; the retry is visible so Windows can explain. */
    @Test
    fun `the reinstall after a removal is retried before giving up`() {
        withDirectory { directory ->
            val installer = directory.resolve("a.msi")
            val body =
                Files.readString(
                    writeUpdateScript(
                        installer = installer,
                        pid = 1,
                        launcher = null,
                        installedProductCode = "{A49CCA56-12E0-3ACF-81D3-649F5B7460D5}",
                    ),
                )

            val attempts = Regex(Regex.escape("""/i "${installer.toAbsolutePath()}"""")).findAll(body).count()
            assertTrue(attempts >= 3, "expected install, retry and visible retry; found $attempts")
        }
    }

    /** A failed update must leave the installer behind so it can be retried. */
    @Test
    fun `the script only deletes itself on success`() {
        withDirectory { directory ->
            val body =
                Files.readString(
                    writeUpdateScript(directory.resolve("a.msi"), pid = 1, launcher = null),
                )

            val failedAt = body.indexOf(":failed")
            val deleteAt = body.indexOf("del \"%~f0\"")
            assertTrue(failedAt in 0..<deleteAt, "the failure path must not reach the delete")
            assertTrue(body.indexOf("exit /b 1") in failedAt..deleteAt, "failure must exit early")
        }
    }

    /** The GUID reaches a shell command, so anything that is not a ProductCode is refused. */
    @Test
    fun `refuses a product code that is not a guid`() {
        withDirectory { directory ->
            val body =
                Files.readString(
                    writeUpdateScript(
                        installer = directory.resolve("a.msi"),
                        pid = 1,
                        launcher = null,
                        installedProductCode = "{} & del /q C:\\Windows\\*",
                    ),
                )

            assertTrue(!body.contains("del /q"), "injected text must not reach the script")
            assertContains(body, "no installed product registered")
        }
    }

    @Test
    fun `installs fresh when no product is registered`() {
        withDirectory { directory ->
            val body =
                Files.readString(
                    writeUpdateScript(directory.resolve("a.msi"), pid = 1, launcher = null),
                )

            assertTrue(!body.contains("msiexec.exe /x"), "nothing to remove")
            assertContains(body, "msiexec.exe /i")
        }
    }

    @Test
    fun `the script deletes itself so the updates folder does not accumulate`() {
        withDirectory { directory ->
            val body =
                Files.readString(
                    writeUpdateScript(directory.resolve("a.msi"), pid = 1, launcher = null),
                )

            assertContains(body, "del \"%~f0\"")
        }
    }
}
