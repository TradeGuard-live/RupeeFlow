package com.naman.rupeeflow.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =============================================================================
// RUPEEFLOW DESIGN SYSTEM — SEMANTIC COLOR ARCHITECTURE
// =============================================================================

/**
 * First-class semantic color tokens for RupeeFlow.
 * Supports both modern fintech Light Theme and Midnight Dark Theme.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val cardBorder: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primaryAccent: Color,
    val primaryAccentContainer: Color,
    val onPrimaryAccent: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val error: Color,
    val errorContainer: Color,
    val onError: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val info: Color,
    val infoContainer: Color,
    val isDark: Boolean
) {
    val cardBackground: Color get() = surface
    val accentContainer: Color get() = primaryAccentContainer
    val incomeGreen: Color get() = success
    val textMuted: Color get() = textTertiary
    val inputBackground: Color get() = if (isDark) surfaceSecondary else surface
}

// Dedicated Light Design System (Fintech Dashboard Aesthetic)
val LightAppColors = AppColors(
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceSecondary = Color(0xFFF1F5F9),
    surfaceElevated = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE5EAF2),
    divider = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF172033),
    textSecondary = Color(0xFF64748B),
    textTertiary = Color(0xFF94A3B8),
    primaryAccent = Color(0xFF2563EB),
    primaryAccentContainer = Color(0xFFDBEAFE),
    onPrimaryAccent = Color(0xFFFFFFFF),
    success = Color(0xFF059669),
    successContainer = Color(0xFFD1FAE5),
    onSuccess = Color(0xFFFFFFFF),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onError = Color(0xFFFFFFFF),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7),
    onWarning = Color(0xFFFFFFFF),
    info = Color(0xFF0891B2),
    infoContainer = Color(0xFFCFFAFE),
    isDark = false
)

// Dedicated Dark Design System (Midnight Dark Aesthetic)
val DarkAppColors = AppColors(
    background = Color(0xFF0A0E17),
    surface = Color(0xFF111827),
    surfaceSecondary = Color(0xFF1E293B),
    surfaceElevated = Color(0xFF1E293B),
    cardBorder = Color(0x3394A3B8),
    divider = Color(0x1A94A3B8),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    primaryAccent = Color(0xFF3B82F6),
    primaryAccentContainer = Color(0xFF1E293B),
    onPrimaryAccent = Color(0xFFFFFFFF),
    success = Color(0xFF10B981),
    successContainer = Color(0x2610B981),
    onSuccess = Color(0xFF0A0E17),
    error = Color(0xFFEF4444),
    errorContainer = Color(0x26EF4444),
    onError = Color(0xFFF8FAFC),
    warning = Color(0xFFF59E0B),
    warningContainer = Color(0x26F59E0B),
    onWarning = Color(0xFF0A0E17),
    info = Color(0xFF06B6D4),
    infoContainer = Color(0x2606B6D4),
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// =============================================================================
// LEGACY CONSTANTS PRESERVED FOR TESTS & DIRECT REFERENCES
// =============================================================================

val MidnightDark = Color(0xFF0A0E17)
val CharcoalSurface = Color(0xFF111827)
val CharcoalCard = Color(0xFF1E293B)
val CharcoalCardHover = Color(0xFF334155)
val GlassBorder = Color(0x3394A3B8)
val GlassBorderSubtle = Color(0x1A94A3B8)

val ElectricBlue = Color(0xFF3B82F6)
val ElectricBlueLight = Color(0xFF60A5FA)
val VibrantCyan = Color(0xFF06B6D4)
val EmeraldMint = Color(0xFF10B981)
val CoralRed = Color(0xFFEF4444)
val RubyCrimson = Color(0xFFEF4444)
val AmberGold = Color(0xFFF59E0B)
val RoyalPurple = Color(0xFF8B5CF6)
val SoftPink = Color(0xFFEC4899)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val LightBackground = Color(0xFFF6F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFF1F5F9)
val LightTextPrimary = Color(0xFF172033)
val LightTextSecondary = Color(0xFF64748B)
val LightBorder = Color(0xFFE5EAF2)
