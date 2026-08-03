package com.lucasserafin94.iptvburo.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/** The ordered first-run steps. `ordinal` drives the progress indicator. */
enum class OnboardingStep {
    LANGUAGE,
    LEGAL,
    SOURCE,
    PROFILE,
    ;

    companion object {
        val total: Int get() = entries.size
    }
}

/**
 * Shared frame for every first-run screen.
 *
 * The steps used to be unrelated full-screen composables, each with its own layout and no sense of
 * sequence, so setup felt like being bounced between unfinished screens rather than guided. A
 * single frame with a fixed brand header, a step counter and a progress bar makes the flow legible:
 * the user can see how many steps remain and that they are still in the same process.
 */
@Composable
fun OnboardingScaffold(
    step: OnboardingStep,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = (step.ordinal + 1f) / OnboardingStep.total,
        animationSpec = tween(durationMillis = 320),
        label = "onboarding-progress",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                // A vertical wash rather than flat black: the brand reads as deliberate from the
                // very first frame, and it costs nothing to draw.
                Brush.verticalGradient(
                    0f to BuroSurface,
                    0.45f to BuroCanvas,
                    1f to BuroCanvas,
                ),
            ),
    ) {
        val compact = maxHeight < 620.dp || maxWidth < 480.dp
        val horizontal = if (maxWidth < 480.dp) 22.dp else if (compact) 32.dp else 56.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = horizontal, vertical = if (compact) 22.dp else 38.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IPTV  BURO",
                    color = BuroTextPrimary,
                    fontSize = if (compact) 17.sp else 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${step.ordinal + 1}/${OnboardingStep.total}",
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(BuroTextSecondary.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(BuroGold),
                )
            }

            Spacer(Modifier.height(if (compact) 26.dp else 40.dp))
            Column(modifier = Modifier.widthIn(max = 620.dp)) {
                Text(
                    text = title,
                    color = BuroTextPrimary,
                    fontSize = if (compact) 26.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = if (compact) 31.sp else 38.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    color = BuroTextSecondary,
                    fontSize = if (compact) 14.sp else 16.sp,
                    lineHeight = if (compact) 20.sp else 23.sp,
                )
            }

            Spacer(Modifier.height(if (compact) 22.dp else 32.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** Full-width choice row used by the language and profile steps. */
@Composable
fun OnboardingOptionRow(
    label: String,
    trailing: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    com.lucasserafin94.iptvburo.ui.components.FocusSurface(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth().height(60.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = BuroTextPrimary,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(text = trailing, color = BuroGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(text = "✓", color = BuroGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
