package com.lucasserafin94.iptvburo.desktop.platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AppWindowLauncherTest {
    @Test
    fun `the page opens as an app window with a profile folder of its own`() {
        val command =
            AppWindowLauncher.command(
                browser = Path.of("C:/Edge/msedge.exe"),
                url = "https://godorama.com/",
                profileDirectory = Path.of("C:/data/web-profiles/p1-godorama"),
            )

        assertEquals("--app=https://godorama.com/", command[1])
        assertEquals("--user-data-dir=${Path.of("C:/data/web-profiles/p1-godorama")}", command[2])
    }

    @Test
    fun `the first installed browser is used, and none means none`() {
        val missing = Path.of("does-not-exist", "msedge.exe")
        val present = Files.createTempFile("chrome", ".exe")

        assertEquals(present, AppWindowLauncher.installedBrowser(listOf(missing, present)))
        assertNull(AppWindowLauncher.installedBrowser(listOf(missing)))
    }

    @Test
    fun `only an https page is opened`() {
        assertFalse(AppWindowLauncher.open("http://godorama.com/", Files.createTempDirectory("profile")))
        assertFalse(AppWindowLauncher.open("file:///C:/x.html", Files.createTempDirectory("profile")))
    }
}
