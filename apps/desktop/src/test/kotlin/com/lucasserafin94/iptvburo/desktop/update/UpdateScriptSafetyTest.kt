package com.lucasserafin94.iptvburo.desktop.update

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The update script must never be able to leave the machine with no app.
 *
 * On 21/08/2026 it did exactly that. The event log recorded a single transaction with no removal
 * and status 1603, caused by error 1316: the script passed `REINSTALLMODE=amus`, which asks Windows
 * to *repair an installed product*, while jpackage mints a fresh ProductCode for every build. The
 * repair therefore addressed a product that was not installed, tried to reinstall components owned
 * by the other ProductCode, and rolled back — deregistering the app. None of the careful fallback
 * below it ever ran.
 *
 * These assertions are about that class of failure, not about wording.
 */
class UpdateScriptSafetyTest {
    private fun script(
        launcher: Path? = Path.of("""C:\Users\x\AppData\Local\IPTVBURO\IPTVBURO.exe"""),
        productCode: String? = "{8F8B1EA8-5D05-3280-ABAB-E9B9F2B63E22}",
    ): String {
        val dir = Files.createTempDirectory("updatescript")
        val installer = dir.resolve("IPTV-BURO-v9.9.9-windows-x64.msi")
        Files.writeString(installer, "x")
        return writeUpdateScript(
            installer = installer,
            pid = 1234L,
            launcher = launcher,
            installedProductCode = productCode,
        ).readText()
    }

    @Test
    fun `the install never asks for a repair`() {
        // The whole defect in one line. REINSTALLMODE targets an installed ProductCode, and this
        // MSI never carries the one already on the machine.
        //
        // Checked on the commands rather than the whole file: the script explains this history in
        // its own comments, and a naive search for the word matches that explanation too.
        val commands =
            script().lines()
                .map(String::trim)
                .filterNot { it.startsWith("rem ") || it.startsWith("echo ") }
        assertFalse(
            commands.any { it.contains("REINSTALLMODE", ignoreCase = true) },
            "REINSTALLMODE turns a major upgrade into a repair of a product that is not installed, " +
                "which is what erased a user's app.",
        )
    }

    @Test
    fun `success is proven on disk before anything is deleted`() {
        val text = script()
        // A zero exit code is not proof: a rolled-back transaction can finish quietly.
        assertTrue(text.contains(":verify"), "There must be a verification step.")
        val verifyAt = text.indexOf(":verify")
        val doneAt = text.indexOf("\n:done", verifyAt).takeIf { it > 0 } ?: text.indexOf(":done", verifyAt)
        assertTrue(verifyAt < doneAt, ":verify must be reached before :done.")
        assertTrue(
            text.contains("""if exist "%LOCALAPPDATA%\IPTVBURO\IPTVBURO.exe" goto :done"""),
            "The launcher must be checked on disk, with the executable name substituted.",
        )
    }

    @Test
    fun `the checks are real paths and not unexpanded placeholders`() {
        val text = script()
        // The first attempt at this check emitted the Kotlin expression verbatim, so every `if
        // exist` compared against a literal "${path.toAbsolutePath()}" and never matched — which
        // would have reported a destroyed app after every successful update.
        assertFalse(text.contains("\${"), "An uninterpolated Kotlin expression reached the batch file.")
        assertFalse(text.contains("\$LAUNCHER_EXE"), "The executable name was not substituted.")
        assertTrue(text.contains("""C:\Users\x\AppData\Local\IPTVBURO\IPTVBURO.exe"""))
    }

    @Test
    fun `a rollback is reported instead of passing silently`() {
        val text = script()
        val verifyBlock = text.substringAfter(":verify").substringBefore(":failed")
        assertTrue(
            verifyBlock.contains("goto :removed_and_failed"),
            "When no launcher is found the user must be told, not left with a silent exit.",
        )
        assertTrue(
            text.contains("Abra esse arquivo para reinstalar"),
            "The failure path must point at the installer that is kept for a retry.",
        )
    }

    @Test
    fun `the script deletes itself only after a verified install`() {
        val text = script()
        val deleteAt = text.indexOf("""del "%~f0"""")
        assertTrue(deleteAt > 0, "The script should clean itself up on the good path.")
        val doneAt = text.lastIndexOf(":done")
        assertTrue(doneAt in 1 until deleteAt, "The only deletion must sit under :done.")
    }

    @Test
    fun `nothing is removed before an install has been tried`() {
        val text = script()
        val firstInstall = text.indexOf("msiexec.exe /i")
        val firstRemoval = text.indexOf("msiexec.exe /x")
        assertTrue(firstInstall > 0, "There must be an install.")
        assertTrue(
            firstRemoval < 0 || firstInstall < firstRemoval,
            "Removing the old product before trying to install is what deletes the app.",
        )
    }

    @Test
    fun `an unregistered product still never removes anything`() {
        val text = script(productCode = null)
        assertFalse(
            text.contains("msiexec.exe /x"),
            "With no registered ProductCode there is nothing to remove, and guessing is dangerous.",
        )
    }
}
