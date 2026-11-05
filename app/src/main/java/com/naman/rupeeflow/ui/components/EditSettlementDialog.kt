package com.naman.rupeeflow.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.theme.*
import java.util.Calendar

@Composable
fun EditSettlementDialog(
    settlement: Settlement,
    friends: List<Friend>,
    onDismiss: () -> Unit,
    onSave: (Settlement) -> Unit
) {
    val context = LocalContext.current

    // Determine initial friend from settlement
    val initialFriendId = settlement.payerId ?: settlement.receiverId
    var selectedFriend by remember {
        mutableStateOf(friends.firstOrNull { it.id == initialFriendId } ?: friends.firstOrNull())
    }

    // Direction: is friend paying me?
    var isFriendPayingMe by remember {
        mutableStateOf(settlement.receiverId == null)
    }

    // Amount
    var amountText by remember {
        mutableStateOf(
            if (settlement.amountPaise > 0L) (settlement.amountPaise / 100.0).toString() else ""
        )
    }

    // Settlement Date & Time
    var selectedDateTimeMillis by remember { mutableStateOf(settlement.settlementDate) }

    // Payment Method
    var selectedMethod by remember { mutableStateOf(settlement.paymentMethod) }

    // Reference / UTR Number
    var referenceNumber by remember { mutableStateOf(settlement.referenceNumber ?: "") }

    // Notes
    var notes by remember { mutableStateOf(settlement.notes ?: "") }

    val amountPaise = remember(amountText) { CurrencyUtils.parseInputToPaise(amountText) }

    var isSaving by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val hasUnsavedChanges = remember(
        selectedFriend, isFriendPayingMe, amountPaise,
        selectedDateTimeMillis, selectedMethod, referenceNumber, notes
    ) {
        selectedFriend?.id != initialFriendId ||
                isFriendPayingMe != (settlement.receiverId == null) ||
                amountPaise != settlement.amountPaise ||
                selectedDateTimeMillis != settlement.settlementDate ||
                selectedMethod != settlement.paymentMethod ||
                referenceNumber != (settlement.referenceNumber ?: "") ||
                notes != (settlement.notes ?: "")
    }

    Dialog(onDismissRequest = {
        if (hasUnsavedChanges) showDiscardConfirm = true else onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Settlement",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.colors.textPrimary
                    )
                    IconButton(onClick = {
                        if (hasUnsavedChanges) showDiscardConfirm = true else onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Friend Selector
                Text(
                    text = "Settlement Partner",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    friends.forEach { friend ->
                        val isSelected = selectedFriend?.id == friend.id
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) AppTheme.colors.accentContainer else AppTheme.colors.cardBackground,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                            ),
                            modifier = Modifier.clickable { selectedFriend = friend }
                        ) {
                            Text(
                                text = friend.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Direction Toggle
                selectedFriend?.let { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surfaceSecondary)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val opt1 = "${friend.name} paid Me"
                        val opt2 = "I paid ${friend.name}"

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFriendPayingMe) AppTheme.colors.incomeGreen.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { isFriendPayingMe = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt1,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFriendPayingMe) AppTheme.colors.incomeGreen else AppTheme.colors.textSecondary,
                                fontWeight = if (isFriendPayingMe) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isFriendPayingMe) CoralRed.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { isFriendPayingMe = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt2,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!isFriendPayingMe) CoralRed else AppTheme.colors.textSecondary,
                                fontWeight = if (!isFriendPayingMe) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                AmountInputField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Settlement Amount"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Settlement Date & Time
                Text(
                    text = "Settlement Date & Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.cardBackground,
                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimeMillis }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val updated = Calendar.getInstance().apply {
                                            timeInMillis = selectedDateTimeMillis
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                        }
                                        selectedDateTimeMillis = updated.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = DateTimeUtils.formatDate(selectedDateTimeMillis, "dd MMM yyyy"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textPrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.cardBackground,
                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimeMillis }
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val updated = Calendar.getInstance().apply {
                                            timeInMillis = selectedDateTimeMillis
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                        }
                                        selectedDateTimeMillis = updated.timeInMillis
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = DateTimeUtils.formatTime(selectedDateTimeMillis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Payment Method
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                PaymentMethodSelectorRow(
                    selectedMethod = selectedMethod,
                    onSelect = { selectedMethod = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Reference / UTR
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("Reference / UTR (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. UPI Ref, NEFT ID", color = AppTheme.colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Cleared bill remainder", color = AppTheme.colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Save Settlement Button
                Button(
                    onClick = {
                        val friend = selectedFriend ?: return@Button
                        if (isSaving || amountPaise <= 0L) return@Button
                        isSaving = true

                        // Preserve settlement.id, settlement.createdAt, and settlement.isReversed
                        val updatedSettlement = settlement.copy(
                            payerId = if (isFriendPayingMe) friend.id else null,
                            receiverId = if (isFriendPayingMe) null else friend.id,
                            amountPaise = amountPaise,
                            settlementDate = selectedDateTimeMillis,
                            paymentMethod = selectedMethod,
                            referenceNumber = referenceNumber.trim().takeIf { it.isNotBlank() },
                            notes = notes.trim().takeIf { it.isNotBlank() }
                        )

                        onSave(updatedSettlement)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFriendPayingMe) AppTheme.colors.incomeGreen else AppTheme.colors.primaryAccent,
                        contentColor = Color.White
                    ),
                    enabled = !isSaving && amountPaise > 0L && selectedFriend != null
                ) {
                    Text(
                        text = "Save Settlement Changes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard Changes?", color = AppTheme.colors.textPrimary) },
            text = { Text("You have unsaved edits to this settlement. Are you sure you want to discard them?", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    }
                ) {
                    Text("Discard", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep Editing", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

