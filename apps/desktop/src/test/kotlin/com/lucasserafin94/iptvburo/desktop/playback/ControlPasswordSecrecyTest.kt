package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The VLC control password must never travel on a command line.
 *
 * On Windows any process running as this user can read another process's command line. That
 * password opens VLC's control interface, and its status document names the playing input — which
 * for a provider stream carries the user's own username and password. Putting it in an argument
 * therefore handed a local reader a route to exactly the credentials this project's rules say must
 * never be written down.
 *
 * It goes in a config file only this account can read. Verified against the bundled engine rather
 * than assumed: VLC answered 200 with the password from the file, 401 without it, and the command
 * line contained only the path.
 */
class ControlPasswordSecrecyTest {
    private val source: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt")
            .readText()

    /** The argument list handed to ProcessBuilder, which is what a local reader can see. */
    private val processArguments: String =
        source.substringAfter("ProcessBuilder(").substringBefore(").directory(")

    @Test
    fun `the password is not an ordinary argument`() {
        assertFalse(
            processArguments.contains("\"--http-password=\$password\""),
            "The control password must not sit in the command line, where any local process reads it.",
        )
    }

    @Test
    fun `the password reaches VLC through a file`() {
        assertTrue(
            source.contains("--config=\${config.absolutePath}"),
            "VLC should be pointed at a config file holding the password.",
        )
        assertTrue(
            source.contains("http-password=\$password"),
            "The config file itself must carry the password.",
        )
    }

    @Test
    fun `the file is restricted before the secret is written into it`() {
        val writer = source.substringAfter("private fun writeControlConfig").substringBefore("private fun randomPassword")
        val permissionsAt = writer.indexOf("setPosixFilePermissions")
        val writeAt = writer.indexOf("Files.writeString")
        assertTrue(permissionsAt > 0, "The file must have its permissions narrowed.")
        assertTrue(
            permissionsAt < writeAt,
            "Permissions must be set before the password is written, or it is briefly readable.",
        )
        assertTrue(writer.contains("rw-------"), "Owner-only, not merely narrowed.")
    }

    @Test
    fun `the file does not outlive the app`() {
        val writer = source.substringAfter("private fun writeControlConfig").substringBefore("private fun randomPassword")
        assertTrue(
            writer.contains("deleteOnExit()"),
            "A crash must not leave a password sitting in the temp directory.",
        )
        val dispose = source.substringAfter("fun dispose()").substringBefore("\n    private fun")
        assertTrue(dispose.contains("configFile?.delete()"), "dispose must remove the file.")
    }

    /**
     * The fallback is still allowed, and deliberately so.
     *
     * A filesystem that refuses the write would otherwise leave the player unable to start at all,
     * which is a worse outcome than an exposure only reachable by a process that could already read
     * this one's memory. The point of the test is that it stays a fallback rather than the path.
     */
    @Test
    fun `the command-line password remains only a fallback`() {
        val startVlc = source.substringAfter("private fun startVlc").substringBefore("ProcessBuilder(")
        val fallbackAt = startVlc.indexOf("--http-password=\$password")
        assertTrue(fallbackAt > 0, "The fallback should still exist for a filesystem that refuses.")
        val guardAt = startVlc.indexOf("if (config != null)")
        assertTrue(
            guardAt in 0 until fallbackAt,
            "The argument form must sit inside the branch taken only when the file could not be written.",
        )
    }
}
