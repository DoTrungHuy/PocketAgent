package com.agentpad.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Graphite = Color(0xFF070A0D)
val GraphiteRaised = Color(0xFF0E1318)
val GraphiteSoft = Color(0xFF171E25)
val ElectricCyan = Color(0xFF46E6D2)
val ElectricCyanDim = Color(0xFF207D77)
val Mist = Color(0xFFEAF7F5)
val Steel = Color(0xFF8FA1A9)
val Warning = Color(0xFFFFC46B)
val Danger = Color(0xFFFF7A84)
val Success = Color(0xFF68D99A)

private val AgentPadColors = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF001F1C),
    primaryContainer = Color(0xFF103A36),
    onPrimaryContainer = Color(0xFFB8FFF6),
    secondary = Color(0xFF9CCDC7),
    onSecondary = Color(0xFF06201D),
    background = Graphite,
    onBackground = Mist,
    surface = GraphiteRaised,
    onSurface = Mist,
    surfaceVariant = GraphiteSoft,
    onSurfaceVariant = Steel,
    error = Danger,
    onError = Color(0xFF370006),
    outline = Color(0xFF263640)
)

@Composable
fun AgentPadTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AgentPadColors,
        content = content
    )
}
