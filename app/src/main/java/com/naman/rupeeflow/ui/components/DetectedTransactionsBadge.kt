package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.ui.theme.*

/**
 * Shared reusable Detected Transactions badge/button used consistently on Overview and Transactions screens.
 * Reuses identical background, text color, icon color, typography, border, shape, padding, height, and click behavior.
 */
@Composable
fun DetectedTransactionsBadge(
    onClick: () -> Unit,
    pendingCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.isDark
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (pendingCount > 0) {
            if (isDark) AmberGold.copy(alpha = 0.2f) else Color(0xFFFEF3C7)
        } else {
            if (isDark) CharcoalCard else AppTheme.colors.surface
        },
        border = BorderStroke(
            1.dp,
            if (pendingCount > 0) {
                if (isDark) AmberGold.copy(alpha = 0.6f) else Color(0xFFF59E0B)
            } else {
                if (isDark) GlassBorder else AppTheme.colors.cardBorder
            }
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pendingCount > 0) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDark) AmberGold else Color(0xFFD97706))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$pendingCount SMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) AmberGold else Color(0xFFD97706),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = "Detected",
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Detected",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
    }
}

