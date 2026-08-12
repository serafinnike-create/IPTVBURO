package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VlcHardwareDecodingTest {
    @Test
    fun `VLC decoder modes use the values accepted by the bundled engine`() {
        assertEquals("--avcodec-hw=none", VlcHardwareDecoding.DISABLED.vlcArgument)
        assertEquals("--avcodec-hw=any", VlcHardwareDecoding.AUTOMATIC.vlcArgument)
    }

    @Test
    fun `multiview opts into automatic hardware decoding`() {
        val surface =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/MultiviewSurface.kt")
                .readText()

        assertTrue(
            surface.contains("hardwareDecoding = VlcHardwareDecoding.AUTOMATIC"),
            "multiview must not run up to four VLC processes with forced software decoding",
        )
    }
}
