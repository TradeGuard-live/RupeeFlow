package com.naman.rupeeflow.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.ExpenseAllocationCalculator
import com.naman.rupeeflow.ui.theme.AppTheme
import java.util.Calendar

@Composable
fun ExpenseSpreadingSection(
    isSpreadEnabled: Boolean,
    onSpreadEnabledChange: (Boolean) -> Unit,
    startDateMillis: Long,
    onStartDateChange: (Long) -> Unit,
    durationDays: Int,
    onDurationDaysChange: (Int) -> Unit,
    amountPaise: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val safeDuration = durationDays.coerceIn(ExpenseAllocationCalculator.MIN_DURATION_DAYS, ExpenseAllocationCalculator.MAX_DURATION_DAYS)
    val endDateMillis = remember(startDateMillis, safeDuration) {
        ExpenseAllocationCalculator.calculateEndDate(startDateMillis, safeDuration)
    }

    val allocations = remember(amountPaise, safeDuration, startDateMillis) {
        if (amountPaise > 0L && safeDuration > 1) {
            ExpenseAllocationCalculator.calculateAllocations(amountPaise, safeDuration, startDateMillis)
        } else emptyList()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Expense Duration",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Checkbox row to enable/disable spreading
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppTheme.colors.cardBackground,
            border = BorderStroke(1.dp, if (isSpreadEnabled) AppTheme.colors.primaryAccent.copy(alpha = 0.5f) else AppTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSpreadEnabledChange(!isSpreadEnabled) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Checkbox(
                    checked = isSpreadEnabled,
                    onCheckedChange = { onSpreadEnabledChange(it) },
                    colors = CheckboxDefaults.colors(checkedColor = AppTheme.colors.primaryAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spread this expense across days",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = if (isSpreadEnabled) "Spread over $safeDuration days (${DateTimeUtils.formatDate(startDateMillis, "dd MMM")} - ${DateTimeUtils.formatDate(endDateMillis, "dd MMM")})" else "Normal expense (1 day)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSpreadEnabled) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary
                    )
                }
            }
        }

        if (isSpreadEnabled) {
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppTheme.colors.cardBackground.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Start Date Picker Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Start date:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppTheme.colors.surfaceSecondary.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            modifier = Modifier.clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val updated = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        onStartDateChange(updated.timeInMillis)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = DateTimeUtils.formatDate(startDateMillis, "d MMMM yyyy"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Duration Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duration:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (safeDuration > ExpenseAllocationCalculator.MIN_DURATION_DAYS) {
                                        onDurationDaysChange(safeDuration - 1)
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                                enabled = safeDuration > ExpenseAllocationCalculator.MIN_DURATION_DAYS
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease duration",
                                    tint = if (safeDuration > ExpenseAllocationCalculator.MIN_DURATION_DAYS) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppTheme.colors.surfaceSecondary.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.widthIn(min = 60.dp)
                            ) {
                                Text(
                                    text = "$safeDuration days",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (safeDuration < ExpenseAllocationCalculator.MAX_DURATION_DAYS) {
                                        onDurationDaysChange(safeDuration + 1)
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                                enabled = safeDuration < ExpenseAllocationCalculator.MAX_DURATION_DAYS
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase duration",
                                    tint = if (safeDuration < ExpenseAllocationCalculator.MAX_DURATION_DAYS) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // End Date Row (also clickable to set duration)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "End date:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppTheme.colors.surfaceSecondary.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            modifier = Modifier.clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = endDateMillis }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val picked = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        val days = ExpenseAllocationCalculator.calculateDurationDays(startDateMillis, picked.timeInMillis)
                                        onDurationDaysChange(days)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = DateTimeUtils.formatDate(endDateMillis, "d MMMM yyyy"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }

                    Text(
                        text = "Maximum duration is 365 days (1 year).",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textTertiary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = AppTheme.colors.cardBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Daily Allocation Summary
                    if (amountPaise > 0L && safeDuration > 1) {
                        val baseDailyPaise = amountPaise / safeDuration
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily allocation:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "₹${CurrencyUtils.formatPaiseToRupees(baseDailyPaise)} per day",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primaryAccent
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Preview Breakdown List
                        Text(
                            text = "Preview:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val previewItems = if (allocations.size <= 8) {
                            allocations
                        } else {
                            allocations.take(4) + allocations.takeLast(4)
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            previewItems.forEachIndexed { idx, alloc ->
                                if (allocations.size > 8 && idx == 4) {
                                    Text(
                                        text = "• • •  (${allocations.size - 8} more days)  • • •",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textTertiary,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        fontSize = 11.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = ExpenseAllocationCalculator.formatPreviewDate(alloc.allocationDate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = "₹${CurrencyUtils.formatPaiseToRupees(alloc.amountPaise)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = AppTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Enter amount and set duration > 1 day to see daily allocation preview.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}
