package com.lucasserafin94.iptvburo.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.theme.Blue
import com.lucasserafin94.iptvburo.ui.theme.Ink
import com.lucasserafin94.iptvburo.ui.theme.Muted
import com.lucasserafin94.iptvburo.ui.theme.Surface
import com.lucasserafin94.iptvburo.ui.theme.Teal
import com.lucasserafin94.iptvburo.ui.theme.White

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
            .background(
                Brush.radialGradient(
                    colors = listOf(Blue.copy(alpha = 0.2f), Ink, Color.Black),
                    radius = 1_400f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxHeight < 480.dp || maxWidth < 900.dp
        val horizontalPadding = if (compact) 32.dp else 96.dp
        val verticalPadding = if (compact) 16.dp else 52.dp
        val contentSpacing = if (compact) 32.dp else 72.dp
        val cardPadding = if (compact) 24.dp else 40.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
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

            Column(
                modifier = Modifier
                    .weight(if (compact) 1.28f else 1.2f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Surface.copy(alpha = 0.94f))
                    .padding(cardPadding),
            ) {
                Text(
                    text = stringResource(R.string.legal_title),
                    color = White,
                    fontSize = if (compact) 26.sp else 32.sp,
                    lineHeight = if (compact) 30.sp else 38.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
                Text(
                    text = stringResource(R.string.legal_body),
                    color = Muted,
                    fontSize = if (compact) 14.sp else 18.sp,
                    lineHeight = if (compact) 19.sp else 27.sp,
                )
                Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
                Text(
                    text = stringResource(R.string.legal_privacy),
                    color = Teal,
                    fontSize = if (compact) 12.sp else 15.sp,
                    lineHeight = if (compact) 17.sp else 22.sp,
                )
                Spacer(Modifier.height(if (compact) 12.dp else 30.dp))
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.legal_accept),
                        modifier = Modifier.padding(
                            vertical = if (compact) 2.dp else 6.dp,
                        ),
                        fontSize = if (compact) 14.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
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
                        listOf(Teal.copy(alpha = 0.95f), Blue),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "▶",
                color = Ink,
                fontSize = if (compact) 48.sp else 70.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        Text(
            text = "IPTV",
            color = Teal,
            fontSize = if (compact) 16.sp else 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (compact) 4.sp else 6.sp,
        )
        Text(
            text = "BURO",
            color = White,
            fontSize = if (compact) 30.sp else 42.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (compact) 3.sp else 4.sp,
        )
    }
}
