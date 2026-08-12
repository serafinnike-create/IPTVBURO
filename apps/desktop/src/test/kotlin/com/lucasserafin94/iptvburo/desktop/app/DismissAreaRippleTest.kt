package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A full-screen dismiss area must not paint over the app.
 *
 * `Modifier.clickable` carries Material's default ripple. On a small control that is what makes it
 * feel pressed; on a click target the size of the window it washes the entire screen grey on hover.
 *
 * That is what the settings panel was doing, and it was reported three times as "the background goes
 * grey" while being read — including by me — as a scrim. There is no scrim: the comment above the
 * code said so, correctly, and the dimming was the hover state of the dismiss area all along.
 *
 * A source-reading test because the effect is a rendering detail of a modifier: there is no
 * behavioural assertion that catches "the screen is slightly grey".
 */
class DismissAreaRippleTest {

    @Test
    fun `full-screen dismiss areas suppress their indication`() {
        val offenders = mutableListOf<String>()

        Files.walk(Path.of("src/main/kotlin")).asSequence()
            .filter { path -> path.toString().endsWith(".kt") }
            .forEach { path ->
                val source = path.readText()
                // A clickable directly on a fillMaxSize modifier, without indication turned off.
                // The window-sized ones are the only ones that matter: a ripple on a button is the
                // point of a ripple.
                Regex("""fillMaxSize\(\)[\s\S]{0,400}?\.clickable\(([\s\S]{0,200}?)\)""")
                    .findAll(source)
                    .forEach { match ->
                        val arguments = match.groupValues[1]
                        val suppressed =
                            arguments.contains("indication = null") ||
                                arguments.contains("enabled = false")
                        if (!suppressed) offenders += "${path.fileName}: ${match.value.take(80)}"
                    }
            }

        assertTrue(
            offenders.isEmpty(),
            "a window-sized click target with a ripple greys the whole app:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun `the settings dismiss area still closes the panel`() {
        // Removing the indication must not remove the behaviour: pressing outside the panel is how
        // most people close it.
        val source = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/SettingsDialog.kt")
            .readText()

        assertTrue(source.contains("onClick = onDismiss"), "the dismiss action must survive")
        assertTrue(source.contains("indication = null"), "and the ripple must not")
    }
}
