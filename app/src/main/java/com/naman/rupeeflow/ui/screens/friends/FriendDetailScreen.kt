package com.naman.rupeeflow.ui.screens.friends

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.domain.model.FriendBalanceStatus
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch
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
                        Text(friend?.name ?: "Friend Ledger", color = AppTheme.colors.textPrimary)
                        if (friend?.isArchived == true) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppTheme.colors.textMuted.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = AppTheme.colors.textPrimary)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (friend == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppTheme.colors.primaryAccent)
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
            // Profile & Avatar Card
            item {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    if (uri != null) {
                        scope.launch {
                            val path = ImageStorageManager.saveProfileImage(context, uri)
                            if (path != null) {
                                viewModel.updateFriendProfileImage(path)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AppTheme.colors.cardBackground,
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            FriendAvatar(
                                friend = friend,
                                modifier = Modifier.size(60.dp),
                                fallbackColor = AppTheme.colors.primaryAccent
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.primaryAccent)
                                    .clickable { galleryLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change profile picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friend.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = AppTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (!friend.phoneNumber.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = friend.phoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            if (!friend.email.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = friend.email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textMuted
                                )
                            }
                        }
                    }
                }
            }

            // Hero Balance Card
            item {
                val (bannerText, bannerColor, bannerIcon) = when (friend.status) {
                    FriendBalanceStatus.THEY_OWE_ME -> Triple(
                        "${friend.name} owes you",
                        AppTheme.colors.incomeGreen,
                        Icons.Default.CallReceived
                    )
                    FriendBalanceStatus.I_OWE_THEM -> Triple(
                        "You owe ${friend.name}",
                        CoralRed,
                        Icons.Default.CallMade
                    )
                    FriendBalanceStatus.SETTLED -> Triple(
                        "All Settled Up",
                        AppTheme.colors.textSecondary,
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
                        containerColor = when (friend.status) {
                            FriendBalanceStatus.THEY_OWE_ME -> AppTheme.colors.incomeGreen
                            FriendBalanceStatus.I_OWE_THEM -> CoralRed
                            FriendBalanceStatus.SETTLED -> AppTheme.colors.primaryAccent
                        },
                        contentColor = Color.White
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
                            color = if (isSelected) AppTheme.colors.accentContainer else AppTheme.colors.cardBackground,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                            ),
                            modifier = Modifier.clickable { viewModel.setFilterType(key) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
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
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(state.settlements, key = { "settle_${it.id}" }) { s ->
                    val isReceived = s.receiverId == null // Friend paid ME
                    val dirText = if (isReceived) "${friend.name} paid You" else "You paid ${friend.name}"
                    val dirColor = if (isReceived) AppTheme.colors.incomeGreen else CoralRed

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
                                    color = if (s.isReversed) AppTheme.colors.textMuted else AppTheme.colors.textPrimary,
                                    textDecoration = if (s.isReversed) TextDecoration.LineThrough else null
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${DateTimeUtils.formatDate(s.settlementDate)} • ${s.paymentMethod.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(s.amountPaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (s.isReversed) AppTheme.colors.textMuted else dirColor,
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
                                        color = AppTheme.colors.incomeGreen,
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
                        color = AppTheme.colors.textPrimary,
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
                                    color = if (txn.isIgnored) AppTheme.colors.textMuted else AppTheme.colors.textPrimary,
                                    textDecoration = if (txn.isIgnored) TextDecoration.LineThrough else null
                                )
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(txn.amountPaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AppTheme.colors.textPrimary
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
                                    color = AppTheme.colors.textSecondary
                                )
                                if (friendParticipant != null) {
                                    Text(
                                        text = "${friend.name}'s share: ${CurrencyUtils.formatPaiseToRupees(friendParticipant.shareAmountPaise)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.primaryAccent
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
            title = { Text("Reverse Settlement?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Are you sure you want to reverse this settlement payment? The ledger balance will be recalculated accordingly.",
                    color = AppTheme.colors.textSecondary
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
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Convert to Normal (Restore) Settlement Dialog
    unreversingSettlementId?.let { sId ->
        AlertDialog(
            onDismissRequest = { unreversingSettlementId = null },
            title = { Text("Convert to Normal?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Are you sure you want to restore this settlement back to active normal state? The ledger balance will be updated accordingly.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unreverseSettlement(sId)
                        unreversingSettlementId = null
                    }
                ) {
                    Text("Restore Settlement", color = AppTheme.colors.incomeGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { unreversingSettlementId = null }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
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

    // Edit Settlement Dialog
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

    // Edit Transaction Dialog
    editingTransaction?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            categories = state.categories,
            friends = state.allFriends.ifEmpty { listOfNotNull(friend) },
            groups = state.friendGroups,
            onDismiss = { editingTransaction = null },
            onSave = { updated -> viewModel.saveTransaction(updated) }
        )
    }

    // Safe Delete / Archive Confirmation Dialog
    if (showDeleteConfirmDialog && friend != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Friend?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "If this friend has associated transactions or settlements, they will be safely archived instead of deleted to protect historical financial records and ledger integrity.",
                    color = AppTheme.colors.textSecondary
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
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
