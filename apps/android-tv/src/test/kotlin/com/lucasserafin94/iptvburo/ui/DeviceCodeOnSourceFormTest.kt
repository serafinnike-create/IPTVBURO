package com.lucasserafin94.iptvburo.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The set's code, on the screen where somebody gives up.
 *
 * It is how a seller finds this television in their panel and configures it remotely, so the person
 * who needs it most is exactly the one who has not managed to fill in a server, a username and a
 * password. Until now it appeared only under Settings, which is not where that person is looking.
 *
 * Read from the source because the alternative is driving a Compose television UI, and what matters
 * here is that the code reaches the screen at all.
 */
class DeviceCodeOnSourceFormTest {
    private fun read(relative: String): String =
        String(Files.readAllBytes(Path.of(relative)), Charsets.UTF_8)

    private val panel =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/XtreamSourcePanel.kt")
    private val shell =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/AppShellScreen.kt")

    @Test
    fun `the form shows the code`() {
        assertTrue(
            "the label has to be on the form itself",
            panel.contains("R.string.license_device_code"),
        )
        assertTrue("and the code drawn", panel.contains("text = deviceCode"))
        assertTrue(
            "and passed in, or the form has nothing to draw",
            shell.contains("deviceCode = state.deviceId.orEmpty()"),
        )
    }

    @Test
    fun `it says what the code is for`() {
        // A row of characters means nothing to whoever is reading it off the screen.
        assertTrue(panel.contains("R.string.license_device_code_help"))
    }

    @Test
    fun `a set with no code yet shows nothing rather than a blank line`() {
        // The code is read aloud and sent by message. A blank one finds nobody in the panel and
        // costs the customer a support message that cannot be acted on.
        assertTrue(panel.contains("if (deviceCode.isNotBlank()) {"))
    }

    @Test
    fun `the wording exists in every language`() {
        // A missing translation is a crash on that locale, not a fallback.
        listOf("values", "values-pt-rBR", "values-de", "values-it", "values-es").forEach { folder ->
            val strings = read("src/main/res/$folder/strings.xml")
            assertTrue(
                "$folder is missing the explanation",
                strings.contains("license_device_code_help"),
            )
        }
    }

    @Test
    fun `an apostrophe in the wording is escaped`() {
        // An unescaped ' in strings.xml fails mergeDebugResources, and the failure names the file
        // rather than the string, so it costs a hunt every time.
        listOf("values", "values-pt-rBR", "values-de", "values-it", "values-es").forEach { folder ->
            val line =
                read("src/main/res/$folder/strings.xml")
                    .lineSequence()
                    .first { it.contains("license_device_code_help") }
            val bare =
                line.withIndex().count { (index, ch) ->
                    ch == '\'' && (index == 0 || line[index - 1] != '\\')
                }
            assertEquals("$folder has an unescaped apostrophe", 0, bare)
        }
    }

    @Test
    fun `a pasted subscription link still fills the three fields`() {
        // Sellers hand out one get.php URL far more often than three separate fields. This already
        // worked here; it is pinned so it is not lost, since the Windows setup form had thrown the
        // credentials away in exactly this place.
        assertTrue(panel.contains("XtreamSubscriptionParser.parse(pasted)"))
        assertTrue(panel.contains("username = link.username"))
        assertTrue(panel.contains("password = link.password"))
    }
}
