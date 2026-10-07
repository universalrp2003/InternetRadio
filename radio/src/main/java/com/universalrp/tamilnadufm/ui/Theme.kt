package com.universalrp.tamilnadufm.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// A warm, clean palette: deep plum background, saffron primary, gold accent.
val Bg = Color(0xFF0B0713)
val SurfaceC = Color(0xFF171021)
val SurfaceHigh = Color(0xFF211731)
val OutlineC = Color(0xFF33254A)
val Saffron = Color(0xFFFF8A3D)
val Gold = Color(0xFFFFD166)
val Teal = Color(0xFF2FD8C4)
val Good = Color(0xFF4ADE80)
val Warn = Color(0xFFFBBF24)
val Danger = Color(0xFFF87171)
val TextPrimary = Color(0xFFF4EEF9)
val TextSecondary = Color(0xFFA99CBE)

private val DarkScheme = darkColorScheme(
    primary = Saffron,
    onPrimary = Color(0xFF2A1200),
    secondary = Gold,
    onSecondary = Color(0xFF2A1D00),
    tertiary = Teal,
    onTertiary = Color(0xFF00312B),
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
fun TamilFmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = AppShapes,
        content = content,
    )
}
