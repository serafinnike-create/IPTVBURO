package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.adaptive.BuroWindowClass
import com.lucasserafin94.iptvburo.ui.adaptive.resolveBuroWindowClass
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun LegalOnboardingScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(
                Brush.radialGradient(
                    colors = listOf(BuroGold.copy(alpha = 0.2f), BuroCanvas, Color.Black),
                    radius = 1_400f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val windowClass = resolveBuroWindowClass(maxWidth.value, maxHeight.value)
        val compact = windowClass != BuroWindowClass.Expanded

        if (windowClass == BuroWindowClass.CompactPortrait) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Top rather than centred: the notice is now longer than a phone screen, and
                // centring a column taller than its viewport pushes the first paragraph off the
                // top — the reader would start half-way through what they are agreeing to.
                verticalArrangement = Arrangement.Top,
            ) {
                BrandMark(
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                LegalCard(
                    compact = true,
                    onAccept = onAccept,
                    focusRequester = focusRequester,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
            }
        } else {
            val horizontalPadding = if (compact) 24.dp else 96.dp
            val verticalPadding = if (compact) 12.dp else 52.dp
            val contentSpacing = if (compact) 24.dp else 72.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (compact) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding,
                    ),
                horizontalArrangement = Arrangement.spacedBy(contentSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(
                    compact = compact,
                    modifier = Modifier.weight(if (compact) 0.72f else 0.8f),
                )
                LegalCard(
                    compact = compact,
                    onAccept = onAccept,
                    focusRequester = focusRequester,
                    modifier = Modifier.weight(if (compact) 1.28f else 1.2f),
                )
            }
        }
    }
}

@Composable
private fun LegalCard(
    compact: Boolean,
    onAccept: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 22.dp else 28.dp))
            .background(BuroSurface.copy(alpha = 0.94f))
            .padding(if (compact) 22.dp else 40.dp),
    ) {
        Text(
            text = stringResource(R.string.legal_title),
            color = BuroTextPrimary,
            fontSize = if (compact) 24.sp else 32.sp,
            lineHeight = if (compact) 29.sp else 38.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
        // Three paragraphs rather than one, because they answer three different questions: what
        // the app is, what the user is declaring by continuing, and who is answerable for what.
        // Run together they read as boilerplate nobody finishes.
        listOf(
            R.string.legal_body,
            R.string.legal_body_two,
            R.string.legal_body_three,
        ).forEachIndexed { index, paragraph ->
            if (index > 0) Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
            Text(
                text = stringResource(paragraph),
                color = BuroTextSecondary,
                fontSize = if (compact) 13.sp else 16.sp,
                lineHeight = if (compact) 19.sp else 24.sp,
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
        Text(
            text = stringResource(R.string.legal_privacy),
            color = BuroAccent,
            fontSize = if (compact) 12.sp else 15.sp,
            lineHeight = if (compact) 17.sp else 22.sp,
        )
        Spacer(Modifier.height(if (compact) 16.dp else 30.dp))
        BuroButton(
            onClick = onAccept,
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.legal_accept),
                modifier = Modifier.padding(
                    vertical = if (compact) 4.dp else 6.dp,
                ),
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BrandMark(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 104.dp else 148.dp)
                .clip(RoundedCornerShape(if (compact) 26.dp else 36.dp))
                .background(
                    Brush.linearGradient(
                        listOf(BuroAccent.copy(alpha = 0.95f), BuroGold),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The product's own mark — the gold aperture ring around the ivory B — rather than a
            // generic play triangle. It is the same vector the launcher icon uses, so the first
            // screen anybody sees shows the same logo as the icon they tapped to get here.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        Text(
            text = "IPTV",
            color = BuroAccent,
            fontSize = if (compact) 16.sp else 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (compact) 4.sp else 6.sp,
        )
        Text(
            text = "BURO",
            color = BuroTextPrimary,
            fontSize = if (compact) 30.sp else 42.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (compact) 3.sp else 4.sp,
        )
    }
}
