package com.lucasserafin94.iptvburo.ui

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The banner plays a trailer on the television, and falls back to the poster when it cannot.
 *
 * A source scan, because the real thing needs an Android runtime, a WebView and a TMDb key. The
 * decision is covered by BannerTrailerTest in the shared model; what is pinned here is that the
 * pieces are connected — a lookup nobody calls and a failure nobody records both compile perfectly,
 * and the symptom would be a black rectangle on the opening screen.
 */
class BannerTrailerWiringTest {
    private val home =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/home/LivingHomeScreen.kt").readText()
    private val components =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/home/HomeComponents.kt").readText()
    private val viewModel =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/MainViewModel.kt").readText()
    private val activity =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/MainActivity.kt").readText()

    /** The banner asks for a trailer, and only for the title it has reached. */
    @Test
    fun `the banner looks up a trailer for the title on screen`() {
        assertTrue(
            "o banner nunca procura trailer nenhum",
            home.contains("onLoadHeroTrailer(heroItem.id, heroItem.title)"),
        )
        assertTrue(
            "nada liga a procura ao modelo",
            activity.contains("onLoadHeroTrailer = viewModel::loadHeroTrailer"),
        )
        // The rotation holds twenty and most are never seen; looking all twenty up would be twenty
        // requests for one viewing.
        assertTrue(
            "o mesmo titulo e procurado outra vez a cada volta da rotacao",
            viewModel.contains("if (mutableState.value.heroTrailers.containsKey(itemId)) return"),
        )
    }

    /** And it plays it behind the title. */
    @Test
    fun `the trailer is drawn in the banner`() {
        assertTrue(
            "nada toca o trailer no banner",
            components.contains("MutedTrailerBackdrop("),
        )
        assertTrue(
            "o banner nao recebe o trailer",
            home.contains("trailerId = heroTrailerFor(heroItem.id)"),
        )
    }

    /**
     * The decision comes from the shared rule.
     *
     * The screen would otherwise have its own opinion about a failed video, and the three apps
     * would drift.
     */
    @Test
    fun `the decision comes from the shared rule`() {
        assertTrue(
            "o banner decide sozinho se toca, em vez de usar a regra partilhada",
            viewModel.contains("BannerTrailer.shouldPlay("),
        )
        assertTrue(
            "nada liga a decisao ao ecra",
            activity.contains("onHeroTrailerFor = viewModel::heroTrailerFor"),
        )
    }

    /**
     * A failure is remembered and eventually forgotten.
     *
     * A video that was pulled stays pulled, so retrying every rotation costs a wait each time for
     * the same answer — but one that comes back is worth picking up without a reinstall.
     */
    @Test
    fun `a failure is remembered and expires`() {
        assertTrue(
            "uma falha do trailer nao e registada",
            viewModel.contains("fun rememberHeroTrailerFailure(itemId: String)"),
        )
        assertTrue(
            "as falhas guardadas nunca expiram",
            viewModel.contains("BannerTrailer.pruneFailures("),
        )
    }

    /**
     * The artwork stays underneath.
     *
     * MutedTrailerBackdrop removes itself when the embed fails rather than fading to nothing, so a
     * trailer that will not load leaves the poster exactly as it was — no black rectangle on the
     * opening screen.
     */
    @Test
    fun `the artwork is drawn under the trailer`() {
        val hero = components.substringAfter("fun BuroHero(").substringBefore("MutedTrailerBackdrop(")

        assertTrue("a capa nao fica por baixo do trailer", hero.contains("RemoteHomeArtwork("))
    }

    /** Nothing plays over the film the viewer chose. */
    @Test
    fun `nothing plays over the viewer's own playback`() {
        assertTrue(
            "o trailer pode tocar por cima do que a pessoa escolheu",
            viewModel.contains("somethingElseIsPlaying = state.content is AppContent.Player"),
        )
    }
}
