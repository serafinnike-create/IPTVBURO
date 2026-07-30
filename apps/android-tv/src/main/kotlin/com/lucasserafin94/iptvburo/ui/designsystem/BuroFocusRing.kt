package com.lucasserafin94.iptvburo.ui.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * A visual-only ring. It never becomes a second focus target and selected state remains distinct
 * from active D-pad focus.
 */
@Composable
fun BuroFocusRing(
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = BuroShapes.Medium,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = BuroTheme.colors
    val preferences = BuroTheme.preferences

    Box(
        modifier = modifier.clip(shape),
        contentAlignment = contentAlignment,
    ) {
        content()
        if (isFocused || selected) {
            val width =
                when {
                    !enabled -> BuroFocusTokens.DisabledRingWidth
                    isFocused && preferences.highContrast ->
                        BuroFocusTokens.HighContrastRingWidth

                    isFocused -> BuroFocusTokens.RingWidth
                    else -> BuroFocusTokens.SelectedRingWidth
                }
            val color =
                when {
                    !enabled -> colors.textMuted
                    isFocused -> colors.focus
                    else -> colors.brandPrimary
                }
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .border(width = width, color = color, shape = shape)
                        .clearAndSetSemantics { },
            )
        }
    }
}
