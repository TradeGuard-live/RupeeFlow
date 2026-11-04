package com.naman.rupeeflow.ui.screens.pending

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.components.FastAddExpenseSheet
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTransactionsScreen(
    viewModel: PendingTransactionsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var selectedDetectedForAdd by remember { mutableStateOf<PendingTransaction?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "PENDING", "ADDED"

    val displayList = remember(state.allDetectedTransactions, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> state.allDetectedTransactions.filter { it.status == PendingStatus.PENDING }
            "ADDED" -> state.allDetectedTransactions.filter { it.status == PendingStatus.RESOLVED }
            else -> state.allDetectedTransactions.filter { it.status != PendingStatus.IGNORED }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detected Transactions", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightDark)
            )
        },
        containerColor = MidnightDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Pills (All / Unadded / Added)
            val pendingCount = state.pendingTransactions.size
            val addedCount = state.addedTransactions.size

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All (${state.allDetectedTransactions.count { it.status != PendingStatus.IGNORED }})",
                    "PENDING" to "Unadded ($pendingCount)",
                    "ADDED" to "Added ($addedCount)"
                ).forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) ElectricBlue.copy(alpha = 0.25f) else CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ElectricBlue else GlassBorder
                        ),
                        modifier = Modifier.clickable { selectedFilter = key }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) ElectricBlueLight else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedFilter == "PENDING") "All Caught Up!" else "No Transactions Found",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedFilter == "PENDING") "No pending SMS transactions awaiting review."
                            else "No detected transactions in this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
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
                        val isAdded = item.status == PendingStatus.RESOLVED
                        // If added: subtle green/card styling. If not added: distinct AmberGold/ElectricBlue styling
                        val cardBorderColor = if (isAdded) EmeraldMint.copy(alpha = 0.35f) else AmberGold
                        val cardBgColor = if (isAdded) CharcoalCard.copy(alpha = 0.7f) else CharcoalCard
                        val iconBgColor = if (isAdded) EmeraldMint.copy(alpha = 0.15f) else AmberGold.copy(alpha = 0.2f)
                        val iconColor = if (isAdded) EmeraldMint else AmberGold

                        RupeeFlowCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = cardBorderColor,
                            backgroundColor = cardBgColor
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
                                            imageVector = if (isAdded) Icons.Default.CheckCircle else Icons.Default.Sms,
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
                                                color = if (isAdded) TextSecondary else TextPrimary
                                            )

                                            // Status Badge
                                            if (isAdded) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = EmeraldMint.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "Added ✓",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = EmeraldMint,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = AmberGold.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "Not Added",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = AmberGold,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${item.detectedMerchant ?: item.detectedBank ?: "Bank"} • ${DateTimeUtils.formatRelativeDate(item.timestamp)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (!isAdded) {
                                            IconButton(onClick = { viewModel.ignorePending(item.id) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Ignore", tint = TextMuted)
                                            }
                                            Button(
                                                onClick = { selectedDetectedForAdd = item },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ElectricBlue,
                                                    contentColor = MidnightDark
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text("Add Expense", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { selectedDetectedForAdd = item },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Re-add", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                            }
                                        }
                                    }
                                }

                                // Raw SMS snippet
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.rawContent,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    maxLines = 2,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Opens Original Add Expense Page (FastAddExpenseSheet) prefilled with detected data!
    selectedDetectedForAdd?.let { item ->
        FastAddExpenseSheet(
            categories = state.categories,
            friends = state.friends,
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
