package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.strings

/**
 * How to obtain an OMDb key, for somebody who has never seen that site either.
 *
 * The settings panel said "Pegue a sua em omdbapi.com" and stopped, which assumes the reader already
 * knows that the site wants an email address rather than an account, that the free tier is a radio
 * button labelled FREE, and — the step people actually miss — that the key arrives by email with an
 * activation link that must be opened before the key works at all. Somebody who pastes the key
 * straight from the email without clicking that link gets no critics' scores and nothing on screen
 * explains why.
 *
 * The TMDb key beside it has had a guide since a customer got stuck on the same kind of form. This is
 * the same door, and deliberately the same machine: [KeyGuideStep], [GuideStep] and [PageSketch] are
 * shared with [TmdbKeyGuideDialog] so the two guides cannot drift into looking like different
 * features.
 */
@Composable
fun OmdbKeyGuideDialog(
    onDismiss: () -> Unit,
    onOpenSite: (String) -> Unit,
) {
    val text = strings
    val steps = omdbGuideSteps(text)
    val backdropInteraction = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BuroColors.Canvas.copy(alpha = 0.86f))
                // Dismiss on the backdrop with no ripple, in the exact shape DismissAreaRippleTest
                // reads — see the note on TmdbKeyGuideDialog for why the interaction source is
                // hoisted out of the argument list rather than constructed inline.
                .clickable(
                    indication = null,
                    interactionSource = backdropInteraction,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.88f)
                    .clip(BuroRadius.Medium)
                    .background(BuroColors.Surface)
                    .border(1.dp, BuroColors.BorderSoft, BuroRadius.Medium)
                    // Swallows clicks so pressing inside the panel does not close it.
                    .clickable(enabled = false) {}
                    .padding(BuroSpacing.Lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text.shareStrings.ratings.criticGuideTitle,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = text.shareStrings.ratings.criticGuideSubtitle,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("✕", color = BuroColors.TextMuted, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(BuroSpacing.Md))

            val listState = rememberLazyListState()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
                ) {
                    itemsIndexed(steps) { index, step ->
                        GuideStep(number = index + 1, step = step)
                    }
                }
                // Coloured explicitly: Compose's default scrollbar is nearly black on this surface,
                // so a guide whose last step is the one that matters reads as a guide with two.
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    style =
                        LocalScrollbarStyle.current.copy(
                            thickness = 10.dp,
                            unhoverColor = BuroColors.BorderSoft,
                            hoverColor = BuroColors.Primary,
                        ),
                )
            }

            Spacer(Modifier.height(BuroSpacing.Md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onOpenSite(OMDB_API_KEY_URL) },
                    shape = BuroRadius.Small,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BuroColors.Primary,
                            contentColor = BuroColors.OnPrimary,
                        ),
                ) {
                    Text(text.shareStrings.ratings.criticGuideOpenSite)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(text.close, color = BuroColors.TextMuted)
                }
            }
        }
    }
}

/**
 * The steps, in the order somebody actually performs them.
 *
 * Four rather than TMDb's six, because OMDb asks for less: no account, no application URL. The last
 * step exists because it is the one that silently costs people the key — the activation link.
 */
@Composable
private fun omdbGuideSteps(text: DesktopStrings): List<KeyGuideStep> {
    val ratings = text.shareStrings.ratings
    return listOf(
        KeyGuideStep(
            title = ratings.criticStep1Title,
            body = ratings.criticStep1Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label("omdbapi.com"),
                        SketchRow.Bar(width = 0.6f),
                        SketchRow.Bar(width = 0.35f, height = 14, highlighted = true),
                        SketchRow.Label("API Key", highlighted = true),
                    ),
                )
            },
        ),
        KeyGuideStep(
            title = ratings.criticStep2Title,
            body = ratings.criticStep2Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Bar(width = 0.3f, height = 12, highlighted = true),
                        SketchRow.Label(ratings.criticSketchFree, highlighted = true),
                        SketchRow.Bar(width = 0.3f),
                    ),
                )
            },
        ),
        KeyGuideStep(
            title = ratings.criticStep3Title,
            body = ratings.criticStep3Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label(ratings.criticSketchEmail),
                        SketchRow.Bar(width = 0.85f, height = 14, highlighted = true),
                        SketchRow.Bar(width = 0.7f),
                        SketchRow.Label(ratings.criticSketchSubmit, highlighted = true),
                    ),
                )
            },
        ),
        KeyGuideStep(
            title = ratings.criticStep4Title,
            body = ratings.criticStep4Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label(ratings.criticSketchInbox, highlighted = true),
                        SketchRow.Bar(width = 0.9f, height = 14, highlighted = true),
                        SketchRow.Label("IPTV BURO · ${text.tmdbGuide.tmdbSketchPaste}"),
                    ),
                )
            },
        ),
    )
}

/**
 * Where OMDb issues the key.
 *
 * The `apikey.aspx` page rather than the site root: the root is API documentation, and somebody sent
 * there has to find this page themselves — which is the gap the guide exists to close.
 */
const val OMDB_API_KEY_URL: String = "https://www.omdbapi.com/apikey.aspx"
