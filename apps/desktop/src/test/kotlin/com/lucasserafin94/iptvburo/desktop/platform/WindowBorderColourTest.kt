package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window frame matches the app, rather than the system accent.
 *
 * Windows paints a one-pixel border around the window in whichever colour the user chose for the
 * desktop. On a light accent that is a pale line along the bottom edge of an otherwise black app,
 * and it was reported exactly that way. Nothing inside the Compose scene can reach it — the frame
 * belongs to the window manager — so it has to be set through DWM.
 *
 * The value is a COLORREF, which is 0x00BBGGRR: blue and red are the reverse of the order they
 * appear in every hex colour elsewhere in the app. Getting that backwards produces a border that is
 * subtly the wrong colour rather than an error, which is why it is pinned here.
 */
class WindowBorderColourTest {
    private val source: String =
        Files.readString(
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/platform/WindowChrome.kt"),
        )

    /** Reads a constant whether it is written in decimal or hexadecimal. */
    private fun constant(name: String): Int {
        val match =
            Regex("""const val $name = (0x[0-9A-Fa-f]+|\d+)""").find(source)
                ?: error("$name is no longer declared; this test is guarding the wrong thing")
        val literal = match.groupValues[1]
        return if (literal.startsWith("0x")) {
            literal.removePrefix("0x").toLong(16).toInt()
        } else {
            literal.toInt()
        }
    }

    @Test
    fun `the border colour is the app's own canvas, byte-reversed for COLORREF`() {
        val canvas = BuroColors.Canvas
        val red = (canvas.red * 255).toInt()
        val green = (canvas.green * 255).toInt()
        val blue = (canvas.blue * 255).toInt()

        // COLORREF packs the channels in the opposite order to the usual RGB hex.
        val expected = (blue shl 16) or (green shl 8) or red

        assertEquals(
            expected,
            constant("BORDER_COLOUR"),
            "the border must be the canvas colour; a plain RGB value here swaps red and blue",
        )
    }

    /**
     * The attribute number itself.
     *
     * DWMWA_BORDER_COLOR is 34. A wrong number is silently ignored by Windows — the call returns an
     * error nobody reads and the border stays as it was — so there is no runtime signal at all.
     */
    @Test
    fun `the border attribute is the documented one`() {
        assertEquals(34, constant("ATTRIBUTE_BORDER_COLOUR"))
    }

    /**
     * The border is set wherever the dark title bar is.
     *
     * Both describe the same window frame, and setting one without the other leaves the app dark
     * with a light outline — which is the state that was reported.
     */
    @Test
    fun `the border is set alongside the dark title bar`() {
        val body = source.substringAfter("fun applyDarkTitleBar").substringBefore("\n    /**")

        assertTrue(
            "ATTRIBUTE_BORDER_COLOUR" in body,
            "applyDarkTitleBar darkens the caption but leaves the frame in the system accent",
        )
    }
}
