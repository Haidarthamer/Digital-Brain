package com.digitallife.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CharcoalBg = Color(0xFF0B0F14)
val CharcoalSurface = Color(0xFF121821)
val CharcoalCard = Color(0xFF1A222E)
val CyanAccent = Color(0xFF00E5FF)
val BlueAccent = Color(0xFF2979FF)
val TextPrimary = Color(0xFFEAF2F8)
val TextSecondary = Color(0xFF8B9BB4)
val ErrorRed = Color(0xFFFF5252)
val SuccessGreen = Color(0xFF00E676)

private val DigitalLifeColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = CharcoalBg,
    secondary = BlueAccent,
    onSecondary = TextPrimary,
    background = CharcoalBg,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

@Composable
fun DigitalLifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DigitalLifeColorScheme,
        content = content
    )
}
