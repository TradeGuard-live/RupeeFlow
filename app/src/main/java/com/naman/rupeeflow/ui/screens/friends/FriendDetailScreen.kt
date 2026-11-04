package com.naman.rupeeflow.ui.screens.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.FriendBalanceStatus
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(
    viewModel: FriendDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val friend = state.friend

    var showSettleDialog by remember { mutableStateOf(false) }
    var reversingSettlementId by remember { mutableStateOf<Long?>(null) }
    var unreversingSettlementId by remember { mutableStateOf<Long?>(null) }
    var selectedSettlementForDetail by remember { mutableStateOf<Settlement?>(null) }
    var editingSettlement by remember { mutableStateOf<Settlement?>(null) }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(friend?.name ?: "Friend Ledger", color = TextPrimary)
                        if (friend?.isArchived == true) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TextMuted.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextPrimary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (friend?.isArchived == true) {
                            DropdownMenuItem(
                                text = { Text("Unarchive Friend") },
                                onClick = {
                                    showMenu = false
                                    viewModel.unarchiveFriend()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Archive Friend") },
                                onClick = {
                                    showMenu = false
                                    viewModel.archiveFriend()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete Friend", color = CoralRed) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightDark)
            )
        },
        containerColor = MidnightDark
    ) { paddingValues ->
        if (friend == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Balance Card
            item {
                val (bannerText, bannerColor, bannerIcon) = when (friend.status) {
                    FriendBalanceStatus.THEY_OWE_ME -> Triple(
                        "${friend.name} owes you",
                        EmeraldMint,
                        Icons.Default.CallReceived
                    )
                    FriendBalanceStatus.I_OWE_THEM -> Triple(
                        "You owe ${friend.name}",
                        CoralRed,
                        Icons.Default.CallMade
                    )
                    FriendBalanceStatus.SETTLED -> Triple(
                        "All Settled Up",
                        TextSecondary,
                        Icons.Default.CheckCircle
                    )
                }

                HeroSummaryCard(
                    title = bannerText,
                    amountFormatted = CurrencyUtils.formatPaiseToRupees(abs(friend.netBalancePaise)),
                    subtitle = if (!friend.phoneNumber.isNullOrBlank()) friend.phoneNumber else "Ledger Balance",
                    icon = bannerIcon,
                    accentColor = bannerColor
                )
            }

            // Settle Up Action Button
            item {
                Button(
                    onClick = { showSettleDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (friend.status == FriendBalanceStatus.THEY_OWE_ME) EmeraldMint else ElectricBlue,
                        contentColor = MidnightDark
                    )
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Settle Up",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Filter Tabs (All / Expenses / Settlements)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "All Activity", "EXPENSES" to "Expenses", "SETTLEMENTS" to "Settlements").forEach { (key, label) ->
                        val isSelected = state.filterType == key
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalCard,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) ElectricBlue else GlassBorder
                            ),
                            modifier = Modifier.clickable { viewModel.setFilterType(key) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) ElectricBlueLight else TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Unified chronological list
            val showExpenses = state.filterType != "SETTLEMENTS"
            val showSettlements = state.filterType != "EXPENSES"

            // Display settlements if applicable
            if (showSettlements && state.settlements.isNotEmpty()) {
                item {
                    Text(
                        text = "Settlement Records",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(state.settlements, key = { "settle_${it.id}" }) { s ->
                    val isReceived = s.receiverId == null // Friend paid ME
                    val dirText = if (isReceived) "${friend.name} paid You" else "You paid ${friend.name}"
                    val dirColor = if (isReceived) EmeraldMint else CoralRed

                    RupeeFlowCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSettlementForDetail = s },
                        cornerRadius = 14.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(dirColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isReceived) Icons.Default.CallReceived else Icons.Default.CallMade,
                                    contentDescription = null,
                                    tint = dirColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dirText,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (s.isReversed) TextMuted else TextPrimary,
                                    textDecoration = if (s.isReversed) TextDecoration.LineThrough else null
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${DateTimeUtils.formatDate(s.settlementDate)} • ${s.paymentMethod.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(s.amountPaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (s.isReversed) TextMuted else dirColor,
                                    textDecoration = if (s.isReversed) TextDecoration.LineThrough else null
                                )
                                if (!s.isReversed) {
                                    Text(
                                        text = "Reverse",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberGold,
                                        modifier = Modifier.clickable { reversingSettlementId = s.id }
                                    )
                                } else {
                                    Text(
                                        text = "Convert to Normal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldMint,
                                        modifier = Modifier.clickable { unreversingSettlementId = s.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Display shared expenses
            if (showExpenses && state.relatedTransactions.isNotEmpty()) {
                item {
                    Text(
                        text = "Shared Expenses",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(state.relatedTransactions, key = { "txn_${it.id}" }) { txn ->
                    val myParticipant = txn.participants.firstOrNull { it.friendId == null }
                    val friendParticipant = txn.participants.firstOrNull { it.friendId == friend.id }

                    RupeeFlowCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTransactionForDetail = txn },
                        cornerRadius = 14.dp
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = txn.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (txn.isIgnored) TextMuted else TextPrimary,
                                    textDecoration = if (txn.isIgnored) TextDecoration.LineThrough else null
                                )
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(txn.amountPaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Paid by ${txn.paidByName} • ${DateTimeUtils.formatDate(txn.transactionDate)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                if (friendParticipant != null) {
                                    Text(
                                        text = "${friend.name}'s share: ${CurrencyUtils.formatPaiseToRupees(friendParticipant.shareAmountPaise)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VibrantCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Settle Up Dialog
    if (showSettleDialog && friend != null) {
        SettleUpDialog(
            friend = friend,
            onDismiss = { showSettleDialog = false },
            onConfirm = { settlement ->
                viewModel.recordSettlement(settlement)
            }
        )
    }

    // Reverse Settlement Dialog
    reversingSettlementId?.let { sId ->
        AlertDialog(
            onDismissRequest = { reversingSettlementId = null },
            title = { Text("Reverse Settlement?", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to reverse this settlement payment? The ledger balance will be recalculated accordingly.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reverseSettlement(sId)
                        reversingSettlementId = null
                    }
                ) {
                    Text("Confirm Reversal", color = AmberGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reversingSettlementId = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Convert to Normal (Restore) Settlement Dialog
    unreversingSettlementId?.let { sId ->
        AlertDialog(
            onDismissRequest = { unreversingSettlementId = null },
            title = { Text("Convert to Normal?", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to restore this settlement back to active normal state? The ledger balance will be updated accordingly.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unreverseSettlement(sId)
                        unreversingSettlementId = null
                    }
                ) {
                    Text("Restore Settlement", color = EmeraldMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { unreversingSettlementId = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Settlement Detail Dialog
    selectedSettlementForDetail?.let { settlement ->
        SettlementDetailDialog(
            settlement = settlement,
            friendName = friend?.name ?: "Friend",
            onDismiss = { selectedSettlementForDetail = null },
            onReverse = { viewModel.reverseSettlement(it.id) },
            onUnreverse = { viewModel.unreverseSettlement(it.id) },
            onDelete = { viewModel.deleteSettlement(it) },
            onEdit = { toEdit -> editingSettlement = toEdit }
        )
    }

    // ✅ Edit Settlement Dialog
    editingSettlement?.let { settlement ->
        EditSettlementDialog(
            settlement = settlement,
            friends = state.allFriends.ifEmpty { listOfNotNull(friend) },
            onDismiss = { editingSettlement = null },
            onSave = { updated -> viewModel.updateSettlement(updated) }
        )
    }

    // Transaction Detail Dialog
    selectedTransactionForDetail?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            onDismiss = { selectedTransactionForDetail = null },
            onToggleIgnore = { viewModel.toggleIgnore(it) },
            onDuplicate = { viewModel.duplicateTransaction(it) },
            onDelete = { viewModel.deleteTransaction(it) },
            onEdit = { toEdit -> editingTransaction = toEdit }
        )
    }

    // ✅ Edit Transaction Dialog
    editingTransaction?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            categories = state.categories,
            friends = state.allFriends.ifEmpty { listOfNotNull(friend) },
            onDismiss = { editingTransaction = null },
            onSave = { updated -> viewModel.saveTransaction(updated) }
        )
    }

    // ✅ Safe Delete / Archive Confirmation Dialog
    if (showDeleteConfirmDialog && friend != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Friend?", color = TextPrimary) },
            text = {
                Text(
                    "If this friend has associated transactions or settlements, they will be safely archived instead of deleted to protect historical financial records and ledger integrity.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteFriend(onComplete = onNavigateBack)
                    }
                ) {
                    Text("Confirm Delete", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
