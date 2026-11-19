package com.naman.rupeeflow.ui.screens.splitpro

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.ui.components.EditTransactionDialog
import com.naman.rupeeflow.ui.components.FastAddExpenseSheet
import com.naman.rupeeflow.ui.components.FriendAvatar
import com.naman.rupeeflow.ui.components.SettlementDetailDialog
import com.naman.rupeeflow.ui.components.TransactionDetailDialog
import com.naman.rupeeflow.ui.components.TransactionItemCard
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitProGroupDetailScreen(
    groupId: Long,
    viewModel: SplitProViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToReportGenerator: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val group = state.groups.firstOrNull { it.id == groupId }
    val groupSummary = state.groupSummaries[groupId]

    val groupTxns = remember(state.allTransactions, groupId) {
        state.allTransactions.filter {
            it.expenseGroupId == groupId && it.transactionStatus == TransactionStatus.ACTIVE
        }.sortedByDescending { it.transactionDate }
    }

    val groupSettlements = remember(state.allSettlements, groupId) {
        state.allSettlements.filter { it.groupId == groupId }
    }

    val activitiesFlow = remember(groupId) { viewModel.getGroupActivities(groupId) }
    val activities by activitiesFlow.collectAsState(initial = emptyList())

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Expenses", "Balances", "Simplify Debts", "Activity")

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var showSettleUpDialog by remember { mutableStateOf(false) }
    var showUpiSettlementDialog by remember { mutableStateOf(false) }
    var upiTargetFriend by remember { mutableStateOf<Friend?>(null) }
    var upiTargetAmountPaise by remember { mutableStateOf(0L) }
    var settleUpPayerId by remember { mutableStateOf<Long?>(null) }
    var settleUpReceiverId by remember { mutableStateOf<Long?>(null) }
    var settleUpAmountPaise by remember { mutableStateOf<Long?>(null) }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var selectedSettlementForDetail by remember { mutableStateOf<Settlement?>(null) }
    var settlementToReverse by remember { mutableStateOf<Settlement?>(null) }
    var settlementToRestore by remember { mutableStateOf<Settlement?>(null) }

    // Activity filter
    var activityFilter by remember { mutableStateOf("ALL") }

    if (group == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (state.isLoading) "Loading..." else "Group Details") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
                )
            },
            containerColor = AppTheme.colors.background
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = AppTheme.colors.primaryAccent)
                } else {
                    Text("Group not found.", color = AppTheme.colors.textSecondary)
                }
            }
        }
        return
    }

    // Simplify Debts mode toggle: true = simplified, false = direct
    var isSimplifyDebtsMode by remember(group.id) { mutableStateOf(group.simplifyDebts) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = group.groupType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToReportGenerator(groupId) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Report", tint = AppTheme.colors.primaryAccent)
                    }
                    IconButton(onClick = { showEditGroupDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Edit Group", tint = AppTheme.colors.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddExpenseDialog = true },
                    containerColor = AppTheme.colors.primaryAccent,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            } else if (selectedTabIndex == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        settleUpPayerId = null
                        settleUpReceiverId = null
                        settleUpAmountPaise = null
                        showSettleUpDialog = true
                    },
                    containerColor = EmeraldMint,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text("Settle Up", fontWeight = FontWeight.Bold) }
                )
            }
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Group Hero Banner
            if (group != null) {
                val myNet = groupSummary?.myNetBalancePaise ?: 0L
                val totalExp = groupSummary?.totalExpensePaise ?: 0L
                val myShare = groupSummary?.mySharePaise ?: 0L

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Group Spending",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(totalExp),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }

                            // Net Balance Badge
                            val statusBg = when {
                                myNet > 0L -> EmeraldMint.copy(alpha = 0.15f)
                                myNet < 0L -> CoralRed.copy(alpha = 0.15f)
                                else -> AppTheme.colors.surface
                            }
                            val statusColor = when {
                                myNet > 0L -> EmeraldMint
                                myNet < 0L -> CoralRed
                                else -> AppTheme.colors.textSecondary
                            }
                            val statusText = when {
                                myNet > 0L -> "You are owed ${CurrencyUtils.formatPaiseToRupees(myNet)}"
                                myNet < 0L -> "You owe ${CurrencyUtils.formatPaiseToRupees(kotlin.math.abs(myNet))}"
                                else -> "All settled up"
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(statusBg)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Your personal share: ${CurrencyUtils.formatPaiseToRupees(myShare)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                            val activeMemberCount = if (groupSummary != null && groupSummary.memberBalances.isNotEmpty()) {
                                groupSummary.memberBalances.size
                            } else {
                                (group.memberCount + 1).coerceAtLeast(1)
                            }
                            Text(
                                text = if (activeMemberCount == 1) "1 member" else "$activeMemberCount members",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.primaryAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = AppTheme.colors.surface,
                contentColor = AppTheme.colors.primaryAccent,
                divider = { Divider(color = AppTheme.colors.divider) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                // 0. Expenses Tab
                0 -> {
                    if (groupTxns.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No expenses yet in this group.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(onClick = { showAddExpenseDialog = true }) {
                                    Text("Add First Expense")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(groupTxns, key = { "txn_${it.id}" }) { txn ->
                                TransactionItemCard(
                                    transaction = txn,
                                    onClick = { selectedTransactionForDetail = txn }
                                )
                            }
                        }
                    }
                }

                // 1. Balances Tab
                1 -> {
                    val balances = groupSummary?.memberBalances ?: emptyList()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Member Balances",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                        }

                        items(balances) { mb ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = AppTheme.colors.surfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(AppTheme.colors.surface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = mb.memberName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = AppTheme.colors.primaryAccent
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = mb.memberName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppTheme.colors.textPrimary
                                            )
                                            Text(
                                                text = "Paid: ${CurrencyUtils.formatPaiseToRupees(mb.totalPaidPaise)} | Share: ${CurrencyUtils.formatPaiseToRupees(mb.totalSharePaise)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.textSecondary
                                            )
                                        }
                                    }

                                    val net = mb.netBalancePaise
                                    Column(horizontalAlignment = Alignment.End) {
                                        val netText = when {
                                            net > 0L -> "+${CurrencyUtils.formatPaiseToRupees(net)}"
                                            net < 0L -> "-${CurrencyUtils.formatPaiseToRupees(SafeMath.safeAbs(net))}"
                                            else -> "Settled"
                                        }
                                        val netColor = when {
                                            net > 0L -> EmeraldMint
                                            net < 0L -> CoralRed
                                            else -> AppTheme.colors.textSecondary
                                        }
                                        Text(
                                            text = netText,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = netColor
                                        )
                                        Text(
                                            text = if (net > 0L) "gets back" else if (net < 0L) "owes" else "clear",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Settlement History Header
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Settlement History (${groupSettlements.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                        }

                        if (groupSettlements.isEmpty()) {
                            item {
                                Text(
                                    text = "No settlements recorded yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        } else {
                            items(groupSettlements) { s ->
                                val friendsMap = state.friends.associateBy { it.id }
                                val pName = if (s.payerId == null) "You" else friendsMap[s.payerId]?.name ?: "Unavailable Member"
                                val rName = if (s.receiverId == null) "You" else friendsMap[s.receiverId]?.name ?: "Unavailable Member"

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSettlementForDetail = s },
                                    shape = RoundedCornerShape(14.dp),
                                    color = AppTheme.colors.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "$pName paid $rName",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (s.isReversed) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary
                                            )
                                            Text(
                                                text = "${DateTimeUtils.formatDate(s.settlementDate, "dd MMMM yyyy")} • ${s.paymentMethod.displayName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.textSecondary
                                            )
                                            if (s.isReversed) {
                                                Text(
                                                    text = "REVERSED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CoralRed,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = CurrencyUtils.formatPaiseToRupees(s.amountPaise),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (s.isReversed) AppTheme.colors.textSecondary else EmeraldMint
                                            )
                                            if (!s.isReversed) {
                                                IconButton(onClick = { settlementToReverse = s }) {
                                                    Icon(Icons.Default.Undo, contentDescription = "Reverse Settlement", tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                                                }
                                            } else {
                                                IconButton(onClick = { settlementToRestore = s }) {
                                                    Icon(Icons.Default.Restore, contentDescription = "Restore Settlement", tint = AppTheme.colors.incomeGreen, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Simplify Debts Tab
                2 -> {
                    val debtsToShow = if (isSimplifyDebtsMode) {
                        groupSummary?.simplifiedDebts ?: emptyList()
                    } else {
                        groupSummary?.directDebts ?: emptyList()
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Explanation & Toggle Card
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = AppTheme.colors.surfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Simplify Debts",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = AppTheme.colors.textPrimary
                                            )
                                            Text(
                                                text = "Minimizes total payments between members while preserving exact net balances.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppTheme.colors.textSecondary
                                            )
                                        }
                                        Switch(
                                            checked = isSimplifyDebtsMode,
                                            onCheckedChange = { isSimplifyDebtsMode = it }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = if (isSimplifyDebtsMode) "Suggested Simplified Transfers (${debtsToShow.size})" else "Original Pairwise Debts (${debtsToShow.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                        }

                        if (debtsToShow.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                                    Text("Everyone is settled up! Zero debts remaining.", style = MaterialTheme.typography.bodyMedium, color = EmeraldMint, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            items(debtsToShow) { debt ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AppTheme.colors.surfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = debt.fromMemberName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                                Icon(
                                                    Icons.Default.ArrowForward,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(horizontal = 6.dp).size(16.dp),
                                                    tint = AppTheme.colors.primaryAccent
                                                )
                                                Text(
                                                    text = debt.toMemberName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Amount: ${CurrencyUtils.formatPaiseToRupees(debt.amountPaise)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.textSecondary
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                settleUpPayerId = debt.fromMemberId
                                                settleUpReceiverId = debt.toMemberId
                                                settleUpAmountPaise = debt.amountPaise
                                                showSettleUpDialog = true
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EmeraldMint,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Settle", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Activity Timeline Tab
                3 -> {
                    val filteredActivities = remember(activities, activityFilter) {
                        when (activityFilter) {
                            "EXPENSES" -> activities.filter {
                                it.activityType == GroupActivityType.EXPENSE_CREATED ||
                                        it.activityType == GroupActivityType.EXPENSE_EDITED ||
                                        it.activityType == GroupActivityType.EXPENSE_DELETED
                            }
                            "PAYMENTS" -> activities.filter {
                                it.activityType == GroupActivityType.SETTLEMENT_RECORDED ||
                                        it.activityType == GroupActivityType.SETTLEMENT_REVERSED ||
                                        it.activityType == GroupActivityType.SETTLEMENT_RESTORED
                            }
                            "MEMBERS" -> activities.filter {
                                it.activityType == GroupActivityType.MEMBER_ADDED ||
                                        it.activityType == GroupActivityType.MEMBER_REMOVED ||
                                        it.activityType == GroupActivityType.GROUP_CREATED
                            }
                            else -> activities
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Filter row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("ALL" to "All", "EXPENSES" to "Expenses", "PAYMENTS" to "Payments", "MEMBERS" to "Members").forEach { (key, label) ->
                                FilterChip(
                                    selected = activityFilter == key,
                                    onClick = { activityFilter = key },
                                    label = { Text(label) }
                                )
                            }
                        }

                        if (filteredActivities.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No activity recorded yet.", color = AppTheme.colors.textSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredActivities, key = { "act_${it.id}_${it.timestamp}_${it.hashCode()}" }) { act ->
                                    val linkedSettlement = remember(act.targetId, state.allSettlements) {
                                        if (act.targetId != null && (act.activityType == GroupActivityType.SETTLEMENT_RECORDED ||
                                                act.activityType == GroupActivityType.SETTLEMENT_REVERSED ||
                                                act.activityType == GroupActivityType.SETTLEMENT_RESTORED)) {
                                            state.allSettlements.firstOrNull { it.id == act.targetId }
                                        } else null
                                    }

                                    val linkedTransaction = remember(act.targetId, state.allTransactions) {
                                        if (act.targetId != null && (act.activityType == GroupActivityType.EXPENSE_CREATED ||
                                                act.activityType == GroupActivityType.EXPENSE_EDITED)) {
                                            state.allTransactions.firstOrNull { it.id == act.targetId && it.transactionStatus == TransactionStatus.ACTIVE }
                                        } else null
                                    }

                                    val clickableMod = when {
                                        linkedSettlement != null -> Modifier.clickable { selectedSettlementForDetail = linkedSettlement }
                                        linkedTransaction != null -> Modifier.clickable { selectedTransactionForDetail = linkedTransaction }
                                        else -> Modifier
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth().then(clickableMod),
                                        shape = RoundedCornerShape(14.dp),
                                        color = AppTheme.colors.surfaceElevated,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val icon = when (act.activityType) {
                                                GroupActivityType.EXPENSE_CREATED -> Icons.Default.AddCircleOutline
                                                GroupActivityType.EXPENSE_EDITED -> Icons.Default.Edit
                                                GroupActivityType.EXPENSE_DELETED -> Icons.Default.DeleteOutline
                                                GroupActivityType.SETTLEMENT_RECORDED -> Icons.Default.CheckCircle
                                                GroupActivityType.SETTLEMENT_REVERSED -> Icons.Default.Undo
                                                GroupActivityType.SETTLEMENT_RESTORED -> Icons.Default.Restore
                                                GroupActivityType.GROUP_CREATED -> Icons.Default.Groups
                                                GroupActivityType.GROUP_UPDATED -> Icons.Default.Settings
                                                GroupActivityType.GROUP_ARCHIVED -> Icons.Default.Archive
                                                GroupActivityType.MEMBER_ADDED -> Icons.Default.PersonAdd
                                                GroupActivityType.MEMBER_REMOVED -> Icons.Default.PersonRemove
                                            }
                                            Icon(icon, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(22.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = act.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = DateTimeUtils.formatDate(act.timestamp, "dd MMM yyyy, hh:mm a"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AppTheme.colors.textSecondary
                                                )
                                            }
                                            if (act.amountPaise != null && act.amountPaise > 0L) {
                                                Text(
                                                    text = CurrencyUtils.formatPaiseToRupees(act.amountPaise),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                            }
                                            if (linkedSettlement != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                if (!linkedSettlement.isReversed) {
                                                    IconButton(onClick = { settlementToReverse = linkedSettlement }) {
                                                        Icon(Icons.Default.Undo, contentDescription = "Reverse Settlement", tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                                                    }
                                                } else {
                                                    IconButton(onClick = { settlementToRestore = linkedSettlement }) {
                                                        Icon(Icons.Default.Restore, contentDescription = "Restore Settlement", tint = AppTheme.colors.incomeGreen, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            } else if (linkedTransaction != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val freshTxn = viewModel.getTransactionById(linkedTransaction.id) ?: linkedTransaction
                                                            editingTransaction = freshTxn
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit Expense", tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Unified Add Transaction Sheet
    if (showAddExpenseDialog && group != null) {
        FastAddExpenseSheet(
            categories = state.categories,
            friends = state.friends,
            groups = emptyList(),
            expenseGroups = state.groups,
            initialExpenseGroupId = groupId,
            lockToExpenseGroup = true,
            onDismiss = { showAddExpenseDialog = false },
            onSave = { newTxn ->
                viewModel.saveGroupExpense(newTxn) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Expense added successfully")
                    }
                }
                showAddExpenseDialog = false
            }
        )
    }

    // Edit Group Dialog
    if (showEditGroupDialog && group != null) {
        EditSplitGroupDialog(
            group = group,
            friends = state.friends,
            groupSummary = groupSummary,
            onDismiss = { showEditGroupDialog = false },
            onSave = { updatedGroup, friendIds ->
                viewModel.updateGroup(updatedGroup, friendIds)
            },
            onArchiveToggle = { viewModel.toggleArchive(group) },
            onDeleteGroup = {
                viewModel.deleteGroup(group.id) {
                    onNavigateBack()
                }
            }
        )
    }

    // Settle Up Dialog
    if (showSettleUpDialog && group != null) {
        GroupSettleUpDialog(
            group = group,
            friends = state.friends,
            groupSummary = groupSummary,
            initialPayerId = settleUpPayerId,
            initialReceiverId = settleUpReceiverId,
            initialAmountPaise = settleUpAmountPaise,
            onDismiss = { showSettleUpDialog = false },
            onConfirmSettlement = { settlement ->
                viewModel.recordGroupSettlement(settlement)
                showSettleUpDialog = false
            },
            onPayWithUpi = { friendId, amountPaise ->
                upiTargetFriend = state.friends.firstOrNull { it.id == friendId }
                upiTargetAmountPaise = amountPaise
                showUpiSettlementDialog = true
            }
        )
    }

    // Direct UPI Settlement Dialog
    if (showUpiSettlementDialog && upiTargetFriend != null && group != null) {
        UpiSettlementDialog(
            initialFriend = upiTargetFriend,
            group = group,
            outstandingDebtPaise = upiTargetAmountPaise,
            upiSettlementManager = viewModel.upiSettlementManager,
            onFriendUpdated = { updated -> viewModel.updateFriend(updated) },
            onDismiss = {
                showUpiSettlementDialog = false
                upiTargetFriend = null
            },
            onSettlementCompleted = {
                showUpiSettlementDialog = false
                upiTargetFriend = null
            }
        )
    }

    // Transaction Detail Dialog
    selectedTransactionForDetail?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            onDismiss = { selectedTransactionForDetail = null },
            onToggleIgnore = { },
            onDuplicate = { },
            onDelete = {
                viewModel.deleteGroupExpense(txn)
                selectedTransactionForDetail = null
            },
            onEdit = { toEdit ->
                scope.launch {
                    val freshTxn = viewModel.getTransactionById(toEdit.id) ?: toEdit
                    editingTransaction = freshTxn
                    selectedTransactionForDetail = null
                }
            },
            onSaveTransaction = { viewModel.saveGroupExpense(it) }
        )
    }

    // Edit Transaction Dialog
    editingTransaction?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            categories = state.categories,
            friends = state.friends,
            groups = emptyList(),
            expenseGroups = state.groups,
            lockToExpenseGroup = true,
            onDismiss = { editingTransaction = null },
            onSave = { updated ->
                viewModel.saveGroupExpense(updated) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Expense updated successfully")
                    }
                }
                editingTransaction = null
            }
        )
    }

    // Settlement Detail Dialog
    selectedSettlementForDetail?.let { s ->
        val friendsMap = state.friends.associateBy { it.id }
        val pName = if (s.payerId == null) "You" else friendsMap[s.payerId]?.name ?: "Unavailable Member"
        val rName = if (s.receiverId == null) "You" else friendsMap[s.receiverId]?.name ?: "Unavailable Member"
        SettlementDetailDialog(
            settlement = s,
            friendName = if (s.payerId == null) rName else pName,
            payerName = pName,
            receiverName = rName,
            onDismiss = { selectedSettlementForDetail = null },
            onReverse = { toRev ->
                viewModel.reverseGroupSettlement(toRev) { success ->
                    if (success) {
                        scope.launch { snackbarHostState.showSnackbar("Settlement reversed") }
                    }
                }
                selectedSettlementForDetail = null
            },
            onUnreverse = { toRes ->
                viewModel.restoreGroupSettlement(toRes) { success ->
                    if (success) {
                        scope.launch { snackbarHostState.showSnackbar("Settlement restored") }
                    }
                }
                selectedSettlementForDetail = null
            },
            onDelete = {
                selectedSettlementForDetail = null
            },
            onEdit = {
                selectedSettlementForDetail = null
            }
        )
    }

    // Settlement Reverse Confirmation Dialog
    settlementToReverse?.let { s ->
        val friendsMap = state.friends.associateBy { it.id }
        val pName = if (s.payerId == null) "You" else friendsMap[s.payerId]?.name ?: "Unavailable Member"
        val rName = if (s.receiverId == null) "You" else friendsMap[s.receiverId]?.name ?: "Unavailable Member"
        AlertDialog(
            onDismissRequest = { settlementToReverse = null },
            title = { Text("Reverse Settlement?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Are you sure you want to reverse this settlement ($pName paid $rName ${CurrencyUtils.formatPaiseToRupees(s.amountPaise)})? Its effect on group balances will be removed.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toRev = s
                        settlementToReverse = null
                        viewModel.reverseGroupSettlement(toRev) { success ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Settlement reversed") }
                            }
                        }
                    }
                ) {
                    Text("Confirm Reverse", color = AmberGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { settlementToReverse = null }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Settlement Restore Confirmation Dialog
    settlementToRestore?.let { s ->
        val friendsMap = state.friends.associateBy { it.id }
        val pName = if (s.payerId == null) "You" else friendsMap[s.payerId]?.name ?: "Unavailable Member"
        val rName = if (s.receiverId == null) "You" else friendsMap[s.receiverId]?.name ?: "Unavailable Member"
        AlertDialog(
            onDismissRequest = { settlementToRestore = null },
            title = { Text("Restore Settlement?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Are you sure you want to restore this settlement ($pName paid $rName ${CurrencyUtils.formatPaiseToRupees(s.amountPaise)})? Its effect on group balances will be reapplied.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toRes = s
                        settlementToRestore = null
                        viewModel.restoreGroupSettlement(toRes) { success ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Settlement restored") }
                            }
                        }
                    }
                ) {
                    Text("Confirm Restore", color = AppTheme.colors.incomeGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { settlementToRestore = null }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

