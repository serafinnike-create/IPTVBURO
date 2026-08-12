package com.lucasserafin94.iptvburo.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * How to get a TMDB key, step by step.
 *
 * The key is free and the process is short, but it runs across four pages of somebody else's site
 * and asks questions ("application URL", "type of use") that mean nothing to a viewer who only
 * wants film posters. Reported repeatedly as the key being impossible to obtain, which is not what
 * was happening: people were stopping at the form.
 *
 * The drawings are shapes rather than screenshots on purpose. A screenshot of TMDB's site would be
 * their copyrighted interface, would need redoing in four languages, and would be wrong the next
 * time they redesign; a diagram of where things sit keeps working.
 */
@Composable
internal fun TmdbKeyGuideSheet(onDismiss: () -> Unit) {
    val androidContext = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .background(BuroSurfaceRaised, RoundedCornerShape(22.dp))
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.tmdb_guide_title),
                color = BuroTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tmdb_guide_intro),
                color = BuroTextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(GUIDE_STEPS) { index, step ->
                    GuideStep(
                        number = index + 1,
                        text = stringResource(step.textResource),
                        illustration = step.illustration,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusSurface(
                    onClick = {
                        runCatching {
                            androidContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(TMDB_SIGNUP_URL)),
                            )
                        }
                    },
                    selected = true,
                    shape = RoundedCornerShape(50),
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(46.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tmdb_guide_open_site),
                        color = BuroGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
                FocusSurface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(50),
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(46.dp),
                ) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = BuroTextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(
    number: Int,
    text: String,
    illustration: GuideIllustration,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(BuroGold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    color = BuroCanvas,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = text,
                color = BuroTextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f),
            )
        }
        GuideDrawing(illustration)
    }
}

/** What each step looks like, drawn rather than photographed. */
private enum class GuideIllustration {
    /** A sign-up form: two fields and a button. */
    SIGN_UP,

    /** A settings page with a highlighted row in a sidebar. */
    SETTINGS_MENU,

    /** A form with a short answer typed into it. */
    APPLICATION_FORM,

    /** The key itself, as a long string on a page, with a copy affordance. */
    THE_KEY,
}

private data class GuideStepContent(
    val textResource: Int,
    val illustration: GuideIllustration,
)

private val GUIDE_STEPS =
    listOf(
        GuideStepContent(R.string.tmdb_guide_step_account, GuideIllustration.SIGN_UP),
        GuideStepContent(R.string.tmdb_guide_step_settings, GuideIllustration.SETTINGS_MENU),
        GuideStepContent(R.string.tmdb_guide_step_request, GuideIllustration.APPLICATION_FORM),
        GuideStepContent(R.string.tmdb_guide_step_copy, GuideIllustration.THE_KEY),
    )

/**
 * A simple diagram of the page the step describes.
 *
 * Shapes, not text: nothing here needs translating, and it stays right whatever TMDB's wording is
 * this month. The gold marks the one thing to look for on that page.
 */
@Composable
private fun GuideDrawing(illustration: GuideIllustration) {
    val gold = BuroGold
    val quiet = BuroTextSecondary.copy(alpha = 0.35f)
    val panel = BuroSurface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(BuroCanvas, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        val w = size.width
        val h = size.height
        val rowHeight = 12f

        fun bar(x: Float, y: Float, width: Float, colour: Color, barHeight: Float = rowHeight) {
            drawRoundRect(
                color = colour,
                topLeft = Offset(x, y),
                size = Size(width, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            )
        }

        when (illustration) {
            GuideIllustration.SIGN_UP -> {
                // A card with two empty fields and a filled button: what a registration page is.
                bar(w * 0.12f, h * 0.10f, w * 0.40f, quiet, 10f)
                bar(w * 0.12f, h * 0.32f, w * 0.76f, panel, 16f)
                bar(w * 0.12f, h * 0.58f, w * 0.76f, panel, 16f)
                bar(w * 0.12f, h * 0.84f, w * 0.30f, gold, 12f)
            }

            GuideIllustration.SETTINGS_MENU -> {
                // A sidebar of rows with one picked out: the API entry inside account settings.
                bar(w * 0.06f, h * 0.12f, w * 0.26f, panel)
                bar(w * 0.06f, h * 0.38f, w * 0.26f, panel)
                bar(w * 0.06f, h * 0.64f, w * 0.26f, gold)
                bar(w * 0.40f, h * 0.12f, w * 0.52f, quiet, 8f)
                bar(w * 0.40f, h * 0.34f, w * 0.44f, quiet, 8f)
                bar(w * 0.40f, h * 0.56f, w * 0.48f, quiet, 8f)
            }

            GuideIllustration.APPLICATION_FORM -> {
                // A longer form, mostly filled, with the submit button waiting.
                bar(w * 0.08f, h * 0.08f, w * 0.52f, quiet, 8f)
                bar(w * 0.08f, h * 0.28f, w * 0.84f, panel, 14f)
                bar(w * 0.08f, h * 0.52f, w * 0.84f, panel, 14f)
                bar(w * 0.08f, h * 0.76f, w * 0.34f, gold, 12f)
            }

            GuideIllustration.THE_KEY -> {
                // The key on the page: a long gold string with something to press beside it.
                bar(w * 0.08f, h * 0.14f, w * 0.44f, quiet, 8f)
                bar(w * 0.08f, h * 0.42f, w * 0.62f, gold, 16f)
                bar(w * 0.74f, h * 0.42f, w * 0.18f, panel, 16f)
                bar(w * 0.08f, h * 0.76f, w * 0.36f, quiet, 8f)
            }
        }
    }
}

/** TMDB's own sign-up page. The API settings live behind it, once an account exists. */
private const val TMDB_SIGNUP_URL = "https://www.themoviedb.org/signup"
