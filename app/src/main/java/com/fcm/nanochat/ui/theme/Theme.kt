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
    primaryContainer = Color(0xFF3C3227),
    onPrimaryContainer = Stone050,
    secondary = Color(0xFFAEB4A5),
    onSecondary = Charcoal950,
    tertiary = Color(0xFF9EB2C3),
    onTertiary = Color(0xFF111A22),
    background = Charcoal950,
    onBackground = Stone050,
    surface = Charcoal900,
    onSurface = Stone050,
    surfaceVariant = Color(0xFF2A2B27),
    surfaceContainer = Charcoal850,
    surfaceContainerHigh = Charcoal800,
    surfaceContainerHighest = Color(0xFF2D2E2B),
    onSurfaceVariant = Stone400,
    outline = Color(0xFF53534D),
    outlineVariant = Color(0xFF3A3B37)
)

private val LightColorScheme = lightColorScheme(
    primary = Ember500,
    onPrimary = Charcoal950,
    primaryContainer = Cream100,
    onPrimaryContainer = Charcoal950,
    secondary = Color(0xFF656E60),
    onSecondary = Cream050,
    tertiary = Color(0xFF6B7A89),
    onTertiary = Cream050,
    background = Cream050,
    onBackground = Charcoal950,
    surface = Color(0xFFFFFFFF),
    onSurface = Charcoal950,
    surfaceVariant = Color(0xFFF0EBE2),
    surfaceContainer = Color(0xFFF6F1E8),
    surfaceContainerHigh = Color(0xFFEFE7DB),
    surfaceContainerHighest = Color(0xFFE6DCCD),
    onSurfaceVariant = Slate500,
    outline = Color(0xFFC4BBAE),
    outlineVariant = Color(0xFFDDD3C6)
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
