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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import com.naman.rupeeflow.upi.UpiQrCodeGenerator
import com.naman.rupeeflow.upi.UpiValidator

@Composable
fun PersonalUpiQrDialog(
    friend: Friend,
    personalUpiId: String?,
    personalUpiName: String?,
    outstandingAmountPaise: Long,
    onSavePersonalUpi: (upiId: String, name: String?) -> Unit,
    onMarkAsReceived: (Settlement) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isConfiguringUpi by remember { mutableStateOf(personalUpiId.isNullOrBlank()) }

    var upiInput by remember(personalUpiId) { mutableStateOf(personalUpiId ?: "") }
    var nameInput by remember(personalUpiName) { mutableStateOf(personalUpiName ?: "") }
    var upiError by remember { mutableStateOf<String?>(null) }

    var currentRef by remember { mutableStateOf(UpiQrCodeGenerator.generateQrReference()) }
    var showConfirmReceiveDialog by remember { mutableStateOf(false) }
    var isProcessingMark by remember { mutableStateOf(false) }

    val formattedAmount = CurrencyUtils.formatPaiseToRupees(outstandingAmountPaise)

    val qrUri = remember(personalUpiId, personalUpiName, outstandingAmountPaise, currentRef) {
        if (!personalUpiId.isNullOrBlank() && outstandingAmountPaise > 0L) {
            try {
                UpiQrCodeGenerator.buildSettlementQrUri(
                    receiverUpiId = personalUpiId,
                    receiverName = personalUpiName ?: "RupeeFlow User",
                    amountPaise = outstandingAmountPaise,
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
                } else if (outstandingAmountPaise <= 0L) {
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
                                text = "${friend.name} does not currently owe you any money. QR generation is disabled for ₹0.00 balances.",
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
                    // Active QR Code Display
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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

                        // Exact Settlement Amount
                        Text(
                            text = formattedAmount,
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

                        // Payer Authorization & Fixed Amount Guidance
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppTheme.colors.surface,
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Payer must scan and authorize payment inside their UPI app (Google Pay, PhonePe, BHIM, Paytm).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = "Fixed Amount: RupeeFlow sets the exact requested amount. Whether an app permits modifying this amount is governed by the payer's UPI app.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textTertiary
                                )
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
                                        val shareText = "RupeeFlow Settlement Request: Please pay $formattedAmount to $personalUpiId using any UPI app (Ref: $currentRef)."
                                        UpiQrCodeGenerator.shareQrCode(context, bmp, shareText, currentRef)
                                    }
                                },
                                enabled = qrBitmap != null,
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
                                enabled = qrBitmap != null,
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

                        // Settlement Safety Notice & Manual Confirmation Action
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Settlement Safety: Generating or sharing this QR does NOT mark the expense as settled. Keep records pending until confirmed in your bank account.",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )

                            Button(
                                onClick = { showConfirmReceiveDialog = true },
                                enabled = !isProcessingMark,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mark as received (not bank verified)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Prompt before recording settlement to prevent accidental double-settlement
    if (showConfirmReceiveDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmReceiveDialog = false },
            title = {
                Text("Confirm Payment Received", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Have you verified that $formattedAmount has been credited to your bank account?\n\n" +
                    "This will record a settlement from ${friend.name} and reduce their outstanding debt. You can reverse this entry anytime from the settlements history."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmReceiveDialog = false
                        isProcessingMark = true
                        val settlement = Settlement(
                            id = 0L,
                            payerId = friend.id,
                            receiverId = null, // ME
                            amountPaise = outstandingAmountPaise,
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
                        Toast.makeText(context, "Settlement recorded: $formattedAmount received", Toast.LENGTH_SHORT).show()
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
