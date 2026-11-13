package com.naman.rupeeflow.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*
import com.naman.rupeeflow.voice.ui.VoiceAssistantSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateToPending: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    var showFastAddSheet by remember { mutableStateOf(false) }
    var showVoiceAssistant by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        containerColor = AppTheme.colors.background,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { showVoiceAssistant = true },
                    containerColor = AppTheme.colors.surfaceElevated,
                    contentColor = AppTheme.colors.primaryAccent,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Assistant",
                        modifier = Modifier.size(20.dp)
                    )
                }
                FloatingActionButton(
                    onClick = { showFastAddSheet = true },
                    containerColor = AppTheme.colors.primaryAccent,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        val isDark = AppTheme.isDark
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.headlineLarge,
                    color = AppTheme.colors.textPrimary
                )
                if (onNavigateToPending != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) CharcoalCard else AppTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) GlassBorder else AppTheme.colors.cardBorder
                        ),
                        modifier = Modifier.clickable { onNavigateToPending() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = "Detected",
                                tint = AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detected",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search title, merchant, ref no...", color = AppTheme.colors.textTertiary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.textSecondary)
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
                            }
                        }
                        IconButton(onClick = { showVoiceAssistant = true }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Assistant", tint = AppTheme.colors.primaryAccent)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                colors = rupeeFlowTextFieldColors()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Group A: Transaction Type Filters (All, Expenses, Income, Refunds, Transfers, Ignored)
            val typeFilters = listOf(
                "ALL" to "All",
                "EXPENSE" to "Expenses",
                "INCOME" to "Income",
                "REFUND" to "Refunds",
                "TRANSFER" to "Transfers",
                "IGNORED" to "Ignored"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                typeFilters.forEach { (key, label) ->
                    val isSelected = state.selectedTypeFilter == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) {
                            if (isDark) ElectricBlue.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer
                        } else {
                            if (isDark) CharcoalCard else AppTheme.colors.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AppTheme.colors.primaryAccent else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                        ),
                        modifier = Modifier.clickable { viewModel.onTypeFilterSelected(key) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                if (isDark) ElectricBlueLight else Color(0xFF1D4ED8)
                            } else {
                                AppTheme.colors.textSecondary
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Group B: Time Period Filters (All Time, Monthly, Weekly)
            val dateFilters = listOf(
                "ALL" to "All Time",
                "MONTHLY" to "Monthly",
                "WEEKLY" to "Weekly"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                dateFilters.forEach { (key, label) ->
                    val isSelected = state.selectedDateFilter == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            if (isDark) ElectricBlue.copy(alpha = 0.25f) else AppTheme.colors.primaryAccentContainer
                        } else {
                            if (isDark) CharcoalCard else AppTheme.colors.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AppTheme.colors.primaryAccent else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                        ),
                        modifier = Modifier.clickable { viewModel.onDateFilterSelected(key) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                if (isDark) ElectricBlueLight else Color(0xFF1D4ED8)
                            } else {
                                AppTheme.colors.textSecondary
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Date Period Navigator for Monthly / Weekly filter
            if (state.selectedDateFilter == "MONTHLY" || state.selectedDateFilter == "WEEKLY") {
                Spacer(modifier = Modifier.height(10.dp))
                PeriodNavigator(
                    label = state.datePeriodLabel,
                    canNavigatePrevious = state.canNavigatePreviousDate,
                    canNavigateNext = state.canNavigateNextDate,
                    onPrevious = { viewModel.previousDatePeriod() },
                    onNext = { viewModel.nextDatePeriod() },
                    showCurrentPill = state.datePeriodOffset != 0,
                    onCurrent = { viewModel.currentDatePeriod() },
                    currentPillText = if (state.selectedDateFilter == "WEEKLY") "This Week" else "This Month",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentDescriptionPrefix = if (state.selectedDateFilter == "WEEKLY") "week" else "month"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Group C: Payment Method Filters (UPI, UPI Lite, Cash, Debit Card, Credit Card)
            val paymentMethods = listOf(
                PaymentMethod.UPI,
                PaymentMethod.UPI_LITE,
                PaymentMethod.CASH,
                PaymentMethod.DEBIT_CARD,
                PaymentMethod.CREDIT_CARD
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                paymentMethods.forEach { method ->
                    val isSelected = state.selectedPaymentMethodFilter == method
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            if (isDark) VibrantCyan.copy(alpha = 0.2f) else Color(0xFFCFFAFE)
                        } else {
                            if (isDark) CharcoalCard else AppTheme.colors.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) {
                                if (isDark) VibrantCyan else Color(0xFF0891B2)
                            } else {
                                if (isDark) GlassBorder else AppTheme.colors.cardBorder
                            }
                        ),
                        modifier = Modifier.clickable {
                            viewModel.onPaymentMethodFilterSelected(if (isSelected) null else method)
                        }
                    ) {
                        Text(
                            text = method.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                if (isDark) VibrantCyan else Color(0xFF0E7490)
                            } else {
                                AppTheme.colors.textSecondary
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Display Mode Toggle: Group by Expense Group vs Individual Transactions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) CharcoalCard else AppTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) GlassBorder else AppTheme.colors.cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Group by Trip button
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (state.isGroupedByExpenseGroup) {
                                AppTheme.colors.primaryAccent
                            } else Color.Transparent,
                            modifier = Modifier.clickable { viewModel.setGroupingMode(true) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (state.isGroupedByExpenseGroup) Color.White else AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Group by Trip",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isGroupedByExpenseGroup) Color.White else AppTheme.colors.textSecondary,
                                    fontWeight = if (state.isGroupedByExpenseGroup) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Individual Transactions button
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (!state.isGroupedByExpenseGroup) {
                                AppTheme.colors.primaryAccent
                            } else Color.Transparent,
                            modifier = Modifier.clickable { viewModel.setGroupingMode(false) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewList,
                                    contentDescription = null,
                                    tint = if (!state.isGroupedByExpenseGroup) Color.White else AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Individual",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!state.isGroupedByExpenseGroup) Color.White else AppTheme.colors.textSecondary,
                                    fontWeight = if (!state.isGroupedByExpenseGroup) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                if (state.isGroupedByExpenseGroup && state.transactionClusters.isNotEmpty()) {
                    val anyExpanded = state.transactionClusters.any { it.isExpanded }
                    TextButton(
                        onClick = {
                            if (anyExpanded) viewModel.collapseAllGroups() else viewModel.expandAllGroups()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (anyExpanded) "Collapse All" else "Expand All",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transactions List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.filteredTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }
                } else if (state.isGroupedByExpenseGroup) {
                    items(state.transactionClusters, key = { it.key }) { cluster ->
                        ExpenseGroupTransactionCard(
                            cluster = cluster,
                            onToggleExpand = { viewModel.toggleGroupExpansion(cluster.key) },
                            onTransactionClick = { selectedTransaction = it }
                        )
                    }
                } else {
                    items(state.filteredTransactions, key = { it.id }) { txn ->
                        TransactionItemCard(
                            transaction = txn,
                            onClick = { selectedTransaction = txn }
                        )
                    }
                }
            }
        }
    }

    // Fast Add Modal Sheet
    if (showFastAddSheet) {
        FastAddExpenseSheet(
            categories = state.categories,
            friends = state.friends,
            groups = state.friendGroups,
            expenseGroups = state.expenseGroups,
            onCreateExpenseGroup = { viewModel.createExpenseGroup(it) },
            onDismiss = { showFastAddSheet = false },
            onSave = { newTxn -> viewModel.saveTransaction(newTxn) },
            onSaveSettlement = { settlement -> viewModel.recordSettlement(settlement) }
        )
    }

    // Voice Assistant Sheet
    if (showVoiceAssistant) {
        VoiceAssistantSheet(
            onDismiss = { showVoiceAssistant = false }
        )
    }

    // Transaction Detail Dialog
    selectedTransaction?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            onDismiss = { selectedTransaction = null },
            onToggleIgnore = { viewModel.toggleIgnore(it) },
            onDuplicate = { viewModel.duplicateTransaction(it) },
            onDelete = { viewModel.deleteTransaction(it) },
            onEdit = { toEdit -> editingTransaction = toEdit }
        )
    }

    // Edit Transaction Dialog
    editingTransaction?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            categories = state.categories,
            friends = state.friends,
            groups = state.friendGroups,
            expenseGroups = state.expenseGroups,
            onCreateExpenseGroup = { viewModel.createExpenseGroup(it) },
            onDismiss = { editingTransaction = null },
            onSave = { updated -> viewModel.saveTransaction(updated) }
        )
    }
}

@Composable
fun ExpenseGroupTransactionCard(
    cluster: ExpenseGroupTransactionCluster,
    onToggleExpand: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.isDark
    val groupColor = try {
        Color(android.graphics.Color.parseColor(cluster.colorHex ?: "#3B82F6"))
    } catch (_: Exception) {
        AppTheme.colors.primaryAccent
    }

    RupeeFlowCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (cluster.isExpanded) groupColor.copy(alpha = 0.5f) else AppTheme.colors.cardBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(groupColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (cluster.groupId == null) Icons.Default.ReceiptLong else Icons.Default.CardTravel,
                        contentDescription = null,
                        tint = groupColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cluster.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (cluster.groupId != null) "Trip Expenses • ${cluster.dateRangeLabel}" else cluster.dateRangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (cluster.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (cluster.isExpanded) "Collapse" else "Expand",
                        tint = AppTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isDark) CharcoalCard else AppTheme.colors.surfaceElevated,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${cluster.transactionCount} ${if (cluster.transactionCount == 1) "transaction" else "transactions"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    if (!cluster.isExpanded) {
                        Text(
                            text = "Tap to view transactions",
                            style = MaterialTheme.typography.labelSmall,
                            color = groupColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Personal Expense",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(cluster.totalPersonalExpensePaise),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }
            }

            AnimatedVisibility(visible = cluster.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = AppTheme.colors.divider)
                    Spacer(modifier = Modifier.height(2.dp))
                    cluster.transactions.forEach { txn ->
                        TransactionItemCard(
                            transaction = txn,
                            onClick = { onTransactionClick(txn) }
                        )
                    }
                }
            }
        }
    }
}
