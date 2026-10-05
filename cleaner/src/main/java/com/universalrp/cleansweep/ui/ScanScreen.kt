package com.universalrp.cleansweep.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.data.shortPath
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary

@Composable
fun ScanScreen(state: UiState, vm: MainViewModel) {
    val progress = state.progress

    val transition = rememberInfiniteTransition(label = "scan")
    val ringScale by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringScale",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAlpha",
    )
    val coreRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "coreRotation",
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(240.dp)) {
                val maxRadius = size.minDimension / 2f
                // Expanding radar ring
                drawCircle(
                    color = AccentCyan.copy(alpha = ringAlpha),
                    radius = maxRadius * ringScale,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
                )
                // Static soft disc
                drawCircle(color = SurfaceHigh, radius = maxRadius * 0.42f)
                // Rotating arc
                val arcSize = androidx.compose.ui.geometry.Size(maxRadius * 1.1f, maxRadius * 1.1f)
                drawArc(
                    color = AccentCyan,
                    startAngle = coreRotation,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(
                        (size.width - arcSize.width) / 2f,
                        (size.height - arcSize.height) / 2f,
                    ),
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Scanning your storage…",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            progress?.currentPath?.shortPath() ?: "Starting…",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceHigh, shape = MaterialTheme.shapes.large)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanStat(
                value = (progress?.filesScanned ?: 0L).toString(),
                label = "files scanned",
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(TextSecondary.copy(alpha = 0.25f))
            )
            ScanStat(
                value = (progress?.junkBytes ?: 0L).formatBytes(),
                label = "junk found",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = { vm.cancelScan() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
        ) {
            Text("Cancel scan")
        }
    }
}

@Composable
private fun ScanStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentCyan,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}
