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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.ui.theme.*

@Composable
fun RupeeFlowCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    cornerRadius: Dp = 20.dp,
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
    val gradient = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.15f),
            CharcoalCard.copy(alpha = 0.95f)
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = CharcoalCard,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
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
                        color = TextSecondary,
                        letterSpacing = 1.2.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = amountFormatted,
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
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
    RupeeFlowCard(
        modifier = modifier,
        borderColor = accentColor.copy(alpha = 0.3f),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = amountFormatted,
            style = MaterialTheme.typography.titleLarge,
            color = accentColor
        )
    }
}
