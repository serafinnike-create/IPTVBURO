package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.lucasserafin94.iptvburo.metadata.CriticScores
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.designsystem.ProviderIdentity
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * The score and the service, shown as figures rather than controls.
 *
 * Nothing here is tappable, deliberately: this is information the eye takes in on the way to the
 * play button, and a rating that opened something would interrupt the one thing the screen is for.
 *
 * ## What is shown, and what is not
 *
 * The percentage comes from the score the catalogue actually carries — the provider's own figure,
 * which is the TMDB score on most playlists. Other services' meters are **not** shown, because the
 * app has no source for them: inventing a Tomatometer from a TMDB number would be presenting a
 * guess as a measurement, and the tomato and popcorn marks belong to Rotten Tomatoes besides.
 *
 * The row is built to hold more entries the day a second real source exists.
 */
@Composable
fun RatingStrip(
    /** The catalogue's score, out of ten. Absent for a title nobody has rated. */
    rating: Double?,
    /** The service the title was filed under, when the category named one. */
    provider: ProviderIdentity?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** How many people voted for [rating]. Absent when the source does not say. */
    voteCount: Int? = null,
    /**
     * What the critics said, from OMDb.
     *
     * Absent when no OMDb key is configured or the services hold nothing for this title, and the
     * row simply shows fewer meters rather than a panel of dashes.
     */
    critics: CriticScores? = null,
) {
    if (rating == null && provider == null && critics?.hasAny != true) return

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.details_ratings),
            color = BuroTextSecondary,
            fontSize = if (compact) 15.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))
        // Everything on screen at once, without scrolling.
        //
        // This used to scroll sideways, which hid whichever meters fell off the right edge — and on
        // a well-known film that is the Tomatometer and the Metascore, the two somebody is most
        // likely looking for. Six meters do fit a phone once each is given an equal share of the
        // width and sized to it; a strip that has to be dragged to be read is a strip that mostly
        // is not read.
        //
        // `SpaceBetween` rather than a fixed gap: the meters then sit evenly however many there
        // are, so a title with no critics' scores does not leave a gap on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            rating?.let { score ->
                val percent = (score.coerceIn(0.0, 10.0) * 10).roundToInt()
                ScoreMeter(
                    percent = percent,
                    caption = stringResource(R.string.details_score_caption),
                    compact = compact,
                    icon = { size -> ScoreDial(percent = percent, size = size) },
                )
                // The audience beside it, as the reference screen sets two meters side by side.
                // Only when the count is real: a percentage with nobody behind it is not a second
                // opinion, it is the same one printed twice.
                voteCount?.takeIf { it > 0 }?.let { votes ->
                    ScoreMeter(
                        value = formatVotes(votes),
                        caption = stringResource(R.string.details_votes),
                        compact = compact,
                        icon = { size -> AudienceGlyph(size = size) },
                    )
                }
            }
            // The critics, each from the service that computed it. Named rather than badged with
            // their marks: the tomato, the popcorn tub and the yellow IMDb square are registered
            // marks of Fandango and Amazon, and the figures are theirs even where the icons cannot
            // be. OMDb republishes them, which is what makes the numbers real.
            critics?.tomatometer?.let { score ->
                ScoreMeter(
                    percent = score,
                    caption = stringResource(R.string.details_tomatometer),
                    compact = compact,
                    icon = { size -> CriticSealGlyph(percent = score, size = size) },
                )
            }
            critics?.imdbRating?.let { score ->
                ScoreMeter(
                    value = "%.1f".format(score),
                    caption = stringResource(R.string.details_imdb),
                    compact = compact,
                    icon = { size -> StarRatingGlyph(size = size) },
                )
            }
            critics?.metascore?.let { score ->
                ScoreMeter(
                    percent = score,
                    caption = stringResource(R.string.details_metascore),
                    compact = compact,
                    icon = { size -> MetascoreGlyph(percent = score, size = size) },
                )
            }
            provider?.let { service ->
                ScoreMeter(
                    value = service.label,
                    caption = stringResource(R.string.details_platform),
                    compact = compact,
                    icon = { size -> ProviderGlyph(provider = service, size = size) },
                )
            }
        }
    }
}

/**
 * One meter: a drawn mark, a figure, and what the figure counts.
 *
 * The shape the reference screens use — icon above, big number, small caption — because it reads at
 * a glance and lines up into a row of comparable things. The marks are this app's own: the tomato,
 * the popcorn tub and the yellow IMDb square are registered marks of Fandango and Amazon, and
 * drawing look-alikes would be using their brands without licence. Their scores are behind
 * commercial APIs this app has no access to besides, so the figures would be invented.
 */
@Composable
private fun RowScope.ScoreMeter(
    caption: String,
    compact: Boolean,
    icon: @Composable (Dp) -> Unit,
    value: String? = null,
    percent: Int? = null,
) {
    // An equal share of the row, so however many meters there are they divide the width between
    // them rather than the last ones falling off the edge.
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(if (compact) 32.dp else 40.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = percent?.let { "$it%" } ?: value.orEmpty(),
            color = BuroTextPrimary,
            fontSize = if (compact) 16.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            // Scaled down rather than truncated: an ellipsis inside a figure would make the number
            // itself unreadable, and these values are short enough to stay legible when squeezed.
            softWrap = false,
            style = LocalTextStyle.current.copy(lineHeight = if (compact) 18.sp else 22.sp),
        )
        Text(
            text = caption,
            color = BuroTextSecondary,
            fontSize = if (compact) 10.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The score as a dial that fills with the rating.
 *
 * Drawn rather than lettered so the row has a picture in it, which is what makes the reference
 * screen readable at a glance. The arc carries the verdict twice over — by how far round it goes
 * and by its colour — so a weak score is never dressed up as a strong one.
 */
@Composable
private fun ScoreDial(
    percent: Int,
    size: Dp,
) {
    // Captured before the draw scope, which is not a composable context.
    val colour =
        when {
            percent >= WELL_LIKED -> BuroGold
            percent >= MIXED -> Color(0xFFC9A227)
            else -> Color(0xFF8C8C8C)
        }
    Canvas(modifier = Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.13f
        val inset = stroke / 2
        drawArc(
            color = colour.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = colour,
            // From the top, clockwise: the direction a dial is read.
            startAngle = -90f,
            sweepAngle = 360f * (percent / 100f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Three figures, for the crowd the vote count speaks for. */
@Composable
private fun AudienceGlyph(size: Dp) {
    // Read here rather than inside the draw scope: a Canvas lambda is not composable, so a theme
    // colour has to be captured before the drawing starts.
    val accent = BuroAccent
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.minDimension
        val headRadius = unit * 0.11f
        // A row of three, the middle one forward: the plainest drawing of "several people".
        listOf(0.24f to 0.46f, 0.5f to 0.38f, 0.76f to 0.46f).forEachIndexed { index, point ->
            val x = point.first
            val y = point.second
            val colour = if (index == 1) accent else accent.copy(alpha = 0.55f)
            drawCircle(
                color = colour,
                radius = if (index == 1) headRadius * 1.15f else headRadius,
                center = Offset(unit * x, unit * y),
            )
            val bodyWidth = unit * (if (index == 1) 0.26f else 0.22f)
            val bodyHeight = unit * 0.22f
            drawRoundRect(
                color = colour,
                topLeft = Offset(unit * x - bodyWidth / 2, unit * (y + 0.10f)),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(bodyWidth / 2, bodyWidth / 2),
            )
        }
    }
}

/** The service's monogram on its own colour, in the same round frame as the other marks. */
@Composable
private fun ProviderGlyph(
    provider: ProviderIdentity,
    size: Dp,
) {
    // The service's real mark when TMDb supplied one — they publish these for exactly this use,
    // with attribution — and the monogram on the brand colour when it did not.
    provider.logoUrl?.let { url ->
        AsyncImage(
            model = url,
            contentDescription = provider.label,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(provider.colour),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = provider.monogram,
            // Against a saturated brand colour white reads; Apple's near-white badge is the one
            // exception, so it takes dark ink instead of vanishing into itself.
            color = if (provider.label == "Apple TV+") Color(0xFF111111) else Color.White,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Thousands past a thousand: the magnitude is the point, not the exact tally. */
internal fun formatVotes(votes: Int): String =
    when {
        votes >= 1_000_000 -> "${votes / 100_000 / 10.0}M"
        votes >= 1_000 -> "${votes / 1_000}k"
        else -> votes.toString()
    }

/** Above this a title reads as well liked; the ring goes gold. */
private const val WELL_LIKED = 70

/** Above this it is mixed rather than poor. */
private const val MIXED = 50

/**
 * The critics' seal: a ring with a notched edge, like a stamp of approval.
 *
 * This app's own mark, not anyone else's. The tomato and the popcorn tub are registered marks of
 * Fandango and the yellow square is Amazon's, so drawing look-alikes would be using their brands
 * without licence — but the *figures* are theirs and real, republished through OMDb. A distinct
 * shape per meter is what stops four different measurements reading as one repeated four times.
 */
@Composable
private fun CriticSealGlyph(
    percent: Int,
    size: Dp,
) {
    val colour =
        when {
            percent >= WELL_LIKED -> BuroGold
            percent >= MIXED -> Color(0xFFC9A227)
            else -> Color(0xFF8C8C8C)
        }
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.minDimension
        val centre = Offset(unit / 2, unit / 2)
        // The notched rim: short spokes around a filled disc, which reads as a seal at any size.
        val spokes = 12
        repeat(spokes) { index ->
            val angle = (index * (360f / spokes)) * (PI / 180f).toFloat()
            val inner = unit * 0.34f
            val outer = unit * 0.46f
            drawLine(
                color = colour,
                start = Offset(centre.x + cos(angle) * inner, centre.y + sin(angle) * inner),
                end = Offset(centre.x + cos(angle) * outer, centre.y + sin(angle) * outer),
                strokeWidth = unit * 0.07f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = colour.copy(alpha = 0.22f), radius = unit * 0.3f, center = centre)
        drawCircle(
            color = colour,
            radius = unit * 0.3f,
            center = centre,
            style = Stroke(width = unit * 0.08f),
        )
    }
}

/** A star, for the score IMDb states out of ten — the shape everyone already reads as a rating. */
@Composable
private fun StarRatingGlyph(size: Dp) {
    val colour = BuroGold
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.minDimension
        val centre = Offset(unit / 2, unit / 2)
        val outer = unit * 0.44f
        val inner = outer * 0.42f
        val path = Path()
        repeat(10) { index ->
            val radius = if (index % 2 == 0) outer else inner
            // Started at the top, so the star sits upright rather than on a point.
            val angle = (-90f + index * 36f) * (PI / 180f).toFloat()
            val x = centre.x + cos(angle) * radius
            val y = centre.y + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path = path, color = colour)
    }
}

/**
 * A stack of bars, for the Metascore — a weighted aggregate rather than a single verdict.
 *
 * Three bars whose heights follow the figure: it reads as "assembled from several opinions", which
 * is exactly what a Metascore is, and it cannot be mistaken for the seal or the star beside it.
 */
@Composable
private fun MetascoreGlyph(
    percent: Int,
    size: Dp,
) {
    val colour =
        when {
            percent >= WELL_LIKED -> BuroGold
            percent >= MIXED -> Color(0xFFC9A227)
            else -> Color(0xFF8C8C8C)
        }
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.minDimension
        val barWidth = unit * 0.18f
        val gap = unit * 0.09f
        val totalWidth = barWidth * 3 + gap * 2
        val left = (unit - totalWidth) / 2
        val bottom = unit * 0.76f
        // Middle tallest, so the shape is legible even when the score is low and the bars short.
        listOf(0.46f, 0.72f, 0.58f).forEachIndexed { index, factor ->
            val height = unit * factor * (percent.coerceIn(0, 100) / 100f).coerceAtLeast(0.25f)
            drawRoundRect(
                color = if (index == 1) colour else colour.copy(alpha = 0.6f),
                topLeft = Offset(left + index * (barWidth + gap), bottom - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}
