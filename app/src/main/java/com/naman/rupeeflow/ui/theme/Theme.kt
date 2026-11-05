package com.naman.rupeeflow.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =============================================================================
// RUPEEFLOW THEME ACCESSOR
// =============================================================================

object AppTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current

    val isDark: Boolean
        @Composable
        get() = LocalAppColors.current.isDark
}

// =============================================================================
// MATERIAL 3 COLOR SCHEMES
// =============================================================================

private val DarkColorScheme = darkColorScheme(
    primary = DarkAppColors.primaryAccent,
    onPrimary = DarkAppColors.onPrimaryAccent,
    primaryContainer = DarkAppColors.primaryAccentContainer,
    onPrimaryContainer = DarkAppColors.textPrimary,
    secondary = DarkAppColors.info,
    onSecondary = DarkAppColors.background,
    secondaryContainer = DarkAppColors.surfaceSecondary,
    onSecondaryContainer = DarkAppColors.info,
    tertiary = DarkAppColors.success,
    onTertiary = DarkAppColors.background,
    tertiaryContainer = DarkAppColors.surfaceSecondary,
    onTertiaryContainer = DarkAppColors.success,
    background = DarkAppColors.background,
    onBackground = DarkAppColors.textPrimary,
    surface = DarkAppColors.surface,
    onSurface = DarkAppColors.textPrimary,
    surfaceVariant = DarkAppColors.surfaceSecondary,
    onSurfaceVariant = DarkAppColors.textSecondary,
    outline = DarkAppColors.cardBorder,
    outlineVariant = DarkAppColors.divider,
    error = DarkAppColors.error,
    onError = DarkAppColors.textPrimary,
    errorContainer = DarkAppColors.errorContainer,
    onErrorContainer = DarkAppColors.error
)

private val LightColorScheme = lightColorScheme(
    primary = LightAppColors.primaryAccent,
    onPrimary = LightAppColors.onPrimaryAccent,
    primaryContainer = LightAppColors.primaryAccentContainer,
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = LightAppColors.info,
    onSecondary = LightAppColors.onPrimaryAccent,
    secondaryContainer = LightAppColors.infoContainer,
    onSecondaryContainer = Color(0xFF0E7490),
    tertiary = LightAppColors.success,
    onTertiary = LightAppColors.onPrimaryAccent,
    tertiaryContainer = LightAppColors.successContainer,
    onTertiaryContainer = Color(0xFF065F46),
    background = LightAppColors.background,
    onBackground = LightAppColors.textPrimary,
    surface = LightAppColors.surface,
    onSurface = LightAppColors.textPrimary,
    surfaceVariant = LightAppColors.surfaceSecondary,
    onSurfaceVariant = LightAppColors.textSecondary,
    outline = LightAppColors.cardBorder,
    outlineVariant = LightAppColors.divider,
    error = LightAppColors.error,
    onError = LightAppColors.onError,
    errorContainer = LightAppColors.errorContainer,
    onErrorContainer = Color(0xFF991B1B)
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

    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = appColors.background.toArgb()
                window.navigationBarColor = appColors.surface.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
