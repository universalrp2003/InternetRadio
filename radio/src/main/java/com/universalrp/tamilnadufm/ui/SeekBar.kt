package com.universalrp.tamilnadufm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalrp.tamilnadufm.player.PlayerBus
import kotlinx.coroutines.delay

/**
 * v1.12: a draggable seek bar for the Now Playing sheet.
 *
 * Two behaviours, because the player has two kinds of content:
 *
 *  * **Recorded file** (local track, podcast): the bar spans the whole duration and a drag
 *    moves the audio to that moment.
 *  * **Live radio**: the stream has no end, so the bar offers the buffered window only
 *    (typically the last 30–60 s) and shows a LIVE pill instead of a total time. Dragging
 *    forward past the live edge is disabled — it would look broken otherwise.
 *
 * The position is read from [PlayerBus.player] once a second while the sheet is open; the
 * player is owned by the playback service, so nothing here creates or releases it.
 */
@Composable
fun SeekBar(
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = Saffron,
) {
    val player = PlayerBus.player
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isLive by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player) {
        while (player != null) {
            if (!dragging) {
                runCatching {
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                    isLive = player.isCurrentMediaItemLive
                    durationMs = player.duration.let { if (it > 0) it else 0L }
                }
            }
            delay(1_000)
        }
    }

    // Live: the usable range is the buffered window behind the live edge.
    val bufferBehindMs = 60_000L
    val rangeStart = if (isLive) (positionMs - bufferBehindMs).coerceAtLeast(0L) else 0L
    val rangeEnd = when {
        isLive -> positionMs.coerceAtLeast(1L)
        durationMs > 0 -> durationMs
        else -> 1L
    }
    val shown = (if (dragging) dragMs else positionMs.toFloat())
        .coerceIn(rangeStart.toFloat(), rangeEnd.toFloat())

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(shown.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            if (isLive) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Warn.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Warn.copy(alpha = 0.35f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("●", fontSize = 9.sp, color = Warn)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Warn,
                        )
                    }
                }
            } else {
                Text(
                    formatTime(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }

        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                dragMs = it
            },
            onValueChangeFinished = {
                dragging = false
                // One seek per gesture, not one per pixel.
                runCatching { player?.seekTo(dragMs.toLong()) }
            },
            valueRange = rangeStart.toFloat()..(rangeEnd.toFloat().coerceAtLeast(rangeStart + 1f)),
            enabled = rangeEnd > rangeStart,
        )

        if (isLive) {
            Row(horizontalArrangement = Arrangement.Start) {
                Text(
                    com.universalrp.updates.AppStrings.translate(
                        "Live stream — drag inside the buffer to go back a little"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
