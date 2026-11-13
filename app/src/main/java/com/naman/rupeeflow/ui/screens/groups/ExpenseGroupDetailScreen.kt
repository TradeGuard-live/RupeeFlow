package com.naman.rupeeflow.ui.screens.groups

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.components.TransactionDetailDialog
import com.naman.rupeeflow.ui.components.TransactionItemCard
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseGroupDetailScreen(
    groupId: Long,
    viewModel: ExpenseGroupViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val group = (state.activeGroups + state.archivedGroups).firstOrNull { it.id == groupId }

    val transactionsFlow = remember(groupId) { viewModel.getTransactionsForGroup(groupId) }
    val transactions by transactionsFlow.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    val filteredTransactions = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.merchantName?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.category?.name?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val groupColor = try {
        Color(android.graphics.Color.parseColor(group?.colorHex ?: "#3B82F6"))
    } catch (_: Exception) {
        AppTheme.colors.primaryAccent
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = group?.name ?: "Expense Group",
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
                    group?.let { g ->
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = AppTheme.colors.primaryAccent
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Group Overview Header Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                RupeeFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = groupColor.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(groupColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardTravel,
                                contentDescription = null,
                                tint = groupColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group?.name ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            if (!group?.description.isNullOrBlank()) {
                                Text(
                                    text = group!!.description!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = AppTheme.colors.divider)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Spending", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                            Text(
                                CurrencyUtils.formatPaiseToRupees(group?.totalSpendingPaise ?: 0L),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Transactions", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                            Text(
                                "${group?.transactionCount ?: 0}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primaryAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cash: ${CurrencyUtils.formatPaiseToRupees(group?.cashSpendingPaise ?: 0L)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary
                        )
                        Text(
                            text = "Online: ${CurrencyUtils.formatPaiseToRupees(group?.onlineSpendingPaise ?: 0L)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            }

            // Search Transactions within Group
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search transactions in this group...", color = AppTheme.colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Transactions Header
            item {
                Text(
                    text = "Transactions (${filteredTransactions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching transactions found." else "No transactions in this group yet. Add an expense or edit transactions to attach them to this trip.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { txn ->
                    TransactionItemCard(
                        transaction = txn,
                        onClick = { selectedTransactionForDetail = txn }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Detail dialog
    selectedTransactionForDetail?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            onDismiss = { selectedTransactionForDetail = null },
            onToggleIgnore = { },
            onDuplicate = { },
            onDelete = { selectedTransactionForDetail = null },
            onEdit = { selectedTransactionForDetail = null }
        )
    }

    // Edit Group dialog
    if (showEditDialog && group != null) {
        ExpenseGroupEditDialog(
            group = group,
            onDismiss = { showEditDialog = false },
            onSave = { name, description, colorHex ->
                viewModel.updateGroup(group.copy(name = name, description = description, colorHex = colorHex))
                showEditDialog = false
            }
        )
    }
}

