package com.naman.rupeeflow.ui.screens.splitpro

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.data.repository.UserPreferencesRepository
import com.naman.rupeeflow.domain.model.SplitMethod
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitProSettingsScreen(
    userPreferencesRepository: UserPreferencesRepository,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val prefs by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = com.naman.rupeeflow.data.repository.UserPreferences()
    )

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

                        Divider(color = AppTheme.colors.divider)

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

