package com.lucasserafin94.iptvburo.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.ui.components.FocusSurface

enum class BuroButtonStyle {
    Primary,
    Secondary,
    Ghost,
    Danger,
}

@Composable
fun BuroScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues =
        PaddingValues(
            horizontal = BuroSpacing.ScreenHorizontal,
            vertical = BuroSpacing.ScreenVertical,
        ),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = BuroTheme.colors
    val preferences = BuroTheme.preferences
    val tier = BuroTheme.performanceTier
    val hasAmbientLayer =
        tier != BuroPerformanceTier.Eco &&
            !preferences.highContrast &&
            !preferences.reducedMotion

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.canvas),
    ) {
        if (hasAmbientLayer) {
            val ambientColors =
                if (preferences.reducedTransparency) {
                    listOf(colors.surface, colors.canvas)
                } else {
                    listOf(
                        colors.brandPrimary.copy(alpha = BuroOpacity.AmbientBrand),
                        colors.surface.copy(alpha = BuroOpacity.AmbientSurface),
                        colors.canvas,
                    )
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(ambientColors)),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun BuroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: BuroButtonStyle = BuroButtonStyle.Primary,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = BuroTheme.colors
    val preferences = BuroTheme.preferences
    val containerColor =
        when (style) {
            BuroButtonStyle.Primary -> colors.brandPrimary
            BuroButtonStyle.Secondary -> colors.surface
            BuroButtonStyle.Ghost ->
                if (preferences.reducedTransparency) {
                    colors.surface
                } else {
                    Color.Transparent
                }

            BuroButtonStyle.Danger -> colors.error
        }
    val focusedContainerColor =
        when (style) {
            BuroButtonStyle.Primary -> colors.brandPrimary
            BuroButtonStyle.Secondary -> colors.elevated
            BuroButtonStyle.Ghost -> colors.elevated
            BuroButtonStyle.Danger -> colors.error
        }
    val contentColor =
        when (style) {
            BuroButtonStyle.Primary,
            BuroButtonStyle.Danger,
            -> colors.onBrand

            BuroButtonStyle.Secondary,
            BuroButtonStyle.Ghost,
            -> colors.textPrimary
        }

    FocusSurface(
        onClick = onClick,
        modifier =
            modifier.defaultMinSize(
                minHeight = BuroComponentSizes.ButtonMinHeight,
            ),
        enabled = enabled,
        selected = selected,
        backgroundColor = containerColor,
        focusedBackgroundColor = focusedContainerColor,
        selectedBackgroundColor = focusedContainerColor,
        shape = BuroShapes.Pill,
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (enabled) {
                    contentColor
                } else {
                    colors.textMuted
                },
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = BuroSpacing.Md,
                        vertical = BuroSpacing.Xs,
                    ),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun BuroIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = BuroTheme.colors
    val preferences = BuroTheme.preferences
    val selectedContainerColor =
        if (preferences.reducedTransparency) {
            colors.elevated
        } else {
            colors.brandPrimary.copy(alpha = BuroOpacity.SelectedContainer)
        }
    FocusSurface(
        onClick = onClick,
        modifier =
            modifier
                .sizeIn(
                    minWidth = BuroComponentSizes.IconButtonMinSize,
                    minHeight = BuroComponentSizes.IconButtonMinSize,
                )
                .semantics {
                    this.contentDescription = contentDescription
                },
        enabled = enabled,
        selected = selected,
        backgroundColor = colors.surface,
        focusedBackgroundColor = colors.elevated,
        selectedBackgroundColor = selectedContainerColor,
        shape = CircleShape,
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (enabled) {
                    colors.textPrimary
                } else {
                    colors.textMuted
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

@Composable
fun BuroChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    val colors = BuroTheme.colors
    val preferences = BuroTheme.preferences
    val selectedContainerColor =
        if (preferences.reducedTransparency) {
            colors.elevated
        } else {
            colors.brandPrimary.copy(alpha = BuroOpacity.SelectedContainer)
        }
    FocusSurface(
        onClick = onClick,
        modifier =
            modifier.defaultMinSize(
                minHeight = BuroComponentSizes.ChipMinHeight,
            ),
        enabled = enabled,
        selected = selected,
        backgroundColor =
            if (selected) {
                selectedContainerColor
            } else {
                colors.surface
            },
        focusedBackgroundColor = colors.elevated,
        selectedBackgroundColor = selectedContainerColor,
        shape = BuroShapes.Pill,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(
                        horizontal = if (compact) BuroSpacing.Xs else BuroSpacing.Sm,
                        vertical = BuroSpacing.Xs,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (enabled) colors.textPrimary else colors.textMuted,
                style =
                    if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                maxLines = 1,
            )
        }
    }
}

@Composable
fun BuroProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color = BuroTheme.colors.brandSecondary,
    trackColor: Color = BuroTheme.colors.borderSubtle,
) {
    val normalizedProgress = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    Box(
        modifier =
            modifier
                .height(BuroComponentSizes.ProgressHeight)
                .clip(BuroShapes.Pill)
                .background(trackColor)
                .semantics {
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = normalizedProgress,
                            range = 0f..1f,
                        )
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(normalizedProgress)
                    .background(progressColor),
        )
    }
}

/**
 * Decorative loading placeholder. It explicitly opts out of focus and accessibility traversal.
 */
@Composable
fun BuroSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = BuroShapes.Medium,
) {
    val colors = BuroTheme.colors
    val motion = BuroTheme.motion
    val preferences = BuroTheme.preferences
    val animatedAlpha =
        if (motion.allowsSkeletonPulse && !preferences.reducedTransparency) {
            val transition = rememberInfiniteTransition(label = "buroSkeleton")
            val alpha by
                transition.animateFloat(
                    initialValue = BuroOpacity.SkeletonMinimum,
                    targetValue = BuroOpacity.SkeletonMaximum,
                    animationSpec =
                        infiniteRepeatable(
                            animation =
                                tween(
                                    durationMillis = BuroMotionTokens.SkeletonPulseMillis,
                                    easing = LinearEasing,
                                ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "buroSkeletonAlpha",
                )
            alpha
        } else {
            if (preferences.reducedTransparency) {
                1f
            } else {
                BuroOpacity.SkeletonStatic
            }
        }

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(colors.elevated.copy(alpha = animatedAlpha))
                .focusProperties { canFocus = false }
                .focusable(enabled = false)
                .clearAndSetSemantics { },
    )
}

@Composable
fun BuroEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    BuroStateLayout(
        title = title,
        message = message,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
        icon =
            icon ?: {
                BuroEmptyStateMark()
            },
    )
}

@Composable
fun BuroErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    BuroStateLayout(
        title = title,
        message = message,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
        icon =
            icon ?: {
                BuroErrorStateMark()
            },
    )
}

@Composable
private fun BuroStateLayout(
    title: String,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BuroTheme.colors
    Column(
        modifier =
            modifier
                .widthIn(max = BuroComponentSizes.StateMaxWidth)
                .padding(BuroSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(BuroSpacing.Md))
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = message,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.height(BuroSpacing.Md))
            BuroButton(
                onClick = onAction,
                style = BuroButtonStyle.Secondary,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun BuroEmptyStateMark() {
    val colors = BuroTheme.colors
    Box(
        modifier =
            Modifier
                .size(BuroComponentSizes.StateGlyph)
                .clip(BuroShapes.Large)
                .background(colors.elevated)
                .border(
                    width = BuroFocusTokens.RingWidth,
                    color = colors.borderSubtle,
                    shape = BuroShapes.Large,
                )
                .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(BuroSpacing.Md)
                    .clip(CircleShape)
                    .background(colors.brandPrimary),
        )
        Box(
            modifier =
                Modifier
                    .size(BuroSpacing.Xs)
                    .clip(CircleShape)
                    .background(colors.brandSecondary),
        )
    }
}

@Composable
private fun BuroErrorStateMark() {
    val colors = BuroTheme.colors
    val preferences = BuroTheme.preferences
    Box(
        modifier =
            Modifier
                .size(BuroComponentSizes.StateGlyph)
                .clip(BuroShapes.Large)
                .background(
                    if (preferences.reducedTransparency) {
                        colors.elevated
                    } else {
                        colors.error.copy(alpha = BuroOpacity.ErrorContainer)
                    },
                )
                .border(
                    width = BuroFocusTokens.RingWidth,
                    color = colors.error,
                    shape = BuroShapes.Large,
                )
                .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "!",
            color = colors.error,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
