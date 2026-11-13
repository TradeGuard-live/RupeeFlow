package com.naman.rupeeflow.ui.screens.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.SplitMethod
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.EditTransactionDialog
import com.naman.rupeeflow.ui.components.FriendAvatar
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.components.TransactionDetailDialog
import com.naman.rupeeflow.ui.components.TransactionItemCard
import com.naman.rupeeflow.ui.components.TripCoverBanner
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

private data class CategorySpend(
    val name: String,
    val totalPaise: Long,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseGroupDetailScreen(
    groupId: Long,
    viewModel: ExpenseGroupViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFriendDetail: (Long) -> Unit = {},
    onNavigateToReportGenerator: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val groupFlow = remember(groupId) { viewModel.getGroupByIdFlow(groupId) }
    val groupFromFlow by groupFlow.collectAsState(initial = null)
    val group = groupFromFlow ?: (state.activeGroups + state.archivedGroups).firstOrNull { it.id == groupId }

    val transactionsFlow = remember(groupId) { viewModel.getTransactionsForGroup(groupId) }
    val transactions by transactionsFlow.collectAsState(initial = emptyList())

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showEditTripDialog by remember { mutableStateOf(false) }
    var showDeleteTripDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }

    val filteredTransactions = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.merchantName?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.category?.name?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    // Scoped Friend Balances for this Trip (Section 6)
    val tripFriendsWithLedger = remember(group, transactions, state.friends, state.allSettlements) {
        if (group == null) emptyList()
        else {
            val memberIds = group.memberFriendIds.toSet()
            val members = state.friends.filter { it.id in memberIds }
            val groupSettlements = state.allSettlements.filter { s ->
                (s.payerId in memberIds) || (s.receiverId in memberIds)
            }
            LedgerCalculator.computeFriendLedgers(members, transactions, groupSettlements)
        }
    }

    // Category Breakdown (Section 4)
    val categoryBreakdown = remember(transactions) {
        transactions
            .filter { it.transactionType == TransactionType.EXPENSE }
            .groupBy { it.category?.name ?: "Other" }
            .map { (catName, txns) ->
                CategorySpend(
                    name = catName,
                    totalPaise = txns.sumOf { it.amountPaise },
                    count = txns.size
                )
            }
            .sortedByDescending { it.totalPaise }
    }

    // Payments and Splits statistics (Section 5)
    val equalSplitCount = remember(transactions) { transactions.count { it.splitMethod == SplitMethod.EQUAL && it.participants.isNotEmpty() } }
    val percentageSplitCount = remember(transactions) { transactions.count { it.splitMethod == SplitMethod.PERCENTAGE } }
    val customSplitCount = remember(transactions) { transactions.count { it.splitMethod == SplitMethod.EXACT_AMOUNT } }
    val personalTxnCount = remember(transactions) { transactions.count { it.participants.isEmpty() } }

    val groupColor = try {
        Color(android.graphics.Color.parseColor(group?.colorHex ?: "#3B82F6"))
    } catch (_: Exception) {
        AppTheme.colors.primaryAccent
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = group?.name ?: "Trip Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    group?.let { g ->
                        IconButton(onClick = { showEditTripDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Trip",
                                tint = AppTheme.colors.primaryAccent
                            )
                        }
                        IconButton(onClick = { showDeleteTripDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Trip",
                                tint = CoralRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
            )
        },
        floatingActionButton = {
            if (group != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddExpenseDialog = true },
                    containerColor = AppTheme.colors.primaryAccent,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Expense", fontWeight = FontWeight.Bold) }
                )
            }
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ==========================================
            // SECTION 1 — TRIP OVERVIEW DASHBOARD
            // ==========================================
            if (group != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Visual Banner
                        TripCoverBanner(
                            group = group,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )

                        RupeeFlowCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = groupColor.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    if (!group.destination.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = AppTheme.colors.primaryAccent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = group.destination!!,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = AppTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                }

                                if (group.isArchived) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AppTheme.colors.cardBorder
                                    ) {
                                        Text(
                                            text = "ARCHIVED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            if (group.startDate != null || group.endDate != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val dateStr = when {
                                    group.startDate != null && group.endDate != null ->
                                        "${dateFormat.format(group.startDate!!)} — ${dateFormat.format(group.endDate!!)}"
                                    group.startDate != null -> "From ${dateFormat.format(group.startDate!!)}"
                                    else -> "Until ${dateFormat.format(group.endDate!!)}"
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppTheme.colors.surfaceElevated
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = AppTheme.colors.primaryAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }

                            if (!group.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = group.description!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = AppTheme.colors.divider)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Personal Share vs Gross Group Bills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "My Personal Share",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    Text(
                                        CurrencyUtils.formatPaiseToRupees(group.totalSpendingPaise),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Total Group Bills",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                    Text(
                                        CurrencyUtils.formatPaiseToRupees(group.grossSpendingPaise),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.primaryAccent
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick metrics: Transactions, Members, Cash, Online
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${group.transactionCount} txns • ${group.memberCount} members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = "Cash: ${CurrencyUtils.formatPaiseToRupees(group.cashSpendingPaise)} • Online: ${CurrencyUtils.formatPaiseToRupees(group.onlineSpendingPaise)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 2 — TRIP MEMBERS
            // ==========================================
            if (group != null) {
                item {
                    val memberFriends = state.friends.filter { it.id in group.memberFriendIds }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trip Members (${memberFriends.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            TextButton(
                                onClick = { showEditTripDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Manage", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.primaryAccent)
                            }
                        }

                        if (memberFriends.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showEditTripDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = AppTheme.colors.primaryAccent)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "No friends added to this trip yet. Tap to invite friends!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        } else {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(memberFriends, key = { it.id }) { friend ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = AppTheme.colors.surfaceElevated,
                                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                        modifier = Modifier
                                            .width(105.dp)
                                            .clickable { onNavigateToFriendDetail(friend.id) }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            FriendAvatar(friend = friend, modifier = Modifier.size(46.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = friend.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 6 — FRIEND BALANCES WITHIN TRIP
            // ==========================================
            if (tripFriendsWithLedger.isNotEmpty()) {
                item {
                    val activeLedgers = tripFriendsWithLedger.filter { it.netBalancePaise != 0L }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Friend Balances for this Trip",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )

                        if (activeLedgers.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceElevated,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "All balances are settled for this trip!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        } else {
                            activeLedgers.forEach { friend ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppTheme.colors.surfaceElevated,
                                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToFriendDetail(friend.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FriendAvatar(friend = friend, modifier = Modifier.size(36.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = friend.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = if (friend.netBalancePaise > 0L) "Owes you for this trip" else "You owe for this trip",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AppTheme.colors.textSecondary
                                                )
                                            }
                                        }

                                        Text(
                                            text = CurrencyUtils.formatPaiseToRupees(abs(friend.netBalancePaise)),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (friend.netBalancePaise > 0L) Color(0xFF10B981) else CoralRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 4 — CATEGORY BREAKDOWN
            // ==========================================
            if (categoryBreakdown.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Category Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )

                        RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                            categoryBreakdown.forEachIndexed { idx, cat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(AppTheme.colors.primaryAccent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${cat.name} (${cat.count})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AppTheme.colors.textPrimary
                                        )
                                    }
                                    Text(
                                        text = CurrencyUtils.formatPaiseToRupees(cat.totalPaise),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                }
                                if (idx < categoryBreakdown.lastIndex) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 5 — PAYMENTS & SPLITS SUMMARY
            // ==========================================
            if (transactions.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Payments and Splits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceElevated,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Equal Splits", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                                    Text("$equalSplitCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceElevated,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Custom / % Splits", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                                    Text("${percentageSplitCount + customSplitCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryAccent)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceElevated,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Personal Only", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                                    Text("$personalTxnCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 7 — TRIP REPORTS (PDF EXPORT)
            // ==========================================
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppTheme.colors.surfaceElevated,
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(ElectricBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "PDF Report",
                                    tint = ElectricBlueLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Trip PDF Report",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Export itemized statement with splits & categories",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToReportGenerator,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                        ) {
                            Text("Export", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 3 — TRIP TRANSACTIONS
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Trip Transactions (${filteredTransactions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search transactions in this trip...", color = AppTheme.colors.textSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = AppTheme.colors.textTertiary, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching transactions found."
                                else "No transactions in this trip yet.\nTap '+ Add Expense' below to add your first expense!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { txn ->
                    TransactionItemCard(
                        transaction = txn,
                        onClick = { selectedTransactionForDetail = txn }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Detail Dialog
    selectedTransactionForDetail?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            onDismiss = { selectedTransactionForDetail = null },
            onToggleIgnore = { },
            onDuplicate = { },
            onDelete = { selectedTransactionForDetail = null },
            onEdit = {
                editingTransaction = txn
                selectedTransactionForDetail = null
            }
        )
    }

    // Edit Transaction Dialog
    editingTransaction?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            categories = state.categories,
            friends = state.friends,
            groups = state.friendGroups,
            expenseGroups = state.activeGroups + state.archivedGroups,
            onDismiss = { editingTransaction = null },
            onSave = { updatedTxn ->
                viewModel.saveTransaction(updatedTxn)
                editingTransaction = null
            }
        )
    }

    // Add Expense Floating Dialog (Phase 5 & 6)
    if (showAddExpenseDialog && group != null) {
        val initialTxn = Transaction(
            id = 0L,
            title = "",
            amountPaise = 0L,
            transactionType = TransactionType.EXPENSE,
            categoryId = state.categories.firstOrNull()?.id ?: 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            expenseGroupId = group.id,
            expenseGroupName = group.name
        )
        EditTransactionDialog(
            transaction = initialTxn,
            categories = state.categories,
            friends = state.friends,
            groups = state.friendGroups,
            expenseGroups = state.activeGroups + state.archivedGroups,
            onDismiss = { showAddExpenseDialog = false },
            onSave = { newTxn ->
                viewModel.saveTransaction(newTxn)
                showAddExpenseDialog = false
            }
        )
    }

    // Edit Trip Dialog (Phase 7)
    if (showEditTripDialog && group != null) {
        EditTripDetailsDialog(
            group = group,
            friends = state.friends,
            friendGroups = state.friendGroups,
            onDismiss = { showEditTripDialog = false },
            onSave = { name, destination, startDate, endDate, description, coverImageUri, colorHex, friendIds ->
                viewModel.updateTrip(
                    group = group.copy(
                        name = name,
                        destination = destination,
                        startDate = startDate,
                        endDate = endDate,
                        description = description,
                        coverImageUri = coverImageUri,
                        colorHex = colorHex
                    ),
                    friendIds = friendIds
                )
                showEditTripDialog = false
            }
        )
    }

    // Safe Delete Dialog (Phase 8)
    if (showDeleteTripDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showDeleteTripDialog = false },
            title = { Text("Delete Trip?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Delete \"${group.name}\"?\n\nDeleting this trip container will NOT delete any transactions, settlements, or friends. All historical financial records will remain preserved in your ledger.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(group.id)
                        showDeleteTripDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete Trip", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTripDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
