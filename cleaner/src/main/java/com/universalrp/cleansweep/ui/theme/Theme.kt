package com.universalrp.cleansweep.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

val Bg = Color(0xFF070D1A)
val SurfaceC = Color(0xFF0E1627)
val SurfaceHigh = Color(0xFF16213A)
val OutlineC = Color(0xFF24304E)
val AccentCyan = Color(0xFF22D3EE)
val AccentViolet = Color(0xFF8B5CF6)
val GoodGreen = Color(0xFF34D399)
val WarnAmber = Color(0xFFFBBF24)
val DangerRed = Color(0xFFF87171)
val TextPrimary = Color(0xFFE5EAF5)
val TextSecondary = Color(0xFF93A0BC)

private val DarkScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF032A33),
    primaryContainer = Color(0xFF0E3A46),
    onPrimaryContainer = AccentCyan,
    secondary = AccentViolet,
    onSecondary = Color(0xFF1B1040),
    tertiary = GoodGreen,
    onTertiary = Color(0xFF052E22),
    background = Bg,
    onBackground = TextPrimary,
    surface = SurfaceC,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = OutlineC,
    error = DangerRed,
    onError = Color(0xFF3D0707),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun CleanSweepTheme(content: @Composable () -> Unit) {
    // The app is designed dark-first; always use the dark scheme.
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = AppShapes,
        content = content,
    )
}
