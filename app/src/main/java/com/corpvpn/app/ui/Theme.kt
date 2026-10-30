package com.corpvpn.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app is always dark, regardless of the system setting.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00121C),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF222630),
    onSurfaceVariant = Color(0xFFA9AFBB),
    error = Color(0xFFFF6B6B),
)

@Composable
fun CorpVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
