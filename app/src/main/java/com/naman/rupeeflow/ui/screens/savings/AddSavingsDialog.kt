package com.naman.rupeeflow.ui.screens.savings

import android.app.DatePickerDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.naman.rupeeflow.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

val SavingsSourcesPresets = listOf(
    "Pocket Money",
    "Hostel Allowance",
    "Salary",
    "Internship",
    "Reduced Expenses",
    "Gift",
    "Bank Transfer",
    "UPI",
    "Cash",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSavingsDialog(
    goalName: String,
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
    var selectedSource by remember { mutableStateOf<String?>("Pocket Money") }
    var note by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
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
                            text = "Add Savings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Goal: $goalName",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.deepPeach,
                            fontWeight = FontWeight.Medium
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

                    // Amount Input
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = {
                            amountInput = it
                            errorMessage = null
                        },
                        label = { Text("Amount Saved (₹) *") },
                        placeholder = { Text("e.g. 500") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.deepPeach,
                            unfocusedBorderColor = AppTheme.colors.cardBorder
                        )
                    )

                    // Date of Saving (defaults to today, supports past dates)
                    Column {
                        Text(
                            text = "Date of Saving *",
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

                    // Description (Important & Required)
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            errorMessage = null
                        },
                        label = { Text("Description *") },
                        placeholder = { Text("e.g. Saved ₹500 from hostel allowance") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.deepPeach,
                            unfocusedBorderColor = AppTheme.colors.cardBorder
                        )
                    )

                    // Quick Description Suggestions
                    Column {
                        Text(
                            text = "Suggestions",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val suggestions = listOf(
                                "Saved from pocket money",
                                "Saved from hostel allowance",
                                "Reduced food expenses",
                                "Avoided unnecessary shopping",
                                "Bonus / Internship savings"
                            )
                            suggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { description = suggestion },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Payment Source / Method
                    Column {
                        Text(
                            text = "Source / Payment Method (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SavingsSourcesPresets.forEach { src ->
                                val isSelected = selectedSource == src
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedSource = if (isSelected) null else src },
                                    label = { Text(src) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppTheme.colors.deepPeach,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Note
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (Optional)") },
                        placeholder = { Text("Any additional details...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.deepPeach,
                            unfocusedBorderColor = AppTheme.colors.cardBorder
                        )
                    )
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
                                errorMessage = "Please enter a description for this saving"
                                return@Button
                            }
                            val amountPaise = CurrencyUtils.parseInputToPaiseOrNull(amountInput)
                            if (amountPaise == null || amountPaise <= 0L) {
                                errorMessage = "Amount must be greater than zero"
                                return@Button
                            }
                            isSubmitting = true
                            onConfirm(
                                amountPaise,
                                selectedDateMillis,
                                trimmedDesc,
                                selectedSource,
                                note.trim().ifBlank { null }
                            )
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.deepPeach,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Save Money", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
