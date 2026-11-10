package com.naman.rupeeflow.ui.screens.transactions

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
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Transaction
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

            // Group C: Payment Method Filters (UPI, Cash, Debit Card, Credit Card)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                PaymentMethod.values().take(4).forEach { method ->
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
            onDismiss = { editingTransaction = null },
            onSave = { updated -> viewModel.saveTransaction(updated) }
        )
    }
}
