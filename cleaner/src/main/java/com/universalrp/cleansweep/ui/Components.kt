package com.universalrp.cleansweep.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.data.JunkKind
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.OutlineC
import com.universalrp.cleansweep.ui.theme.SurfaceC
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary

fun JunkKind.icon(): ImageVector = when (this) {
    JunkKind.RESIDUAL -> Icons.Outlined.DeleteSweep
    JunkKind.THUMBNAILS -> Icons.Outlined.Photo
    JunkKind.DUPLICATES -> Icons.Outlined.ContentCopy
    JunkKind.APK_FILES -> Icons.Outlined.Android
    JunkKind.EMPTY_FOLDERS -> Icons.Outlined.Folder
    JunkKind.OLD_DOWNLOADS -> Icons.Outlined.FileDownload
    JunkKind.LARGE_FILES -> Icons.Outlined.SdStorage
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = SurfaceC,
    ) {
        content()
    }
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, label = "btnAlpha")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(listOf(AccentCyan, AccentViolet))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color(0xFF03202B).copy(alpha = alpha),
            )
            Box(Modifier.size(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF03202B).copy(alpha = alpha),
        )
    }
}

/** Circular gauge showing the used-storage fraction. */
@Composable
fun StorageGauge(usedFraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = usedFraction.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "gauge",
    )
    Canvas(modifier = modifier) {
        val strokeWidth = 20.dp.toPx()
        val side = size.minDimension - strokeWidth
        val arcSize = Size(side, side)
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        drawArc(
            color = Color(0xFF1E2A45),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
        if (animated > 0.004f) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(AccentCyan, AccentViolet),
                    center = Offset(size.width / 2f, size.height / 2f),
                ),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

/** Animated “LED strip” light bar used as decoration across screens. */
@Composable
fun LedBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "led")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ledPhase",
    )
    Canvas(modifier.height(4.dp)) {
        val a = (phase * 1.4f - 0.4f).coerceIn(0f, 1f)
        val b = (phase * 1.4f - 0.2f).coerceIn(0f, 1f)
        val c = (phase * 1.4f).coerceIn(0f, 1f)
        val brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                a to Color.Transparent,
                b to AccentCyan,
                c to Color.Transparent,
                1f to Color.Transparent,
            )
        )
        drawRect(brush)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun ByteChip(bytes: Long, modifier: Modifier = Modifier) {
    Text(
        bytes.formatBytes(),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF122036))
            .border(1.dp, OutlineC, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = AccentCyan,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun StatusDot(active: Boolean, label: String) {
    val color by animateColorAsState(if (active) Color(0xFF34D399) else Color(0xFF64748B), label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Box(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

/**
 * AI answers come back dressed in markdown (**bold**, "- " bullets, "##" headings).
 * This renders them as tidy text with real bold runs and clean bullets, so the screen
 * never shows a wall of asterisks like the old build did.
 */
@Composable
fun AiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val lines = remember(text) { aiLines(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                if (line.kind == 1) {
                    Text(
                        "•",
                        style = style,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Text(
                    text = aiInline(line.text),
                    style = style,
                    color = if (line.kind == 2) AccentCyan else color,
                    fontWeight = if (line.kind == 2) FontWeight.Bold else null,
                )
            }
        }
    }
}

private data class AiLine(val kind: Int, val text: String)

/** kind: 0 = normal, 1 = bullet, 2 = heading. */
private fun aiLines(raw: String): List<AiLine> = raw
    .replace("\r", "")
    .lines()
    .mapNotNull { original ->
        val trimmed = original.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        if (trimmed.all { it == '-' || it == '_' || it == '*' }) return@mapNotNull null
        val heading = trimmed.startsWith("#")
        var body = trimmed.trimStart('#').trim()
        val bullet = body.startsWith("- ") || body.startsWith("* ") || body.startsWith("• ")
        if (bullet) body = body.substring(2).trim()
        when {
            heading -> AiLine(2, body)
            bullet -> AiLine(1, body)
            else -> AiLine(0, body)
        }
    }

/** Turns **bold** runs into real bold text and drops stray back-ticks. */
private fun aiInline(text: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    text.replace("`", "").replace("__", "**").split("**").forEach { chunk ->
        if (chunk.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = if (bold) FontWeight.Bold else null)) {
                append(chunk)
            }
        }
        bold = !bold
    }
}
