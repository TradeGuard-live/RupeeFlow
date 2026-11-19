package com.naman.rupeeflow.ui.screens.splitpro

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun GroupSettleUpDialog(
    group: ExpenseGroup,
    friends: List<Friend>,
    groupSummary: GroupBalanceSummary?,
    initialPayerId: Long? = null,
    initialReceiverId: Long? = null,
    initialAmountPaise: Long? = null,
    onDismiss: () -> Unit,
    onConfirmSettlement: (Settlement) -> Unit,
    onPayWithUpi: ((friendId: Long, amountPaise: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allMemberIds = remember(group.memberFriendIds, groupSummary) {
        val summaryIds = groupSummary?.memberBalances?.map { it.memberId } ?: emptyList()
        (listOf<Long?>(null) + group.memberFriendIds + summaryIds).distinct()
    }
    val friendsMap = remember(friends) { friends.associateBy { it.id } }

    fun getMemberName(mId: Long?): String {
        return if (mId == null) "You" else friendsMap[mId]?.name ?: "Unavailable Member"
    }

    val defaultReceiver = allMemberIds.firstOrNull { it != initialPayerId }
    var payerId by remember(initialPayerId) { mutableStateOf(initialPayerId) }
    var receiverId by remember(initialReceiverId, initialPayerId) {
        mutableStateOf(if (initialReceiverId != null || initialPayerId != null) initialReceiverId else defaultReceiver)
    }

    // Determine outstanding debt between payer and receiver
    val directDebtAmount = remember(payerId, receiverId, groupSummary) {
        if (groupSummary != null) {
            val d = groupSummary.directDebts.firstOrNull { it.fromMemberId == payerId && it.toMemberId == receiverId }
            d?.amountPaise ?: 0L
        } else 0L
    }

    var amountInput by remember {
        mutableStateOf(
            if (initialAmountPaise != null && initialAmountPaise > 0L) {
                CurrencyUtils.formatPaiseToRupees(initialAmountPaise, includeSymbol = false)
            } else if (directDebtAmount > 0L) {
                CurrencyUtils.formatPaiseToRupees(directDebtAmount, includeSymbol = false)
            } else ""
        )
    }

    val amountPaise = remember(amountInput) {
        CurrencyUtils.parseInputToPaise(amountInput)
    }

    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.UPI) }
    var referenceNotes by remember { mutableStateOf("") }
    var proofImagePath by remember { mutableStateOf<String?>(null) }

    val proofPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedFilename = ImageStorageManager.saveBillAttachment(context, uri)
                if (savedFilename != null) {
                    proofImagePath = savedFilename
                }
            }
        }
    }

    val isValid = amountPaise > 0L && payerId != receiverId

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Record Settlement",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                // Payer (Who paid)
                Column {
                    Text(
                        text = "Payer (Who paid)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allMemberIds) { mId ->
                            val isSelected = payerId == mId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    payerId = mId
                                    if (receiverId == mId) {
                                        receiverId = allMemberIds.firstOrNull { it != mId }
                                    }
                                },
                                label = { Text(getMemberName(mId)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CoralRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Receiver (Who received)
                Column {
                    Text(
                        text = "Receiver (Who received)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allMemberIds.filter { it != payerId }) { mId ->
                            val isSelected = receiverId == mId
                            FilterChip(
                                selected = isSelected,
                                onClick = { receiverId = mId },
                                label = { Text(getMemberName(mId)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldMint,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Outstanding balance badge
                if (directDebtAmount > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EmeraldMint.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${getMemberName(payerId)} owes ${getMemberName(receiverId)}: ${CurrencyUtils.formatPaiseToRupees(directDebtAmount)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldMint
                        )
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Settlement Amount *") },
                    placeholder = { Text("0.00") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = rupeeFlowTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Payment Method
                Column {
                    Text(
                        text = "Payment Method",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER, PaymentMethod.NET_BANKING, PaymentMethod.OTHER)) { pm ->
                            val isSelected = selectedPaymentMethod == pm
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPaymentMethod = pm },
                                label = { Text(pm.displayName) }
                            )
                        }
                    }
                }

                // Notes / Reference
                OutlinedTextField(
                    value = referenceNotes,
                    onValueChange = { referenceNotes = it },
                    label = { Text("Notes / UPI Ref No. (Optional)") },
                    singleLine = true,
                    colors = rupeeFlowTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Proof Attachment
                OutlinedButton(
                    onClick = { proofPickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (proofImagePath != null) "Proof: ${proofImagePath?.substringAfterLast('/')}" else "Attach Proof of Payment",
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Direct UPI Pay button when You are paying a Friend
                if (payerId == null && receiverId != null && onPayWithUpi != null) {
                    Button(
                        onClick = {
                            if (isValid) {
                                onPayWithUpi(receiverId!!, amountPaise)
                                onDismiss()
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay with UPI App", fontWeight = FontWeight.Bold)
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", color = AppTheme.colors.textSecondary)
                    }

                    Button(
                        onClick = {
                            if (isValid) {
                                val settlement = Settlement(
                                    payerId = payerId,
                                    receiverId = receiverId,
                                    amountPaise = amountPaise,
                                    settlementDate = System.currentTimeMillis(),
                                    paymentMethod = selectedPaymentMethod,
                                    referenceNumber = referenceNotes.trim().takeIf { it.isNotBlank() },
                                    notes = "Split Pro payment: ${getMemberName(payerId)} to ${getMemberName(receiverId)}",
                                    groupId = group.id,
                                    proofImagePath = proofImagePath
                                )
                                onConfirmSettlement(settlement)
                                onDismiss()
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldMint,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Record Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

