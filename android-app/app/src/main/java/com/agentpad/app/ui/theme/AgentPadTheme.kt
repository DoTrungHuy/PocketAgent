package com.agentpad.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Graphite = Color(0xFFF6F3ED)
val GraphiteRaised = Color(0xFFFFFFFF)
val GraphiteSoft = Color(0xFFE9E4DA)
val ElectricCyan = Color(0xFF0F766E)
val ElectricCyanDim = Color(0xFF115E59)
val Mist = Color(0xFF171717)
val Steel = Color(0xFF5F625F)
val Warning = Color(0xFFB45309)
val Danger = Color(0xFFB91C1C)
val Success = Color(0xFF15803D)

private val AgentPadColors = lightColorScheme(
    primary = ElectricCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3EF),
    onPrimaryContainer = Color(0xFF053632),
    secondary = Color(0xFF6B6257),
    onSecondary = Color.White,
    background = Graphite,
    onBackground = Mist,
    surface = GraphiteRaised,
    onSurface = Mist,
    surfaceVariant = GraphiteSoft,
    onSurfaceVariant = Steel,
    error = Danger,
    onError = Color.White,
    outline = Color(0xFFB9B2A7)
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
