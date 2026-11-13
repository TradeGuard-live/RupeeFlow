package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val isLight = !AppTheme.isDark
    val category = transaction.category
    val catColor = CategoryIconHelper.parseColor(category?.colorHex)
    val catIcon = CategoryIconHelper.getIcon(category?.iconName)

    val isIncomeOrRefund = transaction.transactionType == TransactionType.INCOME ||
            transaction.transactionType == TransactionType.REFUND
    val amountPrefix = if (isIncomeOrRefund) "+" else "-"
    val amountColor = when {
        transaction.isIgnored -> AppTheme.colors.textTertiary
        isIncomeOrRefund -> if (isLight) Color(0xFF059669) else EmeraldMint
        else -> if (isLight) Color(0xFFDC2626) else CoralRed
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
                    .background(catColor.copy(alpha = if (isLight) 0.15f else 0.2f)),
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
                        color = if (transaction.isIgnored) AppTheme.colors.textTertiary else AppTheme.colors.textPrimary,
                        textDecoration = if (transaction.isIgnored) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (transaction.isIgnored) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isLight) Color(0xFFFEF3C7) else AmberGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "IGNORED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLight) Color(0xFFD97706) else AmberGold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
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
                        color = AppTheme.colors.textSecondary
                    )

                    Text("•", color = AppTheme.colors.textTertiary, fontSize = 10.sp)

                    Text(
                        text = transaction.paymentMethod.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )

                    if (transaction.isSplit) {
                        Text("•", color = AppTheme.colors.textTertiary, fontSize = 10.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Split",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.primaryAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!transaction.billImagePath.isNullOrBlank()) {
                        Text("•", color = AppTheme.colors.textTertiary, fontSize = 10.sp)
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "Receipt attached",
                            tint = AppTheme.colors.primaryAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    if (transaction.hasLocation) {
                        Text("•", color = AppTheme.colors.textTertiary, fontSize = 10.sp)
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location attached",
                            tint = AppTheme.colors.primaryAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    if (transaction.hasExpenseGroup) {
                        Text("•", color = AppTheme.colors.textTertiary, fontSize = 10.sp)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isLight) Color(0xFFE0E7FF) else Color(0xFF312E81).copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = transaction.expenseGroupName ?: "Group",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLight) Color(0xFF4338CA) else Color(0xFFA5B4FC),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Amount Column with Reversed Hierarchy for Split Transactions
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (transaction.isSplit && transaction.transactionType == TransactionType.EXPENSE) {
                    // Upper: User's split share prominent, larger, and bold with semantic coloring
                    Text(
                        text = "$amountPrefix${CurrencyUtils.formatPaiseToRupees(transaction.userExpensePaise)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        textDecoration = if (transaction.isIgnored) TextDecoration.LineThrough else null
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Lower: Total transaction amount smaller, secondary text
                    Text(
                        text = "Total amount: ${CurrencyUtils.formatPaiseToRupees(transaction.amountPaise)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        textDecoration = if (transaction.isIgnored) TextDecoration.LineThrough else null
                    )

                    if (transaction.paidByFriendId != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Paid by ${transaction.paidByName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textTertiary,
                            fontSize = 10.sp
                        )
                    }
                } else {
                    // For non-split transactions, income, refunds, transfers:
                    // Total amount displayed prominently with no misleading split share
                    Text(
                        text = "$amountPrefix${CurrencyUtils.formatPaiseToRupees(transaction.amountPaise)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        textDecoration = if (transaction.isIgnored) TextDecoration.LineThrough else null
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
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onToggleArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val isLight = !AppTheme.isDark
    // Strict Color Rules:
    // Green = Money owed to me (They owe you)
    // Red = Money I owe (You owe)
    // Neutral = Zero balance (All settled up)
    val (statusText, statusColor, statusBg) = when (friend.status) {
        FriendBalanceStatus.THEY_OWE_ME -> Triple(
            "They owe you ${CurrencyUtils.formatPaiseToRupees(friend.totalTheyOweMePaise)}",
            if (isLight) Color(0xFF047857) else EmeraldMint,
            if (isLight) Color(0xFFD1FAE5) else EmeraldMint.copy(alpha = 0.15f)
        )
        FriendBalanceStatus.I_OWE_THEM -> Triple(
            "You owe ${CurrencyUtils.formatPaiseToRupees(friend.totalIOweThemPaise)}",
            if (isLight) Color(0xFFB91C1C) else CoralRed,
            if (isLight) Color(0xFFFEE2E2) else CoralRed.copy(alpha = 0.15f)
        )
        FriendBalanceStatus.SETTLED -> Triple(
            "All settled up",
            if (isLight) Color(0xFF64748B) else TextSecondary,
            if (isLight) Color(0xFFF1F5F9) else CharcoalCard
        )
    }

    var showMenu by remember { mutableStateOf(false) }

    val menuContent: @Composable () -> Unit = {
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("View Details") },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AppTheme.colors.primaryAccent)
                },
                onClick = {
                    showMenu = false
                    onClick()
                }
            )
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text("Edit Friend") },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = AppTheme.colors.primaryAccent)
                    },
                    onClick = {
                        showMenu = false
                        onEdit()
                    }
                )
            }
            if (onToggleArchive != null) {
                val isArchived = friend.isArchived
                DropdownMenuItem(
                    text = { Text(if (isArchived) "Unarchive Friend" else "Archive Friend") },
                    leadingIcon = {
                        Icon(
                            if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = null,
                            tint = AppTheme.colors.textSecondary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleArchive()
                    }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete Friend", color = if (isLight) Color(0xFFDC2626) else CoralRed) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = if (isLight) Color(0xFFDC2626) else CoralRed
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }

    val statusPill: @Composable () -> Unit = {
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
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    val overflowMenuButton: @Composable () -> Unit = {
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options for ${friend.name}",
                    tint = AppTheme.colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            menuContent()
        }
    }

    RupeeFlowCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrow = maxWidth < 340.dp
            if (isNarrow) {
                // Two-Row Responsive Layout: Prevents vertical letter/digit wrapping
                // Row 1: Avatar + Name & Phone Column
                // Row 2: Status Pill + Spacer + Overflow Menu
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FriendAvatar(
                            friend = friend,
                            modifier = Modifier.size(46.dp),
                            fallbackColor = AppTheme.colors.primaryAccent
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friend.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = AppTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!friend.phoneNumber.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = friend.phoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        statusPill()
                        overflowMenuButton()
                    }
                }
            } else {
                // Single-Row Layout (For wide screens / full list)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FriendAvatar(
                        friend = friend,
                        modifier = Modifier.size(46.dp),
                        fallbackColor = AppTheme.colors.primaryAccent
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 90.dp)
                    ) {
                        Text(
                            text = friend.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!friend.phoneNumber.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = friend.phoneNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    statusPill()

                    Spacer(modifier = Modifier.width(4.dp))

                    overflowMenuButton()
                }
            }
        }
    }
}
