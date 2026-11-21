package com.naman.rupeeflow.ui.screens.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import com.naman.rupeeflow.upi.UpiQrCodeGenerator
import com.naman.rupeeflow.upi.UpiSettlementManager
import com.naman.rupeeflow.upi.UpiValidator
import kotlinx.coroutines.delay

@Composable
fun PersonalUpiQrDialog(
    friend: Friend,
    personalUpiId: String?,
    personalUpiName: String?,
    initialAmountPaise: Long = 0L,
    totalOutstandingPaise: Long = 0L,
    upiSettlementManager: UpiSettlementManager? = null,
    settlementRepository: SettlementRepository? = null,
    onSavePersonalUpi: (upiId: String, name: String?) -> Unit,
    onMarkAsReceived: (Settlement) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isConfiguringUpi by remember { mutableStateOf(personalUpiId.isNullOrBlank()) }

    var upiInput by remember(personalUpiId) { mutableStateOf(personalUpiId ?: "") }
    var nameInput by remember(personalUpiName) { mutableStateOf(personalUpiName ?: "") }
    var upiError by remember { mutableStateOf<String?>(null) }

    // Settlement amount state (Bug 3 fix: editable with separate original and remaining values)
    val defaultTargetPaise = if (initialAmountPaise > 0L) initialAmountPaise else totalOutstandingPaise
    var amountInput by remember(defaultTargetPaise) {
        mutableStateOf(
            if (defaultTargetPaise > 0L) CurrencyUtils.formatPaiseToRupees(defaultTargetPaise, includeSymbol = false) else ""
        )
    }
    val currentAmountPaise = remember(amountInput) {
        CurrencyUtils.parseInputToPaise(amountInput)
    }

    var currentRef by remember { mutableStateOf(UpiQrCodeGenerator.generateQrReference()) }
    var showConfirmReceiveDialog by remember { mutableStateOf(false) }
    var isProcessingMark by remember { mutableStateOf(false) }

    // Real-time automatic reconciliation state (Bug 2 fix)
    var activePendingSettlementId by remember { mutableStateOf<Long?>(null) }
    var reconciledSettlement by remember { mutableStateOf<Settlement?>(null) }
    var autoCloseJobStarted by remember { mutableStateOf(false) }

    // Initiate or update pending settlement session in Room
    LaunchedEffect(friend.id, personalUpiId, currentAmountPaise, currentRef) {
        if (!personalUpiId.isNullOrBlank() && currentAmountPaise > 0L && upiSettlementManager != null) {
            try {
                val currentId = activePendingSettlementId
                if (currentId == null) {
                    val sId = upiSettlementManager.initiateQrSettlement(
                        friend = friend,
                        amountPaise = currentAmountPaise,
                        isPayingFriend = false, // Friend is paying Me
                        qrReference = currentRef,
                        note = "RupeeFlow QR settlement from ${friend.name}"
                    )
                    activePendingSettlementId = sId
                } else {
                    upiSettlementManager.updatePendingSettlementAmount(
                        settlementId = currentId,
                        newAmountPaise = currentAmountPaise,
                        newQrReference = currentRef
                    )
                }
            } catch (_: Exception) {}
        }
    }

    // Observe active pending settlement for real-time bank SMS reconciliation
    LaunchedEffect(activePendingSettlementId, settlementRepository) {
        val sId = activePendingSettlementId ?: return@LaunchedEffect
        val repo = settlementRepository ?: return@LaunchedEffect
        repo.getSettlementByIdFlow(sId).collect { settlement ->
            if (settlement?.upiPaymentStatus == SettlementPaymentStatus.SUCCESS) {
                reconciledSettlement = settlement
                if (!autoCloseJobStarted) {
                    autoCloseJobStarted = true
                    delay(1200L)
                    onDismiss()
                }
            }
        }
    }

    // Dynamic NPCI UPI payment request URI: exact user-entered amount in 'am' parameter
    val qrUri = remember(personalUpiId, personalUpiName, currentAmountPaise, currentRef) {
        if (!personalUpiId.isNullOrBlank() && currentAmountPaise > 0L) {
            try {
                UpiQrCodeGenerator.buildSettlementQrUri(
                    receiverUpiId = personalUpiId,
                    receiverName = personalUpiName ?: "RupeeFlow User",
                    amountPaise = currentAmountPaise,
                    note = "RupeeFlow settlement from ${friend.name}",
                    transactionRef = currentRef
                )
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val qrBitmap: Bitmap? = remember(qrUri) {
        qrUri?.let { uri ->
            try {
                UpiQrCodeGenerator.generateQrBitmap(uri, 512)
            } catch (_: Exception) {
                null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isConfiguringUpi) "Personal UPI Setup" else "UPI Settlement QR",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Payment request for ${friend.name}",
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

                // If UPI is missing or user requested reconfiguration
                if (isConfiguringUpi) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppTheme.colors.surface,
                            border = BorderStroke(1.dp, AppTheme.colors.divider)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Enter your personal UPI ID (VPA) to generate offline QR payment requests. RupeeFlow will never ask for your PIN, bank password, or OTP.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = upiInput,
                            onValueChange = {
                                upiInput = it
                                val trimmed = it.trim()
                                upiError = if (trimmed.isNotBlank() && !UpiValidator.isValidUpiId(trimmed)) {
                                    "Invalid UPI ID format (e.g. name@bank)"
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
                                } else {
                                    Text("Standard personal UPI address", color = AppTheme.colors.textTertiary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = rupeeFlowTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("My UPI Display Name") },
                            placeholder = { Text("e.g. Rahul Sharma") },
                            singleLine = true,
                            supportingText = {
                                Text("Name shown to friends on their payment app", color = AppTheme.colors.textTertiary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = rupeeFlowTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!personalUpiId.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { isConfiguringUpi = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel")
                                }
                            }
                            Button(
                                onClick = {
                                    val trimmed = upiInput.trim()
                                    if (trimmed.isBlank() || !UpiValidator.isValidUpiId(trimmed)) {
                                        upiError = "Please enter a valid UPI address"
                                        return@Button
                                    }
                                    onSavePersonalUpi(trimmed, nameInput.trim().ifBlank { null })
                                    isConfiguringUpi = false
                                    Toast.makeText(context, "Personal UPI details saved", Toast.LENGTH_SHORT).show()
                                },
                                enabled = upiInput.isNotBlank() && upiError == null,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                            ) {
                                Text("Save & Generate QR")
                            }
                        }
                    }
                } else if (totalOutstandingPaise <= 0L && currentAmountPaise <= 0L) {
                    // Friend owes ₹0.00: QR generation disabled
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = AppTheme.colors.surface,
                        border = BorderStroke(1.dp, AppTheme.colors.divider)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldMint,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "All Settled Up",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "${friend.name} does not currently owe you any money.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = AppTheme.colors.textSecondary
                            )
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                            ) {
                                Text("Close")
                            }
                        }
                    }
                } else {
                    // Active QR Code & Amount Configuration
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Real-time Reconciliation Status Banner
                        if (reconciledSettlement != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = EmeraldMint.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldMint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Payment Detected & Reconciled!",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldMint
                                        )
                                        Text(
                                            text = "Received ${CurrencyUtils.formatPaiseToRupees(reconciledSettlement?.amountPaise ?: currentAmountPaise)} • Closing in a moment...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceSecondary,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = AppTheme.colors.primaryAccent
                                    )
                                    Text(
                                        text = "Awaiting bank payment • Reconciles automatically on SMS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        }

                        // Editable Settlement Amount Input (Bug 3 Fix)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { input ->
                                    amountInput = input
                                    val parsed = CurrencyUtils.parseInputToPaise(input)
                                    if (parsed > 0L && parsed != currentAmountPaise) {
                                        currentRef = UpiQrCodeGenerator.generateQrReference()
                                    }
                                },
                                label = { Text("Settlement Amount") },
                                placeholder = { Text("0.00") },
                                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = currentAmountPaise <= 0L,
                                supportingText = {
                                    if (currentAmountPaise <= 0L) {
                                        Text("Please enter a valid amount greater than zero", color = CoralRed)
                                    } else {
                                        Text("QR code updates immediately with entered amount", color = AppTheme.colors.textTertiary)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = rupeeFlowTextFieldColors(),
                                shape = RoundedCornerShape(14.dp)
                            )

                            // Quick Partial/Full Shortcuts
                            if (totalOutstandingPaise > 0L) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = {
                                            amountInput = CurrencyUtils.formatPaiseToRupees(totalOutstandingPaise, includeSymbol = false)
                                            currentRef = UpiQrCodeGenerator.generateQrReference()
                                        },
                                        label = { Text("Full Balance (${CurrencyUtils.formatPaiseToRupees(totalOutstandingPaise)})") }
                                    )
                                    if (totalOutstandingPaise > 200L) {
                                        AssistChip(
                                            onClick = {
                                                val half = totalOutstandingPaise / 2L
                                                amountInput = CurrencyUtils.formatPaiseToRupees(half, includeSymbol = false)
                                                currentRef = UpiQrCodeGenerator.generateQrReference()
                                            },
                                            label = { Text("50% Partial") }
                                        )
                                    }
                                }
                            }

                            // Remaining Balance Preview (Bug 3 Requirement 6)
                            if (totalOutstandingPaise > 0L && currentAmountPaise > 0L) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        currentAmountPaise < totalOutstandingPaise -> AppTheme.colors.surfaceSecondary
                                        currentAmountPaise == totalOutstandingPaise -> AppTheme.colors.incomeGreen.copy(alpha = 0.12f)
                                        else -> AmberGold.copy(alpha = 0.12f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            currentAmountPaise < totalOutstandingPaise -> AppTheme.colors.cardBorder
                                            currentAmountPaise == totalOutstandingPaise -> AppTheme.colors.incomeGreen.copy(alpha = 0.3f)
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
                                                currentAmountPaise < totalOutstandingPaise -> "Remaining balance after payment:"
                                                currentAmountPaise == totalOutstandingPaise -> "Full Settlement status:"
                                                else -> "Advance / Credit after payment:"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = when {
                                                currentAmountPaise < totalOutstandingPaise -> CurrencyUtils.formatPaiseToRupees(totalOutstandingPaise - currentAmountPaise)
                                                currentAmountPaise == totalOutstandingPaise -> "Fully Settled (₹0.00)"
                                                else -> "+${CurrencyUtils.formatPaiseToRupees(currentAmountPaise - totalOutstandingPaise)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                currentAmountPaise < totalOutstandingPaise -> AppTheme.colors.primaryAccent
                                                currentAmountPaise == totalOutstandingPaise -> AppTheme.colors.incomeGreen
                                                else -> AmberGold
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // QR Code Card
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            shadowElevation = 3.dp,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "Settlement QR Code",
                                        modifier = Modifier.size(220.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = AppTheme.colors.primaryAccent
                                    )
                                }
                            }
                        }

                        // Exact Settlement Amount Display
                        Text(
                            text = CurrencyUtils.formatPaiseToRupees(currentAmountPaise),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldMint
                        )

                        // Request Details
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = AppTheme.colors.surface,
                            border = BorderStroke(1.dp, AppTheme.colors.divider)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Receiver UPI:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = personalUpiId ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppTheme.colors.primaryAccent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                clipboard?.setPrimaryClip(ClipData.newPlainText("Receiver UPI ID", personalUpiId ?: ""))
                                                Toast.makeText(context, "Receiver UPI ID copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy UPI ID",
                                                tint = AppTheme.colors.primaryAccent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                if (!personalUpiName.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Display Name:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                        Text(personalUpiName, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("From Friend:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                    Text(friend.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Reference:", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = currentRef,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textTertiary
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                clipboard?.setPrimaryClip(ClipData.newPlainText("Reference", currentRef))
                                                Toast.makeText(context, "Reference copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Reference",
                                                tint = AppTheme.colors.textTertiary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Share & Save Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    qrBitmap?.let { bmp ->
                                        val amtStr = CurrencyUtils.formatPaiseToRupees(currentAmountPaise)
                                        val shareText = "RupeeFlow Settlement Request: Please pay $amtStr to $personalUpiId using any UPI app (Ref: $currentRef)."
                                        UpiQrCodeGenerator.shareQrCode(context, bmp, shareText, currentRef)
                                    }
                                },
                                enabled = qrBitmap != null && currentAmountPaise > 0L,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share QR")
                            }

                            OutlinedButton(
                                onClick = {
                                    qrBitmap?.let { bmp ->
                                        val uri = UpiQrCodeGenerator.saveQrBitmapToStorage(context, bmp, currentRef)
                                        if (uri != null) {
                                            Toast.makeText(context, "QR saved to Pictures/RupeeFlow", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to save QR to storage", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = qrBitmap != null && currentAmountPaise > 0L,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save QR")
                            }
                        }

                        // Regenerate QR / Edit UPI Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    currentRef = UpiQrCodeGenerator.generateQrReference()
                                    Toast.makeText(context, "Regenerated fresh QR code reference", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate QR", style = MaterialTheme.typography.labelMedium)
                            }

                            TextButton(
                                onClick = { isConfiguringUpi = true }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit My UPI", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        HorizontalDivider(color = AppTheme.colors.divider)

                        // Manual Confirmation Action
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Automatic Reconcile: RupeeFlow automatically records the settlement when your bank credit SMS arrives.",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )

                            Button(
                                onClick = { showConfirmReceiveDialog = true },
                                enabled = !isProcessingMark && currentAmountPaise > 0L,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mark as received (${CurrencyUtils.formatPaiseToRupees(currentAmountPaise)})",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Prompt before recording settlement manually
    if (showConfirmReceiveDialog) {
        val confirmFormatted = CurrencyUtils.formatPaiseToRupees(currentAmountPaise)
        AlertDialog(
            onDismissRequest = { showConfirmReceiveDialog = false },
            title = {
                Text("Confirm Payment Received", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Have you verified that $confirmFormatted has been credited to your bank account?\n\n" +
                    "This will record a settlement of $confirmFormatted from ${friend.name} and update their ledger balance."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmReceiveDialog = false
                        isProcessingMark = true
                        val settlement = Settlement(
                            id = activePendingSettlementId ?: 0L,
                            payerId = friend.id,
                            receiverId = null, // ME
                            amountPaise = currentAmountPaise,
                            settlementDate = System.currentTimeMillis(),
                            paymentMethod = PaymentMethod.UPI,
                            referenceNumber = currentRef,
                            notes = "Personal UPI QR Settlement from ${friend.name}",
                            isReversed = false,
                            upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
                            upiVerificationStatus = SettlementVerificationStatus.MANUAL_UNVERIFIED,
                            upiTransactionRef = currentRef,
                            upiReceiverVpa = personalUpiId
                        )
                        onMarkAsReceived(settlement)
                        Toast.makeText(context, "Settlement recorded: $confirmFormatted received", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint)
                ) {
                    Text("Confirm & Settle")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmReceiveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
