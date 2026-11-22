package com.naman.rupeeflow.ui.screens.splitpro

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.ui.components.FriendAvatar
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import com.naman.rupeeflow.upi.UpiAppInfo
import com.naman.rupeeflow.upi.UpiDiagnostics
import com.naman.rupeeflow.upi.UpiIntentBuilder
import com.naman.rupeeflow.upi.UpiSettlementManager
import com.naman.rupeeflow.upi.UpiValidator
import kotlinx.coroutines.launch

enum class TargetPhoneApp(
    val packageName: String,
    val appName: String,
    val vpaHandle: String,
    val actionHint: String
) {
    PHONEPE(
        packageName = UpiIntentBuilder.PHONEPE_PACKAGE,
        appName = "PhonePe",
        vpaHandle = "@ybl",
        actionHint = "To Mobile Number"
    ),
    BHIM(
        packageName = UpiIntentBuilder.BHIM_PACKAGE,
        appName = "BHIM",
        vpaHandle = "@upi",
        actionHint = "Send"
    )
}

enum class UpiPaymentMode {
    UPI_ID,
    PHONE_NUMBER
}

private enum class UpiDialogStep {
    CONFIGURE,
    CONFIRMATION,
    RESULT
}

@Composable
fun UpiSettlementDialog(
    initialFriend: Friend? = null,
    availableFriends: List<Friend> = emptyList(),
    group: ExpenseGroup? = null,
    outstandingDebtPaise: Long = 0L,
    upiSettlementManager: UpiSettlementManager,
    onFriendUpdated: ((Friend) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSettlementCompleted: (Settlement) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(UpiDialogStep.CONFIGURE) }

    // Friend selection
    var selectedFriend by remember {
        mutableStateOf(initialFriend ?: availableFriends.firstOrNull { it.netBalancePaise < 0L } ?: availableFriends.firstOrNull())
    }

    // Dynamic debt computation if friend selected
    val debtPaise = remember(selectedFriend, outstandingDebtPaise) {
        if (outstandingDebtPaise > 0L) {
            outstandingDebtPaise
        } else if (selectedFriend != null && selectedFriend!!.netBalancePaise < 0L) {
            SafeMath.safeAbs(selectedFriend!!.netBalancePaise)
        } else {
            0L
        }
    }

    // Payment mode selection: UPI ID vs Phone Number (PhonePe / BHIM)
    var paymentMode by remember(selectedFriend) {
        mutableStateOf(
            if (selectedFriend?.upiId.isNullOrBlank() && !selectedFriend?.phoneNumber.isNullOrBlank()) {
                UpiPaymentMode.PHONE_NUMBER
            } else {
                UpiPaymentMode.UPI_ID
            }
        )
    }

    var upiIdInput by remember(selectedFriend) {
        mutableStateOf(selectedFriend?.upiId ?: "")
    }
    var upiIdError by remember { mutableStateOf<String?>(null) }

    var phoneInput by remember(selectedFriend) {
        mutableStateOf(selectedFriend?.phoneNumber ?: "")
    }
    var phoneError by remember { mutableStateOf<String?>(null) }

    var selectedPhoneApp by remember {
        mutableStateOf(TargetPhoneApp.PHONEPE)
    }

    var isDirectAppLaunchFlow by remember { mutableStateOf(false) }

    var amountInput by remember(debtPaise) {
        mutableStateOf(
            if (debtPaise > 0L) CurrencyUtils.formatPaiseToRupees(debtPaise, includeSymbol = false) else ""
        )
    }
    var noteInput by remember {
        mutableStateOf(
            if (group != null) "Split Pro: ${group.name}" else "Split Pro Settlement"
        )
    }

    val currentAmountPaise = remember(amountInput) {
        CurrencyUtils.parseInputToPaise(amountInput)
    }

    var amountError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Installed UPI Apps
    val installedApps = remember {
        UpiIntentBuilder.getInstalledUpiApps(context)
    }
    var selectedTargetPackage by remember {
        mutableStateOf<String?>(installedApps.firstOrNull()?.packageName)
    }

    // Active initiated settlement tracking
    var activeSettlementId by remember { mutableStateOf<Long?>(null) }
    var activeSettlementResult by remember { mutableStateOf<Settlement?>(null) }
    var isProcessingLaunch by remember { mutableStateOf(false) }
    var showFallbackChooserPrompt by remember { mutableStateOf(false) }
    var fallbackPromptAppName by remember { mutableStateOf("") }

    // UPI Activity Launcher
    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val sId = activeSettlementId
        if (sId != null) {
            scope.launch {
                try {
                    val updated = upiSettlementManager.handlePaymentResult(
                        settlementId = sId,
                        resultCode = result.resultCode,
                        data = result.data
                    )
                    activeSettlementResult = updated
                    currentStep = UpiDialogStep.RESULT
                    if (updated.affectsLedger) {
                        onSettlementCompleted(updated)
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to process payment response"
                    currentStep = UpiDialogStep.RESULT
                }
            }
        }
    }

    // Auto-reconciliation listener for incoming bank SMS / notification while UPI dialog is active
    LaunchedEffect(upiSettlementManager, activeSettlementId) {
        val sId = activeSettlementId
        upiSettlementManager.settlementReconciledEvents.collect { event ->
            if (sId != null && event.id == sId) {
                activeSettlementResult = event
                currentStep = UpiDialogStep.RESULT
                if (event.affectsLedger) {
                    onSettlementCompleted(event)
                }
            }
        }
    }

    fun validateConfiguration(): Boolean {
        var isValid = true
        errorMessage = null

        // Validate friend
        val friend = selectedFriend
        if (friend == null) {
            errorMessage = "Please select a recipient"
            return false
        }

        if (paymentMode == UpiPaymentMode.UPI_ID) {
            // Validate UPI ID
            val cleanUpi = upiIdInput.trim()
            if (cleanUpi.isBlank() || !UpiValidator.isValidUpiId(cleanUpi)) {
                upiIdError = "Please enter a valid UPI address (e.g. name@bank)"
                isValid = false
            } else {
                upiIdError = null
                // Save UPI ID to friend profile if updated or newly entered
                if (friend.upiId != cleanUpi) {
                    val updatedFriend = friend.copy(
                        upiId = cleanUpi,
                        upiLastUpdated = System.currentTimeMillis()
                    )
                    selectedFriend = updatedFriend
                    onFriendUpdated?.invoke(updatedFriend)
                }
            }
        } else {
            // Validate Phone Number
            val cleanPhone = UpiValidator.cleanPhoneNumber(phoneInput)
            if (cleanPhone == null || !UpiValidator.isValidPhoneNumber(cleanPhone)) {
                phoneError = "Please enter a valid 10-digit mobile number"
                isValid = false
            } else {
                phoneError = null
                // Save phone number to friend profile if updated or newly entered
                if (friend.phoneNumber != cleanPhone) {
                    val updatedFriend = friend.copy(
                        phoneNumber = cleanPhone
                    )
                    selectedFriend = updatedFriend
                    onFriendUpdated?.invoke(updatedFriend)
                }
            }
        }

        // Validate amount
        if (currentAmountPaise <= 0L) {
            amountError = "Amount must be greater than zero"
            isValid = false
        } else if (debtPaise > 0L && currentAmountPaise > debtPaise) {
            amountError = "Amount exceeds outstanding balance (${CurrencyUtils.formatPaiseToRupees(debtPaise)})"
            isValid = false
        } else {
            amountError = null
        }

        return isValid
    }

    fun launchDirectAppPayment() {
        val friend = selectedFriend ?: return
        if (!validateConfiguration()) return

        val cleanPhone = UpiValidator.cleanPhoneNumber(phoneInput) ?: return
        val targetPkg = selectedPhoneApp.packageName
        val appName = selectedPhoneApp.appName

        isProcessingLaunch = true
        errorMessage = null

        scope.launch {
            try {
                // 1. Copy phone number to clipboard
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Phone Number", cleanPhone)
                clipboard?.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Phone number copied: $cleanPhone", android.widget.Toast.LENGTH_SHORT).show()

                // 2. Initiate settlement record
                val settlementId = upiSettlementManager.initiateDirectAppSettlement(
                    friend = friend,
                    amountPaise = currentAmountPaise,
                    targetPackage = targetPkg,
                    groupId = group?.id,
                    note = noteInput.trim(),
                    phoneNumber = cleanPhone
                )
                activeSettlementId = settlementId
                isDirectAppLaunchFlow = true

                // 3. Launch the target app directly (PhonePe or BHIM)
                val launchIntent = UpiIntentBuilder.getAppLaunchIntent(context, targetPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    currentStep = UpiDialogStep.RESULT
                } else {
                    errorMessage = "$appName is not installed on this device"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to open $appName"
            } finally {
                isProcessingLaunch = false
            }
        }
    }

    fun launchUpiPayment(forceGenericChooser: Boolean = false) {
        val friend = selectedFriend ?: return
        if (!validateConfiguration()) return
        isProcessingLaunch = true
        errorMessage = null

        val targetPkg = if (forceGenericChooser) {
            null
        } else if (paymentMode == UpiPaymentMode.PHONE_NUMBER) {
            selectedPhoneApp.packageName
        } else {
            selectedTargetPackage
        }

        val appName = if (paymentMode == UpiPaymentMode.PHONE_NUMBER) {
            selectedPhoneApp.appName
        } else {
            installedApps.find { it.packageName == targetPkg }?.appName
                ?: (if (targetPkg == null) "System Chooser" else "Selected App")
        }

        scope.launch {
            try {
                val (settlementId, intent) = if (paymentMode == UpiPaymentMode.PHONE_NUMBER) {
                    val cleanPhone = UpiValidator.cleanPhoneNumber(phoneInput)
                    upiSettlementManager.initiateSettlement(
                        friend = friend,
                        amountPaise = currentAmountPaise,
                        groupId = group?.id,
                        note = noteInput.trim(),
                        targetPackage = targetPkg,
                        phoneNumber = cleanPhone
                    )
                } else {
                    upiSettlementManager.initiateSettlement(
                        friend = friend,
                        amountPaise = currentAmountPaise,
                        groupId = group?.id,
                        note = noteInput.trim(),
                        targetPackage = targetPkg,
                        customVpa = upiIdInput.trim()
                    )
                }
                activeSettlementId = settlementId
                isDirectAppLaunchFlow = false

                val resolvedComponent = UpiIntentBuilder.getResolvedActivityComponent(context, intent)
                UpiDiagnostics.logActivityResolution(targetPkg, resolvedComponent)
                UpiDiagnostics.logLaunchAttempt(targetPkg, isGenericChooser = targetPkg == null)

                // If explicitly targeted to a package (e.g. PhonePe or BHIM), verify resolution before launch
                if (resolvedComponent == null && targetPkg != null) {
                    val notFound = ActivityNotFoundException("No activity found to handle explicit intent for $targetPkg")
                    UpiDiagnostics.logLaunchException(targetPkg, notFound)
                    fallbackPromptAppName = appName
                    showFallbackChooserPrompt = true
                    return@launch
                }

                try {
                    upiLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    UpiDiagnostics.logLaunchException(targetPkg, e)
                    if (targetPkg != null) {
                        fallbackPromptAppName = appName
                        showFallbackChooserPrompt = true
                    } else {
                        errorMessage = "No compatible UPI app found to process this payment"
                    }
                }
            } catch (e: Exception) {
                UpiDiagnostics.logLaunchException(targetPkg, e)
                errorMessage = e.message ?: "Failed to launch UPI application"
            } finally {
                isProcessingLaunch = false
            }
        }
    }

    Dialog(onDismissRequest = {
        if (currentStep != UpiDialogStep.RESULT || activeSettlementResult?.affectsLedger == true) {
            onDismiss()
        }
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
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
                                .background(AppTheme.colors.primaryAccentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when (currentStep) {
                                    UpiDialogStep.CONFIGURE -> "Pay with UPI"
                                    UpiDialogStep.CONFIRMATION -> "Confirm Payment"
                                    UpiDialogStep.RESULT -> "Payment Status"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            if (group != null) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.primaryAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                // Error message banner
                errorMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = CoralRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CoralRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = msg, style = MaterialTheme.typography.bodySmall, color = CoralRed)
                        }
                    }
                }

                when (currentStep) {
                    UpiDialogStep.CONFIGURE -> {
                        // Friend Selector (if available)
                        if (availableFriends.size > 1 && initialFriend == null) {
                            Column {
                                Text(
                                    text = "Select Recipient",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(availableFriends) { f ->
                                        val isSelected = selectedFriend?.id == f.id
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedFriend = f
                                                upiIdInput = f.upiId ?: ""
                                                phoneInput = f.phoneNumber ?: ""
                                                if (f.upiId.isNullOrBlank() && !f.phoneNumber.isNullOrBlank()) {
                                                    paymentMode = UpiPaymentMode.PHONE_NUMBER
                                                }
                                                val d = if (f.netBalancePaise < 0L) SafeMath.safeAbs(f.netBalancePaise) else 0L
                                                if (d > 0L) {
                                                    amountInput = CurrencyUtils.formatPaiseToRupees(d, includeSymbol = false)
                                                }
                                            },
                                            label = { Text(f.name) },
                                            leadingIcon = {
                                                FriendAvatar(friend = f, modifier = Modifier.size(18.dp))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Selected Recipient Card
                        selectedFriend?.let { friend ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = AppTheme.colors.surface,
                                border = BorderStroke(1.dp, AppTheme.colors.divider)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FriendAvatar(friend = friend, modifier = Modifier.size(44.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = friend.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        if (debtPaise > 0L) {
                                            Text(
                                                text = "Outstanding Balance: ${CurrencyUtils.formatPaiseToRupees(debtPaise)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CoralRed,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Payment Mode Selector: UPI ID vs Phone Number
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppTheme.colors.surfaceSecondary)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // UPI ID Tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (paymentMode == UpiPaymentMode.UPI_ID) AppTheme.colors.primaryAccent else Color.Transparent)
                                    .clickable {
                                        paymentMode = UpiPaymentMode.UPI_ID
                                        errorMessage = null
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AlternateEmail,
                                        contentDescription = null,
                                        tint = if (paymentMode == UpiPaymentMode.UPI_ID) Color.White else AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "UPI ID / VPA",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (paymentMode == UpiPaymentMode.UPI_ID) FontWeight.Bold else FontWeight.Normal,
                                        color = if (paymentMode == UpiPaymentMode.UPI_ID) Color.White else AppTheme.colors.textSecondary
                                    )
                                }
                            }

                            // Phone Number Tab (PhonePe / BHIM)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (paymentMode == UpiPaymentMode.PHONE_NUMBER) AppTheme.colors.primaryAccent else Color.Transparent)
                                    .clickable {
                                        paymentMode = UpiPaymentMode.PHONE_NUMBER
                                        errorMessage = null
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = if (paymentMode == UpiPaymentMode.PHONE_NUMBER) Color.White else AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Phone Number",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (paymentMode == UpiPaymentMode.PHONE_NUMBER) FontWeight.Bold else FontWeight.Normal,
                                        color = if (paymentMode == UpiPaymentMode.PHONE_NUMBER) Color.White else AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        }

                        if (paymentMode == UpiPaymentMode.UPI_ID) {
                            // Recipient UPI ID Input
                            OutlinedTextField(
                                value = upiIdInput,
                                onValueChange = {
                                    upiIdInput = it
                                    if (upiIdError != null) validateConfiguration()
                                },
                                label = { Text("Recipient UPI ID / VPA *") },
                                placeholder = { Text("e.g. friend@okhdfcbank") },
                                isError = upiIdError != null,
                                supportingText = {
                                    if (upiIdError != null) {
                                        Text(upiIdError ?: "", color = CoralRed)
                                    } else {
                                        Text(
                                            "Format check only. Verify ownership before paying.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textTertiary
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = rupeeFlowTextFieldColors(),
                                shape = RoundedCornerShape(14.dp)
                            )
                        } else {
                            // Phone Number Mode for PhonePe & BHIM
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = {
                                        phoneInput = it
                                        if (phoneError != null) validateConfiguration()
                                    },
                                    label = { Text("Recipient Phone Number *") },
                                    placeholder = { Text("9876543210") },
                                    leadingIcon = {
                                        Text(
                                            text = "+91",
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.primaryAccent,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    },
                                    isError = phoneError != null,
                                    supportingText = {
                                        if (phoneError != null) {
                                            Text(phoneError ?: "", color = CoralRed)
                                        } else {
                                            Text(
                                                "10-digit Indian phone number for PhonePe & BHIM.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.textTertiary
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = rupeeFlowTextFieldColors(),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                // App Selection (PhonePe & BHIM)
                                Column {
                                    Text(
                                        text = "Target UPI App",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isPhonePeSelected = selectedPhoneApp == TargetPhoneApp.PHONEPE
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedPhoneApp = TargetPhoneApp.PHONEPE },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isPhonePeSelected) AppTheme.colors.primaryAccent.copy(alpha = 0.12f) else AppTheme.colors.surfaceSecondary,
                                            border = BorderStroke(
                                                1.5.dp,
                                                if (isPhonePeSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.AccountBalanceWallet,
                                                        contentDescription = null,
                                                        tint = if (isPhonePeSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "PhonePe",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isPhonePeSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textPrimary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Auto-fills via @ybl or open to 'To Mobile'",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AppTheme.colors.textTertiary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        val isBhimSelected = selectedPhoneApp == TargetPhoneApp.BHIM
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedPhoneApp = TargetPhoneApp.BHIM },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isBhimSelected) AppTheme.colors.primaryAccent.copy(alpha = 0.12f) else AppTheme.colors.surfaceSecondary,
                                            border = BorderStroke(
                                                1.5.dp,
                                                if (isBhimSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Payments,
                                                        contentDescription = null,
                                                        tint = if (isBhimSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "BHIM",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isBhimSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textPrimary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Official NPCI (@upi) or open to 'Send'",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AppTheme.colors.textTertiary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Notice explaining auto-fill vs open app
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppTheme.colors.primaryAccentContainer.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = AppTheme.colors.primaryAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Can auto-fill amount via phone UPI, or open ${selectedPhoneApp.appName} directly with copied number.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Amount Input
                        Column {
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = {
                                    amountInput = it
                                    if (amountError != null) validateConfiguration()
                                },
                                label = { Text("Settlement Amount (INR) *") },
                                placeholder = { Text("0.00") },
                                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = amountError != null,
                                supportingText = amountError?.let { { Text(it, color = CoralRed) } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = rupeeFlowTextFieldColors(),
                                shape = RoundedCornerShape(14.dp)
                            )

                            // Quick Partial / Full amount shortcuts
                            if (debtPaise > 0L) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = {
                                            amountInput = CurrencyUtils.formatPaiseToRupees(debtPaise, includeSymbol = false)
                                        },
                                        label = { Text("Full Balance") }
                                    )
                                    AssistChip(
                                        onClick = {
                                            val halfPaise = debtPaise / 2L
                                            amountInput = CurrencyUtils.formatPaiseToRupees(halfPaise, includeSymbol = false)
                                        },
                                        label = { Text("50% Partial") }
                                    )
                                }
                            }

                            // Remaining Balance Preview (Bug 3 Requirement 6)
                            if (debtPaise > 0L && currentAmountPaise > 0L) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        currentAmountPaise < debtPaise -> AppTheme.colors.surfaceSecondary
                                        currentAmountPaise == debtPaise -> AppTheme.colors.incomeGreen.copy(alpha = 0.12f)
                                        else -> AmberGold.copy(alpha = 0.12f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            currentAmountPaise < debtPaise -> AppTheme.colors.cardBorder
                                            currentAmountPaise == debtPaise -> AppTheme.colors.incomeGreen.copy(alpha = 0.3f)
                                            else -> AmberGold.copy(alpha = 0.3f)
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when {
                                                currentAmountPaise < debtPaise -> "Remaining balance after payment:"
                                                currentAmountPaise == debtPaise -> "Full Settlement status:"
                                                else -> "Advance / Credit after payment:"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = when {
                                                currentAmountPaise < debtPaise -> CurrencyUtils.formatPaiseToRupees(debtPaise - currentAmountPaise)
                                                currentAmountPaise == debtPaise -> "Fully Settled (₹0.00)"
                                                else -> "+${CurrencyUtils.formatPaiseToRupees(currentAmountPaise - debtPaise)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                currentAmountPaise < debtPaise -> AppTheme.colors.primaryAccent
                                                currentAmountPaise == debtPaise -> AppTheme.colors.incomeGreen
                                                else -> AmberGold
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Payment Note
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            label = { Text("Payment Note (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = rupeeFlowTextFieldColors(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Cancel", color = AppTheme.colors.textSecondary)
                            }
                            Button(
                                onClick = {
                                    if (validateConfiguration()) {
                                        currentStep = UpiDialogStep.CONFIRMATION
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppTheme.colors.primaryAccent,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = if (paymentMode == UpiPaymentMode.PHONE_NUMBER) "Continue (${selectedPhoneApp.appName})" else "Pay with UPI",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    UpiDialogStep.CONFIRMATION -> {
                        // Confirmation Summary
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = AppTheme.colors.surface,
                            border = BorderStroke(1.dp, AppTheme.colors.divider)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Recipient:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                    Text(selectedFriend?.name ?: "Recipient", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                }
                                if (paymentMode == UpiPaymentMode.PHONE_NUMBER) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Phone Number:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                UpiValidator.formatPhoneDisplay(phoneInput),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppTheme.colors.primaryAccent
                                            )
                                            IconButton(
                                                onClick = {
                                                    val cleanPhone = UpiValidator.cleanPhoneNumber(phoneInput) ?: phoneInput
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Phone Number", cleanPhone)
                                                    clipboard?.setPrimaryClip(clip)
                                                    android.widget.Toast.makeText(context, "Phone number copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Phone",
                                                    tint = AppTheme.colors.primaryAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Target App:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                        Text(
                                            "${selectedPhoneApp.appName} (${selectedPhoneApp.vpaHandle})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("UPI ID:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(upiIdInput.trim(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.primaryAccent)
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("UPI ID", upiIdInput.trim())
                                                    clipboard?.setPrimaryClip(clip)
                                                    android.widget.Toast.makeText(context, "UPI ID copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy UPI ID",
                                                    tint = AppTheme.colors.primaryAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Amount:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(CurrencyUtils.formatPaiseToRupees(currentAmountPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldMint)
                                        IconButton(
                                            onClick = {
                                                val amountStr = CurrencyUtils.formatPaiseToRupees(currentAmountPaise, includeSymbol = false)
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Amount", amountStr)
                                                clipboard?.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(context, "Amount copied: ₹$amountStr", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Amount",
                                                tint = EmeraldMint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                if (group != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Group:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                        Text(group.name, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Note:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                    Text(noteInput.ifBlank { "RupeeFlow Settlement" }, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        if (paymentMode == UpiPaymentMode.PHONE_NUMBER) {
                            // Information card on PhonePe / BHIM options
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppTheme.colors.primaryAccentContainer.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Payment Mode for ${selectedPhoneApp.appName}:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.primaryAccent
                                    )
                                    Text(
                                        text = "• Auto-fill & Pay: Attempts to open ${selectedPhoneApp.appName} with amount pre-filled via ${selectedPhoneApp.vpaHandle}.\n• Open App Directly: Copies phone & amount and opens ${selectedPhoneApp.appName} directly if intent fails.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }

                            // Actions for Phone Number Mode
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { currentStep = UpiDialogStep.CONFIGURE },
                                        modifier = Modifier.weight(0.7f).height(48.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("Back", color = AppTheme.colors.textSecondary)
                                    }
                                    OutlinedButton(
                                        onClick = { launchDirectAppPayment() },
                                        modifier = Modifier.weight(1.3f).height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = !isProcessingLaunch,
                                        border = BorderStroke(1.dp, AppTheme.colors.primaryAccent)
                                    ) {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppTheme.colors.primaryAccent)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open App Directly", fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryAccent)
                                    }
                                }
                                Button(
                                    onClick = { launchUpiPayment() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = !isProcessingLaunch,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldMint,
                                        contentColor = Color.White
                                    )
                                ) {
                                    if (isProcessingLaunch) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Auto-fill & Pay with ${selectedPhoneApp.appName}", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // App Selection for standard UPI ID
                            if (installedApps.isNotEmpty()) {
                                Column {
                                    Text(
                                        text = "Choose UPI Application",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(installedApps) { app ->
                                            val isSelected = selectedTargetPackage == app.packageName
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedTargetPackage = app.packageName },
                                                label = { Text(app.appName) }
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = selectedTargetPackage == null,
                                                onClick = { selectedTargetPackage = null },
                                                label = { Text("Any App (Chooser)") }
                                            )
                                        }
                                    }
                                }
                            }

                            // BHIM compatibility notice
                            if (selectedTargetPackage == UpiIntentBuilder.BHIM_PACKAGE ||
                                selectedTargetPackage?.contains("npci") == true) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFE65100),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "BHIM enforces strict NPCI security rules. If BHIM shows an error, " +
                                                "open BHIM manually and use \"Send\" or \"Scan QR\" with the UPI ID shown above.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }
                            }

                            // Security Advisory
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(VibrantCyan.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "RupeeFlow does not authorize bank payments. You will authenticate and enter your UPI PIN inside your chosen UPI application.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }

                            // Actions for UPI ID Mode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { currentStep = UpiDialogStep.CONFIGURE },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Back", color = AppTheme.colors.textSecondary)
                                }
                                Button(
                                    onClick = { launchUpiPayment() },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = !isProcessingLaunch,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldMint,
                                        contentColor = Color.White
                                    )
                                ) {
                                    if (isProcessingLaunch) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text("Pay with UPI", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    UpiDialogStep.RESULT -> {
                        val res = activeSettlementResult
                        val isVerifiedSuccess = res?.affectsLedger == true

                        if (isDirectAppLaunchFlow && !isVerifiedSuccess) {
                            // Direct App Guidance & Manual Confirmation Screen for PhonePe / BHIM
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(AppTheme.colors.primaryAccent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = AppTheme.colors.primaryAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Text(
                                    text = "${selectedPhoneApp.appName} Payment",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = AppTheme.colors.surface,
                                    border = BorderStroke(1.dp, AppTheme.colors.divider)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Recipient:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                            Text(selectedFriend?.name ?: "Friend", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Phone Number:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(UpiValidator.formatPhoneDisplay(phoneInput), fontWeight = FontWeight.SemiBold, color = AppTheme.colors.primaryAccent)
                                                IconButton(
                                                    onClick = {
                                                        val cleanPhone = UpiValidator.cleanPhoneNumber(phoneInput) ?: phoneInput
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Phone Number", cleanPhone)
                                                        clipboard?.setPrimaryClip(clip)
                                                        android.widget.Toast.makeText(context, "Phone number copied", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Phone", tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Amount to Enter:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(CurrencyUtils.formatPaiseToRupees(currentAmountPaise), fontWeight = FontWeight.Bold, color = EmeraldMint)
                                                IconButton(
                                                    onClick = {
                                                        val amountStr = CurrencyUtils.formatPaiseToRupees(currentAmountPaise, includeSymbol = false)
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Amount", amountStr)
                                                        clipboard?.setPrimaryClip(clip)
                                                        android.widget.Toast.makeText(context, "Amount copied: ₹$amountStr", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Amount", tint = EmeraldMint, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppTheme.colors.surfaceSecondary
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Steps to complete in ${selectedPhoneApp.appName}:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = "1. In ${selectedPhoneApp.appName}, tap \"${selectedPhoneApp.actionHint}\"",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = "2. Paste recipient's phone number",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = "3. Enter ₹${CurrencyUtils.formatPaiseToRupees(currentAmountPaise, includeSymbol = false)} and pay with your UPI PIN",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val sId = activeSettlementId ?: return@Button
                                        scope.launch {
                                            val confirmed = upiSettlementManager.confirmManuallyUnverified(sId)
                                            activeSettlementResult = confirmed
                                            if (confirmed.affectsLedger) {
                                                onSettlementCompleted(confirmed)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldMint,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("I Have Paid (Confirm Settlement)", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Cancel / Close", color = AppTheme.colors.textSecondary)
                                }
                            }
                        } else {
                            // Standard result view
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isVerifiedSuccess) EmeraldMint.copy(alpha = 0.15f)
                                            else AmberGold.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isVerifiedSuccess) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = if (isVerifiedSuccess) EmeraldMint else AmberGold,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Text(
                                    text = when (res?.upiPaymentStatus) {
                                        SettlementPaymentStatus.SUCCESS -> "Payment Completed"
                                        SettlementPaymentStatus.CANCELLED -> "Payment Cancelled"
                                        SettlementPaymentStatus.FAILED -> "Payment Failed"
                                        SettlementPaymentStatus.PENDING_VERIFICATION -> "Pending Verification"
                                        else -> "Payment Incomplete"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )

                                // Verification Status badge
                                Text(
                                    text = "Status: ${res?.upiVerificationStatus?.displayName ?: "Unverified"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isVerifiedSuccess) EmeraldMint else AmberGold
                                )

                                // Recipient & amount summary
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = AppTheme.colors.surface,
                                    border = BorderStroke(1.dp, AppTheme.colors.divider)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Recipient:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                            Text(selectedFriend?.name ?: "Friend", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Amount:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                            Text(CurrencyUtils.formatPaiseToRupees(res?.amountPaise ?: currentAmountPaise), fontWeight = FontWeight.Bold, color = EmeraldMint)
                                        }
                                        if (!res?.referenceNumber.isNullOrBlank()) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Ref No:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                                Text(res?.referenceNumber ?: "", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textPrimary)
                                            }
                                        }
                                    }
                                }

                                if (!isVerifiedSuccess && res?.upiPaymentStatus == SettlementPaymentStatus.PENDING_VERIFICATION) {
                                    Text(
                                        text = "The UPI app returned without authoritative proof. You can confirm manually if the amount was debited.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = AppTheme.colors.textSecondary
                                    )

                                    Button(
                                        onClick = {
                                            val sId = activeSettlementId ?: return@Button
                                            scope.launch {
                                                val confirmed = upiSettlementManager.confirmManuallyUnverified(sId)
                                                activeSettlementResult = confirmed
                                                onSettlementCompleted(confirmed)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AmberGold,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Confirm Payment (Unverified)", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppTheme.colors.primaryAccent,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFallbackChooserPrompt) {
        AlertDialog(
            onDismissRequest = { showFallbackChooserPrompt = false },
            title = {
                Text(
                    text = "App Launch Notice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = if (paymentMode == UpiPaymentMode.PHONE_NUMBER) {
                        "$fallbackPromptAppName cannot open this payment intent directly due to app security rules. " +
                            "Would you like to open $fallbackPromptAppName directly (phone number & amount will be copied), or try the system chooser?"
                    } else {
                        "$fallbackPromptAppName cannot open this payment request directly. " +
                            "Would you like to try opening with the system app chooser, or copy the UPI ID?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                if (paymentMode == UpiPaymentMode.PHONE_NUMBER) {
                    Button(
                        onClick = {
                            showFallbackChooserPrompt = false
                            launchDirectAppPayment()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Open $fallbackPromptAppName Directly")
                    }
                } else {
                    Button(
                        onClick = {
                            showFallbackChooserPrompt = false
                            selectedTargetPackage = null
                            launchUpiPayment(forceGenericChooser = true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Open Chooser")
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (paymentMode == UpiPaymentMode.PHONE_NUMBER) {
                        OutlinedButton(
                            onClick = {
                                showFallbackChooserPrompt = false
                                selectedTargetPackage = null
                                launchUpiPayment(forceGenericChooser = true)
                            }
                        ) {
                            Text("Open Chooser")
                        }
                        OutlinedButton(
                            onClick = {
                                val cleanPhone = UpiValidator.cleanPhoneNumber(phoneInput) ?: phoneInput
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Phone Number", cleanPhone)
                                clipboard?.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Phone number copied", android.widget.Toast.LENGTH_SHORT).show()
                                showFallbackChooserPrompt = false
                            }
                        ) {
                            Text("Copy Number")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("UPI ID", upiIdInput.trim())
                                clipboard?.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "UPI ID copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                showFallbackChooserPrompt = false
                            }
                        ) {
                            Text("Copy UPI ID")
                        }
                    }
                    TextButton(onClick = { showFallbackChooserPrompt = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

