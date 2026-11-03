package com.naman.rupeeflow.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = MidnightDark,
    primaryContainer = CharcoalCard,
    onPrimaryContainer = TextPrimary,
    secondary = VibrantCyan,
    onSecondary = MidnightDark,
    secondaryContainer = CharcoalCard,
    onSecondaryContainer = VibrantCyan,
    tertiary = EmeraldMint,
    onTertiary = MidnightDark,
    background = MidnightDark,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    error = CoralRed,
    onError = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = LightSurface,
    primaryContainer = LightCard,
    onPrimaryContainer = LightTextPrimary,
    secondary = VibrantCyan,
    onSecondary = LightSurface,
    secondaryContainer = LightCard,
    onSecondaryContainer = LightTextPrimary,
    tertiary = EmeraldMint,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = CoralRed,
    onError = LightSurface
)

@Composable
fun RupeeFlowTheme(
    themeMode: String = "DARK", // "DARK", "LIGHT", "SYSTEM"
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true // Dark by default
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
