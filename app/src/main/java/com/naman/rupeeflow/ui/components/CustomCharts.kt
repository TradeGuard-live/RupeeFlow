package com.naman.rupeeflow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.CategorySpending
import com.naman.rupeeflow.domain.model.DailySpending
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.theme.*

@Composable
fun CustomBarChart(
    data: List<DailySpending>,
    modifier: Modifier = Modifier,
    barColor: Color = ElectricBlue,
    activeBarColor: Color = VibrantCyan
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No spending recorded for this period", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxAmount = (data.maxOfOrNull { it.amountPaise } ?: 1L).coerceAtLeast(1L)
    val animProgress = remember { Animatable(0f) }
    var selectedIndex by remember(data) { mutableStateOf<Int?>(null) }

    LaunchedEffect(data) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tapped Bar Info Chip
        val selectedItem = selectedIndex?.let { data.getOrNull(it) }
        if (selectedItem != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = activeBarColor.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, activeBarColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(activeBarColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${selectedItem.dayLabel}: ${CurrencyUtils.formatPaiseToRupees(selectedItem.amountPaise)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(data) {
                        detectTapGestures { offset ->
                            val count = data.size
                            if (count > 0) {
                                val spacing = size.width / (count + 1)
                                val clickedIdx = ((offset.x - spacing / 2f) / spacing).toInt().coerceIn(0, count - 1)
                                selectedIndex = if (selectedIndex == clickedIdx) null else clickedIdx
                            }
                        }
                    }
            ) {
                val count = data.size
                val spacing = size.width / (count + 1)
                val barWidth = (spacing * 0.65f).coerceAtMost(36.dp.toPx())
                val chartHeight = size.height - 24.dp.toPx()

                // Draw horizontal guide lines
                val guideLines = 3
                for (g in 1..guideLines) {
                    val y = chartHeight * (g.toFloat() / (guideLines + 1))
                    drawLine(
                        color = GlassBorder.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw bars
                data.forEachIndexed { index, item ->
                    val x = spacing * (index + 1)
                    val normalizedHeight = (item.amountPaise.toFloat() / maxAmount.toFloat()) * chartHeight
                    val currentHeight = normalizedHeight * animProgress.value
                    val top = chartHeight - currentHeight

                    val isSelected = selectedIndex == index
                    val isMax = item.amountPaise == maxAmount && maxAmount > 0L
                    val color = if (isSelected) activeBarColor else if (isMax) activeBarColor.copy(alpha = 0.85f) else barColor

                    val brush = Brush.verticalGradient(
                        colors = listOf(
                            color,
                            color.copy(alpha = if (isSelected) 0.6f else 0.35f)
                        ),
                        startY = top,
                        endY = chartHeight
                    )

                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x - barWidth / 2, top),
                        size = Size(barWidth, currentHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Selection highlight outline
                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(x - barWidth / 2 - 1.dp.toPx(), top - 1.dp.toPx()),
                            size = Size(barWidth + 2.dp.toPx(), currentHeight + 2.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
        }

        // Labels Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                Text(
                    text = item.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) activeBarColor else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        selectedIndex = if (selectedIndex == index) null else index
                    }
                )
            }
        }
    }
}

@Composable
fun CustomLineChart(
    data: List<DailySpending>,
    modifier: Modifier = Modifier,
    lineColor: Color = ElectricBlue,
    fillGradientStart: Color = VibrantCyan.copy(alpha = 0.3f)
) {
    if (data.isEmpty() || data.size < 2) {
        Box(
            modifier = modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Insufficient trend data", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxAmount = (data.maxOfOrNull { it.amountPaise } ?: 1L).coerceAtLeast(1L)
    val animProgress = remember { Animatable(0f) }
    var selectedIndex by remember(data) { mutableStateOf<Int?>(null) }

    LaunchedEffect(data) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tapped Point Info Chip
        val selectedItem = selectedIndex?.let { data.getOrNull(it) }
        if (selectedItem != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = lineColor.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, lineColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(lineColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${selectedItem.dayLabel}: ${CurrencyUtils.formatPaiseToRupees(selectedItem.amountPaise)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(vertical = 12.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val count = data.size
                        if (count > 1) {
                            val stepX = size.width / (count - 1)
                            val clickedIdx = ((offset.x + stepX / 2f) / stepX).toInt().coerceIn(0, count - 1)
                            selectedIndex = if (selectedIndex == clickedIdx) null else clickedIdx
                        }
                    }
                }
        ) {
            val chartWidth = size.width
            val chartHeight = size.height - 16.dp.toPx()
            val stepX = chartWidth / (data.size - 1)

            val points = data.mapIndexed { index, item ->
                val x = index * stepX
                val normalizedY = (item.amountPaise.toFloat() / maxAmount.toFloat()) * chartHeight
                val y = chartHeight - (normalizedY * animProgress.value)
                Offset(x, y)
            }

            // Build smooth bezier path
            val path = Path()
            val fillPath = Path()

            if (points.isNotEmpty()) {
                path.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, chartHeight)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) / 2
                    path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }

                fillPath.lineTo(points.last().x, chartHeight)
                fillPath.close()

                // Draw filled gradient area
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(fillGradientStart, Color.Transparent),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                // Draw smooth curve stroke
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw data dots
                points.forEachIndexed { index, point ->
                    val isSelected = selectedIndex == index
                    drawCircle(
                        color = MidnightDark,
                        radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = if (isSelected) Color.White else lineColor,
                        radius = if (isSelected) 4.5.dp.toPx() else 2.5.dp.toPx(),
                        center = point
                    )
                }
            }
        }

        // Labels Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                Text(
                    text = item.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) lineColor else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        selectedIndex = if (selectedIndex == index) null else index
                    }
                )
            }
        }
    }
}

@Composable
fun CustomDonutChart(
    breakdown: List<CategorySpending>,
    totalAmountPaise: Long,
    modifier: Modifier = Modifier
) {
    if (breakdown.isEmpty() || totalAmountPaise <= 0L) {
        Box(
            modifier = modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No categorical expenses recorded", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(breakdown) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeWidth = 22.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val centerOffset = Offset(size.width / 2, size.height / 2)

                var startAngle = -90f

                breakdown.forEach { item ->
                    val sweep = (item.totalSpentPaise.toFloat() / totalAmountPaise.toFloat()) * 360f * animProgress.value
                    val sliceColor = CategoryIconHelper.parseColor(item.category.colorHex)

                    drawArc(
                        color = sliceColor,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }

            // Center Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total Spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = CurrencyUtils.formatPaiseCompact(totalAmountPaise),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend chips
        Column(modifier = Modifier.fillMaxWidth()) {
            breakdown.take(5).forEach { item ->
                val sliceColor = CategoryIconHelper.parseColor(item.category.colorHex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(sliceColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = CurrencyUtils.formatPaiseToRupees(item.totalSpentPaise),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${String.format(java.util.Locale.US, "%.1f", item.percentage)}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
