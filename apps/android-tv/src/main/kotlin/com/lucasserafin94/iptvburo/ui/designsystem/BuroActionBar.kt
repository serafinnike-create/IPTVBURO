package com.lucasserafin94.iptvburo.ui.designsystem

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasserafin94.iptvburo.ui.components.FocusSurface

/**
 * One action in a [BuroActionBar].
 *
 * [label] is both the caption under the glyph and the accessibility name, so the two can never
 * disagree — a screen reader announcing "button" over an icon whose caption says "Favoritar" is
 * exactly the drift that separate fields invite.
 *
 * [active] is for actions that are a state rather than a one-off: Favoritar and Lembrete are on or
 * off, and the bar tints them so that is legible without opening anything.
 */
data class BuroAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val active: Boolean = false,
    /** The tint when [active]. Defaults to the brand accent. */
    val activeTint: Color? = null,
)

/**
 * The secondary actions of a details page, as one compact row of glyphs.
 *
 * These were six pill buttons, each carrying its own word, wrapped in a FlowRow. On a phone in
 * portrait that filled three lines before the synopsis began — the page opened on a wall of
 * controls rather than on the film. Every one of them is a small, instant action that a person
 * takes rarely, which is the shape an icon suits and a labelled pill does not.
 *
 * What the pills got right is kept:
 *
 * - **nothing is hidden.** The obvious way to save space is a "⋮" menu, which would put Compartilhar
 *   and Trailer behind a press that gives no clue they are there. Every action stays visible.
 * - **the row never reflows.** A caption is measured against a fixed width rather than its own text,
 *   so Favoritar → Favoritado does not resize its slot and shove the rest sideways under the finger
 *   that pressed it — the failure that made these buttons "behave differently on every film".
 * - **disabled rather than absent.** An action the title cannot offer keeps its place, greyed. A
 *   slot that vanishes moves everything after it, so the bar would be laid out differently per film.
 *
 * The primary action — Assistir — deliberately does not belong here. It stays a full labelled
 * button: it is what the page is for, and demoting it to a glyph among six others would hide the
 * one control most people came to press.
 */
@Composable
fun BuroActionBar(
    actions: List<BuroAction>,
    modifier: Modifier = Modifier,
) {
    val colors = BuroTheme.colors
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // The slot width is divided out of the space there actually is, rather than fixed.
        //
        // Fixed, it did not fit: six 58dp slots and their gaps come to 368dp, and a 720x1640 phone
        // at density 2 is 360dp wide before the page's own padding — so the last action, Enviar à
        // tela, was clipped at the right edge. A row of actions that runs off the screen is the
        // same bug the labelled pills had, in a new shape.
        //
        // Floored, so the glyphs stay tappable on a very narrow screen: below this the row scrolls
        // instead of shrinking further, which keeps every action reachable rather than making them
        // all too small to hit.
        val slotWidth =
            maxOf(
                MIN_SLOT_WIDTH,
                minOf(
                    MAX_SLOT_WIDTH,
                    (maxWidth - SLOT_GAP * (actions.size - 1)) / actions.size,
                ),
            )
        Row(
            // Scrolls only when even the floor does not fit, which on an ordinary phone it does:
            // the row is sized to the screen above, so this is the last resort and not the norm.
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SLOT_GAP),
            verticalAlignment = Alignment.Top,
        ) {
        actions.forEach { action ->
            val tint =
                when {
                    !action.enabled -> colors.textMuted
                    action.active -> action.activeTint ?: colors.brandSecondary
                    else -> colors.textPrimary
                }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // Fixed, so a caption cannot set the column's width: "Compartilhar" is twice the
                // width of "Trailer", and letting each slot size itself would space the glyphs
                // unevenly and move them as a label changed.
                modifier = Modifier.width(slotWidth),
            ) {
                FocusSurface(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.size(GLYPH_SIZE),
                    shape = BuroShapes.Pill,
                    // Transparent at rest, like the Ghost button these replace, so the row reads as
                    // part of the page rather than as six competing chips. The focus and press
                    // states come from FocusSurface, which is what makes them reachable by D-pad.
                    backgroundColor = Color.Transparent,
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = action.icon,
                        // The label lives on the icon rather than on the caption below, because the
                        // caption sits outside this FocusSurface: the button merges the semantics of
                        // its own descendants, so a null here would leave the control announcing
                        // "button" and nothing else. The caption is decoration for people who can
                        // see it; this is the name for everyone else.
                        contentDescription = action.label,
                        tint = tint,
                        modifier = Modifier.size(GLYPH_ICON_SIZE),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier =
                        Modifier
                            .widthIn(max = slotWidth)
                            // Hidden from accessibility: the icon above already carries this word
                            // as its name, and leaving the caption readable would have a screen
                            // reader announce every action twice.
                            .clearAndSetSemantics { },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = action.label,
                        color = if (action.enabled) colors.textSecondary else colors.textMuted,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Center,
                        // Two lines, so "Compartilhar" and "Enviar à tela" are readable in full at
                        // this width rather than being cut to "Compart…". The bar reserves the
                        // height either way, so a one-line caption does not shift its neighbours up.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        }
    }
}

private val SLOT_GAP = 4.dp

/** Wide enough for "Compartilhar" over two lines at 10sp; the ceiling on a roomy screen. */
private val MAX_SLOT_WIDTH = 58.dp

/**
 * The narrowest a slot may become before the row scrolls instead.
 *
 * Holds the glyph and its 44dp touch target with a little air. Shrinking past this to fit one more
 * action would trade a row that runs off the edge for one nobody can hit accurately.
 */
private val MIN_SLOT_WIDTH = 46.dp
private val GLYPH_SIZE = 44.dp
private val GLYPH_ICON_SIZE = 22.dp
