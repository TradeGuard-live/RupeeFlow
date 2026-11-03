package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendBalanceStatus
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.theme.*
import kotlin.math.abs

@Composable
fun SettleUpDialog(
    friend: Friend,
    onDismiss: () -> Unit,
    onConfirm: (Settlement) -> Unit
) {
    // Default direction based on who owes whom
    val friendOwesMe = friend.status == FriendBalanceStatus.THEY_OWE_ME
    var isFriendPayingMe by remember { mutableStateOf(friendOwesMe) }

    val defaultAmount = if (friendOwesMe) friend.totalTheyOweMePaise else friend.totalIOweThemPaise
    var amountText by remember {
        mutableStateOf(if (defaultAmount > 0L) (defaultAmount / 100.0).toString() else "")
    }

    var selectedMethod by remember { mutableStateOf(PaymentMethod.UPI) }
    var notes by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }

    val amountPaise = remember(amountText) { CurrencyUtils.parseInputToPaise(amountText) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CharcoalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settle with ${friend.name}",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Direction Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalCard)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val opt1 = "${friend.name} paid Me"
                    val opt2 = "I paid ${friend.name}"

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFriendPayingMe) EmeraldMint.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { isFriendPayingMe = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = opt1,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFriendPayingMe) EmeraldMint else TextSecondary,
                            fontWeight = if (isFriendPayingMe) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isFriendPayingMe) CoralRed.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { isFriendPayingMe = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = opt2,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isFriendPayingMe) CoralRed else TextSecondary,
                            fontWeight = if (!isFriendPayingMe) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                AmountInputField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Settlement Amount"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Payment Method
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                PaymentMethodSelectorRow(
                    selectedMethod = selectedMethod,
                    onSelect = { selectedMethod = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Optional Reference
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("Reference / UPI UTR (Optional)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (amountPaise <= 0L) return@Button
                        val settlement = Settlement(
                            payerId = if (isFriendPayingMe) friend.id else null,
                            receiverId = if (isFriendPayingMe) null else friend.id,
                            amountPaise = amountPaise,
                            settlementDate = System.currentTimeMillis(),
                            paymentMethod = selectedMethod,
                            referenceNumber = referenceNumber.takeIf { it.isNotBlank() },
                            notes = notes.takeIf { it.isNotBlank() }
                        )
                        onConfirm(settlement)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFriendPayingMe) EmeraldMint else ElectricBlue,
                        contentColor = MidnightDark
                    ),
                    enabled = amountPaise > 0L
                ) {
                    Text(
                        text = "Record Settlement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
