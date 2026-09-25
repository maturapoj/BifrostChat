package com.example.bifrostchat.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF1F3A68)
val NavyDeep = Color(0xFF0B1426)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF0A1B3D),
    secondary = Color(0xFF4A5F80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF1B2B45),
    tertiary = Color(0xFF3F6AA8),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C22),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE0E4EF),
    onSurfaceVariant = Color(0xFF43474F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F4FC),
    surfaceContainer = Color(0xFFECEFF8),
    surfaceContainerHigh = Color(0xFFE6EAF4),
    surfaceContainerHighest = Color(0xFFE0E4EF),
    outline = Color(0xFF737784),
    outlineVariant = Color(0xFFC3C6D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF0A2A5C),
    primaryContainer = Navy,
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF2E3D57),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFF8FB4F0),
    onTertiary = Color(0xFF0A2A5C),
    background = NavyDeep,
    onBackground = Color(0xFFE1E6F2),
    surface = NavyDeep,
    onSurface = Color(0xFFE1E6F2),
    surfaceVariant = Color(0xFF1C2940),
    onSurfaceVariant = Color(0xFFC3CAD9),
    surfaceContainerLowest = Color(0xFF070E1C),
    surfaceContainerLow = Color(0xFF0F1A2F),
    surfaceContainer = Color(0xFF132038),
    surfaceContainerHigh = Color(0xFF1A2842),
    surfaceContainerHighest = Color(0xFF22314D),
    outline = Color(0xFF8C95A8),
    outlineVariant = Color(0xFF3A4458),
)

@Composable
fun BifrostChatTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
