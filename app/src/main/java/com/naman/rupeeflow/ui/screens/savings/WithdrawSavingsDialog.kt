package com.naman.rupeeflow.ui.screens.savings

import android.app.DatePickerDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawSavingsDialog(
    goalName: String,
    availableBalancePaise: Long,
    onDismiss: () -> Unit,
    onConfirm: (
        amountPaise: Long,
        date: Long,
        description: String,
        paymentMethod: String?,
        note: String?
    ) -> Unit
) {
    val context = LocalContext.current
    var amountInput by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var isConfirmed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp)),
            color = AppTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Withdraw Savings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Goal: $goalName",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Available Balance Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppTheme.colors.surfaceSecondary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Available Saved Balance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(availableBalancePaise),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                            Button(
                                onClick = {
                                    amountInput = (availableBalancePaise / 100.0).toString().removeSuffix(".0")
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppTheme.colors.cardBorder,
                                    contentColor = AppTheme.colors.textPrimary
                                )
                            ) {
                                Text("Withdraw All", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = AppTheme.colors.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = AppTheme.colors.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Withdrawal Amount
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = {
                            amountInput = it
                            errorMessage = null
                        },
                        label = { Text("Withdrawal Amount (₹) *") },
                        placeholder = { Text("e.g. 1000") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = AppTheme.colors.cardBorder
                        )
                    )

                    // Date of Withdrawal
                    Column {
                        Text(
                            text = "Withdrawal Date *",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, AppTheme.colors.cardBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val selected = Calendar.getInstance().apply {
                                                set(y, m, d)
                                            }
                                            selectedDateMillis = selected.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                            color = AppTheme.colors.surfaceSecondary
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = AppTheme.colors.deepPeach,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = dateFormat.format(Date(selectedDateMillis)),
                                        color = AppTheme.colors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.EditCalendar,
                                    contentDescription = "Change date",
                                    tint = AppTheme.colors.deepPeach,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Description (Required)
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            errorMessage = null
                        },
                        label = { Text("Reason / Description *") },
                        placeholder = { Text("e.g. Needed emergency cash, bought equipment") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = AppTheme.colors.cardBorder
                        )
                    )

                    // Note (Optional)
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (Optional)") },
                        placeholder = { Text("Any additional notes...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = AppTheme.colors.cardBorder
                        )
                    )

                    // Confirmation Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isConfirmed = !isConfirmed }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isConfirmed,
                            onCheckedChange = { isConfirmed = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFEF4444)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "I confirm that I want to withdraw this amount from my goal",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (isSubmitting) return@Button
                            val trimmedDesc = description.trim()
                            if (trimmedDesc.isBlank()) {
                                errorMessage = "Please enter a description for this withdrawal"
                                return@Button
                            }
                            val amountPaise = CurrencyUtils.parseInputToPaiseOrNull(amountInput)
                            if (amountPaise == null || amountPaise <= 0L) {
                                errorMessage = "Withdrawal amount must be greater than zero"
                                return@Button
                            }
                            if (amountPaise > availableBalancePaise) {
                                errorMessage = "Withdrawal cannot exceed available balance (${CurrencyUtils.formatPaiseToRupees(availableBalancePaise)})"
                                return@Button
                            }
                            if (!isConfirmed) {
                                errorMessage = "Please confirm the withdrawal by checking the box"
                                return@Button
                            }
                            isSubmitting = true
                            onConfirm(
                                amountPaise,
                                selectedDateMillis,
                                trimmedDesc,
                                null,
                                note.trim().ifBlank { null }
                            )
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Withdraw", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
