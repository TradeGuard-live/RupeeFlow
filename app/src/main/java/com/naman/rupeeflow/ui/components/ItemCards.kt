package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.theme.*

@Composable
fun TransactionItemCard(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val category = transaction.category
    val catColor = CategoryIconHelper.parseColor(category?.colorHex)
    val catIcon = CategoryIconHelper.getIcon(category?.iconName)

    val isIncomeOrRefund = transaction.transactionType == TransactionType.INCOME ||
            transaction.transactionType == TransactionType.REFUND
    val amountPrefix = if (isIncomeOrRefund) "+" else "-"
    val amountColor = when {
        transaction.isIgnored -> TextMuted
        isIncomeOrRefund -> EmeraldMint
        else -> TextPrimary
    }

    RupeeFlowCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = catIcon,
                    contentDescription = null,
                    tint = catColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (transaction.isIgnored) TextMuted else TextPrimary,
                        textDecoration = if (transaction.isIgnored) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (transaction.isIgnored) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AmberGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "IGNORED",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = DateTimeUtils.formatRelativeDate(transaction.transactionDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    Text("•", color = TextMuted, fontSize = 10.sp)

                    Text(
                        text = transaction.paymentMethod.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    if (transaction.isSplit) {
                        Text("•", color = TextMuted, fontSize = 10.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = VibrantCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Split",
                                style = MaterialTheme.typography.labelSmall,
                                color = VibrantCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix${CurrencyUtils.formatPaiseToRupees(transaction.amountPaise)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor,
                    textDecoration = if (transaction.isIgnored) TextDecoration.LineThrough else null
                )

                if (transaction.isSplit && transaction.transactionType == com.naman.rupeeflow.domain.model.TransactionType.EXPENSE) {
                    Text(
                        text = "Your share: ${CurrencyUtils.formatPaiseToRupees(transaction.userExpensePaise)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VibrantCyan,
                        fontSize = 11.sp
                    )
                }

                if (transaction.isSplit && transaction.paidByFriendId != null) {
                    Text(
                        text = "Paid by ${transaction.paidByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FriendItemCard(
    friend: Friend,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (statusText, statusColor, statusBg) = when (friend.status) {
        FriendBalanceStatus.THEY_OWE_ME -> Triple(
            "Owes you ${CurrencyUtils.formatPaiseToRupees(friend.totalTheyOweMePaise)}",
            EmeraldMint,
            EmeraldMint.copy(alpha = 0.15f)
        )
        FriendBalanceStatus.I_OWE_THEM -> Triple(
            "You owe ${CurrencyUtils.formatPaiseToRupees(friend.totalIOweThemPaise)}",
            CoralRed,
            CoralRed.copy(alpha = 0.15f)
        )
        FriendBalanceStatus.SETTLED -> Triple(
            "Settled up",
            TextSecondary,
            CharcoalCard
        )
    }

    RupeeFlowCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Friend Avatar
            val initials = friend.name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials.ifBlank { "F" },
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricBlueLight
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Friend Name & Phone
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                if (!friend.phoneNumber.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = friend.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (friend.status) {
                        FriendBalanceStatus.THEY_OWE_ME -> Icons.Default.CallReceived
                        FriendBalanceStatus.I_OWE_THEM -> Icons.Default.CallMade
                        FriendBalanceStatus.SETTLED -> Icons.Default.Check
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
        }
    }
}
