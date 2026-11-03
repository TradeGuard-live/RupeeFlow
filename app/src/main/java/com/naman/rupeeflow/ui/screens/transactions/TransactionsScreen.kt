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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateToPending: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    var showFastAddSheet by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        containerColor = MidnightDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFastAddSheet = true },
                containerColor = ElectricBlue,
                contentColor = MidnightDark,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense", modifier = Modifier.size(28.dp))
            }
        }
    ) { paddingValues ->
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
                    color = TextPrimary
                )
                if (onNavigateToPending != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
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
                placeholder = { Text("Search title, merchant, ref no...", color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = CharcoalCard,
                    unfocusedContainerColor = CharcoalCard
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Type Filter Tabs
            val typeFilters = listOf(
                "ALL" to "All",
                "EXPENSE" to "Expenses",
                "INCOME" to "Income",
                "TRANSFER" to "Transfers",
                "IGNORED" to "Ignored"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                typeFilters.forEach { (key, label) ->
                    val isSelected = state.selectedTypeFilter == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ElectricBlue else GlassBorder
                        ),
                        modifier = Modifier.clickable { viewModel.onTypeFilterSelected(key) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) ElectricBlueLight else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category & Payment method quick filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Payment Method Pill Filter
                PaymentMethod.values().take(4).forEach { method ->
                    val isSelected = state.selectedPaymentMethodFilter == method
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) VibrantCyan.copy(alpha = 0.2f) else CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) VibrantCyan else GlassBorder
                        ),
                        modifier = Modifier.clickable {
                            viewModel.onPaymentMethodFilterSelected(if (isSelected) null else method)
                        }
                    ) {
                        Text(
                            text = method.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) VibrantCyan else TextMuted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                                color = TextSecondary
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
            onDismiss = { showFastAddSheet = false },
            onSave = { newTxn -> viewModel.saveTransaction(newTxn) },
            onSaveSettlement = { settlement -> viewModel.recordSettlement(settlement) }
        )
    }

    // Transaction Detail Dialog
    selectedTransaction?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            onDismiss = { selectedTransaction = null },
            onToggleIgnore = { viewModel.toggleIgnore(it) },
            onDuplicate = { viewModel.duplicateTransaction(it) },
            onDelete = { viewModel.deleteTransaction(it) }
        )
    }
}
