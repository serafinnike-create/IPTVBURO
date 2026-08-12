package com.lucasserafin94.iptvburo.desktop.app

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
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
import com.lucasserafin94.iptvburo.desktop.ui.strings

/**
 * How to obtain a TMDb key, for somebody who has never seen the site.
 *
 * The settings screen offered a link straight to `themoviedb.org/settings/api`, which is a page you
 * cannot reach without an account and cannot use without knowing what to answer. A customer who has
 * never registered for a developer key lands on a form asking for an application URL and a summary
 * of intended use, and stops there.
 *
 * Each step is drawn rather than screenshotted. Screenshots of somebody else's website go stale the
 * moment they redesign it, and shipping their interface in this app would put their branding in a
 * product they have nothing to do with. A simple sketch of the shape of each page survives a
 * redesign and is honest about being a diagram.
 */
@Composable
fun TmdbKeyGuideDialog(
    onDismiss: () -> Unit,
    onOpenSite: (String) -> Unit,
) {
    val text = strings
    val steps = tmdbGuideSteps(text)
    val backdropInteraction = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BuroColors.Canvas.copy(alpha = 0.86f))
                // Dismiss on the backdrop, without the ripple a full-screen click would draw.
                //
                // The interaction source is hoisted out of the argument list deliberately. Left
                // inline, its own closing parenthesis ends the match that DismissAreaRippleTest
                // performs, so the `indication = null` that follows is never seen and a correct
                // dismiss area is reported as a screen-greying one. The check is worth keeping
                // strict — this is the third time that ripple has been reported — so the code
                // stays in the shape the check can read.
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
                    //
                    // `enabled = false` rather than an empty handler: a disabled clickable still
                    // consumes the press, carries no indication at all, and is the same form the
                    // settings panel uses for the same purpose.
                    .clickable(enabled = false) {}
                    .padding(BuroSpacing.Lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text.tmdbGuide.tmdbGuideTitle,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = text.tmdbGuide.tmdbGuideSubtitle,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("✕", color = BuroColors.TextMuted, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(BuroSpacing.Md))

            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
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
                // Given a colour, not left at the default.
                //
                // Compose's own scrollbar is nearly black on a dark surface, so it was there and
                // could not be seen — and a guide whose sixth step is the one that matters reads as
                // a guide with three steps. The same style every other long surface in the app uses.
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
                    onClick = { onOpenSite(TMDB_SIGNUP_URL) },
                    shape = BuroRadius.Small,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BuroColors.Primary,
                            contentColor = BuroColors.OnPrimary,
                        ),
                ) {
                    Text(text.tmdbGuide.tmdbGuideOpenSignup)
                }
                TextButton(onClick = { onOpenSite(TMDB_API_SETTINGS_URL) }) {
                    Text(text.tmdbGuide.tmdbGuideOpenApiPage, color = BuroColors.Primary)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(text.close, color = BuroColors.TextMuted)
                }
            }
        }
    }
}

/** One numbered step: a sketch of the page, what to do there, and what to expect. */
private data class TmdbGuideStep(
    val title: String,
    val body: String,
    /** A rough sketch of the page in question. See the note on [TmdbKeyGuideDialog]. */
    val sketch: @Composable () -> Unit,
)

@Composable
private fun GuideStep(number: Int, step: TmdbGuideStep) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Small)
                .background(BuroColors.SurfaceRaised)
                .padding(BuroSpacing.Md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(BuroColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                color = BuroColors.OnPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(BuroSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = step.body,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            step.sketch()
        }
    }
}

/**
 * A drawn stand-in for a web page, not a screenshot of one.
 *
 * Enough to recognise the page when it appears in the browser — a header bar, some fields, a
 * highlighted target — without reproducing anybody else's interface or going stale on their next
 * redesign.
 */
@Composable
private fun PageSketch(
    rows: List<SketchRow>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Small)
                .background(BuroColors.Canvas)
                .border(1.dp, BuroColors.BorderSoft, BuroRadius.Small)
                .padding(BuroSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            when (row) {
                is SketchRow.Bar ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(row.width)
                                .height(row.height.dp)
                                .clip(BuroRadius.Small)
                                .background(
                                    if (row.highlighted) {
                                        BuroColors.Primary.copy(alpha = 0.75f)
                                    } else {
                                        BuroColors.SurfaceHover
                                    },
                                ),
                    )

                is SketchRow.Label ->
                    Text(
                        text = row.text,
                        color = if (row.highlighted) BuroColors.Primary else BuroColors.TextSubtle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (row.highlighted) FontWeight.Bold else FontWeight.Normal,
                    )
            }
        }
    }
}

private sealed interface SketchRow {
    data class Bar(
        val width: Float,
        val height: Int = 10,
        val highlighted: Boolean = false,
    ) : SketchRow

    data class Label(val text: String, val highlighted: Boolean = false) : SketchRow
}

/**
 * The steps, in the order somebody actually performs them.
 *
 * Written from the real flow: an account first, then the API request form — which asks for a type,
 * an application name and a URL, and is where most people give up. The answers are spelled out
 * because "Developer" and "personal use" are the correct ones and are not obvious.
 */
@Composable
private fun tmdbGuideSteps(text: com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings): List<TmdbGuideStep> =
    listOf(
        TmdbGuideStep(
            title = text.tmdbGuide.tmdbStep1Title,
            body = text.tmdbGuide.tmdbStep1Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label("themoviedb.org"),
                        SketchRow.Bar(width = 0.55f),
                        SketchRow.Bar(width = 0.75f),
                        SketchRow.Bar(width = 0.35f, height = 14, highlighted = true),
                        SketchRow.Label(text.tmdbGuide.tmdbSketchSignUp, highlighted = true),
                    ),
                )
            },
        ),
        TmdbGuideStep(
            title = text.tmdbGuide.tmdbStep2Title,
            body = text.tmdbGuide.tmdbStep2Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label("themoviedb.org / settings"),
                        SketchRow.Bar(width = 0.6f),
                        SketchRow.Bar(width = 0.3f, height = 12, highlighted = true),
                        SketchRow.Label(text.tmdbGuide.tmdbSketchApiMenu, highlighted = true),
                    ),
                )
            },
        ),
        TmdbGuideStep(
            title = text.tmdbGuide.tmdbStep3Title,
            body = text.tmdbGuide.tmdbStep3Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label(text.tmdbGuide.tmdbSketchRequestType),
                        SketchRow.Bar(width = 0.4f, height = 12, highlighted = true),
                        SketchRow.Label(text.tmdbGuide.tmdbSketchDeveloper, highlighted = true),
                    ),
                )
            },
        ),
        TmdbGuideStep(
            title = text.tmdbGuide.tmdbStep4Title,
            body = text.tmdbGuide.tmdbStep4Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label(text.tmdbGuide.tmdbSketchFormFields),
                        SketchRow.Bar(width = 0.8f),
                        SketchRow.Bar(width = 0.65f),
                        SketchRow.Bar(width = 0.9f),
                        SketchRow.Bar(width = 0.3f, height = 12, highlighted = true),
                    ),
                )
            },
        ),
        TmdbGuideStep(
            title = text.tmdbGuide.tmdbStep5Title,
            body = text.tmdbGuide.tmdbStep5Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label(text.tmdbGuide.tmdbSketchApiKeyLabel, highlighted = true),
                        SketchRow.Bar(width = 0.85f, height = 14, highlighted = true),
                        SketchRow.Label(text.tmdbGuide.tmdbSketchCopy),
                    ),
                )
            },
        ),
        TmdbGuideStep(
            title = text.tmdbGuide.tmdbStep6Title,
            body = text.tmdbGuide.tmdbStep6Body,
            sketch = {
                PageSketch(
                    listOf(
                        SketchRow.Label("IPTV BURO · ${text.tmdbGuide.tmdbSketchSettings}"),
                        SketchRow.Bar(width = 0.9f, height = 14, highlighted = true),
                        SketchRow.Label(text.tmdbGuide.tmdbSketchPaste, highlighted = true),
                    ),
                )
            },
        ),
    )

/** Where an account is created. The guide sends a newcomer here rather than to the API form. */
const val TMDB_SIGNUP_URL: String = "https://www.themoviedb.org/signup"
