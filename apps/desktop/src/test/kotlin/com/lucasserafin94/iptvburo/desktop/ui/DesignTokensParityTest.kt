package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.ui.graphics.Color
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Keeps `BuroColors` honest about its claim to mirror `packages/design-tokens/tokens.json`.
 *
 * The JSON is the canonical colour contract — it matches the GDD
 * (`docs/gdd-v2/02-cinematic-design-system.md`) — and the theme header says it mirrors the file,
 * but nothing checked that, and the two drifted apart silently: the accent and status colours
 * still agree, while the neutrals (canvas, surfaces, text) no longer do. The same drift exists in
 * the Android tokens, so neither platform is the odd one out; the contract and both themes simply
 * parted ways during the 0.2 visual rebuild.
 *
 * Realigning them is a product decision — it changes the shipped palette on two platforms, or it
 * changes the GDD's canonical values — so it is not made here. What this test does instead:
 *
 * 1. pins the colours that *do* agree, so they cannot drift next;
 * 2. pins the *set* of drifted names, so the drift cannot grow unnoticed and any realignment
 *    forces a deliberate edit here — the same "the pending list only shrinks" pattern as
 *    `NoHardcodedTextTest`.
 *
 * Motion and focus tokens never drifted and are pinned in full. Shape tokens are out of scope on
 * purpose: the desktop radius scale (14/20 dp medium/large) differs from the contract (16/24) as
 * a deliberate per-platform choice that the Android shapes do follow.
 */
class DesignTokensParityTest {
    private val tokens: String = Path.of("../../packages/design-tokens/tokens.json").readText()

    /** Every colour the contract declares, mapped to the theme property that claims to mirror it. */
    private val colourMapping: Map<String, Color> =
        mapOf(
            "canvas" to BuroColors.Canvas,
            "surface" to BuroColors.Surface,
            "elevated" to BuroColors.SurfaceRaised,
            "surfaceHover" to BuroColors.SurfaceHover,
            "textPrimary" to BuroColors.Text,
            "textSecondary" to BuroColors.TextMuted,
            "textMuted" to BuroColors.TextSubtle,
            "brandPrimary" to BuroColors.Primary,
            "brandSecondary" to BuroColors.Accent,
            "success" to BuroColors.Success,
            "warning" to BuroColors.Warning,
            "error" to BuroColors.Error,
        )

    /**
     * The colours where theme and contract still agree. Accent and status are the identity of the
     * palette — the gold, the ivory, the three statuses — so their parity is the part that must
     * never be lost.
     */
    private val inParity = setOf("brandPrimary", "brandSecondary", "success", "warning", "error")

    /**
     * The neutrals that drifted from the GDD contract during the 0.2 rebuild, on desktop and
     * Android alike. A name leaving this set means someone aligned a colour — update the test and
     * say which side moved. A name joining it means new drift: fail.
     */
    private val knownDrift =
        setOf("canvas", "surface", "elevated", "surfaceHover", "textPrimary", "textSecondary", "textMuted")

    private fun tokenColour(name: String): Color {
        val hex =
            Regex(""""$name"\s*:\s*"#([0-9A-Fa-f]{6})"""")
                .find(tokens)
                ?.groupValues?.get(1)
                ?: error("colour token '$name' is missing from tokens.json")
        return Color(("FF$hex").toLong(16))
    }

    private fun tokenNumber(section: String, name: String): Double {
        val block =
            Regex(""""$section"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
                .find(tokens)
                ?.groupValues?.get(1)
                ?: error("section '$section' is missing from tokens.json")
        return Regex(""""$name"\s*:\s*([0-9.]+)""")
            .find(block)
            ?.groupValues
            ?.get(1)
            ?.toDouble()
            ?: error("token '$section.$name' is missing from tokens.json")
    }

    @Test
    fun `the contract declares exactly the colours the theme mirrors`() {
        val block =
            Regex(""""color"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
                .find(tokens)
                ?.groupValues
                ?.get(1)
                ?: error("the color section is missing from tokens.json")
        val declared = Regex(""""(\w+)"\s*:""").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(
            colourMapping.keys,
            declared,
            "tokens.json declares a colour the theme does not mirror, or lost one it did",
        )
    }

    @Test
    fun `accent and status colours match the contract exactly`() {
        inParity.forEach { name ->
            assertEquals(
                tokenColour(name),
                colourMapping.getValue(name),
                "colour token '$name' drifted from the canonical contract",
            )
        }
    }

    @Test
    fun `the known neutral drift does not grow`() {
        val drifted =
            colourMapping.keys
                .filter { name -> tokenColour(name) != colourMapping.getValue(name) }
                .toSet()
        assertEquals(
            knownDrift,
            drifted,
            "The set of colours drifting from tokens.json changed. Realigning one is progress — " +
                "shrink knownDrift and say which side moved. A new entry is new drift: fix the " +
                "colour, not this test.",
        )
    }

    @Test
    fun `motion and focus tokens match the contract`() {
        assertEquals(BuroMotion.MicroMillis.toDouble(), tokenNumber("motionMs", "microFast"))
        assertEquals(BuroMotion.FocusMillis.toDouble(), tokenNumber("motionMs", "focus"))
        assertEquals(BuroMotion.NavigationMillis.toDouble(), tokenNumber("motionMs", "navigation"))
        assertEquals(BuroMotion.CinematicMillis.toDouble(), tokenNumber("motionMs", "cinematic"))
        // Compared as Float on both sides: 1.045f widened to Double is not 1.045.
        assertEquals(BuroInteraction.CardHoverScale, tokenNumber("focus", "cardScale").toFloat())
        assertEquals(BuroInteraction.CompactHoverScale, tokenNumber("focus", "compactScale").toFloat())
        assertEquals(BuroInteraction.RingWidth.value, tokenNumber("focus", "ringWidthDp").toFloat())
    }
}
