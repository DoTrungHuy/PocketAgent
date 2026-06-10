package com.agentpad.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Graphite = Color(0xFF14171B)
val GraphiteRaised = Color(0xFF20252B)
val GraphiteSoft = Color(0xFF2B323A)
val ElectricCyan = Color(0xFF7DEFE1)
val ElectricCyanDim = Color(0xFF3DBDB1)
val Mist = Color(0xFFF5FAF9)
val Steel = Color(0xFFC0CBD1)
val Warning = Color(0xFFFFCF75)
val Danger = Color(0xFFFF8E98)
val Success = Color(0xFF83E5AD)

private val AgentPadColors = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF08211E),
    primaryContainer = Color(0xFF245551),
    onPrimaryContainer = Color(0xFFE8FFFC),
    secondary = Color(0xFFC3DFDA),
    onSecondary = Color(0xFF102421),
    background = Graphite,
    onBackground = Mist,
    surface = GraphiteRaised,
    onSurface = Mist,
    surfaceVariant = GraphiteSoft,
    onSurfaceVariant = Steel,
    error = Danger,
    onError = Color(0xFF3D0008),
    outline = Color(0xFF6B7882)
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
