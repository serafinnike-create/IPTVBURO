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
    fun `installs even when the launcher cannot be found`() {
        withDirectory { directory ->
            val installer = directory.resolve("IPTVBURO-0.3.0.msi")
            val body = Files.readString(writeUpdateScript(installer, pid = 1, launcher = null))

            assertContains(body, "msiexec.exe")
            assertTrue(!body.contains("start \"\" \""), "no relaunch should be attempted")
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
