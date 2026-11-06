package com.naman.rupeeflow.ui.screens.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.CategoryIconHelper
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToPending: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showSmsTesterDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showSmsDisclosureDialog by remember { mutableStateOf(false) }
    var showNotificationDisclosureDialog by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setSmsDetectionEnabled(true)
            Toast.makeText(context, "SMS Detection Enabled", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setSmsDetectionEnabled(false)
            Toast.makeText(context, "SMS permission denied. Manual transaction entry remains active.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = AppTheme.colors.textPrimary
                )
            }

            // Theme Mode
            item {
                SettingsSectionHeader("Appearance")
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "App Theme", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surfaceSecondary)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("DARK" to "Dark Mode", "LIGHT" to "Light", "SYSTEM" to "System").forEach { (key, label) ->
                            val isSelected = state.themeMode == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AppTheme.colors.accentContainer else Color.Transparent)
                                    .clickable { viewModel.setThemeMode(key) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Security & Privacy
            item {
                SettingsSectionHeader("Security & Privacy")
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchRow(
                        title = "Biometric App Lock",
                        subtitle = "Require fingerprint or face authentication on open",
                        checked = state.biometricLockEnabled,
                        onCheckedChange = { viewModel.setBiometricLockEnabled(it) },
                        icon = Icons.Default.Fingerprint,
                        iconTint = AppTheme.colors.primaryAccent
                    )
                }
            }

            // SMS Detection & Automation
            item {
                SettingsSectionHeader("SMS & Notification Detection")
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchRow(
                        title = "Auto-detect Financial SMS",
                        subtitle = "Automatically recognize debits, credits & UPI messages",
                        checked = state.smsDetectionEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECEIVE_SMS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    viewModel.setSmsDetectionEnabled(true)
                                } else {
                                    showSmsDisclosureDialog = true
                                }
                            } else {
                                viewModel.setSmsDetectionEnabled(false)
                            }
                        },
                        icon = Icons.Default.Sms,
                        iconTint = AppTheme.colors.primaryAccent
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "Notification Listener Service",
                        subtitle = "Companion detection for Google Pay, PhonePe, Paytm & Bank apps",
                        checked = state.notificationListenerEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showNotificationDisclosureDialog = true
                            } else {
                                viewModel.setNotificationListenerEnabled(false)
                            }
                        },
                        icon = Icons.Default.NotificationsActive,
                        iconTint = AmberGold
                    )

                    if (onNavigateToPending != null) {
                        HorizontalDivider(color = AppTheme.colors.divider, modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPending() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AmberGold.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inbox,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "View Detected Transactions",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "Review all captured SMS & app notifications",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AppTheme.colors.textSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = AppTheme.colors.divider, modifier = Modifier.padding(vertical = 12.dp))

                    // SMS Simulator / Tester
                    Button(
                        onClick = { showSmsTesterDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.accentContainer,
                            contentColor = AppTheme.colors.primaryAccent
                        )
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test SMS Parsing Engine")
                    }
                }
            }

            // Category Management
            item {
                SettingsSectionHeader("Category Management")
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Categories", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                            Text(text = "${state.categories.size} categories active", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        }
                        IconButton(
                            onClick = { showAddCategoryDialog = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppTheme.colors.accentContainer)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Category", tint = AppTheme.colors.primaryAccent)
                        }
                    }
                }
            }

            // Backup & Export
            item {
                SettingsSectionHeader("Backup & Data Export")
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    // Export JSON Backup
                    SettingsActionRow(
                        title = "Export Complete Backup (JSON)",
                        subtitle = "Back up all transactions, friends & ledger records",
                        icon = Icons.Default.Backup,
                        onClick = {
                            viewModel.exportBackup { json ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("RupeeFlow Backup", json)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, modifier = Modifier.padding(vertical = 10.dp))

                    // Restore JSON Backup
                    SettingsActionRow(
                        title = "Restore from JSON Backup",
                        subtitle = "Restore database from copied JSON string",
                        icon = Icons.Default.Restore,
                        onClick = { showRestoreDialog = true }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, modifier = Modifier.padding(vertical = 10.dp))

                    // Export Transactions CSV
                    SettingsActionRow(
                        title = "Export Transactions (CSV)",
                        subtitle = "Spreadsheet export of all transactions",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            viewModel.exportTransactionsCsv { csv ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("RupeeFlow Transactions CSV", csv)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Transactions CSV copied to clipboard!", Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    HorizontalDivider(color = AppTheme.colors.divider, modifier = Modifier.padding(vertical = 10.dp))

                    // Export Friends CSV
                    SettingsActionRow(
                        title = "Export Friends & Ledger (CSV)",
                        subtitle = "Spreadsheet export of friend balances and debts",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            viewModel.exportFriendsCsv { csv ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("RupeeFlow Friends CSV", csv)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Friends CSV copied to clipboard!", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }

            // Danger Zone: Reset Application
            item {
                SettingsSectionHeader("Danger Zone")
                RupeeFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CoralRed.copy(alpha = 0.5f),
                    backgroundColor = AppTheme.colors.cardBackground
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CoralRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = CoralRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset Application",
                                style = MaterialTheme.typography.titleMedium,
                                color = CoralRed,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Wipe all transactions, split ledgers, friends & restore fresh defaults",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralRed.copy(alpha = 0.2f),
                            contentColor = CoralRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CoralRed)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset App to Initial State", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Privacy & Architecture Info
            item {
                SettingsSectionHeader("About RupeeFlow")
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "100% Offline & Private",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.incomeGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your financial data never leaves this device. No servers, no tracking, no paid APIs. All SMS parsing and ledger accounting happen locally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailRow("Version", "1.0.0 (Release)")
                    DetailRow("Architecture", "Clean Architecture + MVVM + Room")
                    DetailRow("Target Phone", "Samsung Galaxy S24 Ultra")
                }
            }
        }
    }

    // SMS Tester Dialog
    if (showSmsTesterDialog) {
        SmsTesterDialog(
            viewModel = viewModel,
            onDismiss = {
                viewModel.clearTestSmsResult()
                showSmsTesterDialog = false
            }
        )
    }

    // Add Category Dialog
    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onAdd = { name, icon, color ->
                viewModel.addCategory(name, icon, color)
                showAddCategoryDialog = false
            }
        )
    }

    // Restore Backup Dialog
    if (showRestoreDialog) {
        RestoreBackupDialog(
            viewModel = viewModel,
            onDismiss = { showRestoreDialog = false },
            onRestoreSuccess = {
                Toast.makeText(context, "Database restored successfully!", Toast.LENGTH_LONG).show()
                showRestoreDialog = false
            }
        )
    }

    // Reset Application Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = AppTheme.colors.surfaceElevated,
            title = {
                Text(
                    text = "Reset Entire Application?",
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently erase all transactions, friends, split debts, and custom categories. The app will be restored to its initial state.\n\nThis action cannot be undone.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetApp {
                            Toast.makeText(context, "RupeeFlow reset to initial state", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.error,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Yes, Reset Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            }
        )
    }

    // SMS Detection Prominent Disclosure Dialog (Google Play Sensitive Permissions Compliance)
    if (showSmsDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showSmsDisclosureDialog = false },
            containerColor = AppTheme.colors.surfaceElevated,
            icon = {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = AppTheme.colors.primaryAccent,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "SMS Financial Detection",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RupeeFlow requests SMS access strictly to automatically detect financial debit, credit, and UPI expense messages from your bank.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "• Only bank transaction alerts are parsed locally on your device.\n" +
                                "• Personal messages, OTPs, and private text chats are never stored or tracked.\n" +
                                "• All processing is 100% offline and on-device. No data is ever transmitted to remote servers.\n" +
                                "• You can disable this feature at any time. Manual transaction entry is always available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSmsDisclosureDialog = false
                        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                ) {
                    Text("Consent & Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsDisclosureDialog = false }) {
                    Text("Decline / Keep Manual", color = AppTheme.colors.textSecondary)
                }
            }
        )
    }

    // Notification Listener Service Disclosure Dialog
    if (showNotificationDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDisclosureDialog = false },
            containerColor = AppTheme.colors.surfaceElevated,
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Notification Access",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RupeeFlow can monitor payment notifications from apps like Google Pay, PhonePe, Paytm, and your bank to automatically create draft transactions for your review.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "• Notification content is processed strictly on-device to extract transaction amounts and merchants.\n" +
                                "• No private notifications, messages, or credentials are saved or uploaded.\n" +
                                "• You will be guided to Android System Settings to grant access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationDisclosureDialog = false
                        viewModel.setNotificationListenerEnabled(true)
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open Notification Settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Text("Proceed to Settings", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDisclosureDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.textSecondary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    iconTint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = iconTint,
                checkedTrackColor = AppTheme.colors.accentContainer
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.accentContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppTheme.colors.textMuted)
    }
}

@Composable
fun SmsTesterDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var smsText by remember {
        mutableStateOf("Dear Customer, your A/c XX1234 has been debited by Rs. 850.00 on 02-Nov-2026 for UPI transaction to SWIGGY. UPI Ref 432190812345.")
    }
    val state by viewModel.uiState.collectAsState()
    val result = state.testSmsResult

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
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SMS Parser Diagnostic", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.textPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = smsText,
                    onValueChange = { smsText = it },
                    label = { Text("Enter or Paste Financial SMS", color = AppTheme.colors.textSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.testParseSms(smsText, "HDFCBK") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent, contentColor = Color.White)
                ) {
                    Text("Run Diagnostic Parse")
                }

                if (result != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (result.isValidTransaction) AppTheme.colors.incomeGreen else AmberGold
                    ) {
                        Text(
                            text = if (result.isValidTransaction) "VALID TRANSACTION DETECTED" else "NOT A FINANCIAL TRANSACTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (result.isValidTransaction) AppTheme.colors.incomeGreen else AmberGold,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (result.isValidTransaction) {
                            Text("Amount: ${CurrencyUtils.formatPaiseToRupees(result.amountPaise)}", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                            Text("Type: ${result.type.name}", color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            Text("Bank: ${result.bankName}", color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            Text("Account ending: ${result.accountLast4 ?: "N/A"}", color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            Text("Merchant: ${result.merchant ?: "N/A"}", color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            Text("Ref No: ${result.referenceNumber ?: "N/A"}", color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            if (result.isOtp) Text("Classified as OTP verification message (excluded)", color = AppTheme.colors.textSecondary)
                            if (result.isFailed) Text("Classified as failed/declined transaction (excluded)", color = AppTheme.colors.textSecondary)
                            if (result.isBalanceInquiry) Text("Classified as balance-only inquiry (excluded)", color = AppTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, icon: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#3B82F6") }

    val presetColors = listOf("#3B82F6", "#06B6D4", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Add Custom Category", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.textPrimary)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name", color = AppTheme.colors.textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Pick Color", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.forEach { colorHex ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(colorHex)))
                                .clickable { selectedColor = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank()) onAdd(name, "category", selectedColor)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent, contentColor = Color.White),
                    enabled = name.isNotBlank()
                ) {
                    Text("Add Category")
                }
            }
        }
    }
}

@Composable
fun RestoreBackupDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onRestoreSuccess: () -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val preview = remember(jsonText, password) {
        if (jsonText.isNotBlank()) {
            viewModel.inspectBackup(jsonText, password.takeIf { it.isNotBlank() })
        } else null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Restore Backup",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMessage = null
                    },
                    label = { Text("Paste Backup Content", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("Paste plaintext or encrypted JSON payload...", color = AppTheme.colors.textTertiary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (preview?.isEncrypted == true) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Backup Password", color = AppTheme.colors.textSecondary) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                if (preview != null && preview.isValid && (!preview.isEncrypted || password.isNotBlank())) {
                    Spacer(modifier = Modifier.height(12.dp))
                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = AppTheme.colors.incomeGreen
                    ) {
                        Text(
                            text = "BACKUP VERIFIED (v${preview.version})",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.incomeGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        DetailRow("Categories", "${preview.categoriesCount}")
                        DetailRow("Friends", "${preview.friendsCount}")
                        DetailRow("Friend Groups", "${preview.groupsCount}")
                        DetailRow("Transactions", "${preview.transactionsCount}")
                        DetailRow("Settlements", "${preview.settlementsCount}")
                        DetailRow("Total Volume", CurrencyUtils.formatPaiseToRupees(preview.totalVolumePaise))
                        if (preview.imagesCount > 0) {
                            DetailRow("Images / Receipts", "${preview.imagesCount}")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Atomic restore guarantee: All records and attachments will be written together. If an error occurs, the live database will be preserved unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                } else if (preview != null && !preview.isValid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = preview.errorMessage ?: "Invalid or corrupted backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.error
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = AppTheme.colors.textSecondary)
                    }

                    Button(
                        onClick = {
                            if (jsonText.isNotBlank()) {
                                isRestoring = true
                                errorMessage = null
                                if (preview?.isEncrypted == true) {
                                    viewModel.restoreEncryptedBackup(jsonText, password) { success ->
                                        isRestoring = false
                                        if (success) onRestoreSuccess() else errorMessage = "Decryption or restore failed. Check password."
                                    }
                                } else {
                                    viewModel.restoreBackup(jsonText) { success ->
                                        isRestoring = false
                                        if (success) onRestoreSuccess() else errorMessage = "Restore failed: Invalid schema or corrupted data."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        ),
                        enabled = jsonText.isNotBlank() && !isRestoring && (preview?.isValid == true)
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Confirm Restore")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
    }
}
