package com.fcm.nanochat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Ember500,
    onPrimary = Charcoal950,
    primaryContainer = Color(0xFF4A2A1E),
    onPrimaryContainer = Stone050,
    secondary = Stone200,
    onSecondary = Charcoal950,
    tertiary = Ember400,
    onTertiary = Charcoal950,
    background = Charcoal950,
    onBackground = Stone050,
    surface = Charcoal900,
    onSurface = Stone050,
    surfaceContainer = Charcoal850,
    surfaceContainerHigh = Charcoal800,
    surfaceContainerHighest = Color(0xFF363636),
    onSurfaceVariant = Stone400,
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF343434)
)

private val LightColorScheme = lightColorScheme(
    primary = Ember500,
    onPrimary = Cream050,
    primaryContainer = Cream100,
    onPrimaryContainer = Charcoal950,
    secondary = Slate700,
    onSecondary = Cream050,
    tertiary = Ember400,
    onTertiary = Charcoal950,
    background = Cream050,
    onBackground = Charcoal950,
    surface = Color(0xFFFFFFFF),
    onSurface = Charcoal950,
    surfaceContainer = Color(0xFFF7F2EA),
    surfaceContainerHigh = Color(0xFFF1EBDD),
    surfaceContainerHighest = Color(0xFFE9E3D8),
    onSurfaceVariant = Slate500,
    outline = Color(0xFFC8C0B2),
    outlineVariant = Color(0xFFE0D8CB)
)

@Composable
fun NanoChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
