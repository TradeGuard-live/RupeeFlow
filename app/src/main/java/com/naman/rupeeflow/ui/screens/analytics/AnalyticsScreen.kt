package com.naman.rupeeflow.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val data = state.analyticsData

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            item {
                Text(
                    text = "Financial Analytics",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
            }

            // Period Selector Tabs (Weekly, Monthly, Yearly)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalCard)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly").forEach { (key, label) ->
                        val isSelected = state.selectedPeriod == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { viewModel.setPeriod(key) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) ElectricBlueLight else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Total Spending Card with % change vs previous period
            item {
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL SPENDING",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )

                        if (data.percentageChangeVsPrevious != 0f) {
                            val isHigher = data.percentageChangeVsPrevious > 0f
                            val badgeColor = if (isHigher) CoralRed else EmeraldMint
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = badgeColor.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isHigher) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${kotlin.math.abs(data.percentageChangeVsPrevious).toInt()}% vs prev",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(data.totalSpentPaise),
                        style = MaterialTheme.typography.displayLarge,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Daily average: ${CurrencyUtils.formatPaiseToRupees(data.dailyAveragePaise)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Income & Savings Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStatCard(
                        title = "Income Recorded",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(data.totalIncomePaise),
                        accentColor = EmeraldMint,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatCard(
                        title = "Savings",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(data.totalSavingsPaise),
                        accentColor = VibrantCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Cash vs Online Breakdown
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStatCard(
                        title = "Online Spending",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(data.onlineSpendingPaise),
                        accentColor = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatCard(
                        title = "Cash Spending",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(data.cashSpendingPaise),
                        accentColor = AmberGold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Spending Trend Chart (With Month/Year Info & Line/Bar Toggle)
            item {
                var isBarChart by remember { mutableStateOf(true) }

                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Spending Trend",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            if (state.periodInfo.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = state.periodInfo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlueLight
                                )
                            }
                        }

                        // Toggle between Line and Bar Chart
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CharcoalCard)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { isBarChart = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isBarChart) ElectricBlue.copy(alpha = 0.25f) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Bar Chart",
                                    tint = if (isBarChart) ElectricBlue else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { isBarChart = false },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (!isBarChart) ElectricBlue.copy(alpha = 0.25f) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = "Line Chart",
                                    tint = if (!isBarChart) ElectricBlue else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isBarChart) {
                        CustomBarChart(data = data.dailySpendingTrend, modifier = Modifier.fillMaxWidth())
                    } else {
                        CustomLineChart(data = data.dailySpendingTrend, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Category Distribution Donut Chart
            item {
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Category Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    CustomDonutChart(
                        breakdown = data.categoryBreakdown,
                        totalAmountPaise = data.totalSpentPaise,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Friends Debt & Credit Overview in Analytics
            item {
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Friend Debt & Credit Position",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Total Friends Owe You", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatPaiseToRupees(state.totalOwedToMe),
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldMint
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Total You Owe Friends", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatPaiseToRupees(state.totalIOwe),
                                style = MaterialTheme.typography.titleLarge,
                                color = CoralRed
                            )
                        }
                    }
                }
            }
        }
    }
}
