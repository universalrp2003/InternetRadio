package com.universalrp.pulseeq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.pulseeq.EqUiState
import com.universalrp.pulseeq.EqViewModel
import com.universalrp.pulseeq.audio.Bands
import com.universalrp.pulseeq.audio.EqPreset
import com.universalrp.pulseeq.audio.Presets

@Composable
fun PresetsScreen(state: EqUiState, vm: EqViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = Cyan)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Presets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Ready-made curves for music, movies and speech — apply one, then fine-tune it by hand",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        items(Presets.ALL) { preset ->
            PresetRow(
                preset = preset,
                selected = preset.name == state.presetName,
                onClick = { vm.applyPreset(preset) },
            )
        }

        item {
            PanelCard(Modifier.fillMaxWidth(), color = SurfaceHigh) {
                Text(
                    "Your current curve: ${state.presetName}. Once you move a fader it becomes “Custom” " +
                        "and keeps its own shape — presets only change the 20 gains, never your LED or " +
                        "background settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun PresetRow(preset: EqPreset, selected: Boolean, onClick: () -> Unit) {
    PanelCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Cyan.copy(alpha = 0.18f) else SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(preset.icon, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Cyan else TextPrimary,
                )
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                if (kotlin.math.abs(preset.preampDb) > 0.1f) {
                    Text(
                        "preamp %.1f dB".format(preset.preampDb),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            CurveSparkline(preset.gains, selected)
        }
    }
}

/** Tiny 20-point curve drawing so each preset is recognisable at a glance. */
@Composable
private fun CurveSparkline(gains: FloatArray, highlighted: Boolean) {
    Canvas(
        Modifier
            .width(84.dp)
            .height(40.dp)
    ) {
        val mid = size.height / 2f
        val stepX = size.width / (gains.size - 1).coerceAtLeast(1)
        val path = Path()
        gains.forEachIndexed { i, g ->
            val x = i * stepX
            val y = mid - (g / Bands.MAX_DB) * (size.height * 0.42f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // Zero line
        drawLine(
            color = OutlineC,
            start = Offset(0f, mid),
            end = Offset(size.width, mid),
            strokeWidth = 1f,
        )
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                if (highlighted) listOf(Cyan, Violet) else listOf(TextSecondary, TextSecondary)
            ),
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }
}
