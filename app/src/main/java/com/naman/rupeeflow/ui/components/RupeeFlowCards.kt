package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.ui.theme.*

@Composable
fun rupeeFlowTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppTheme.colors.primaryAccent,
    unfocusedBorderColor = AppTheme.colors.cardBorder,
    focusedContainerColor = AppTheme.colors.surface,
    unfocusedContainerColor = AppTheme.colors.surface,
    focusedTextColor = AppTheme.colors.textPrimary,
    unfocusedTextColor = AppTheme.colors.textPrimary,
    focusedLabelColor = AppTheme.colors.primaryAccent,
    unfocusedLabelColor = AppTheme.colors.textSecondary,
    cursorColor = AppTheme.colors.primaryAccent,
    errorBorderColor = AppTheme.colors.error,
    errorContainerColor = AppTheme.colors.surface
)

@Composable
fun RupeeFlowDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
        tonalElevation = if (AppTheme.isDark) 0.dp else 4.dp,
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun RupeeFlowCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppTheme.colors.surface,
    borderColor: Color = AppTheme.colors.cardBorder,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier.clip(shape)
    }

    Surface(
        modifier = cardModifier,
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun HeroSummaryCard(
    title: String,
    amountFormatted: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isDark = AppTheme.isDark
    val gradient = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.15f),
                CharcoalCard.copy(alpha = 0.95f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFEFF6FF),
                Color(0xFFFFFFFF)
            )
        )
    }

    val cardBg = if (isDark) CharcoalCard else Color.White
    val borderStroke = if (isDark) {
        BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, Color(0xFFBFDBFE))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = borderStroke
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) accentColor.copy(alpha = 0.2f) else Color(0xFFDBEAFE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isDark) accentColor else Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = amountFormatted,
                    style = MaterialTheme.typography.displayLarge,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
fun MiniStatCard(
    title: String,
    amountFormatted: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isDark = AppTheme.isDark
    RupeeFlowCard(
        modifier = modifier,
        backgroundColor = AppTheme.colors.surface,
        borderColor = if (isDark) accentColor.copy(alpha = 0.3f) else AppTheme.colors.cardBorder,
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = amountFormatted,
            style = MaterialTheme.typography.titleLarge,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}
