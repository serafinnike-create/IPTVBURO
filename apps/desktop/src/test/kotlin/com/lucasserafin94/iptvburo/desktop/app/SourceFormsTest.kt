package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The two ways a subscription gets added, both reported broken on the same day.
 *
 * A source scan because these are Compose forms whose state lives in a composition, and what is
 * worth pinning is that the fields exist and that the button explains itself — the two things that
 * were missing.
 */
class SourceFormsTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val login =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamLoginDialog.kt")
    private val onboarding =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/OnboardingFlow.kt")
    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")

    /**
     * The connect form asked for the address, the user and the password and never for a name, so
     * every subscription added that way was labelled after its host.
     */
    @Test
    fun `the connect form asks what to call the list`() {
        assertTrue(login.contains("listLabel"), "sem campo de nome")
        assertTrue(login.contains("setupListName"), "o campo nao usa o rotulo traduzido")
    }

    /** A name nobody records is a field that does nothing. */
    @Test
    fun `the name reaches the library`() {
        assertTrue(
            login.contains("onConnect(form.consume(), listLabel.trim())"),
            "o nome nao sai do formulario",
        )
        assertTrue(
            state.contains("sourceLibrary.create(") && state.contains("listLabel.ifBlank"),
            "o nome nao chega a biblioteca de fontes",
        )
    }

    /** Blank must keep the old behaviour rather than producing a nameless row. */
    @Test
    fun `a blank name falls back to the host`() {
        assertTrue(state.contains("hostLabel()"), "sem recuo para o host")
    }

    /**
     * The second report: Continuar did nothing and said nothing.
     *
     * The one empty field was the profile name, off the top of a scrolling form. A disabled button
     * with no reason is indistinguishable from a broken one.
     */
    @Test
    fun `the continue button says why it is disabled`() {
        assertTrue(onboarding.contains("setupMissingProfileName"), "nao nomeia o perfil em falta")
        assertTrue(onboarding.contains("setupMissingConnection"), "nao nomeia a ligacao em falta")
        assertTrue(onboarding.contains("val canSubmit = missing == null"), "a razao nao decide o botao")
    }

    /** The reason has to be drawn, not merely computed. */
    @Test
    fun `the reason is shown on screen`() {
        assertTrue(onboarding.contains("missing?.let { reason ->"), "a razao nunca e desenhada")
    }

    /**
     * The switch that turns several lists into one catalogue.
     *
     * Only with more than one list: with a single one there is nothing to merge, and the switch
     * would be a question about nothing.
     */
    @Test
    fun `the merge switch appears only when there is more than one list`() {
        assertTrue(onboarding.contains("savedSources.size > 1"), "sem a condicao das duas listas")
        assertTrue(
            onboarding.contains("mergeSourcesTitle"),
            "o interruptor nao usa o rotulo traduzido",
        )
    }

    /** A switch nobody records is a switch that does nothing. */
    @Test
    fun `the merge choice is stored`() {
        assertTrue(state.contains("userStore.setMergeAllSources"), "a escolha nao e guardada")
        assertTrue(state.contains("userStore.mergeAllSources()"), "a escolha nao e lida de volta")
    }

    /** Choosing a saved list must still skip the connection fields. */
    @Test
    fun `a saved list needs no server, user or password`() {
        assertTrue(
            onboarding.contains("reusedSourceId != null -> null"),
            "uma lista ja configurada voltou a exigir credenciais",
        )
    }
}
