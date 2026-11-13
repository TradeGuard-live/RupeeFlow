package com.naman.rupeeflow.ui.screens.report

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.naman.rupeeflow.domain.model.ReportDraft
import com.naman.rupeeflow.domain.model.ReportPeriodType
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.report.PdfReportGenerator
import com.naman.rupeeflow.ui.components.PeriodNavigator
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportGeneratorScreen(
    viewModel: ReportGeneratorViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showManageTransactionsDialog by remember { mutableStateOf(false) }
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    var showDraftsListDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Expense Report Generator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    if (state.savedDrafts.isNotEmpty()) {
                        IconButton(onClick = { showDraftsListDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = "Saved Drafts",
                                tint = AppTheme.colors.primaryAccent
                            )
                        }
                    }
                    IconButton(onClick = { showSaveDraftDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Draft",
                            tint = AppTheme.colors.primaryAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
            )
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period Type Chips
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportPeriodType.values().forEach { type ->
                        val isSelected = state.periodType == type
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.surfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setPeriodType(type) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Period Selector controls
            item {
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    when (state.periodType) {
                        ReportPeriodType.WEEKLY -> {
                            val startFormatted = state.selectedWeekStart.format(DateTimeFormatter.ofPattern("dd MMM"))
                            val endFormatted = state.selectedWeekStart.plusDays(6).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.selectWeek(state.selectedWeekStart.minusWeeks(1)) }) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Week", tint = AppTheme.colors.primaryAccent)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Monday Start Week", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                                    Text("$startFormatted – $endFormatted", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                }
                                IconButton(onClick = { viewModel.selectWeek(state.selectedWeekStart.plusWeeks(1)) }) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Week", tint = AppTheme.colors.primaryAccent)
                                }
                            }
                        }
                        ReportPeriodType.MONTHLY -> {
                            val ym = java.time.YearMonth.of(state.selectedYear, state.selectedMonth)
                            val label = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    val prev = ym.minusMonths(1)
                                    viewModel.selectMonth(prev.year, prev.monthValue)
                                }) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = AppTheme.colors.primaryAccent)
                                }
                                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                IconButton(onClick = {
                                    val next = ym.plusMonths(1)
                                    viewModel.selectMonth(next.year, next.monthValue)
                                }) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = AppTheme.colors.primaryAccent)
                                }
                            }
                        }
                        ReportPeriodType.YEARLY -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.selectYear(state.selectedYear - 1) }) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year", tint = AppTheme.colors.primaryAccent)
                                }
                                Text("${state.selectedYear}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                IconButton(onClick = { viewModel.selectYear(state.selectedYear + 1) }) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Year", tint = AppTheme.colors.primaryAccent)
                                }
                            }
                        }
                        ReportPeriodType.CUSTOM -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Custom Date Range",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${DateTimeUtils.formatDate(state.customStartDate, "dd MMM yyyy")} to ${DateTimeUtils.formatDate(state.customEndDate, "dd MMM yyyy")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.primaryAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            // Quick preset: Last 30 days
                                            val end = System.currentTimeMillis()
                                            val start = end - 30L * 24 * 60 * 60 * 1000L
                                            viewModel.setCustomDateRange(start, end)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Last 30 Days", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            // Quick preset: Last 90 days
                                            val end = System.currentTimeMillis()
                                            val start = end - 90L * 24 * 60 * 60 * 1000L
                                            viewModel.setCustomDateRange(start, end)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Last 90 Days", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Summary Financial Preview
            item {
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Report Statement Preview",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Personal Expenses", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                            Text(
                                CurrencyUtils.formatPaiseToRupees(state.totalExpensesPaise),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (AppTheme.isDark) CoralRed else Color(0xFFDC2626)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Personal Income", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                            Text(
                                CurrencyUtils.formatPaiseToRupees(state.totalIncomePaise),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (AppTheme.isDark) EmeraldMint else Color(0xFF059669)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = AppTheme.colors.divider)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.totalRefundsPaise > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Personal Refunds", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text(
                                "+ ${CurrencyUtils.formatPaiseToRupees(state.totalRefundsPaise)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (AppTheme.isDark) EmeraldMint else Color(0xFF059669)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Net Personal Spending", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(
                            CurrencyUtils.formatPaiseToRupees(state.netSpendingPaise),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                    }

                    if (state.grossBillsPaise > 0L && state.grossBillsPaise != state.totalExpensesPaise) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Bills (All Participants)", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textTertiary)
                            Text(
                                CurrencyUtils.formatPaiseToRupees(state.grossBillsPaise),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textTertiary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cash vs Online", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(
                            "${CurrencyUtils.formatPaiseToRupees(state.cashSpendingPaise)} / ${CurrencyUtils.formatPaiseToRupees(state.onlineSpendingPaise)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Transactions Included", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(
                            "${state.includedTransactions.size} of ${state.rawTransactionsInRange.size} (${state.excludedTransactionIds.size} excluded)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.primaryAccent
                        )
                    }
                }
            }

            // Excluded Transactions Management
            item {
                RupeeFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showManageTransactionsDialog = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.accentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Manage Included Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (state.excludedTransactionIds.isEmpty()) "All transactions included" else "${state.excludedTransactionIds.size} transactions omitted from report",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                }
            }

            // Category Distribution Preview
            if (state.categoryBreakdown.isNotEmpty()) {
                item {
                    RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Top Spending Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        state.categoryBreakdown.take(5).forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${cat.categoryName} (${cat.transactionCount})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "${CurrencyUtils.formatPaiseToRupees(cat.amountPaise)} (${String.format(java.util.Locale.ROOT, "%.1f%%", cat.percentage)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Generated PDF Banner & Share Actions
            state.generatedPdfFile?.let { file ->
                item {
                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = AppTheme.colors.primaryAccent,
                        backgroundColor = AppTheme.colors.accentContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PDF Report Ready!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { PdfReportGenerator.sharePdf(context, file) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppTheme.colors.primaryAccent,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share PDF", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        PdfReportGenerator.sharePdf(context, file)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open PDF")
                            }
                        }
                    }
                }
            }

            // Generate Button
            item {
                Button(
                    onClick = { viewModel.generatePdf(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primaryAccent,
                        contentColor = Color.White
                    ),
                    enabled = !state.isGeneratingPdf && state.includedTransactions.isNotEmpty()
                ) {
                    if (state.isGeneratingPdf) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Generating PDF Statement...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Expense Report (PDF)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Dialog to manage included / excluded transactions
    if (showManageTransactionsDialog) {
        Dialog(onDismissRequest = { showManageTransactionsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AppTheme.colors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Included Transactions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "${state.includedTransactions.size} included • ${state.excludedTransactionIds.size} excluded",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                        IconButton(onClick = { showManageTransactionsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Uncheck any transaction to omit it from this report statement. This will NOT modify your ledger or delete actual transaction data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textTertiary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.setAllTransactionsExcluded(false) }) {
                            Text("Include All", color = AppTheme.colors.primaryAccent)
                        }
                        TextButton(onClick = { viewModel.setAllTransactionsExcluded(true) }) {
                            Text("Exclude All", color = AppTheme.colors.textSecondary)
                        }
                    }

                    HorizontalDivider(color = AppTheme.colors.divider)

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.rawTransactionsInRange, key = { it.id }) { txn ->
                            val isIncluded = !state.excludedTransactionIds.contains(txn.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.toggleTransactionExclusion(txn.id) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isIncluded,
                                    onCheckedChange = { viewModel.toggleTransactionExclusion(txn.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AppTheme.colors.primaryAccent,
                                        uncheckedColor = AppTheme.colors.textSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = txn.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isIncluded) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
                                        fontWeight = if (isIncluded) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${DateTimeUtils.formatDate(txn.transactionDate, "dd MMM")} • ${txn.category?.name ?: "Other"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textTertiary
                                    )
                                }
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(txn.amountPaise),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncluded) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showManageTransactionsDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Save Draft Dialog
    if (showSaveDraftDialog) {
        var draftName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDraftDialog = false },
            title = { Text("Save Report Draft", color = AppTheme.colors.textPrimary) },
            text = {
                Column {
                    Text("Save this report's period, date range, and excluded transactions as a preset:", color = AppTheme.colors.textSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = { Text("e.g. Monthly Tax Statement, Trip Expenses") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveDraft(draftName)
                        showSaveDraftDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primaryAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Save Draft")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDraftDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Drafts List Dialog
    if (showDraftsListDialog) {
        Dialog(onDismissRequest = { showDraftsListDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AppTheme.colors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Saved Report Drafts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                        IconButton(onClick = { showDraftsListDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.savedDrafts, key = { it.id }) { draft ->
                            RupeeFlowCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    viewModel.loadDraft(draft)
                                    showDraftsListDialog = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(draft.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                        Text(
                                            "${draft.periodType.displayName} • ${DateTimeUtils.formatDate(draft.startDate, "dd MMM")} to ${DateTimeUtils.formatDate(draft.endDate, "dd MMM yyyy")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteDraft(draft.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Draft", tint = CoralRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

