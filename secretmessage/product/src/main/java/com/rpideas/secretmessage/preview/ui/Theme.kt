package com.rpideas.secretmessage.preview.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Night = Color(0xFF101922)
internal val Panel = Color(0xFF192633)
internal val PanelRaised = Color(0xFF223342)
internal val Accent = Color(0xFFB9D7FF)
internal val Warm = Color(0xFFFFC983)
internal val Ink = Color(0xFFF3F6FA)
internal val Muted = Color(0xFFB2BFCA)

// WhisperPad input-deck palette.
internal val DeckBackground = Color(0xFF10141D)
internal val ComposerBackground = Color(0xFF1B2230)
internal val ComposerBorder = Color(0xFF2E3B52)
internal val CanvasBackground = Color(0xFF0B0F17)
internal val StrokeInk = Color(0xFF00E5FF)
internal val GlyphBackground = Color(0xFF121824)
internal val KeyBackground = Color(0xFF1E2736)
internal val KeyBorder = Color(0xFF3B4B68)
internal val KeyModifier = Color(0xFF2D3748)
internal val KeyDelete = Color(0xFF4A5568)
internal val SendGreen = Color(0xFF00B894)
internal val InsertBlue = Color(0xFF0984E3)
internal val DrawPurple = Color(0xFF6C5CE7)
internal val ToolGrey = Color(0xFF2D3436)

@Composable
internal fun SecretMessageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Warm,
            background = Night,
            surface = Panel,
            onPrimary = Night,
            onSecondary = Night,
            onBackground = Ink,
            onSurface = Ink,
        ),
        content = content,
    )
}
