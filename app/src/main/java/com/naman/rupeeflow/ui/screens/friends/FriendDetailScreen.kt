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
import com.naman.rupeeflow.domain.model.ExpenseObligationDirection
import com.naman.rupeeflow.domain.model.FriendBalanceStatus
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.UnsettledExpenseItem
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
    var showUpiSettlementDialog by remember { mutableStateOf(false) }
    var showPersonalUpiQrDialog by remember { mutableStateOf(false) }
    var upiTargetAmountPaise by remember { mutableStateOf(0L) }
    var reversingSettlementId by remember { mutableStateOf<Long?>(null) }
    var unreversingSettlementId by remember { mutableStateOf<Long?>(null) }
    var selectedSettlementForDetail by remember { mutableStateOf<Settlement?>(null) }
    var editingSettlement by remember { mutableStateOf<Settlement?>(null) }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
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
                        DropdownMenuItem(
                            text = { Text("Edit Friend") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = AppTheme.colors.primaryAccent)
                            },
                            onClick = {
                                showMenu = false
                                showEditDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Request Payment (QR)") },
                            leadingIcon = {
                                Icon(Icons.Default.QrCode, contentDescription = null, tint = AppTheme.colors.primaryAccent)
                            },
                            enabled = friend?.status == FriendBalanceStatus.THEY_OWE_ME,
                            onClick = {
                                showMenu = false
                                upiTargetAmountPaise = abs(friend?.netBalancePaise ?: 0L)
                                showPersonalUpiQrDialog = true
                            }
                        )
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

        val allTimelineItems = remember(state.expenseHistory, state.settlements) {
            val list = mutableListOf<FriendTimelineItem>()
            state.expenseHistory.forEach { list.add(FriendTimelineItem.Expense(it)) }
            state.settlements.forEach { list.add(FriendTimelineItem.SettlementItem(it)) }
            list.sortedByDescending { it.timestamp }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
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
                            if (!friend.upiId.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "UPI: ${friend.upiId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.primaryAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (friend.upiVerifiedName != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "(${friend.upiVerifiedName})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldMint
                                        )
                                    }
                                }
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

            // Settle Up Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSettleDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Settle Up",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    if (friend.status == FriendBalanceStatus.I_OWE_THEM) {
                        Button(
                            onClick = {
                                upiTargetAmountPaise = abs(friend.netBalancePaise)
                                showUpiSettlementDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldMint,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (friend.upiId.isNullOrBlank() && !friend.phoneNumber.isNullOrBlank()) "Pay via Phone" else "Pay with UPI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    } else if (friend.status == FriendBalanceStatus.THEY_OWE_ME) {
                        Button(
                            onClick = {
                                upiTargetAmountPaise = abs(friend.netBalancePaise)
                                showPersonalUpiQrDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldMint,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Request Payment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                showPersonalUpiQrDialog = true
                            },
                            enabled = false,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Request Payment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ✅ Balance and Settlement Totals Summary Row
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppTheme.colors.cardBackground,
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Settlements Received",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                            Text(
                                text = CurrencyUtils.formatPaiseToRupees(state.totalPaymentsReceivedPaise),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.incomeGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Settlements Paid",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                            Text(
                                text = CurrencyUtils.formatPaiseToRupees(state.totalPaymentsMadePaise),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = CoralRed
                            )
                        }
                    }
                }
            }

            // ✅ Filter Tabs (Outstanding / Settlement History / All Activity)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val outstandingCount = state.unsettledExpenses.size
                    val settlementsCount = state.settlements.size
                    val allCount = state.expenseHistory.size + state.settlements.size
                    listOf(
                        "OUTSTANDING" to "Outstanding ($outstandingCount)",
                        "SETTLEMENTS" to "Settlements ($settlementsCount)",
                        "ALL" to "All Activity ($allCount)"
                    ).forEach { (key, label) ->
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

            when (state.filterType) {
                "OUTSTANDING" -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Outstanding Expenses",
                                style = MaterialTheme.typography.titleMedium,
                                color = AppTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.unsettledExpenses.isNotEmpty()) {
                                Text(
                                    text = "${state.unsettledExpenses.size} pending",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    if (state.unsettledExpenses.isEmpty() && abs(friend.netBalancePaise) == 0L) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AppTheme.colors.cardBackground,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AppTheme.colors.incomeGreen,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "All Settled Up",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No outstanding expenses with ${friend.name}.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else if (state.unsettledExpenses.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AppTheme.colors.cardBackground,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Outstanding Balance: ${CurrencyUtils.formatPaiseToRupees(abs(friend.netBalancePaise))}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (friend.status == FriendBalanceStatus.THEY_OWE_ME) AppTheme.colors.incomeGreen else CoralRed
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Historical transactions and settlements are balanced in All Activity.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.colors.textSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.unsettledExpenses, key = { "unsettled_${it.transaction.id}" }) { item ->
                            FriendExpenseCard(
                                item = item,
                                friend = friend,
                                onClick = { selectedTransactionForDetail = item.transaction }
                            )
                        }
                    }
                }
                "SETTLEMENTS" -> {
                    item {
                        Text(
                            text = "Settlement History",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (state.settlements.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AppTheme.colors.cardBackground,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No settlements recorded yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.settlements, key = { "settle_${it.id}" }) { s ->
                            FriendSettlementCard(
                                settlement = s,
                                friend = friend,
                                onReverse = { reversingSettlementId = it },
                                onUnreverse = { unreversingSettlementId = it },
                                onClick = { selectedSettlementForDetail = s }
                            )
                        }
                    }
                }
                else -> { // "ALL"
                    item {
                        Text(
                            text = "All Activity",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (allTimelineItems.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AppTheme.colors.cardBackground,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No activity recorded with ${friend.name} yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(allTimelineItems, key = { item ->
                            when (item) {
                                is FriendTimelineItem.Expense -> "all_exp_${item.item.transaction.id}"
                                is FriendTimelineItem.SettlementItem -> "all_set_${item.settlement.id}"
                            }
                        }) { item ->
                            when (item) {
                                is FriendTimelineItem.Expense -> {
                                    FriendExpenseCard(
                                        item = item.item,
                                        friend = friend,
                                        onClick = { selectedTransactionForDetail = item.item.transaction }
                                    )
                                }
                                is FriendTimelineItem.SettlementItem -> {
                                    FriendSettlementCard(
                                        settlement = item.settlement,
                                        friend = friend,
                                        onReverse = { reversingSettlementId = it },
                                        onUnreverse = { unreversingSettlementId = it },
                                        onClick = { selectedSettlementForDetail = item.settlement }
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
            },
            onPayWithUpi = { targetFriend, amountPaise ->
                upiTargetAmountPaise = amountPaise
                showSettleDialog = false
                showUpiSettlementDialog = true
            },
            onRequestPaymentQr = { targetFriend, amountPaise ->
                upiTargetAmountPaise = amountPaise
                showSettleDialog = false
                showPersonalUpiQrDialog = true
            }
        )
    }

    // Direct UPI Settlement Dialog
    if (showUpiSettlementDialog && friend != null) {
        com.naman.rupeeflow.ui.screens.splitpro.UpiSettlementDialog(
            initialFriend = friend,
            outstandingDebtPaise = if (upiTargetAmountPaise > 0L) upiTargetAmountPaise else abs(friend.netBalancePaise),
            upiSettlementManager = viewModel.upiSettlementManager,
            onFriendUpdated = { updated -> viewModel.updateFriend(updated) },
            onDismiss = {
                showUpiSettlementDialog = false
                upiTargetAmountPaise = 0L
            },
            onSettlementCompleted = {
                showUpiSettlementDialog = false
                upiTargetAmountPaise = 0L
            }
        )
    }

    // Personal UPI Settlement QR Dialog
    if (showPersonalUpiQrDialog && friend != null) {
        PersonalUpiQrDialog(
            friend = friend,
            personalUpiId = state.userPreferences.personalUpiId,
            personalUpiName = state.userPreferences.personalUpiName,
            initialAmountPaise = if (upiTargetAmountPaise > 0L) upiTargetAmountPaise else abs(friend.netBalancePaise),
            totalOutstandingPaise = abs(friend.netBalancePaise),
            upiSettlementManager = viewModel.upiSettlementManager,
            settlementRepository = viewModel.settlementRepo,
            onSavePersonalUpi = { upiId, name ->
                viewModel.updatePersonalUpi(upiId, name)
            },
            onMarkAsReceived = { settlement ->
                if (settlement.id > 0L) {
                    viewModel.updateSettlement(settlement)
                } else {
                    viewModel.recordSettlement(settlement)
                }
                showPersonalUpiQrDialog = false
                upiTargetAmountPaise = 0L
            },
            onDismiss = {
                showPersonalUpiQrDialog = false
                upiTargetAmountPaise = 0L
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
            onEdit = { toEdit -> editingSettlement = toEdit },
            onConfirmReceived = { toConfirm ->
                if (toConfirm.id > 0L) {
                    viewModel.updateSettlement(toConfirm)
                } else {
                    viewModel.recordSettlement(toConfirm)
                }
                selectedSettlementForDetail = null
            }
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
            onEdit = { toEdit -> editingTransaction = toEdit },
            onSaveTransaction = { viewModel.saveTransaction(it) }
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
            onSave = { updated -> viewModel.saveTransaction(updated) },
            onSearchMerchantCategory = { merchant -> viewModel.getSuggestedCategoryForMerchant(merchant) }
        )
    }

    // Edit Friend Dialog
    if (showEditDialog && friend != null) {
        EditFriendDialog(
            friend = friend,
            allFriends = state.allFriends,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                viewModel.updateFriend(updated)
                showEditDialog = false
            }
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

private sealed interface FriendTimelineItem {
    val timestamp: Long

    data class Expense(val item: UnsettledExpenseItem) : FriendTimelineItem {
        override val timestamp: Long get() = item.transaction.transactionDate
    }

    data class SettlementItem(val settlement: Settlement) : FriendTimelineItem {
        override val timestamp: Long get() = settlement.settlementDate
    }
}

@Composable
private fun FriendExpenseCard(
    item: UnsettledExpenseItem,
    friend: com.naman.rupeeflow.domain.model.Friend,
    onClick: () -> Unit
) {
    val txn = item.transaction
    val isOwedToMe = item.direction == ExpenseObligationDirection.THEY_OWE_ME
    val obligationColor = if (isOwedToMe) AppTheme.colors.incomeGreen else CoralRed
    val directionLabel = if (isOwedToMe) "${friend.name} owes you" else "You owe ${friend.name}"

    RupeeFlowCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = txn.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (item.isFullySettled) "₹0.00" else CurrencyUtils.formatPaiseToRupees(item.remainingOutstandingPaise),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isFullySettled) AppTheme.colors.incomeGreen else obligationColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paid by ${txn.paidByName} • ${DateTimeUtils.formatDate(txn.transactionDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary
                )
                Text(
                    text = if (item.isFullySettled) "Settled" else directionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isFullySettled) AppTheme.colors.incomeGreen else obligationColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            when {
                item.isFullySettled -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppTheme.colors.incomeGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AppTheme.colors.incomeGreen.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Fully Settled",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.incomeGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Original: ${CurrencyUtils.formatPaiseToRupees(item.originalShareAmountPaise)} • Settled: ${CurrencyUtils.formatPaiseToRupees(item.settledAmountPaise)} • Remaining: ₹0.00",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.incomeGreen
                            )
                        }
                    }
                }
                item.isPartiallySettled -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmberGold.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Partially Settled",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Settled: ${CurrencyUtils.formatPaiseToRupees(item.settledAmountPaise)} of ${CurrencyUtils.formatPaiseToRupees(item.originalShareAmountPaise)} • Remaining: ${CurrencyUtils.formatPaiseToRupees(item.remainingOutstandingPaise)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold
                            )
                        }
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppTheme.colors.surfaceSecondary,
                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Unpaid",
                                style = MaterialTheme.typography.labelSmall,
                                color = obligationColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Share: ${CurrencyUtils.formatPaiseToRupees(item.originalShareAmountPaise)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendSettlementCard(
    settlement: Settlement,
    friend: com.naman.rupeeflow.domain.model.Friend,
    onReverse: (Long) -> Unit,
    onUnreverse: (Long) -> Unit,
    onClick: () -> Unit
) {
    val isReceived = settlement.receiverId == null
    val dirText = if (isReceived) "${friend.name} paid You" else "You paid ${friend.name}"
    val dirColor = if (isReceived) AppTheme.colors.incomeGreen else CoralRed

    RupeeFlowCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    color = if (settlement.isReversed) AppTheme.colors.textMuted else AppTheme.colors.textPrimary,
                    textDecoration = if (settlement.isReversed) TextDecoration.LineThrough else null
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${DateTimeUtils.formatDate(settlement.settlementDate)} • ${settlement.paymentMethod.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    if (!settlement.affectsLedger && !settlement.isReversed) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AmberGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Verification Pending",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = AmberGold,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyUtils.formatPaiseToRupees(settlement.amountPaise),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (settlement.isReversed) AppTheme.colors.textMuted else dirColor,
                    textDecoration = if (settlement.isReversed) TextDecoration.LineThrough else null
                )
                if (!settlement.isReversed) {
                    Text(
                        text = "Reverse",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        modifier = Modifier.clickable { onReverse(settlement.id) }
                    )
                } else {
                    Text(
                        text = "Convert to Normal",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.incomeGreen,
                        modifier = Modifier.clickable { onUnreverse(settlement.id) }
                    )
                }
            }
        }
    }
}
