package com.naman.rupeeflow.ui.screens.pending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.components.FastAddExpenseSheet
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.theme.*

/**
 * Screen for reviewing detected SMS and notification transactions.
 *
 * Requirements:
 * - 4 distinct sections/tabs: Pending, Approved, Ignored, All
 * - Clear status badges: Pending (Amber), Added (Green), Ignored (Muted/Coral)
 * - Actions per section:
 *   - Pending: Approve (Add Expense), Ignore
 *   - Approved: View Details, Re-add (Duplicate protection)
 *   - Ignored: Restore to Pending, Search, Filter, View Details, Delete
 *   - All: Comprehensive history across all states
 * - Specific empty states for every filter
 * - Complete detail inspection modal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTransactionsScreen(
    viewModel: PendingTransactionsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var selectedDetectedForAdd by remember { mutableStateOf<PendingTransaction?>(null) }
    var selectedDetectedForDetail by remember { mutableStateOf<PendingTransaction?>(null) }
    var selectedFilter by remember { mutableStateOf("PENDING") } // "PENDING", "APPROVED", "IGNORED", "ALL"

    // Search and filter state for Ignored & All tabs
    var searchQuery by remember { mutableStateOf("") }

    val pendingCount = state.pendingTransactions.size
    val approvedCount = state.addedTransactions.size
    val ignoredCount = state.ignoredTransactions.size
    val totalCount = state.allDetectedTransactions.size

    val displayList = remember(state.allDetectedTransactions, selectedFilter, searchQuery) {
        val baseList = when (selectedFilter) {
            "PENDING" -> state.pendingTransactions
            "APPROVED" -> state.addedTransactions
            "IGNORED" -> state.ignoredTransactions
            else -> state.allDetectedTransactions
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.trim().lowercase()
            baseList.filter { item ->
                (item.detectedMerchant?.lowercase()?.contains(q) == true) ||
                (item.detectedBank?.lowercase()?.contains(q) == true) ||
                (item.referenceNumber?.lowercase()?.contains(q) == true) ||
                item.rawContent.lowercase().contains(q) ||
                CurrencyUtils.formatPaiseToRupees(item.detectedAmountPaise).contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detected Transactions", color = AppTheme.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 4-Filter Pills Row: Pending, Approved, Ignored, All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "PENDING" to "Pending ($pendingCount)",
                    "APPROVED" to "Approved ($approvedCount)",
                    "IGNORED" to "Ignored ($ignoredCount)",
                    "ALL" to "All ($totalCount)"
                ).forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) AppTheme.colors.accentContainer else AppTheme.colors.cardBackground,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                        ),
                        modifier = Modifier.clickable {
                            selectedFilter = key
                            searchQuery = ""
                        }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Search Bar for Ignored & All tabs (or when searching any list)
            if (selectedFilter == "IGNORED" || selectedFilter == "ALL" || (searchQuery.isNotBlank())) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (selectedFilter == "IGNORED") "Search ignored transactions..." else "Search by merchant, bank or SMS...",
                                color = AppTheme.colors.textMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.primaryAccent,
                            unfocusedBorderColor = AppTheme.colors.cardBorder,
                            focusedTextColor = AppTheme.colors.textPrimary,
                            unfocusedTextColor = AppTheme.colors.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Empty States according to selected section
            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        val emptyIcon = when (selectedFilter) {
                            "PENDING" -> Icons.Default.CheckCircle
                            "APPROVED" -> Icons.Default.DoneAll
                            "IGNORED" -> Icons.Default.VisibilityOff
                            else -> Icons.Default.Sms
                        }
                        val emptyTint = when (selectedFilter) {
                            "PENDING" -> AppTheme.colors.incomeGreen
                            "APPROVED" -> AppTheme.colors.primaryAccent
                            "IGNORED" -> AppTheme.colors.textMuted
                            else -> AppTheme.colors.primaryAccent
                        }
                        val emptyTitle = when (selectedFilter) {
                            "PENDING" -> "No pending detected transactions."
                            "APPROVED" -> "No approved transactions yet."
                            "IGNORED" -> "No ignored transactions."
                            else -> "No detected transactions yet."
                        }
                        val emptySubtitle = when (selectedFilter) {
                            "PENDING" -> "All detected SMS & notification transactions have been reviewed."
                            "APPROVED" -> "Transactions approved from SMS detection will appear here."
                            "IGNORED" -> if (searchQuery.isNotBlank()) "No ignored records matched \"$searchQuery\"." else "Transactions you ignore from review will be preserved here."
                            else -> "SMS transaction detection will capture incoming bank and UPI alerts automatically."
                        }

                        Icon(
                            imageVector = emptyIcon,
                            contentDescription = null,
                            tint = emptyTint,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = emptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = emptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(displayList, key = { it.id }) { item ->
                        val isPending = item.status == PendingStatus.PENDING
                        val isApproved = item.status == PendingStatus.RESOLVED
                        val isIgnored = item.status == PendingStatus.IGNORED

                        val cardBorderColor = when {
                            isPending -> AmberGold
                            isApproved -> AppTheme.colors.incomeGreen.copy(alpha = 0.35f)
                            else -> AppTheme.colors.cardBorder
                        }

                        val iconBgColor = when {
                            isPending -> AmberGold.copy(alpha = 0.18f)
                            isApproved -> AppTheme.colors.incomeGreen.copy(alpha = 0.15f)
                            else -> AppTheme.colors.surfaceSecondary
                        }

                        val iconColor = when {
                            isPending -> AmberGold
                            isApproved -> AppTheme.colors.incomeGreen
                            else -> AppTheme.colors.textMuted
                        }

                        val iconVector = when {
                            isPending -> Icons.Default.Sms
                            isApproved -> Icons.Default.CheckCircle
                            else -> Icons.Default.VisibilityOff
                        }

                        RupeeFlowCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDetectedForDetail = item },
                            borderColor = cardBorderColor,
                            backgroundColor = AppTheme.colors.cardBackground
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(iconBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = CurrencyUtils.formatPaiseToRupees(item.detectedAmountPaise),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = if (isApproved || isIgnored) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary
                                            )

                                            // Status Badge
                                            when {
                                                isPending -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AmberGold.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "Pending",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AmberGold,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                isApproved -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AppTheme.colors.incomeGreen.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "Added ✓",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AppTheme.colors.incomeGreen,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                isIgnored -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AppTheme.colors.surfaceSecondary
                                                    ) {
                                                        Text(
                                                            text = "Ignored",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AppTheme.colors.textSecondary,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${item.detectedMerchant ?: item.detectedBank ?: "Bank"} • ${DateTimeUtils.formatRelativeDate(item.timestamp)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }

                                    // Action Buttons based on Status
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        when {
                                            isPending -> {
                                                // Ignore Action
                                                IconButton(onClick = { viewModel.ignorePending(item.id) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Ignore", tint = AppTheme.colors.textMuted)
                                                }
                                                // Approve Action
                                                Button(
                                                    onClick = { selectedDetectedForAdd = item },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = AppTheme.colors.primaryAccent,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Add Expense", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            isIgnored -> {
                                                // Restore Action
                                                OutlinedButton(
                                                    onClick = { viewModel.restorePending(item.id) },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.primaryAccent),
                                                    border = BorderStroke(1.dp, AppTheme.colors.primaryAccent.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Restore", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            isApproved -> {
                                                // Re-add Action
                                                OutlinedButton(
                                                    onClick = { selectedDetectedForAdd = item },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textSecondary),
                                                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Re-add", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Raw SMS snippet
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.rawContent,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textMuted,
                                    maxLines = 2,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Inspection Modal
    selectedDetectedForDetail?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedDetectedForDetail = null },
            title = {
                Text(
                    text = "Detected Transaction Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Amount:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(
                            CurrencyUtils.formatPaiseToRupees(item.detectedAmountPaise),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(
                            when (item.status) {
                                PendingStatus.PENDING -> "Pending Review"
                                PendingStatus.RESOLVED -> "Approved (Added)"
                                PendingStatus.IGNORED -> "Ignored"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when (item.status) {
                                PendingStatus.PENDING -> AmberGold
                                PendingStatus.RESOLVED -> AppTheme.colors.incomeGreen
                                PendingStatus.IGNORED -> AppTheme.colors.textSecondary
                            }
                        )
                    }
                    if (!item.detectedMerchant.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Merchant / Entity:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text(item.detectedMerchant, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                        }
                    }
                    if (!item.detectedBank.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bank / Institution:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text(item.detectedBank, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                        }
                    }
                    if (!item.referenceNumber.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reference / UTR:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text(item.referenceNumber, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Detection Source:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(item.source.name, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Detected On:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(DateTimeUtils.formatRelativeDate(item.timestamp), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Original Message / Notification:", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppTheme.colors.surfaceSecondary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.rawContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                when (item.status) {
                    PendingStatus.PENDING -> {
                        Button(
                            onClick = {
                                selectedDetectedForAdd = item
                                selectedDetectedForDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                        ) {
                            Text("Approve & Add")
                        }
                    }
                    PendingStatus.IGNORED -> {
                        Button(
                            onClick = {
                                viewModel.restorePending(item.id)
                                selectedDetectedForDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                        ) {
                            Text("Restore to Pending")
                        }
                    }
                    PendingStatus.RESOLVED -> {
                        TextButton(onClick = { selectedDetectedForDetail = null }) {
                            Text("Close")
                        }
                    }
                }
            },
            dismissButton = {
                if (item.status == PendingStatus.PENDING) {
                    OutlinedButton(
                        onClick = {
                            viewModel.ignorePending(item.id)
                            selectedDetectedForDetail = null
                        }
                    ) {
                        Text("Ignore")
                    }
                } else {
                    TextButton(onClick = { selectedDetectedForDetail = null }) {
                        Text("Close")
                    }
                }
            },
            containerColor = AppTheme.colors.surfaceElevated
        )
    }

    // Opens Add Expense Sheet (FastAddExpenseSheet) prefilled with detected data!
    selectedDetectedForAdd?.let { item ->
        FastAddExpenseSheet(
            categories = state.categories,
            friends = state.friends,
            groups = state.friendGroups,
            onDismiss = { selectedDetectedForAdd = null },
            initialAmountPaise = item.detectedAmountPaise,
            initialTitle = item.detectedMerchant ?: item.detectedBank,
            initialType = item.detectedType,
            initialPaymentMethod = PaymentMethod.UPI,
            initialPendingId = item.id,
            onSave = { newTxn ->
                viewModel.saveTransaction(newTxn, item.id)
                selectedDetectedForAdd = null
            },
            onSaveSettlement = { settlement ->
                viewModel.saveSettlement(settlement, item.id)
                selectedDetectedForAdd = null
            },
            onPendingResolved = { id ->
                viewModel.resolvePending(id)
                selectedDetectedForAdd = null
            }
        )
    }
}
