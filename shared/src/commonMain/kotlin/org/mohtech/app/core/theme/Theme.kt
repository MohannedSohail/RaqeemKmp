package org.mohtech.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = RaqeemColors.EmeraldGreen,
    onPrimary = RaqeemColors.BackgroundSlate,
    secondary = RaqeemColors.ElectricCyan,
    onSecondary = RaqeemColors.BackgroundSlate,
    background = RaqeemColors.BackgroundSlate,
    onBackground = RaqeemColors.TextPrimary,
    surface = RaqeemColors.SurfaceCard,
    onSurface = RaqeemColors.TextPrimary,
    surfaceVariant = RaqeemColors.SurfaceElevated,
    onSurfaceVariant = RaqeemColors.TextSecondary,
    outline = RaqeemColors.SurfaceBorder,
    error = RaqeemColors.CoralRose,
    onError = RaqeemColors.TextPrimary
)

@Composable
fun RaqeemTheme(
    darkTheme: Boolean = true, // Default to Fintech Slate Dark
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val cairoFont = getCairoFontFamily()
    val typography = remember(cairoFont) { createRaqeemTypography(cairoFont) }

    // Enforce Arabic RTL globally
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
