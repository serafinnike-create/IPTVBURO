package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HeroSynopsisIdentityTest {
    private val stateSource =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    @Test
    fun `film and series with the same provider id have different synopsis keys`() {
        assertNotEquals(
            heroSynopsisKey(XtreamContentType.MOVIE, "1"),
            heroSynopsisKey(XtreamContentType.SERIES, "1"),
        )
    }

    @Test
    fun `an old catalogue request cannot repopulate the synopsis cache`() {
        val loader = stateSource.substringAfter("fun loadHeroSynopsis").substringBefore("var streamingKind")
        assertTrue(loader.contains("requestGeneration != dailyHomeRequestGeneration"))

        val clear = stateSource.substringAfter("private fun clearXtreamUiState").take(1_500)
        assertTrue(clear.contains("heroSynopsis = emptyMap()"))
    }
}
