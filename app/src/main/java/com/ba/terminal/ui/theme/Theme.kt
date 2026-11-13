package com.ba.terminal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Option 3 — Minimal Glass Clean
val GlassBg = Color(0xFF121417)
val GlassCard = Color(0xFF1C1F24)
val GlassBlue = Color(0xFFA8C7FA)
val GlassText = Color(0xFFE8EAED)
val GlassMuted = Color(0xFF9AA0A6)
val GlassGreen = Color(0xFF34D399)

private val Scheme = darkColorScheme(
    primary = GlassBlue,
    onPrimary = Color(0xFF121417),
    background = GlassBg,
    surface = GlassCard,
    onBackground = GlassText,
    onSurface = GlassText,
    secondary = GlassGreen
)

@Composable
fun BaTerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

val MonoFont = FontFamily.Monospace
