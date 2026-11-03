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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.theme.*

@Composable
fun SettlementDetailDialog(
    settlement: Settlement,
    friendName: String,
    onDismiss: () -> Unit,
    onReverse: (Settlement) -> Unit,
    onUnreverse: (Settlement) -> Unit,
    onDelete: (Settlement) -> Unit,
    onEdit: (Settlement) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReverseConfirm by remember { mutableStateOf(false) }
    var showUnreverseConfirm by remember { mutableStateOf(false) }

    val isFriendPayingMe = settlement.receiverId == null
    val directionText = if (isFriendPayingMe) "$friendName paid You" else "You paid $friendName"
    val accentColor = if (isFriendPayingMe) EmeraldMint else CoralRed

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
                // Header Row
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
                                .background(accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFriendPayingMe) Icons.Default.CallReceived else Icons.Default.CallMade,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Settlement Details",
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
                    text = CurrencyUtils.formatPaiseToRupees(settlement.amountPaise),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (settlement.isReversed) TextMuted else accentColor,
                    textDecoration = if (settlement.isReversed) TextDecoration.LineThrough else null
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Direction & Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = directionText,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (settlement.isReversed) AmberGold.copy(alpha = 0.2f) else EmeraldMint.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (settlement.isReversed) "REVERSED" else "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (settlement.isReversed) AmberGold else EmeraldMint,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Rows
                DetailRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Settlement Date",
                    value = "${DateTimeUtils.formatDate(settlement.settlementDate)} at ${DateTimeUtils.formatTime(settlement.settlementDate)}"
                )

                DetailRow(
                    icon = Icons.Default.Payment,
                    label = "Payment Method",
                    value = settlement.paymentMethod.displayName
                )

                DetailRow(
                    icon = Icons.Default.Person,
                    label = "Settled With",
                    value = friendName
                )

                if (!settlement.referenceNumber.isNullOrBlank()) {
                    DetailRow(
                        icon = Icons.Default.Tag,
                        label = "Reference / UTR",
                        value = settlement.referenceNumber
                    )
                }

                if (!settlement.notes.isNullOrBlank()) {
                    DetailRow(
                        icon = Icons.Default.Notes,
                        label = "Notes",
                        value = settlement.notes
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // ✅ Edit Settlement Button
                    Button(
                        onClick = {
                            onDismiss()
                            onEdit(settlement)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan, contentColor = MidnightDark),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Settlement", fontWeight = FontWeight.Bold)
                    }

                    if (!settlement.isReversed) {
                        OutlinedButton(
                            onClick = { showReverseConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reverse Settlement", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = { showUnreverseConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = MidnightDark),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert to Normal (Restore)", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Settlement", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Confirmation for Reverse
    if (showReverseConfirm) {
        AlertDialog(
            onDismissRequest = { showReverseConfirm = false },
            title = { Text("Reverse Settlement?", color = TextPrimary) },
            text = {
                Text(
                    "Reversing will cancel this settlement payment. Ledger balances with $friendName will be restored to include this amount again.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReverseConfirm = false
                        onReverse(settlement)
                        onDismiss()
                    }
                ) {
                    Text("Confirm Reverse", color = AmberGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReverseConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirmation for Un-reverse (Restore to Normal)
    if (showUnreverseConfirm) {
        AlertDialog(
            onDismissRequest = { showUnreverseConfirm = false },
            title = { Text("Convert to Normal?", color = TextPrimary) },
            text = {
                Text(
                    "This will restore this settlement back to active normal state and recalculate the ledger balances with $friendName.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnreverseConfirm = false
                        onUnreverse(settlement)
                        onDismiss()
                    }
                ) {
                    Text("Confirm Restore", color = EmeraldMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnreverseConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirmation for Delete
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Settlement Record?", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to permanently delete this settlement record? This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(settlement)
                        onDismiss()
                    }
                ) {
                    Text("Delete Permanently", color = CoralRed, fontWeight = FontWeight.Bold)
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
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}
