package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Reading two minutes ahead of the picture, for a film but never for a channel.
 *
 * A film that stops because the connection stumbled is the most visible failure this app has, and
 * it is avoidable: a film is a file, so the player can be minutes ahead and never notice the gap.
 * A live channel cannot be read ahead of the broadcast, so the same buffer there buys nothing and
 * costs a later start and a picture that sits behind.
 *
 * A source scan because the buffer is a VLC command-line argument fixed at process launch, which a
 * unit test cannot observe. What is worth pinning is that the value reaches it and that the two
 * kinds stay apart.
 */
class ReadAheadWiringTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val overlay =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt")
    private val request =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayback.kt")
    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
    private val player =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt")

    @Test
    fun `the player is told how far ahead to read`() {
        assertTrue(
            overlay.contains("networkCachingMillis = PlaybackBuffering.millisFor"),
            "o leitor nao recebe o tamanho do buffer",
        )
    }

    /** The player cannot infer this from an address, so the request has to carry it. */
    @Test
    fun `the request says whether the stream is live`() {
        assertTrue(request.contains("val isLive: Boolean"), "o pedido nao diz se e ao vivo")
        assertTrue(
            overlay.contains("isLive = request.isLive"),
            "a bandeira nunca chega ao leitor",
        )
    }

    /**
     * The default is false, so every live path has to say so explicitly — a channel wrongly
     * treated as a film would start two minutes late.
     */
    @Test
    fun `every live path marks itself as live`() {
        assertTrue(
            state.contains("target.contentType == XtreamContentType.LIVE"),
            "um canal Xtream nao e reconhecido como ao vivo",
        )
        assertTrue(
            state.contains("isLive = true"),
            "um canal de playlist M3U nao e reconhecido como ao vivo",
        )
    }

    /**
     * The guard was written when every stream was live and sixty seconds was plainly absurd. A
     * film's two-minute read-ahead has to fit inside it, or the player throws at construction.
     */
    @Test
    fun `the player accepts a film's read-ahead`() {
        assertTrue(
            player.contains("PlaybackBuffering.isWithinLimit(networkCachingMillis)"),
            "o limite do leitor nao acompanha o buffer de um filme",
        )
    }
}
