package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class VlcLiveRetryWiringTest {
    private val source =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt")
            .readText()

    @Test
    fun `the request is retained before automatic polling can retry it`() {
        val startBody = source.substringAfter("private fun startIfNeeded").substringBefore("private fun startVlc")

        assertTrue(
            startBody.contains("lastRequest = request"),
            "the retry loop consumes attempts but cannot reopen a stream when lastRequest is null",
        )
    }
}
