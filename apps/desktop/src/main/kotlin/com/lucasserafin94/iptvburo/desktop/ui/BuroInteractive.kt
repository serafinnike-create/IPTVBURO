package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * State of a pointer/keyboard-driven surface, resolved from a single [MutableInteractionSource].
 *
 * Reading all three signals from one source keeps hover, focus and press mutually consistent — a
 * card cannot end up drawn as hovered while the ring says it is not focused.
 */
data class BuroInteractionState(
    val hovered: Boolean,
    val focused: Boolean,
    val pressed: Boolean,
) {
    val active: Boolean get() = hovered || focused
}

@Composable
fun rememberBuroInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }

@Composable
fun MutableInteractionSource.buroState(): BuroInteractionState =
    BuroInteractionState(
        hovered = collectIsHoveredAsState().value,
        focused = collectIsFocusedAsState().value,
        pressed = collectIsPressedAsState().value,
    )

/**
 * Clickable surface with the focus physics described in the GDD: a short scale-up, a luminous
 * border and a lift, all driven by hover **or** keyboard focus so mouse and keyboard users get the
 * same affordance.
 *
 * [compact] selects the larger travel used for small cards, where 1.045 is too subtle to notice.
 */
@Composable
fun BuroInteractiveSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = BuroRadius.Medium,
    compact: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    background: Color = Color.Transparent,
    activeBackground: Color = background,
    ringColor: Color = BuroColors.Focus,
    content: @Composable (BuroInteractionState) -> Unit,
) {
    val interactionSource = rememberBuroInteractionSource()
    val state = interactionSource.buroState()

    val targetScale =
        when {
            !enabled -> BuroInteraction.RestScale
            state.pressed -> BuroInteraction.PressedScale
            state.active ->
                if (compact) BuroInteraction.CompactHoverScale else BuroInteraction.CardHoverScale
            else -> BuroInteraction.RestScale
        }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec =
            tween(
                durationMillis = BuroMotion.FocusMillis,
                easing = if (state.active) BuroMotion.EnterEasing else BuroMotion.ExitEasing,
            ),
        label = "buro-surface-scale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (state.focused) 1f else if (state.hovered) 0.55f else 0f,
        animationSpec = tween(BuroMotion.FocusMillis),
        label = "buro-surface-ring",
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (state.active) activeBackground else background,
        animationSpec = tween(BuroMotion.FocusMillis),
        label = "buro-surface-color",
    )

    Box(
        modifier =
            modifier
                .scale(scale)
                .clip(shape)
                .background(surfaceColor)
                .border(BuroInteraction.RingWidth, ringColor.copy(alpha = ringAlpha), shape)
                .hoverable(interactionSource, enabled = enabled)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription?.let { this.contentDescription = it }
                },
    ) {
        content(state)
    }
}

/**
 * Row-style surface for lists (sidebar entries, categories, channels).
 *
 * Lists must not scale — moving every row under the pointer makes long lists feel unstable — so
 * this variant expresses state purely through background and a leading accent bar.
 */
@Composable
fun BuroInteractiveRow(
    onClick: () -> Unit,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentDescription: String? = null,
    content: @Composable (BuroInteractionState) -> Unit,
) {
    val interactionSource = rememberBuroInteractionSource()
    val state = interactionSource.buroState()

    val target =
        when {
            selected -> BuroColors.SurfaceHover
            state.active -> BuroColors.SurfaceRaised
            else -> Color.Transparent
        }
    val surfaceColor by animateColorAsState(
        targetValue = target,
        animationSpec = tween(BuroMotion.MicroMillis),
        label = "buro-row-color",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (state.focused) 0.85f else 0f,
        animationSpec = tween(BuroMotion.FocusMillis),
        label = "buro-row-ring",
    )

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(surfaceColor)
                .border(1.dp, BuroColors.Focus.copy(alpha = ringAlpha), shape)
                .hoverable(interactionSource, enabled = enabled)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    role = Role.Button
                    this.selected = selected
                    contentDescription?.let { this.contentDescription = it }
                },
    ) {
        content(state)
    }
}
