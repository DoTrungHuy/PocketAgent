package com.agentpad.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Graphite = Color(0xFF0A0E12)
val GraphiteRaised = Color(0xFF11171D)
val GraphiteSoft = Color(0xFF172028)
val ElectricCyan = Color(0xFF3DE1D1)
val ElectricCyanDim = Color(0xFF1E8E86)
val Mist = Color(0xFFE7F4F2)
val Steel = Color(0xFF94A5AC)
val Warning = Color(0xFFFFC56E)
val Danger = Color(0xFFFF7C82)
val Success = Color(0xFF67D69E)

private val AgentPadColors = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF123B38),
    onPrimaryContainer = Color(0xFFB4FFF5),
    secondary = Color(0xFF9DCFC9),
    onSecondary = Color(0xFF06201D),
    background = Graphite,
    onBackground = Mist,
    surface = GraphiteRaised,
    onSurface = Mist,
    surfaceVariant = GraphiteSoft,
    onSurfaceVariant = Steel,
    error = Danger,
    onError = Color(0xFF370006),
    outline = Color(0xFF33434A)
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
