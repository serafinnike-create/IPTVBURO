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
            home.contains("onLoadHeroTrailer(heroItem.id, heroItem.title,"),
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

    /**
     * The embed always loads muted, whatever the sound preference says.
     *
     * No engine autoplays audio. Asked for sound up front the trailer does not start at all and
     * leaves a play button over a still frame — seen exactly that way on the Windows banner, which
     * used the same embed. So mute=1 here is not a preference, it is the only way it plays.
     */
    @Test
    fun `the embed loads muted so it can start`() {
        val backdrop =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/MutedTrailerBackdrop.kt")
                .readText()

        assertTrue("o embed pede audio ao arrancar e o motor bloqueia-o", backdrop.contains("mute=1"))
    }

    /**
     * A series is searched as a series.
     *
     * TMDb keeps television and film in separate catalogues, so a series searched as a film finds
     * nothing at all. The lookup passed `year = null` and no kind, which meant every series on this
     * app got the film catalogue and therefore no trailer — the same defect Windows had, where no
     * series trailer ever played until it was fixed.
     */
    @Test
    fun `a series trailer is searched in the television catalogue`() {
        assertTrue(
            "a procura ignora o tipo, e uma serie procurada nos filmes nao encontra nada",
            viewModel.contains("client.findTrailer(title = title, year = year, isSeries = isSeries)"),
        )
        val shell =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/AppShellScreen.kt")
                .readText()
        assertTrue(
            "o cartao do Descobrir sabe o tipo e nao o envia",
            shell.contains("channel.contentType == CatalogContentType.SERIES"),
        )
    }

    /**
     * The Descobrir card plays the same trailer the banner would.
     *
     * Two lookups would mean two answers for the same film — a trailer on the home screen and none
     * on the card, or the other way round, for no reason a viewer could see. And only the card on
     * top gets one: the card behind it is a sliver giving the pile depth, and a video playing where
     * nobody can see it is a wasted player.
     */
    @Test
    fun `the discover card plays the banner's trailer`() {
        val shell =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/AppShellScreen.kt")
                .readText()
        val discover =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/DiscoverScreen.kt")
                .readText()

        assertTrue(
            "o cartao do Descobrir usa uma procura diferente da do banner",
            shell.contains("trailerFor = { channel -> onHeroTrailerFor(channel.id) }"),
        )
        assertTrue(
            "nada toca o trailer no cartao do Descobrir",
            discover.contains("MutedTrailerBackdrop("),
        )
        assertTrue(
            "o trailer nao chega ao cartao de cima",
            discover.contains("trailerId = trailerFor(deck.first())"),
        )
    }

    /**
     * The sound switch reaches the banner, and only while there is a trailer to hear.
     *
     * A control for a sound that cannot happen is a button that does nothing when pressed, and on a
     * TV it would also be one more stop on the D-pad path to the only action that matters.
     */
    @Test
    fun `the sound switch is offered only while a trailer plays`() {
        assertTrue(
            "o botao de som nao esta no banner",
            components.contains("onToggleTrailerSound"),
        )
        assertTrue(
            "o botao aparece mesmo sem trailer para ouvir",
            components.contains("if (trailerId != null && onToggleTrailerSound != null)"),
        )
        assertTrue(
            "o interruptor nao chega ao ecra a partir da app",
            activity.contains("onToggleBannerTrailerSound = viewModel::toggleBannerTrailerSound"),
        )
    }

    /**
     * The choice is remembered, so nobody has to make it twice.
     *
     * Household-wide rather than per profile, matching Windows: whether the room wants noise from
     * the opening screen is a property of the room.
     */
    @Test
    fun `the sound choice survives the app closing`() {
        val preferences =
            Path.of(
                "src/main/kotlin/com/lucasserafin94/iptvburo/data/preferences/BannerSoundPreferences.kt",
            ).readText()

        assertTrue(
            "a escolha do som nao e guardada em lado nenhum",
            preferences.contains("booleanPreferencesKey(\"banner_trailer_sound\")"),
        )
        assertTrue(
            "o som comeca ligado, e a TV passa a falar sozinha ao ligar",
            preferences.contains("stored[SOUND_ON] ?: false"),
        )
    }

    /**
     * Turning the sound on answers the trailer already playing, not only the next one.
     *
     * The sound is otherwise raised from the playing callback, which for the trailer currently on
     * screen has long since passed — so without this the switch would appear to do nothing until
     * the banner rotated. Silencing matters more still: somebody reaching for it means now.
     */
    @Test
    fun `the switch answers the trailer already on screen`() {
        val backdrop =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/MutedTrailerBackdrop.kt")
                .readText()

        assertTrue(
            "nada silencia um trailer que ja esta a tocar",
            backdrop.contains("TRAILER_SILENCE"),
        )
        assertTrue(
            "mudar o interruptor nao mexe no trailer que ja esta no ecra",
            backdrop.contains("LaunchedEffect(soundOn, pageReady)"),
        )
    }

    /**
     * And the sound is raised only from the playing callback.
     *
     * Anywhere earlier is a request the engine can still refuse; by the time the player reports
     * PLAYING the autoplay has been granted and the volume is no longer a new ask.
     */
    @Test
    fun `the sound is raised only once it is playing`() {
        val backdrop =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/MutedTrailerBackdrop.kt")
                .readText()

        assertTrue("nada levanta o som depois do arranque", backdrop.contains("TRAILER_RAISE_SOUND"))

        val marker = "fun onPlaying() {"
        assertTrue("onPlaying mudou: este teste ja nao le nada", backdrop.contains(marker))
        val playing = backdrop.substringAfter(marker).substringBefore("\n                        }")
        assertTrue(
            "o som nao e levantado no momento em que o video comeca a tocar",
            playing.contains("TRAILER_RAISE_SOUND"),
        )
    }
}
