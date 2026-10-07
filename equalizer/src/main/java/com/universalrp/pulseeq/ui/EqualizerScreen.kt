package com.universalrp.pulseeq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.universalrp.pulseeq.EqTab
import com.universalrp.pulseeq.EqUiState
import com.universalrp.pulseeq.EqViewModel
import com.universalrp.pulseeq.audio.Bands
import com.universalrp.pulseeq.audio.EqCore

@Composable
fun EqualizerScreen(state: EqUiState, vm: EqViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state, vm) }

        item {
            LedSpectrum(
                engine = EqCore.eq,
                gains = state.gains,
                bypassed = state.bypass,
                brightness = state.ledBrightness,
                animate = state.ledAnimation,
                live = state.ledAnimation && !state.bypass,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { StatusRow(state, vm) }

        item {
            BandBoard(state, vm)
        }

        item { PreampCard(state, vm) }

        item { LevelTools(state, vm) }

        item {
            Text(
                "Tip: PulseEQ's own 20-band engine runs inside the built-in player (Player tab) so the " +
                    "curve is exact. The system-wide effect and cooperating players use the device's own " +
                    "equalizer engine, which usually has fewer bands — PulseEQ maps your curve onto it.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun Header(state: EqUiState, vm: EqViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Cyan, Violet))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Equalizer, contentDescription = null, tint = Color(0xFF04202A))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "PulseEQ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "20-band sound equalizer",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        IconButton(onClick = { vm.resetFlat() }) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Reset to flat", tint = TextSecondary)
        }
        IconButton(onClick = { vm.setBypass(!state.bypass) }) {
            Icon(
                Icons.Outlined.PowerSettingsNew,
                contentDescription = "Bypass",
                tint = if (state.bypass) Danger else Good,
            )
        }
    }
}

@Composable
private fun StatusRow(state: EqUiState, vm: EqViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(
            text = if (state.bypass) "BYPASSED" else state.presetName.uppercase(),
            color = if (state.bypass) Danger else Cyan,
        )
        StatusPill(
            text = if (state.serviceRunning) "BACKGROUND ON" else "BACKGROUND OFF",
            color = if (state.serviceRunning) Good else TextSecondary,
        )
        StatusPill(
            text = when {
                state.systemEqActive -> "SYSTEM-WIDE"
                state.cooperatingSessions > 0 -> "${state.cooperatingSessions} PLAYER(S)"
                else -> "PLAYER ONLY"
            },
            color = if (state.systemEqActive) Violet else TextSecondary,
        )
    }
}

/**
 * The 20 faders. Each one is a vertical strip; drag up for boost, down for cut,
 * double-tap the label to zero that band. Gains are applied to the DSP instantly
 * as you drag, so you hear the change while you make it.
 */
@Composable
private fun BandBoard(state: EqUiState, vm: EqViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "20 bands",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "drag to shape • tap label to zero",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (i in 0 until Bands.COUNT) {
                    BandFader(
                        index = i,
                        gain = state.gains.getOrElse(i) { 0f },
                        onGain = { vm.setBand(i, it) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            ) {
                for (i in 0 until Bands.COUNT) {
                    Text(
                        Bands.label(i),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BandFader(
    index: Int,
    gain: Float,
    onGain: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = Bands.MAX_DB - Bands.MIN_DB
    var dragStart by remember { mutableStateOf(gain) }

    Box(modifier) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Gain readout
            Text(
                if (gain >= 0.05f) "+${"%.1f".format(gain)}" else "%.1f".format(gain),
                style = MaterialTheme.typography.labelSmall,
                color = if (kotlin.math.abs(gain) < 0.05f) TextSecondary else Cyan,
                fontWeight = if (kotlin.math.abs(gain) < 0.05f) FontWeight.Normal else FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))

            // The rail + LED fill
            Box(
                Modifier
                    .weight(1f)
                    .width(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(SurfaceHigh)
                    .border(1.dp, OutlineC, RoundedCornerShape(9.dp))
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            val perPixel = range / 1400f
                            dragStart = (dragStart - delta * perPixel)
                                .coerceIn(Bands.MIN_DB, Bands.MAX_DB)
                            onGain(dragStart)
                        },
                        onDragStarted = { dragStart = gain },
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val fraction = ((gain - Bands.MIN_DB) / range).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (gain >= 0f) Cyan else Danger,
                                    if (gain >= 0f) Violet else Danger.copy(alpha = 0.6f),
                                )
                            )
                        )
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PreampCard(state: EqUiState, vm: EqViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = Cyan)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Preamp",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%.1f dB".format(state.preampDb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cyan,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Pulls the whole curve down so big boosts can't clip. It follows your boosts automatically, " +
                    "but you can trim it by hand here.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Slider(
                value = state.preampDb,
                onValueChange = { vm.setPreamp(it) },
                valueRange = -12f..0f,
                colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan),
            )
        }
    }
}

@Composable
private fun LevelTools(state: EqUiState, vm: EqViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = Warn)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "LED spectrum",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.ledAnimation)
                            "Bars follow the real audio energy in each band"
                        else
                            "Bars show your EQ curve instead of live audio",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = state.ledAnimation,
                    onCheckedChange = { vm.setLedAnimation(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF04202A),
                        checkedTrackColor = Cyan,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Brightness",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Slider(
                value = state.ledBrightness,
                onValueChange = { vm.setLedBrightness(it) },
                valueRange = 0.35f..1f,
                colors = SliderDefaults.colors(thumbColor = Warn, activeTrackColor = Warn),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Shift the whole curve",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconChip(Icons.Outlined.Tune, "Louder +2 dB") { vm.nudgeAll(2f) }
                IconChip(Icons.Outlined.Tune, "Quieter −2 dB") { vm.nudgeAll(-2f) }
                IconChip(Icons.Outlined.Equalizer, "Presets", tint = Violet) { vm.navigate(EqTab.PRESETS) }
            }
        }
    }
}
