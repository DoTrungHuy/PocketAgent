package com.agentpad.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF0F766E)
val AccentDark = Color(0xFF5EEAD4)
val Success = Color(0xFF15803D)
val Warning = Color(0xFFB45309)
val Danger = Color(0xFFB91C1C)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5EF),
    onPrimaryContainer = Color(0xFF073B36),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1C1D1B),
    surface = Color.White,
    onSurface = Color(0xFF1C1D1B),
    surfaceVariant = Color(0xFFECEAE4),
    onSurfaceVariant = Color(0xFF5D605C),
    outline = Color(0xFFC6C5BE),
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF135E57),
    onPrimaryContainer = Color(0xFFB9FFF4),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE6E9E5),
    surface = Color(0xFF191D1A),
    onSurface = Color(0xFFE6E9E5),
    surfaceVariant = Color(0xFF252A27),
    onSurfaceVariant = Color(0xFFB9BDB8),
    outline = Color(0xFF4B514D),
    error = Color(0xFFFFB4AB)
)

@Composable
fun AgentPadTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
