package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which part of the picture a double tap landed on. */
internal enum class SeekZone { BACK, MIDDLE, FORWARD }

/** Which edge a vertical swipe started on: brightness on the left, volume on the right. */
internal enum class LevelSide { BRIGHTNESS, VOLUME }

/**
 * What a gesture just did, drawn over the picture for a moment.
 *
 * Without it a double tap that moved ten seconds looked exactly like one that did nothing, and a
 * swipe gave no hint of where the level had landed until the sound or the screen changed.
 */
internal sealed interface GestureFeedback {
    data class Seek(val seconds: Int) : GestureFeedback
    data class Level(val side: LevelSide, val value: Float) : GestureFeedback
    data class Scale(val mode: VideoScaleMode) : GestureFeedback
}

/**
 * The outer thirds seek, the middle pauses — the layout every phone player has taught people.
 *
 * Thirds rather than halves so that a tap meant for pausing, which naturally lands near the
 * centre, never jumps the film instead.
 */
internal fun seekZoneFor(x: Float, width: Float): SeekZone =
    when {
        width <= 0f -> SeekZone.MIDDLE
        x < width / 3f -> SeekZone.BACK
        x > width * 2f / 3f -> SeekZone.FORWARD
        else -> SeekZone.MIDDLE
    }

internal fun levelSideFor(x: Float, width: Float): LevelSide =
    if (x < width / 2f) LevelSide.BRIGHTNESS else LevelSide.VOLUME

/**
 * The level after a swipe of [upwardFraction] of the screen's height.
 *
 * A little faster than one-to-one, so that a comfortable thumb movement covers the whole range in
 * landscape, where the screen is short.
 */
internal fun adjustedLevel(current: Float, upwardFraction: Float): Float =
    (current + upwardFraction * LEVEL_SWIPE_GAIN).coerceIn(0f, 1f)

/**
 * Seconds a run of double taps has moved so far.
 *
 * Taps in the same direction add up, as people expect when they tap four times to skip forty
 * seconds; a tap the other way starts a fresh count rather than cancelling out.
 */
internal fun accumulatedSeekSeconds(previous: Int?, zone: SeekZone): Int {
    val step = when (zone) {
        SeekZone.BACK -> -DOUBLE_TAP_SEEK_SECONDS
        SeekZone.FORWARD -> DOUBLE_TAP_SEEK_SECONDS
        SeekZone.MIDDLE -> return 0
    }
    return if (previous != null && previous != 0 && (previous > 0) == (step > 0)) previous + step else step
}

/**
 * Touch gestures over the video: tap, double tap, swipes on either side and a pinch.
 *
 * [enabled] covers everything but the plain tap, which must keep working while the controls are
 * locked or on a television so the screen can still be woken. Swipes starting at the very top or
 * bottom are left to the system, whose own swipes bring the status bar and the home gesture.
 */
internal fun Modifier.playerGestures(
    enabled: Boolean,
    /** Restarts the detectors when it changes, so the callbacks never act on a released player. */
    key: Any?,
    onTap: () -> Unit,
    onDoubleTap: (SeekZone) -> Unit,
    onLevelDragStart: (LevelSide) -> Unit,
    onLevelDrag: (LevelSide, upwardFraction: Float) -> Unit,
    onLevelDragEnd: () -> Unit,
    onPinch: (zoomIn: Boolean) -> Unit,
): Modifier =
    this
        .pointerInput(enabled, key) {
            detectTapGestures(
                onTap = { onTap() },
                // Null when disabled, so a single tap is not held back waiting for a second one.
                onDoubleTap =
                    if (enabled) {
                        { offset: Offset -> onDoubleTap(seekZoneFor(offset.x, size.width.toFloat())) }
                    } else {
                        null
                    },
            )
        }
        // After the tap detector in the chain, so it sees each event first: consuming a swipe here
        // is what stops the tap detector reading the end of that swipe as a tap.
        .pointerInput(enabled, key) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val systemEdge = SYSTEM_EDGE_DP.dp.toPx()
                val startsAtSystemEdge =
                    down.position.y < systemEdge || down.position.y > size.height - systemEdge
                var dragSide: LevelSide? = null
                var pinchStartSpan = 0f
                var pinchHandled = false
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break

                    if (pressed.size >= 2 && dragSide == null) {
                        val span = (pressed[0].position - pressed[1].position).getDistance()
                        if (pinchStartSpan == 0f) {
                            pinchStartSpan = span
                        } else if (!pinchHandled && pinchStartSpan > 0f) {
                            val ratio = span / pinchStartSpan
                            if (ratio > PINCH_THRESHOLD || ratio < 1f / PINCH_THRESHOLD) {
                                onPinch(ratio > 1f)
                                pinchHandled = true
                            }
                        }
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    if (pinchStartSpan > 0f) {
                        // A pinch that drops to one finger stays a pinch: the remaining finger
                        // lifting off is not a swipe the user meant.
                        event.changes.forEach { it.consume() }
                        continue
                    }

                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (dragSide == null && !startsAtSystemEdge) {
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx) * VERTICAL_DOMINANCE) {
                            dragSide = levelSideFor(down.position.x, size.width.toFloat())
                            onLevelDragStart(dragSide)
                        }
                    }
                    val side = dragSide
                    if (side != null) {
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        onLevelDrag(side, -change.positionChange().y / height)
                        change.consume()
                    }
                }
                if (dragSide != null) onLevelDragEnd()
            }
        }

/** The badge a gesture leaves over the picture. */
@Composable
internal fun GestureFeedbackBadge(
    feedback: GestureFeedback,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BuroCanvas.copy(alpha = 0.78f))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (feedback) {
            is GestureFeedback.Seek -> {
                Icon(
                    imageVector = if (feedback.seconds < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                    contentDescription = null,
                    tint = BuroTextPrimary,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    text =
                        if (feedback.seconds < 0) {
                            stringResource(R.string.player_gesture_seek_back, -feedback.seconds)
                        } else {
                            stringResource(R.string.player_gesture_seek_forward, feedback.seconds)
                        },
                    color = BuroTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            is GestureFeedback.Level -> {
                Icon(
                    imageVector =
                        if (feedback.side == LevelSide.VOLUME) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.Default.BrightnessHigh
                        },
                    contentDescription =
                        stringResource(
                            if (feedback.side == LevelSide.VOLUME) R.string.player_volume else R.string.player_brightness,
                        ),
                    tint = BuroTextPrimary,
                    modifier = Modifier.size(30.dp),
                )
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(feedback.value.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(BuroAccent),
                    )
                }
                Text(
                    text = "${(feedback.value * 100).roundToInt()}%",
                    color = BuroTextPrimary,
                    fontSize = 15.sp,
                )
            }

            is GestureFeedback.Scale -> {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = BuroTextPrimary,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = stringResource(feedback.mode.labelResource),
                    color = BuroTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

internal const val DOUBLE_TAP_SEEK_SECONDS = 10
private const val LEVEL_SWIPE_GAIN = 1.4f
private const val PINCH_THRESHOLD = 1.2f
private const val VERTICAL_DOMINANCE = 1.5f
private const val SYSTEM_EDGE_DP = 40
