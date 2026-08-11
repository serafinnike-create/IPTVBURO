package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiviewStartupTest {
    @Test
    fun `four provider requests are staggered rather than sent as one burst`() {
        assertEquals(0L, multiviewStartupDelay(0))
        assertEquals(750L, multiviewStartupDelay(1))
        assertEquals(1_500L, multiviewStartupDelay(2))
        assertEquals(2_250L, multiviewStartupDelay(3))
    }

    @Test
    fun `automatic retry clears the failed VLC input before reopening it`() {
        val source =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt")
                .readText()
        val retry = source.substringAfter("recoverable &&").substringBefore("snapshot =")

        assertTrue(retry.contains("control.command(\"pl_stop\")"))
        assertTrue(retry.contains("control.command(\"pl_empty\")"))
        assertTrue(retry.contains("control.command(\"in_play\""))
    }
}
