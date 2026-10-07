package com.universalrp.appforge.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Bg = Color(0xFF0B0F1E)
val SurfaceC = Color(0xFF131A2E)
val SurfaceHigh = Color(0xFF1B2440)
val OutlineC = Color(0xFF2A3554)
val Accent = Color(0xFF22D3EE)
val Accent2 = Color(0xFF8B5CF6)
val Good = Color(0xFF34D399)
val Warn = Color(0xFFFBBF24)
val Danger = Color(0xFFF87171)
val TextPrimary = Color(0xFFE7ECF7)
val TextSecondary = Color(0xFF96A2BE)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF04202A),
    secondary = Accent2,
    onSecondary = Color(0xFF1B1040),
    tertiary = Good,
    background = Bg,
    onBackground = TextPrimary,
    surface = SurfaceC,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = OutlineC,
    error = Danger,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    // AppForge is dark-first, like the rest of the workspace.
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = AppShapes,
        content = content,
    )
}
