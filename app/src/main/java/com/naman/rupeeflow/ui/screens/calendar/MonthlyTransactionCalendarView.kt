package com.naman.rupeeflow.ui.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.MascotEmptyState
import com.naman.rupeeflow.ui.components.MascotPose
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.components.TransactionItemCard
import com.naman.rupeeflow.ui.theme.AppTheme
import com.naman.rupeeflow.ui.theme.GlassBorder

@Composable
fun MonthlyTransactionCalendarView(
    gridData: MonthlyCalendarGridData?,
    selectedDateMillis: Long,
    selectedDaySummary: CalendarDaySummary?,
    onDateSelected: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onAddTransactionForDate: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.isDark
    val now = System.currentTimeMillis()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month Navigation Header Card
        item(key = "calendar_header_card") {
            RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Month Navigation Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPreviousMonth,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Month",
                                tint = AppTheme.colors.textPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = gridData?.monthTitle ?: "Calendar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Today Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.primaryAccentContainer,
                                border = BorderStroke(
                                    1.dp,
                                    AppTheme.colors.primaryAccent.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.clickable { onTodayClick() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Today,
                                        contentDescription = "Today",
                                        modifier = Modifier.size(14.dp),
                                        tint = AppTheme.colors.primaryAccent
                                    )
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.primaryAccent
                                    )
                                }
                            }

                            IconButton(
                                onClick = onNextMonth,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Month",
                                    tint = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }

                    // Month Summary Stats (Total Expenses / Total Income / Total Refund)
                    if (gridData != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Month Total: ${CurrencyUtils.formatPaiseToRupees(gridData.totalMonthExpensePaise, showDecimals = false)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.expenseRed
                            )
                            if (gridData.totalMonthIncomePaise > 0L) {
                                Text(
                                    text = "Income: ${CurrencyUtils.formatPaiseToRupees(gridData.totalMonthIncomePaise, showDecimals = false)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.incomeGreen
                                )
                            }
                            if (gridData.totalMonthRefundPaise > 0L) {
                                Text(
                                    text = "Refunds: ${CurrencyUtils.formatPaiseToRupees(gridData.totalMonthRefundPaise, showDecimals = false)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                        }
                    }

                    // Monday-First Day of Week Header
                    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        dayNames.forEach { name ->
                            Text(
                                text = name,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }

                    // 7-Column Calendar Grid
                    if (gridData != null) {
                        val cells = gridData.cells
                        val totalRows = gridData.totalRowCount
                        for (r in 0 until totalRows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (c in 0 until 7) {
                                    val cellIndex = r * 7 + c
                                    val cell = if (cellIndex < cells.size) cells[cellIndex] else null
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cell != null) {
                                            val isSelected = cell.dateMillis != null &&
                                                    MonthlyCalendarEngine.isSameDay(cell.dateMillis, selectedDateMillis)
                                            val isToday = cell.dateMillis != null &&
                                                    MonthlyCalendarEngine.isSameDay(cell.dateMillis, now)

                                            CalendarCellItem(
                                                cell = cell,
                                                isSelected = isSelected,
                                                isToday = isToday,
                                                onDateSelected = {
                                                    cell.dateMillis?.let { onDateSelected(it) }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Date Summary & Actions Card
        item(key = "selected_date_card") {
            RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Complete Formatted Date String & Add Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = MonthlyCalendarEngine.getFormattedDateLabel(selectedDateMillis),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            val txnSummaryText = if (selectedDaySummary != null && selectedDaySummary.hasTransactions) {
                                "${selectedDaySummary.transactionCount} transaction${if (selectedDaySummary.transactionCount == 1) "" else "s"}"
                            } else {
                                "No transactions recorded"
                            }
                            Text(
                                text = txnSummaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        // Add Transaction for this date Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppTheme.colors.primaryAccent,
                            contentColor = Color.White,
                            modifier = Modifier.clickable { onAddTransactionForDate(selectedDateMillis) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add for this date",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Text(
                                    text = "Add",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Daily Financial Totals (Expense / Income / Refund)
                    if (selectedDaySummary != null && selectedDaySummary.hasTransactions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppTheme.colors.surfaceSecondary)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Expense",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(selectedDaySummary.totalExpensePaise, showDecimals = false),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.expenseRed
                                )
                            }

                            if (selectedDaySummary.totalIncomePaise > 0L) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Income",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = CurrencyUtils.formatPaiseToRupees(selectedDaySummary.totalIncomePaise, showDecimals = false),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.incomeGreen
                                    )
                                }
                            }

                            if (selectedDaySummary.totalRefundPaise > 0L) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Refund",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = CurrencyUtils.formatPaiseToRupees(selectedDaySummary.totalRefundPaise, showDecimals = false),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3B82F6)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Transactions list for selected date
        val transactions = selectedDaySummary?.transactions ?: emptyList()
        if (transactions.isEmpty()) {
            item(key = "empty_day_state") {
                MascotEmptyState(
                    pose = MascotPose.THINKING,
                    title = "No transactions on this day",
                    subtitle = "Tap + Add to record an expense for this date."
                )
            }
        } else {
            items(
                items = transactions,
                key = { "cal_txn_${it.id}" }
            ) { txn ->
                TransactionItemCard(
                    transaction = txn,
                    onClick = { onTransactionClick(txn) }
                )
            }
        }
    }
}

@Composable
private fun CalendarCellItem(
    cell: CalendarGridCell,
    isSelected: Boolean,
    isToday: Boolean,
    onDateSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.isDark
    val isCurrentMonth = cell.isCurrentMonth
    val summary = cell.summary

    if (!isCurrentMonth) {
        // Cells outside current month
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            cell.dayOfMonth?.let { day ->
                Text(
                    text = day.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val cellBackground = when {
        isSelected -> AppTheme.colors.primaryAccentContainer
        else -> AppTheme.colors.surfaceElevated
    }

    val cellBorder = when {
        isSelected -> BorderStroke(1.5.dp, AppTheme.colors.primaryAccent)
        isToday -> BorderStroke(1.5.dp, AppTheme.colors.primaryAccent.copy(alpha = 0.6f))
        else -> BorderStroke(0.5.dp, if (isDark) GlassBorder else AppTheme.colors.cardBorder)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cellBackground,
        border = cellBorder,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(vertical = 2.dp)
            .clickable { onDateSelected() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Day Number
            Text(
                text = (cell.dayOfMonth ?: "").toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isToday || isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textPrimary
            )

            // Financial Indicator (Expense in red, Income in green, Refund in blue)
            if (summary != null && summary.hasTransactions) {
                val amountText = when {
                    summary.totalExpensePaise > 0L -> CurrencyUtils.formatPaiseCompact(summary.totalExpensePaise)
                    summary.totalIncomePaise > 0L -> CurrencyUtils.formatPaiseCompact(summary.totalIncomePaise)
                    summary.totalRefundPaise > 0L -> CurrencyUtils.formatPaiseCompact(summary.totalRefundPaise)
                    else -> ""
                }
                val amountColor = when {
                    summary.totalExpensePaise > 0L -> AppTheme.colors.expenseRed
                    summary.totalIncomePaise > 0L -> AppTheme.colors.incomeGreen
                    summary.totalRefundPaise > 0L -> Color(0xFF3B82F6)
                    else -> AppTheme.colors.textSecondary
                }

                if (amountText.isNotEmpty()) {
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
