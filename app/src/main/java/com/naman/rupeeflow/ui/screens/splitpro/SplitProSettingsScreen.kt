package com.naman.rupeeflow.ui.screens.splitpro

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.data.repository.UserPreferencesRepository
import com.naman.rupeeflow.domain.model.SplitMethod
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import com.naman.rupeeflow.upi.UpiValidator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitProSettingsScreen(
    userPreferencesRepository: UserPreferencesRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = com.naman.rupeeflow.data.repository.UserPreferences()
    )

    var personalUpiInput by remember(prefs.personalUpiId) {
        mutableStateOf(prefs.personalUpiId ?: "")
    }
    var personalNameInput by remember(prefs.personalUpiName) {
        mutableStateOf(prefs.personalUpiName ?: "")
    }
    var upiError by remember { mutableStateOf<String?>(null) }
    var isSavingUpi by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Split Pro Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
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
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal UPI Configuration for Payment Requests / QR
            item {
                Text(
                    text = "Personal UPI Settlement",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primaryAccent
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "My UPI ID & Display Name",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Used to generate local settlement QR codes when friends owe you money",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }

                        // My UPI ID (VPA)
                        OutlinedTextField(
                            value = personalUpiInput,
                            onValueChange = { input ->
                                personalUpiInput = input
                                val trimmed = input.trim()
                                upiError = if (trimmed.isNotBlank() && !UpiValidator.isValidUpiId(trimmed)) {
                                    "Invalid UPI ID format (e.g. name@bank or 9876543210@upi)"
                                } else {
                                    null
                                }
                            },
                            label = { Text("My UPI ID (VPA) *") },
                            placeholder = { Text("e.g. yourname@okhdfcbank") },
                            singleLine = true,
                            isError = upiError != null,
                            supportingText = {
                                if (upiError != null) {
                                    Text(upiError ?: "", color = CoralRed)
                                } else if (!prefs.personalUpiId.isNullOrBlank()) {
                                    Text("Active receiver VPA", color = EmeraldMint)
                                } else {
                                    Text("Standard personal UPI address", color = AppTheme.colors.textTertiary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = rupeeFlowTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // My Display Name
                        OutlinedTextField(
                            value = personalNameInput,
                            onValueChange = { personalNameInput = it },
                            label = { Text("My Display Name (Payee Name)") },
                            placeholder = { Text("e.g. Rahul Sharma") },
                            singleLine = true,
                            supportingText = {
                                Text("Displayed to friends when scanning your settlement QR", color = AppTheme.colors.textTertiary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = rupeeFlowTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Credential Security Advisory
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AppTheme.colors.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = EmeraldMint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "RupeeFlow never requests or stores your UPI PIN, bank password, OTP, or any account credentials.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }

                        // Action Buttons: Save & Clear
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!prefs.personalUpiId.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            userPreferencesRepository.setPersonalUpiDetails(null, null)
                                            personalUpiInput = ""
                                            personalNameInput = ""
                                            upiError = null
                                            Toast.makeText(context, "Personal UPI details removed", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Clear")
                                }
                            }

                            Button(
                                onClick = {
                                    val trimmedUpi = personalUpiInput.trim()
                                    if (trimmedUpi.isBlank() || !UpiValidator.isValidUpiId(trimmedUpi)) {
                                        upiError = "Please enter a valid UPI address"
                                        return@Button
                                    }
                                    isSavingUpi = true
                                    scope.launch {
                                        userPreferencesRepository.setPersonalUpiDetails(
                                            upiId = trimmedUpi,
                                            displayName = personalNameInput.trim().ifBlank { null }
                                        )
                                        isSavingUpi = false
                                        Toast.makeText(context, "Personal UPI details saved", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = personalUpiInput.isNotBlank() && upiError == null && !isSavingUpi,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                            ) {
                                Text("Save UPI Details")
                            }
                        }
                    }
                }
            }

            // General Splitting Preferences
            item {
                Text(
                    text = "Splitting Defaults",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primaryAccent
                )
            }

            // Default Split Method
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Default Split Method",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Initial method selected when creating group expenses",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(SplitMethod.EQUAL, SplitMethod.EXACT_AMOUNT, SplitMethod.PERCENTAGE, SplitMethod.SHARES).forEach { method ->
                                FilterChip(
                                    selected = prefs.defaultSplitMethod == method,
                                    onClick = {
                                        scope.launch {
                                            userPreferencesRepository.setDefaultSplitMethod(method)
                                        }
                                    },
                                    label = { Text(method.displayName) }
                                )
                            }
                        }
                    }
                }
            }

            // Default Participant Selection
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select All Members by Default",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "Automatically checks all group members when adding a new expense",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = prefs.defaultParticipantSelection == "ALL",
                            onCheckedChange = { checked ->
                                scope.launch {
                                    userPreferencesRepository.setDefaultParticipantSelection(if (checked) "ALL" else "CUSTOM")
                                }
                            }
                        )
                    }
                }
            }

            // Financial Calculations
            item {
                Text(
                    text = "Financial Calculations",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primaryAccent
                )
            }

            // Debt Simplification
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Debt Simplification",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "Calculates minimal cash flow settlement plans while strictly preserving each member's exact net balance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = prefs.debtSimplificationEnabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    userPreferencesRepository.setDebtSimplificationEnabled(checked)
                                }
                            }
                        )
                    }
                }
            }

            // Currency
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Group Currency",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Active currency for Split Pro groups",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("INR", "USD", "EUR", "GBP").forEach { curr ->
                                FilterChip(
                                    selected = prefs.splitProCurrency == curr,
                                    onClick = {
                                        scope.launch {
                                            userPreferencesRepository.setSplitProCurrency(curr)
                                        }
                                    },
                                    label = { Text(curr) }
                                )
                            }
                        }
                    }
                }
            }

            // Notifications & Reminders
            item {
                Text(
                    text = "Notifications & Reminders",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primaryAccent
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Group Activity Notifications",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Alerts when expenses or settlements are added",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            Switch(
                                checked = prefs.groupNotificationsEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        userPreferencesRepository.setGroupNotificationsEnabled(checked)
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = AppTheme.colors.divider)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Settlement Reminders",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Notify when friend balances remain outstanding",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            Switch(
                                checked = prefs.settlementRemindersEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        userPreferencesRepository.setSettlementRemindersEnabled(checked)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Receipt Handling
            item {
                Text(
                    text = "Receipts & Storage",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primaryAccent
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Compress Bill Receipts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "Optimizes bill images and documents for local offline storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = prefs.autoCompressReceipts,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    userPreferencesRepository.setAutoCompressReceipts(checked)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

