package com.naman.rupeeflow.ui.screens.savings

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

val SavingsColorPresets = listOf(
    "#FFAD91", // Peach
    "#F58B72", // Deep Peach
    "#F5A6B8", // Pink
    "#B9E5D2", // Mint
    "#FFD98E", // Warm Yellow
    "#3B82F6", // Blue
    "#8B5CF6", // Purple
    "#10B981"  // Emerald
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditSavingsGoalDialog(
    goalToEdit: SavingsGoal? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        targetAmountPaise: Long,
        initialSavedPaise: Long,
        targetDate: Long?,
        category: SavingsGoalCategory,
        description: String?,
        colorHex: String,
        priority: SavingsGoalPriority
    ) -> Unit
) {
    val context = LocalContext.current
    val isEditing = goalToEdit != null

    var name by remember { mutableStateOf(goalToEdit?.name ?: "") }
    var targetAmountInput by remember {
        mutableStateOf(if (goalToEdit != null) (goalToEdit.targetAmountPaise / 100.0).toString().removeSuffix(".0") else "")
    }
    var initialAmountInput by remember { mutableStateOf("") }
    var description by remember { mutableStateOf(goalToEdit?.description ?: "") }
    var selectedCategory by remember { mutableStateOf(goalToEdit?.category ?: SavingsGoalCategory.CUSTOM) }
    var selectedColor by remember { mutableStateOf(goalToEdit?.colorHex ?: "#FFAD91") }
    var selectedPriority by remember { mutableStateOf(goalToEdit?.priority ?: SavingsGoalPriority.MEDIUM) }
    var targetDateMillis by remember { mutableStateOf<Long?>(goalToEdit?.targetDate) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
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
                    Text(
                        text = if (isEditing) "Edit Savings Goal" else "Create Savings Goal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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

                    // Goal Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        label = { Text("Goal Name *") },
                        placeholder = { Text("e.g. New Laptop, Emergency Fund") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.deepPeach,
                            unfocusedBorderColor = AppTheme.colors.cardBorder
                        )
                    )

                    // Target Amount
                    OutlinedTextField(
                        value = targetAmountInput,
                        onValueChange = {
                            targetAmountInput = it
                            errorMessage = null
                        },
                        label = { Text("Target Amount (₹) *") },
                        placeholder = { Text("e.g. 50000") },
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

                    // Initial Saved Amount (only when creating new goal)
                    if (!isEditing) {
                        OutlinedTextField(
                            value = initialAmountInput,
                            onValueChange = {
                                initialAmountInput = it
                                errorMessage = null
                            },
                            label = { Text("Initial Saved Amount (₹) (Optional)") },
                            placeholder = { Text("0") },
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
                    }

                    // Target Date Picker
                    Column {
                        Text(
                            text = "Target Date (Optional)",
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
                                    val cal = Calendar.getInstance().apply {
                                        targetDateMillis?.let { timeInMillis = it }
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val selected = Calendar.getInstance().apply {
                                                set(y, m, d, 23, 59, 59)
                                            }
                                            targetDateMillis = selected.timeInMillis
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
                                        text = targetDateMillis?.let { dateFormat.format(Date(it)) } ?: "Select target date",
                                        color = if (targetDateMillis != null) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (targetDateMillis != null) {
                                    IconButton(
                                        onClick = { targetDateMillis = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear date",
                                            tint = AppTheme.colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Category Chips
                    Column {
                        Text(
                            text = "Category",
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
                            SavingsGoalCategory.values().forEach { cat ->
                                val selected = selectedCategory == cat
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.displayName) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppTheme.colors.deepPeach,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Color Selection
                    Column {
                        Text(
                            text = "Goal Color Accent",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SavingsColorPresets.forEach { hex ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                val isSelected = selectedColor.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) AppTheme.colors.textPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Priority
                    Column {
                        Text(
                            text = "Priority",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SavingsGoalPriority.values().forEach { priority ->
                                val selected = selectedPriority == priority
                                val priorityColor = when (priority) {
                                    SavingsGoalPriority.HIGH -> Color(0xFFEF4444)
                                    SavingsGoalPriority.MEDIUM -> Color(0xFFF59E0B)
                                    SavingsGoalPriority.LOW -> Color(0xFF10B981)
                                }
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) priorityColor else AppTheme.colors.cardBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedPriority = priority },
                                    color = if (selected) priorityColor.copy(alpha = 0.15f) else AppTheme.colors.surfaceSecondary
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = priority.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) priorityColor else AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Description / Note
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Note (Optional)") },
                        placeholder = { Text("Why are you saving for this?") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3,
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
                            val trimmedName = name.trim()
                            if (trimmedName.isBlank()) {
                                errorMessage = "Please enter a goal name"
                                return@Button
                            }
                            val targetPaise = CurrencyUtils.parseInputToPaiseOrNull(targetAmountInput)
                            if (targetPaise == null || targetPaise <= 0L) {
                                errorMessage = "Target amount must be greater than zero"
                                return@Button
                            }
                            val initialPaise = if (!isEditing && initialAmountInput.isNotBlank()) {
                                CurrencyUtils.parseInputToPaiseOrNull(initialAmountInput) ?: 0L
                            } else 0L

                            onSave(
                                trimmedName,
                                targetPaise,
                                initialPaise,
                                targetDateMillis,
                                selectedCategory,
                                description.trim().ifBlank { null },
                                selectedColor,
                                selectedPriority
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.deepPeach,
                            contentColor = Color.White
                        )
                    ) {
                        Text(if (isEditing) "Save Changes" else "Create Goal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
