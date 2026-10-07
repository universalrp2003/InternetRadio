package com.universalrp.pulseeq.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.universalrp.pulseeq.audio.Bands

/**
 * The LED light bars.
 *
 * Each of the 20 columns is a stack of small LED segments (like a real spectrum
 * analyser) that light up to the real, measured energy of that band — the engine
 * fills [levels] while audio flows through it. When audio is paused the segments
 * fall and a soft idle shimmer keeps the display alive, clearly marked as idle.
 *
 * Segment colours are the classic green → amber → red ladder.
 */
@Composable
fun LedSpectrum(
    engine: com.universalrp.pulseeq.audio.EqEngine,
    gains: List<Float>,
    bypassed: Boolean,
    brightness: Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
    live: Boolean = true,
) {
    val idle = rememberInfiniteTransition(label = "ledIdle")
    val shimmer by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Canvas(modifier.height(150.dp)) {
        val useLevels = animate && live
        val barCount = Bands.COUNT
        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val segments = 16
        val segGap = size.height * 0.008f
        val segHeight = (size.height - segGap * (segments - 1)) / segments

        for (b in 0 until barCount) {
            val x = b * (barWidth + gap)

            // How high this column should be: measured energy again, or the
            // user's own EQ gain when the LEDs run in "show my curve" mode.
            val level: Float = if (useLevels) {
                engine.levels[b]
            } else {
                val gain = gains.getOrElse(b) { 0f }
                (0.45f + gain / (Bands.MAX_DB * 2f)).coerceIn(0.08f, 1f)
            }

            var phase = (shimmer * 1.6f - b * 0.04f)
            phase -= kotlin.math.floor(phase)
            val idlePulse = 0.12f + 0.10f * kotlin.math.sin(phase * 6.2832f)
            val levelFinal = (if (useLevels) level else level).coerceIn(0f, 1f)
            val lit = if (useLevels) {
                levelFinal
            } else {
                (levelFinal * 0.55f + idlePulse * 0.45f).coerceIn(0.05f, 1f)
            }

            for (s in 0 until segments) {
                val fromBottom = s.toFloat() / segments
                val on = fromBottom <= lit
                val peak = engine.peaks[b]
                val isPeak = useLevels && kotlin.math.abs(fromBottom + 0.5f / segments - peak) < 0.035f

                val color = when {
                    bypassed -> LedGreen.copy(alpha = 0.18f)
                    isPeak -> Color.White.copy(alpha = 0.85f * brightness)
                    on && fromBottom > 0.78f -> LedRed.copy(alpha = brightness)
                    on && fromBottom > 0.55f -> LedAmber.copy(alpha = brightness)
                    on -> LedGreen.copy(alpha = brightness)
                    else -> LedDim.copy(alpha = 0.30f * brightness)
                }

                val y = size.height - (s + 1) * (segHeight + segGap)
                val glow = if (on && !bypassed) 1.0f else 0.0f
                if (glow > 0f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                color.copy(alpha = (color.alpha * 0.55f).coerceIn(0f, 1f)),
                                color,
                            )
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, segHeight),
                        cornerRadius = CornerRadius(barWidth * 0.35f),
                    )
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, segHeight),
                        cornerRadius = CornerRadius(barWidth * 0.35f),
                    )
                }
            }
        }
    }
}

val LedGreen = Color(0xFF2FE38B)
val LedAmber = Color(0xFFFFC53D)
val LedRed = Color(0xFFFF5A6E)
val LedDim = Color(0xFF1C2740)
