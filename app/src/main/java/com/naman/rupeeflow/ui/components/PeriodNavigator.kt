package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.ui.theme.*

/**
 * Reusable date-period navigation component providing consistent backward and forward
 * navigation across Weekly, Monthly, and Yearly reports.
 *
 * Supports disabled states (preventing future period navigation), accessible content descriptions,
 * responsive layout, and adaptive styling for both dark and light themes.
 */
@Composable
fun PeriodNavigator(
    label: String,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onCurrent: (() -> Unit)? = null,
    showCurrentPill: Boolean = false,
    currentPillText: String = "Today",
    contentDescriptionPrefix: String = "Period"
) {
    val isDark = AppTheme.isDark

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Label Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Navigation Controls Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Previous Period Arrow
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(36.dp),
                enabled = canNavigatePrevious
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous $contentDescriptionPrefix",
                    tint = if (canNavigatePrevious) AppTheme.colors.primaryAccent else AppTheme.colors.textMuted
                )
            }

            // Optional "Today" / "Current" Pill
            if (showCurrentPill && onCurrent != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) ElectricBlue.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCurrent() }
                ) {
                    Text(
                        text = currentPillText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) ElectricBlueLight else Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Next Period Arrow (Disabled when current period is selected)
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(36.dp),
                enabled = canNavigateNext
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next $contentDescriptionPrefix",
                    tint = if (canNavigateNext) AppTheme.colors.primaryAccent else AppTheme.colors.textMuted
                )
            }
        }
    }
}

