package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The banner moves to the next title when its trailer actually ends, not on a fixed guess.
 *
 * The rotation used to hold a playing trailer for a flat sixty seconds regardless of the video's
 * real length — cutting a long trailer off mid-scene, and sitting on a short one's last frame for
 * whatever was left of the guess. YouTube's own "ended" state (0) fires once, but only for a video
 * that is allowed to end: the banner used to loop its trailer like the Descobrir card, and a
 * looping player never reports 0 at all.
 *
 * A source scan, because the real check needs a window, Chromium and a rotation of more than one
 * title. What it pins is that the banner disables the loop and reacts to the real end.
 */
class BannerTrailerEndedTest {
    private val home =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamDailyHome.kt").readText()

    @Test
    fun `the banner plays its trailer once instead of looping`() {
        val marker = "HeroTrailer(\n                    youtubeId = activeTrailerId,"
        assertTrue(marker in home, "a chamada do trailer do banner mudou: este teste ja nao le nada")

        val call = home.substringAfter(marker).substringBefore("\n            }\n        }")

        assertTrue(
            "loop = false" in call,
            "o banner volta a repetir o trailer, e o sinal de fim do YouTube nunca dispara",
        )
        assertTrue(
            "onEnded = onTrailerEnded" in call,
            "o banner deixou de ouvir o fim real do trailer",
        )
    }

    @Test
    fun `reaching the real end of the trailer advances the rotation`() {
        val marker = "onTrailerEnded = {"
        assertTrue(marker in home, "o callback de fim do trailer mudou de nome: este teste ja nao le nada")

        val body = home.substringAfter(marker).substringBefore("},")

        assertTrue(
            "heroIndex = (heroIndex + 1) % rotation.size" in body,
            "o fim do trailer ja nao avanca para o proximo titulo do banner",
        )
    }
}
