package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
    friendName: String = "",
    payerName: String? = null,
    receiverName: String? = null,
    onDismiss: () -> Unit,
    onReverse: (Settlement) -> Unit,
    onUnreverse: (Settlement) -> Unit,
    onDelete: (Settlement) -> Unit,
    onEdit: (Settlement) -> Unit,
    onConfirmReceived: ((Settlement) -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReverseConfirm by remember { mutableStateOf(false) }
    var showUnreverseConfirm by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val directionText = when {
        settlement.payerId != null && settlement.receiverId != null -> {
            val p = payerName ?: "Member #${settlement.payerId}"
            val r = receiverName ?: "Member #${settlement.receiverId}"
            "$p paid $r"
        }
        settlement.receiverId == null -> {
            val p = payerName ?: friendName.ifBlank { "Friend" }
            "$p paid You"
        }
        else -> {
            val r = receiverName ?: friendName.ifBlank { "Friend" }
            "You paid $r"
        }
    }
    val isFriendPayingMe = settlement.receiverId == null
    val accentColor = if (settlement.receiverId == null) AppTheme.colors.incomeGreen else AppTheme.colors.primaryAccent

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
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
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                Text(
                    text = CurrencyUtils.formatPaiseToRupees(settlement.amountPaise),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (settlement.isReversed) AppTheme.colors.textMuted else accentColor,
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
                        color = AppTheme.colors.textPrimary
                    )

                    val statusText = when {
                        settlement.isReversed -> "REVERSED"
                        !settlement.affectsLedger -> "VERIFICATION PENDING"
                        else -> "ACTIVE"
                    }
                    val statusColor = when {
                        settlement.isReversed -> AmberGold
                        !settlement.affectsLedger -> AmberGold
                        else -> AppTheme.colors.incomeGreen
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = AppTheme.colors.divider)
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
                    label = if (settlement.payerId != null && settlement.receiverId != null) "Parties" else "Settled With",
                    value = if (settlement.payerId != null && settlement.receiverId != null) {
                        "${payerName ?: "Member #${settlement.payerId}"} -> ${receiverName ?: "Member #${settlement.receiverId}"}"
                    } else friendName.ifBlank { payerName ?: receiverName ?: "Friend" }
                )

                val ref = settlement.referenceNumber?.takeIf { it.isNotBlank() } ?: settlement.upiTransactionRef?.takeIf { it.isNotBlank() }
                if (!ref.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tag, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Reference / UTR", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = ref,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("UPI Reference / UTR", ref)
                                    clipboard?.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Reference / UTR copied", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Reference / UTR",
                                    tint = AppTheme.colors.primaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                if (settlement.paymentMethod == com.naman.rupeeflow.domain.model.PaymentMethod.UPI) {
                    DetailRow(
                        icon = Icons.Default.CheckCircle,
                        label = "Verification Status",
                        value = settlement.upiVerificationStatus.displayName
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
                HorizontalDivider(color = AppTheme.colors.divider)
                Spacer(modifier = Modifier.height(16.dp))

                // Verification Pending Notice & One-Tap Confirmation
                if (!settlement.affectsLedger && !settlement.isReversed) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AmberGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Bank SMS has not arrived yet. If payment was credited to your bank, confirm it below to deduct this from the outstanding ledger balance immediately.",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Responsive Action Button Layout (Phase 3: Zero Text Clipping)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isNarrow = maxWidth < 280.dp

                    val confirmReceivedButton: @Composable (Modifier) -> Unit = { modifier ->
                        Button(
                            onClick = {
                                val confirmed = settlement.copy(
                                    upiPaymentStatus = com.naman.rupeeflow.domain.model.SettlementPaymentStatus.SUCCESS,
                                    upiVerificationStatus = com.naman.rupeeflow.domain.model.SettlementVerificationStatus.MANUAL_UNVERIFIED
                                )
                                onConfirmReceived?.invoke(confirmed)
                                onDismiss()
                            },
                            modifier = modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldMint,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Confirm Payment Received",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val editButton: @Composable (Modifier) -> Unit = { modifier ->
                        Button(
                            onClick = {
                                onDismiss()
                                onEdit(settlement)
                            },
                            modifier = modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primaryAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val reverseRestoreButton: @Composable (Modifier) -> Unit = { modifier ->
                        if (!settlement.isReversed) {
                            OutlinedButton(
                                onClick = { showReverseConfirm = true },
                                modifier = modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
                                border = BorderStroke(1.dp, AmberGold),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reverse",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Button(
                                onClick = { showUnreverseConfirm = true },
                                modifier = modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.incomeGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Restore",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    val deleteButton: @Composable (Modifier) -> Unit = { modifier ->
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                            border = BorderStroke(1.dp, CoralRed),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete Settlement",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = CoralRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isNarrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onConfirmReceived != null && !settlement.affectsLedger && !settlement.isReversed) {
                                confirmReceivedButton(Modifier.fillMaxWidth())
                            }
                            editButton(Modifier.fillMaxWidth())
                            reverseRestoreButton(Modifier.fillMaxWidth())
                            deleteButton(Modifier.fillMaxWidth())
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (onConfirmReceived != null && !settlement.affectsLedger && !settlement.isReversed) {
                                confirmReceivedButton(Modifier.fillMaxWidth())
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                editButton(Modifier.weight(1f))
                                reverseRestoreButton(Modifier.weight(1f))
                            }
                            deleteButton(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    // Confirmation for Reverse
    if (showReverseConfirm) {
        AlertDialog(
            onDismissRequest = { showReverseConfirm = false },
            title = { Text("Reverse Settlement?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Reversing will cancel this settlement payment. Ledger balances with $friendName will be restored to include this amount again.",
                    color = AppTheme.colors.textSecondary
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
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirmation for Un-reverse (Restore to Normal)
    if (showUnreverseConfirm) {
        AlertDialog(
            onDismissRequest = { showUnreverseConfirm = false },
            title = { Text("Convert to Normal?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "This will restore this settlement back to active normal state and recalculate the ledger balances with $friendName.",
                    color = AppTheme.colors.textSecondary
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
                    Text("Confirm Restore", color = AppTheme.colors.incomeGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnreverseConfirm = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirmation for Delete
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Settlement Record?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Are you sure you want to permanently delete this settlement record? This action cannot be undone.",
                    color = AppTheme.colors.textSecondary
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
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
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
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
        }
    }
}
