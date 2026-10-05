package com.universalrp.pulseeq.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Bg = Color(0xFF05070F)
val SurfaceC = Color(0xFF0E1424)
val SurfaceHigh = Color(0xFF16203A)
val OutlineC = Color(0xFF23304F)
val Cyan = Color(0xFF22D3EE)
val Violet = Color(0xFF8B5CF6)
val Good = Color(0xFF34D399)
val Warn = Color(0xFFFBBF24)
val Danger = Color(0xFFF87171)
val TextPrimary = Color(0xFFE8EDF9)
val TextSecondary = Color(0xFF8E9CBC)

private val DarkScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF04202A),
    secondary = Violet,
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
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = AppShapes,
        content = content,
    )
}
