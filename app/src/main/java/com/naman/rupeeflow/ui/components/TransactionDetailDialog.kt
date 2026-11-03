package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.theme.*

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onToggleIgnore: (Transaction) -> Unit,
    onDuplicate: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onEdit: (Transaction) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val category = transaction.category
    val catColor = CategoryIconHelper.parseColor(category?.colorHex)
    val catIcon = CategoryIconHelper.getIcon(category?.iconName)

    val isIncomeOrRefund = transaction.transactionType == TransactionType.INCOME ||
            transaction.transactionType == TransactionType.REFUND
    val amountPrefix = if (isIncomeOrRefund) "+" else "-"
    val amountColor = if (isIncomeOrRefund) EmeraldMint else TextPrimary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CharcoalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(catColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = category?.name ?: "Expense",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                Text(
                    text = "$amountPrefix${CurrencyUtils.formatPaiseToRupees(transaction.amountPaise)}",
                    style = MaterialTheme.typography.displayLarge,
                    color = amountColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                if (transaction.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata cards
                RupeeFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp
                ) {
                    DetailRow("Date", DateTimeUtils.formatDate(transaction.transactionDate, "dd MMMM yyyy"))
                    DetailRow("Time", DateTimeUtils.formatTime(transaction.transactionDate))
                    DetailRow("Type", transaction.transactionType.displayName)
                    DetailRow("Payment Method", transaction.paymentMethod.displayName)
                    DetailRow("Paid By", transaction.paidByName)
                    if (transaction.isSplit) {
                        DetailRow("Split Method", transaction.splitMethod.displayName)
                        DetailRow("Your Share (Counted)", CurrencyUtils.formatPaiseToRupees(transaction.userExpensePaise))
                        val friendsShare = (transaction.amountPaise - transaction.userExpensePaise).coerceAtLeast(0L)
                        DetailRow("Friends' Share (Debt/Credit)", CurrencyUtils.formatPaiseToRupees(friendsShare))
                    }
                    if (!transaction.referenceNumber.isNullOrBlank()) {
                        DetailRow("Reference", transaction.referenceNumber)
                    }
                    if (!transaction.merchantName.isNullOrBlank()) {
                        DetailRow("Merchant", transaction.merchantName)
                    }
                    DetailRow("Source", transaction.source.name)
                    DetailRow("Status", transaction.transactionStatus.name)
                }

                // Participants / Split breakdown
                if (transaction.participants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Participants & Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp
                    ) {
                        transaction.participants.forEach { part ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (part.friendId == null) "Me (You)" else part.friendName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = if (part.friendId == null) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (part.percentage > 0.0) {
                                        Text(
                                            text = "${part.percentage}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(part.shareAmountPaise),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (part.friendId == null) EmeraldMint else VibrantCyan
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ✅ Edit Button
                    Button(
                        onClick = {
                            onDismiss()
                            onEdit(transaction)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VibrantCyan,
                            contentColor = MidnightDark
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Ignore / Restore Button
                    OutlinedButton(
                        onClick = {
                            onToggleIgnore(transaction)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (transaction.isIgnored) EmeraldMint else AmberGold
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (transaction.isIgnored) EmeraldMint else AmberGold
                        )
                    ) {
                        Icon(
                            imageVector = if (transaction.isIgnored) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (transaction.isIgnored) "Restore" else "Ignore",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Duplicate Button
                    OutlinedButton(
                        onClick = {
                            onDuplicate(transaction)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }

                    // Delete Button
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoralRed.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CoralRed)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete '${transaction.title}'? This will permanently remove its accounting records.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(transaction)
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    }
}
