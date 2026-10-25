package com.translate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BlueDarkTheme,
    onPrimary = BackgroundDark,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = SurfaceLight,
    primaryContainer = GoogleBlueLight,
    onPrimaryContainer = GoogleBlueDark,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    outline = OutlineLight
)

@Composable
fun SimpleTranslateTheme(
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
