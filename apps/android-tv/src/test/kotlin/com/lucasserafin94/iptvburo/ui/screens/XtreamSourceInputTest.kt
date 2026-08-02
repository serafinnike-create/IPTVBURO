package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamSourceInputTest {
    @Test
    fun `copied endpoint drops query credentials immediately`() {
        val sanitized =
            sanitizeXtreamServerField(
                "http://media.example.test/panel/get.php?username=private&password=private",
            )

        assertEquals("http://media.example.test/panel", sanitized)
    }

    @Test
    fun `partial manual input is preserved`() {
        assertEquals(
            "media.example.test:8080",
            sanitizeXtreamServerField("media.example.test:8080"),
        )
    }
}
