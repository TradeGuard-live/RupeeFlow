package com.naman.rupeeflow.ui.screens.overview

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*
import com.naman.rupeeflow.voice.ui.VoiceAssistantSheet
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToFriendDetail: (Long) -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToSplitPro: () -> Unit = {},
    onNavigateToExpenseGroups: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToSavingsGoals: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // rememberSaveable keeps the sheet open across biometric lock/unlock and activity recreation
    var showFastAddSheet by rememberSaveable { mutableStateOf(false) }
    var fastAddInitialType by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var showVoiceAssistant by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var settlingFriend by remember { mutableStateOf<Friend?>(null) }
    var showUpiDialog by remember { mutableStateOf(false) }
    var upiTargetFriend by remember { mutableStateOf<Friend?>(null) }
    var upiTargetAmountPaise by remember { mutableStateOf(0L) }

    val todayDateStr = remember {
        val sdf = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
        sdf.format(Date())
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { showVoiceAssistant = true },
                    containerColor = AppTheme.colors.surfaceElevated,
                    contentColor = AppTheme.colors.primaryAccent,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Assistant",
                        modifier = Modifier.size(20.dp)
                    )
                }
                FloatingActionButton(
                    onClick = {
                        fastAddInitialType = TransactionType.EXPENSE
                        showFastAddSheet = true
                    },
                    containerColor = AppTheme.colors.primaryAccent,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Expense",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        val isDark = AppTheme.isDark
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Personalized Greeting with Cute Bunny Mascot & Date
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        RupeeBunnyMascot(
                            pose = MascotPose.HAPPY,
                            size = 56.dp,
                            animated = true
                        )
                        Column {
                            Text(
                                text = todayDateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$greeting!",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                        }
                    }

                    DetectedTransactionsBadge(
                        pendingCount = state.pendingTransactions.size,
                        onClick = onNavigateToPending
                    )
                }
            }

            // Quick Actions Horizontal Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionPill(
                        label = "Add Expense",
                        icon = Icons.Default.AddCircleOutline,
                        accentColor = Color(0xFFF58B72),
                        backgroundColor = Color(0xFFFFF0E6),
                        onClick = {
                            fastAddInitialType = TransactionType.EXPENSE
                            showFastAddSheet = true
                        }
                    )
                    QuickActionPill(
                        label = "Add Income",
                        icon = Icons.Default.TrendingUp,
                        accentColor = Color(0xFF16A34A),
                        backgroundColor = Color(0xFFDCFCE7),
                        onClick = {
                            fastAddInitialType = TransactionType.INCOME
                            showFastAddSheet = true
                        }
                    )
                    QuickActionPill(
                        label = "Savings",
                        icon = Icons.Default.Savings,
                        accentColor = Color(0xFFF58B72),
                        backgroundColor = Color(0xFFFFF0E6),
                        onClick = onNavigateToSavingsGoals
                    )
                    QuickActionPill(
                        label = "Split Pro",
                        icon = Icons.Default.Groups,
                        accentColor = Color(0xFF3B82F6),
                        backgroundColor = Color(0xFFEFF6FF),
                        onClick = onNavigateToSplitPro
                    )
                    QuickActionPill(
                        label = "Trips",
                        icon = Icons.Default.Flight,
                        accentColor = Color(0xFF9333EA),
                        backgroundColor = Color(0xFFFDF4FF),
                        onClick = onNavigateToExpenseGroups
                    )
                    QuickActionPill(
                        label = "Reports",
                        icon = Icons.Default.Description,
                        accentColor = Color(0xFFD97706),
                        backgroundColor = Color(0xFFFEF3C7),
                        onClick = onNavigateToReports
                    )
                    QuickActionPill(
                        label = "Calendar",
                        icon = Icons.Default.DateRange,
                        accentColor = Color(0xFF0D9488),
                        backgroundColor = Color(0xFFF0FDFA),
                        onClick = onNavigateToCalendar
                    )
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

            // Income & Estimated Savings Row
            item {
                val estimatedSavings = maxOf(0L, state.summary.totalIncomeThisMonthPaise - state.summary.totalExpenseThisMonthPaise)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStatCard(
                        title = "Monthly Income",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(state.summary.totalIncomeThisMonthPaise),
                        accentColor = if (isDark) EmeraldMint else Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatCard(
                        title = "Estimated Savings",
                        amountFormatted = CurrencyUtils.formatPaiseToRupees(estimatedSavings),
                        accentColor = if (isDark) VibrantCyan else Color(0xFF0284C7),
                        modifier = Modifier.weight(1f)
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

            // Split Pro Shortcut Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToSplitPro() },
                    color = AppTheme.colors.surfaceElevated,
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(VibrantCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = VibrantCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Split Pro",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(VibrantCyan.copy(alpha = 0.2f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "PRO",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = VibrantCyan,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "Group Expenses & Debt Simplification",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Split Pro",
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                }
            }

            // Savings Goals Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToSavingsGoals() },
                    color = AppTheme.colors.surfaceElevated,
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppTheme.colors.primaryPeach.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Savings,
                                        contentDescription = "Savings Goals",
                                        tint = AppTheme.colors.deepPeach,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Savings Goals",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        if (state.savingsSummary.activeGoalsCount > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(AppTheme.colors.mint.copy(alpha = 0.4f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${state.savingsSummary.activeGoalsCount} ACTIVE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppTheme.colors.textPrimary,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Saved this month: ${CurrencyUtils.formatPaiseToRupees(state.savingsSummary.savingsAddedThisMonthPaise)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Savings Goals",
                                tint = AppTheme.colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "Total Active Savings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(state.savingsSummary.totalSavedPaise),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }

                            Text(
                                text = "${String.format(Locale.US, "%.1f", state.savingsSummary.overallProgressPercentage)}% overall",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.deepPeach
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { (state.savingsSummary.overallProgressPercentage / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AppTheme.colors.deepPeach,
                            trackColor = AppTheme.colors.primaryAccentContainer
                        )
                    }
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
                                    color = AppTheme.colors.primaryAccentContainer,
                                    modifier = Modifier.clickable { viewModel.currentWeek() }
                                ) {
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.primaryAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.nextWeek() },
                                modifier = Modifier.size(32.dp),
                                enabled = state.canNavigateNextWeek
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Week",
                                    tint = if (state.canNavigateNextWeek) AppTheme.colors.primaryAccent else AppTheme.colors.textMuted
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

            // Top Spending Categories
            if (state.categories.isNotEmpty()) {
                item {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.categories.take(6).forEach { cat ->
                            CategoryBadge(
                                categoryName = cat.name,
                                showLabel = true
                            )
                        }
                    }
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
                        Spacer(modifier = Modifier.width(4.dp))
                        state.friendsWithBalances.take(5).forEach { friend ->
                            FriendItemCard(
                                friend = friend,
                                modifier = Modifier.widthIn(min = 260.dp, max = 290.dp),
                                onClick = { onNavigateToFriendDetail(friend.id) }
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
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
                    MascotEmptyState(
                        pose = MascotPose.THINKING,
                        title = "No transactions yet",
                        subtitle = "Tap + or use Quick Actions above to record your first expense!",
                        actionLabel = "Add Expense",
                        onAction = {
                            fastAddInitialType = TransactionType.EXPENSE
                            showFastAddSheet = true
                        }
                    )
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
            expenseGroups = state.expenseGroups,
            onCreateExpenseGroup = { viewModel.createExpenseGroup(it) },
            onDismiss = { showFastAddSheet = false },
            onPayWithUpi = { targetFriend, amountPaise ->
                upiTargetFriend = targetFriend
                upiTargetAmountPaise = amountPaise
                showFastAddSheet = false
                showUpiDialog = true
            },
            onSave = { newTxn -> viewModel.saveTransaction(newTxn) },
            onSaveSettlement = { settlement -> viewModel.recordSettlement(settlement) },
            initialType = fastAddInitialType
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
            onEdit = { toEdit -> editingTransaction = toEdit },
            onSaveTransaction = { viewModel.saveTransaction(it) }
        )
    }

    // Edit Transaction Dialog
    editingTransaction?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            categories = state.categories,
            friends = state.allFriends,
            groups = state.friendGroups,
            expenseGroups = state.expenseGroups,
            onCreateExpenseGroup = { viewModel.createExpenseGroup(it) },
            onDismiss = { editingTransaction = null },
            onSave = { updated -> viewModel.saveTransaction(updated) }
        )
    }

    // Voice Assistant Sheet
    if (showVoiceAssistant) {
        VoiceAssistantSheet(
            onDismiss = { showVoiceAssistant = false }
        )
    }

    // Settle Up Dialog
    settlingFriend?.let { friend ->
        SettleUpDialog(
            friend = friend,
            onDismiss = { settlingFriend = null },
            onConfirm = { settlement -> viewModel.recordSettlement(settlement) },
            onPayWithUpi = { targetFriend, amountPaise ->
                settlingFriend = null
                upiTargetFriend = targetFriend
                upiTargetAmountPaise = amountPaise
                showUpiDialog = true
            }
        )
    }

    // Direct UPI Settlement Dialog
    val targetFriendForUpi = upiTargetFriend
    if (showUpiDialog && targetFriendForUpi != null) {
        com.naman.rupeeflow.ui.screens.splitpro.UpiSettlementDialog(
            initialFriend = targetFriendForUpi,
            outstandingDebtPaise = if (upiTargetAmountPaise > 0L) upiTargetAmountPaise else kotlin.math.abs(targetFriendForUpi.netBalancePaise),
            upiSettlementManager = viewModel.upiSettlementManager,
            onFriendUpdated = { updated -> viewModel.updateFriend(updated) },
            onDismiss = {
                showUpiDialog = false
                upiTargetFriend = null
                upiTargetAmountPaise = 0L
            },
            onSettlementCompleted = {
                showUpiDialog = false
                upiTargetFriend = null
                upiTargetAmountPaise = 0L
            }
        )
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val isDark = AppTheme.isDark
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) AppTheme.colors.surfaceElevated else backgroundColor,
        border = BorderStroke(1.dp, if (isDark) AppTheme.colors.cardBorder else accentColor.copy(alpha = 0.25f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) AppTheme.colors.textPrimary else AppTheme.colors.textPrimary
            )
        }
    }
}
