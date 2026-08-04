package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A profile avatar drawn as vector art rather than as an emoji glyph.
 *
 * The emoji set rendered through Segoe UI Emoji looked flat and inconsistent beside the rest of the
 * app, and its appearance was decided by whichever font the machine happened to have. Drawing them
 * gives a single deliberate look, stays sharp at any size, and adds nothing to the installer.
 *
 * The volume is built the way a lit sphere reads: a radial base whose light comes from the upper
 * left, a bright specular highlight offset toward that light, and a darker rim opposite it.
 */
data class BuroAvatar(
    val id: String,
    val base: Color,
    val shade: Color,
    val motif: AvatarMotif,
)

/** The shape drawn on top of the sphere. Kept simple so it stays legible at 28dp. */
enum class AvatarMotif {
    CLAPPER,
    POPCORN,
    ROCKET,
    FOX,
    MOON,
    BALL,
    GUITAR,
    CAT,
    CROWN,
    GHOST,
    ROBOT,
    HEART,
    STAR,
    LEAF,
    WAVE,
    BOLT,
}

/**
 * The avatar set, addressed by position.
 *
 * Profiles store an index, so the order is part of the saved data: appending is safe, reordering
 * would silently change the avatar of every existing profile.
 */
val BURO_AVATARS: List<BuroAvatar> =
    listOf(
        BuroAvatar("clapper", Color(0xFF7C6BF5), Color(0xFF3B2F8F), AvatarMotif.CLAPPER),
        BuroAvatar("popcorn", Color(0xFFFF6F91), Color(0xFF9B2C50), AvatarMotif.POPCORN),
        BuroAvatar("rocket", Color(0xFF5AA9FF), Color(0xFF204C8F), AvatarMotif.ROCKET),
        BuroAvatar("fox", Color(0xFFFF9351), Color(0xFF9E4415), AvatarMotif.FOX),
        BuroAvatar("moon", Color(0xFFF3C969), Color(0xFF8C6A1E), AvatarMotif.MOON),
        BuroAvatar("ball", Color(0xFF4ED59B), Color(0xFF1B6E4C), AvatarMotif.BALL),
        BuroAvatar("guitar", Color(0xFFB388FF), Color(0xFF56308F), AvatarMotif.GUITAR),
        BuroAvatar("cat", Color(0xFFFFB865), Color(0xFF9A5F14), AvatarMotif.CAT),
        BuroAvatar("crown", Color(0xFFE0B64F), Color(0xFF8A6412), AvatarMotif.CROWN),
        BuroAvatar("ghost", Color(0xFF9FB4C7), Color(0xFF44586B), AvatarMotif.GHOST),
        BuroAvatar("robot", Color(0xFF6FD3C7), Color(0xFF1F6C64), AvatarMotif.ROBOT),
        BuroAvatar("heart", Color(0xFFFF7BA9), Color(0xFF9C2B54), AvatarMotif.HEART),
        BuroAvatar("star", Color(0xFF7BC6FF), Color(0xFF23608F), AvatarMotif.STAR),
        BuroAvatar("leaf", Color(0xFF62C99A), Color(0xFF1E6B4A), AvatarMotif.LEAF),
        BuroAvatar("wave", Color(0xFF5FA8D3), Color(0xFF1E5479), AvatarMotif.WAVE),
        BuroAvatar("bolt", Color(0xFFFFD166), Color(0xFF95700F), AvatarMotif.BOLT),
    )

/** Wraps out-of-range indices instead of failing, so a stored index can never blank the avatar. */
fun avatarAt(index: Int): BuroAvatar =
    BURO_AVATARS[((index % BURO_AVATARS.size) + BURO_AVATARS.size) % BURO_AVATARS.size]

@Composable
fun BuroProfileAvatar(
    index: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val avatar = avatarAt(index)
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            drawSphere(avatar)
            drawMotif(avatar.motif)
        }
    }
}

/** The lit sphere every avatar shares. */
private fun DrawScope.drawSphere(avatar: BuroAvatar) {
    val radius = this.size.minDimension / 2f
    val centre = Offset(this.size.width / 2f, this.size.height / 2f)
    // Light from the upper left, the convention that reads as "lit from above" to the eye.
    val lightSource = Offset(centre.x - radius * 0.35f, centre.y - radius * 0.4f)

    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(avatar.base, avatar.shade),
                center = lightSource,
                radius = radius * 1.7f,
            ),
        radius = radius,
        center = centre,
    )
    // Rim light opposite the source. Without it the sphere reads as a flat disc with a gradient.
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f)),
                center = Offset(centre.x + radius * 0.45f, centre.y + radius * 0.5f),
                radius = radius * 0.95f,
            ),
        radius = radius,
        center = centre,
    )
    // Specular highlight: small, offset, and soft-edged.
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = lightSource,
                radius = radius * 0.55f,
            ),
        radius = radius * 0.55f,
        center = lightSource,
    )
}

/**
 * Draws the motif inside the sphere.
 *
 * Everything is expressed as a fraction of the canvas so one path serves every size, from the 28dp
 * header chip to the 96dp picker tile.
 */
private fun DrawScope.drawMotif(motif: AvatarMotif) {
    val w = this.size.width
    val h = this.size.height
    val ink = Color.White.copy(alpha = 0.94f)
    val shadow = Color.Black.copy(alpha = 0.22f)

    fun p(x: Float, y: Float) = Offset(w * x, h * y)

    // A soft drop shadow under the motif, offset with the light. Flat white shapes read as stickers
    // pasted on the sphere; a shadow is what makes them sit *on* it.
    translate(left = w * 0.018f, top = h * 0.022f) {
        drawMotifShape(motif, Color.Black.copy(alpha = 0.28f), Color.Transparent, w, h)
    }
    drawMotifShape(motif, ink, shadow, w, h)
}

/**
 * The motif itself, drawn twice: once offset as its own shadow, once as the shape.
 *
 * [detail] is the darker inner colour — eyes, panel lines — and is transparent on the shadow pass so
 * the silhouette stays solid.
 */
private fun DrawScope.drawMotifShape(
    motif: AvatarMotif,
    ink: Color,
    detail: Color,
    w: Float,
    h: Float,
) {
    val shadow = detail

    fun p(x: Float, y: Float) = Offset(w * x, h * y)

    when (motif) {
        AvatarMotif.CLAPPER -> {
            drawRect(color = ink, topLeft = p(0.26f, 0.44f), size = Size(w * 0.48f, h * 0.26f))
            drawRect(color = shadow, topLeft = p(0.26f, 0.30f), size = Size(w * 0.48f, h * 0.12f))
            repeat(3) { index ->
                drawRect(
                    color = ink,
                    topLeft = p(0.30f + index * 0.15f, 0.30f),
                    size = Size(w * 0.06f, h * 0.12f),
                )
            }
        }
        AvatarMotif.POPCORN -> {
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.32f, h * 0.44f)
                        lineTo(w * 0.68f, h * 0.44f)
                        lineTo(w * 0.62f, h * 0.74f)
                        lineTo(w * 0.38f, h * 0.74f)
                        close()
                    },
                color = ink,
                style = Fill,
            )
            listOf(0.38f to 0.34f, 0.50f to 0.29f, 0.62f to 0.34f).forEach { (x, y) ->
                drawCircle(color = ink, radius = w * 0.08f, center = p(x, y))
            }
        }
        AvatarMotif.ROCKET -> {
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.50f, h * 0.24f)
                        cubicTo(w * 0.66f, h * 0.40f, w * 0.66f, h * 0.58f, w * 0.58f, h * 0.70f)
                        lineTo(w * 0.42f, h * 0.70f)
                        cubicTo(w * 0.34f, h * 0.58f, w * 0.34f, h * 0.40f, w * 0.50f, h * 0.24f)
                        close()
                    },
                color = ink,
            )
            drawCircle(color = shadow, radius = w * 0.07f, center = p(0.50f, 0.45f))
        }
        AvatarMotif.FOX, AvatarMotif.CAT -> {
            // Ears then face: the same construction, differing only in ear width.
            val earSpread = if (motif == AvatarMotif.FOX) 0.20f else 0.17f
            listOf(-1f, 1f).forEach { side ->
                drawPath(
                    path =
                        Path().apply {
                            moveTo(w * (0.50f + side * earSpread), h * 0.26f)
                            lineTo(w * (0.50f + side * (earSpread + 0.09f)), h * 0.46f)
                            lineTo(w * (0.50f + side * 0.06f), h * 0.42f)
                            close()
                        },
                    color = ink,
                )
            }
            drawCircle(color = ink, radius = w * 0.21f, center = p(0.50f, 0.55f))
            listOf(-1f, 1f).forEach { side ->
                drawCircle(color = shadow, radius = w * 0.035f, center = p(0.50f + side * 0.08f, 0.52f))
            }
            drawCircle(color = shadow, radius = w * 0.03f, center = p(0.50f, 0.62f))
        }
        AvatarMotif.MOON -> {
            drawCircle(color = ink, radius = w * 0.22f, center = p(0.54f, 0.50f))
            // Bite out of the disc, filled with the sphere's own shading rather than a flat colour.
            drawCircle(color = Color.Black.copy(alpha = 0.30f), radius = w * 0.18f, center = p(0.64f, 0.44f))
        }
        AvatarMotif.BALL -> {
            drawCircle(color = ink, radius = w * 0.22f, center = p(0.50f, 0.52f))
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.50f, h * 0.36f)
                        lineTo(w * 0.62f, h * 0.47f)
                        lineTo(w * 0.57f, h * 0.62f)
                        lineTo(w * 0.43f, h * 0.62f)
                        lineTo(w * 0.38f, h * 0.47f)
                        close()
                    },
                color = shadow,
            )
        }
        AvatarMotif.GUITAR -> {
            drawCircle(color = ink, radius = w * 0.16f, center = p(0.44f, 0.62f))
            drawCircle(color = shadow, radius = w * 0.05f, center = p(0.44f, 0.62f))
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.52f, h * 0.56f)
                        lineTo(w * 0.70f, h * 0.28f)
                        lineTo(w * 0.76f, h * 0.32f)
                        lineTo(w * 0.58f, h * 0.60f)
                        close()
                    },
                color = ink,
            )
        }
        AvatarMotif.CROWN -> {
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.28f, h * 0.66f)
                        lineTo(w * 0.32f, h * 0.36f)
                        lineTo(w * 0.42f, h * 0.52f)
                        lineTo(w * 0.50f, h * 0.32f)
                        lineTo(w * 0.58f, h * 0.52f)
                        lineTo(w * 0.68f, h * 0.36f)
                        lineTo(w * 0.72f, h * 0.66f)
                        close()
                    },
                color = ink,
            )
        }
        AvatarMotif.GHOST -> {
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.32f, h * 0.70f)
                        lineTo(w * 0.32f, h * 0.48f)
                        cubicTo(w * 0.32f, h * 0.28f, w * 0.68f, h * 0.28f, w * 0.68f, h * 0.48f)
                        lineTo(w * 0.68f, h * 0.70f)
                        lineTo(w * 0.60f, h * 0.62f)
                        lineTo(w * 0.50f, h * 0.70f)
                        lineTo(w * 0.40f, h * 0.62f)
                        close()
                    },
                color = ink,
            )
            listOf(-1f, 1f).forEach { side ->
                drawCircle(color = shadow, radius = w * 0.04f, center = p(0.50f + side * 0.09f, 0.47f))
            }
        }
        AvatarMotif.ROBOT -> {
            drawRoundRectMotif(p(0.30f, 0.38f), Size(w * 0.40f, h * 0.32f), ink, w * 0.08f)
            listOf(-1f, 1f).forEach { side ->
                drawCircle(color = shadow, radius = w * 0.05f, center = p(0.50f + side * 0.10f, 0.50f))
            }
            drawRect(color = ink, topLeft = p(0.48f, 0.26f), size = Size(w * 0.04f, h * 0.12f))
            drawCircle(color = ink, radius = w * 0.04f, center = p(0.50f, 0.24f))
        }
        AvatarMotif.HEART -> {
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.50f, h * 0.72f)
                        cubicTo(w * 0.20f, h * 0.54f, w * 0.30f, h * 0.28f, w * 0.50f, h * 0.42f)
                        cubicTo(w * 0.70f, h * 0.28f, w * 0.80f, h * 0.54f, w * 0.50f, h * 0.72f)
                        close()
                    },
                color = ink,
            )
        }
        AvatarMotif.STAR -> {
            drawPath(
                path =
                    Path().apply {
                        val cx = w * 0.50f
                        val cy = h * 0.52f
                        val outer = w * 0.24f
                        val inner = w * 0.10f
                        repeat(10) { step ->
                            val radius = if (step % 2 == 0) outer else inner
                            val angle = Math.toRadians((step * 36 - 90).toDouble())
                            val x = cx + radius * Math.cos(angle).toFloat()
                            val y = cy + radius * Math.sin(angle).toFloat()
                            if (step == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    },
                color = ink,
            )
        }
        AvatarMotif.LEAF -> {
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.34f, h * 0.68f)
                        cubicTo(w * 0.34f, h * 0.34f, w * 0.60f, h * 0.26f, w * 0.70f, h * 0.32f)
                        cubicTo(w * 0.70f, h * 0.60f, w * 0.50f, h * 0.72f, w * 0.34f, h * 0.68f)
                        close()
                    },
                color = ink,
            )
            drawLineMotif(p(0.38f, 0.66f), p(0.66f, 0.36f), shadow, w * 0.02f)
        }
        AvatarMotif.WAVE -> {
            repeat(2) { row ->
                val y = 0.46f + row * 0.14f
                drawPath(
                    path =
                        Path().apply {
                            moveTo(w * 0.28f, h * y)
                            cubicTo(w * 0.38f, h * (y - 0.08f), w * 0.46f, h * (y + 0.08f), w * 0.56f, h * y)
                            cubicTo(w * 0.64f, h * (y - 0.07f), w * 0.68f, h * (y + 0.04f), w * 0.72f, h * y)
                        },
                    color = ink,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.055f),
                )
            }
        }
        AvatarMotif.BOLT -> {
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.56f, h * 0.24f)
                        lineTo(w * 0.36f, h * 0.54f)
                        lineTo(w * 0.48f, h * 0.54f)
                        lineTo(w * 0.44f, h * 0.76f)
                        lineTo(w * 0.64f, h * 0.46f)
                        lineTo(w * 0.52f, h * 0.46f)
                        close()
                    },
                color = ink,
            )
        }
    }
}

private fun DrawScope.drawRoundRectMotif(
    topLeft: Offset,
    size: Size,
    color: Color,
    corner: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
    )
}

private fun DrawScope.drawLineMotif(
    start: Offset,
    end: Offset,
    color: Color,
    width: Float,
) {
    drawLine(color = color, start = start, end = end, strokeWidth = width)
}

/** Default size for the picker tiles, shared so every screen agrees. */
val AvatarPickerSize: Dp = 52.dp
