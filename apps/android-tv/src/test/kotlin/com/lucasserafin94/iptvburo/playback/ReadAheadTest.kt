package com.lucasserafin94.iptvburo.playback

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading two minutes ahead of the picture, for a film but never for a channel.
 *
 * A film that stops because the connection stumbled is avoidable: a film is a file, so the player
 * can be minutes ahead and never notice the gap. A live channel cannot be read ahead of the
 * broadcast, so the same buffer buys nothing there and costs a later start.
 *
 * A source scan because the buffer is decided when ExoPlayer is built, and building one needs an
 * Android runtime. What is worth pinning is that the value reaches the player and that UNKNOWN
 * stays on the cautious side.
 */
class ReadAheadTest {
    private val factory =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/playback/PlaybackSessionFactory.kt")
            .readText()

    @Test
    fun `the player is given a load control sized for the content`() {
        assertTrue(
            "o leitor nao recebe o tamanho do buffer",
            factory.contains(".setLoadControl(loadControlFor(channel))"),
        )
        assertTrue(
            "o tamanho nao vem da regra partilhada",
            factory.contains("PlaybackBuffering.millisFor(isLive ="),
        )
    }

    /**
     * UNKNOWN counts as live, which is the safe way round: a film treated as live keeps the
     * smaller buffer it has today, while a channel treated as a film would start two minutes late.
     */
    @Test
    fun `an unknown content type is treated as live`() {
        assertTrue(
            "um tipo desconhecido arriscaria dois minutos de espera",
            factory.contains("contentType == CatalogContentType.UNKNOWN"),
        )
    }

    /**
     * Only the maximum moves. The other three decide how quickly playback begins, and raising them
     * would delay the first frame by the whole read-ahead — the opposite of the point.
     */
    @Test
    fun `only the maximum buffer is changed`() {
        listOf(
            "DEFAULT_MIN_BUFFER_MS",
            "DEFAULT_BUFFER_FOR_PLAYBACK_MS",
            "DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS",
        ).forEach { untouched ->
            assertTrue("$untouched deixou de usar o valor do Media3", factory.contains(untouched))
        }
    }
}
