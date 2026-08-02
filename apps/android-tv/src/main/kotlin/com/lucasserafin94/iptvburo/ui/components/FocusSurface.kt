package com.lucasserafin94.iptvburo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.lucasserafin94.iptvburo.ui.designsystem.BuroFocusRing
import com.lucasserafin94.iptvburo.ui.designsystem.BuroMotionTokens
import com.lucasserafin94.iptvburo.ui.designsystem.BuroOpacity
import com.lucasserafin94.iptvburo.ui.designsystem.BuroShapes
import com.lucasserafin94.iptvburo.ui.designsystem.BuroTheme
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised

@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = BuroSurface,
    selected: Boolean = false,
    focusedBackgroundColor: Color = BuroSurfaceRaised,
    selectedBackgroundColor: Color = BuroSurfaceRaised,
    shape: Shape = BuroShapes.Medium,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = BuroTheme.motion
    val isVisuallyFocused = focused && enabled
    val targetScale =
        when {
            !motion.allowsFocusZoom -> 1f
            pressed && enabled -> motion.pressedScale
            isVisuallyFocused -> motion.focusScale
            else -> 1f
        }
    val scale by
        animateFloatAsState(
            targetValue = targetScale,
            animationSpec =
                tween(
                    durationMillis = motion.focusDurationMillis,
                    easing =
                        if (isVisuallyFocused) {
                            BuroMotionTokens.EnterEasing
                        } else {
                            BuroMotionTokens.ExitEasing
                        },
                ),
            label = "buroFocusScale",
        )
    val containerColor =
        when {
            pressed && enabled -> focusedBackgroundColor
            isVisuallyFocused -> focusedBackgroundColor
            selected -> selectedBackgroundColor
            else -> backgroundColor
        }

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else BuroOpacity.Disabled
                }
                .background(containerColor, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .focusable(enabled)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    this.selected = selected
                    if (!enabled) disabled()
                },
        propagateMinConstraints = true,
    ) {
        BuroFocusRing(
            isFocused = isVisuallyFocused,
            selected = selected,
            enabled = enabled,
            shape = shape,
            contentAlignment = contentAlignment,
        ) {
            content(isVisuallyFocused)
        }
    }
}
