package com.naman.rupeeflow.ui.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToFriendDetail: (Long) -> Unit,
    onNavigateToPending: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var showFastAddSheet by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var settlingFriend by remember { mutableStateOf<Friend?>(null) }

    val todayDateStr = remember {
        val sdf = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
        sdf.format(Date())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFastAddSheet = true },
                containerColor = AppTheme.colors.primaryAccent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Expense",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { paddingValues ->
        val isDark = AppTheme.isDark
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Greeting & Date
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = todayDateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Financial Overview",
                            style = MaterialTheme.typography.headlineLarge,
                            color = AppTheme.colors.textPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (state.pendingTransactions.isNotEmpty()) {
                            if (isDark) AmberGold.copy(alpha = 0.2f) else Color(0xFFFEF3C7)
                        } else {
                            if (isDark) CharcoalCard else AppTheme.colors.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.pendingTransactions.isNotEmpty()) {
                                if (isDark) AmberGold.copy(alpha = 0.6f) else Color(0xFFF59E0B)
                            } else {
                                if (isDark) GlassBorder else AppTheme.colors.cardBorder
                            }
                        ),
                        modifier = Modifier.clickable { onNavigateToPending() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.pendingTransactions.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) AmberGold else Color(0xFFD97706))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${state.pendingTransactions.size} SMS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) AmberGold else Color(0xFFD97706),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = "Detected",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Detected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Pending Transactions Banner if any detected
            if (state.pendingTransactions.isNotEmpty()) {
                item {
                    val pending = state.pendingTransactions.first()
                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (isDark) AmberGold.copy(alpha = 0.6f) else Color(0xFFF59E0B),
                        backgroundColor = if (isDark) CharcoalCard else Color(0xFFFFFBEB)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SMS Transaction Detected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) AmberGold else Color(0xFFD97706),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${CurrencyUtils.formatPaiseToRupees(pending.detectedAmountPaise)} at ${pending.detectedMerchant ?: pending.detectedBank ?: "Bank"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { viewModel.ignorePending(pending.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.textSecondary)
                                ) {
                                    Text("Ignore", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { onNavigateToPending() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) AmberGold else Color(0xFFD97706),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Review", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Hero Card: Month Total Spending
            item {
                HeroSummaryCard(
                    title = "This Month's Spending",
                    amountFormatted = CurrencyUtils.formatPaiseToRupees(state.summary.totalExpenseThisMonthPaise),
                    subtitle = "Daily average: ${CurrencyUtils.formatPaiseToRupees(state.summary.averageDailySpendingPaise)}",
                    icon = Icons.Default.TrendingUp,
                    accentColor = AppTheme.colors.primaryAccent
                )
            }

            // Quick Stats Row: Owed to me & I owe
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStatCard(
                        title = "Owed to Me",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(state.summary.totalOwedToMePaise),
                        accentColor = if (isDark) EmeraldMint else Color(0xFF059669),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToFriends
                    )
                    MiniStatCard(
                        title = "I Owe Friends",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(state.summary.totalIOwePaise),
                        accentColor = if (isDark) CoralRed else Color(0xFFDC2626),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToFriends
                    )
                }
            }

            // Cash vs Online Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStatCard(
                        title = "Online Spending",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(state.summary.totalOnlineSpendingPaise),
                        accentColor = if (isDark) VibrantCyan else Color(0xFF0891B2),
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatCard(
                        title = "Cash Spending",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(state.summary.totalCashSpendingPaise),
                        accentColor = if (isDark) AmberGold else Color(0xFFD97706),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Weekly Spending Bar Chart Card
            item {
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Weekly Activity",
                                style = MaterialTheme.typography.titleMedium,
                                color = AppTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.weekRangeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        // Prev / Next Week Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.previousWeek() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Week",
                                    tint = AppTheme.colors.primaryAccent
                                )
                            }

                            if (state.weekOffset != 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDark) ElectricBlue.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer,
                                    modifier = Modifier.clickable { viewModel.currentWeek() }
                                ) {
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDark) ElectricBlueLight else Color(0xFF1D4ED8),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.nextWeek() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Week",
                                    tint = AppTheme.colors.primaryAccent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CustomBarChart(
                        data = state.weeklySpending,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Friends with Outstanding Balances
            if (state.friendsWithBalances.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Outstanding Balances",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "See All",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onNavigateToFriends() }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.friendsWithBalances.take(5).forEach { friend ->
                            FriendItemCard(
                                friend = friend,
                                modifier = Modifier.width(280.dp),
                                onClick = { onNavigateToFriendDetail(friend.id) }
                            )
                        }
                    }
                }
            }

            // Recent Transactions Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.primaryAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToTransactions() }
                    )
                }
            }

            // Recent Transactions List
            if (state.recentTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions yet. Tap + to add one!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            } else {
                items(state.recentTransactions, key = { it.id }) { txn ->
                    TransactionItemCard(
                        transaction = txn,
                        onClick = { selectedTransaction = txn }
                    )
                }
            }
        }
    }

    // Fast Add Modal Sheet
    if (showFastAddSheet) {
        FastAddExpenseSheet(
            categories = state.categories,
            friends = state.allFriends,
            groups = state.friendGroups,
            onDismiss = { showFastAddSheet = false },
            onSave = { newTxn -> viewModel.saveTransaction(newTxn) },
            onSaveSettlement = { settlement -> viewModel.recordSettlement(settlement) }
        )
    }

    // Transaction Detail Dialog
    selectedTransaction?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            onDismiss = { selectedTransaction = null },
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
            friends = state.allFriends,
            groups = state.friendGroups,
            onDismiss = { editingTransaction = null },
            onSave = { updated -> viewModel.saveTransaction(updated) }
        )
    }

    // Settle Up Dialog
    settlingFriend?.let { friend ->
        SettleUpDialog(
            friend = friend,
            onDismiss = { settlingFriend = null },
            onConfirm = { settlement -> viewModel.recordSettlement(settlement) }
        )
    }
}
