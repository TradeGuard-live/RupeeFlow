package com.naman.rupeeflow.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.screens.calendar.MonthlyTransactionCalendarView
import com.naman.rupeeflow.ui.screens.calendar.TransactionsViewMode
import com.naman.rupeeflow.ui.theme.*
import com.naman.rupeeflow.voice.ui.VoiceAssistantSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateToPending: (() -> Unit)? = null,
    onNavigateToTripDetail: (Long) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // rememberSaveable keeps the sheet open across biometric lock/unlock and activity recreation
    var showFastAddSheet by rememberSaveable { mutableStateOf(false) }
    var fastAddInitialDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showVoiceAssistant by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        containerColor = AppTheme.colors.background,
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
                        fastAddInitialDateMillis = null
                        showFastAddSheet = true
                    },
                    containerColor = AppTheme.colors.primaryAccent,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        val isDark = AppTheme.isDark
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.headlineLarge,
                    color = AppTheme.colors.textPrimary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // List / Calendar Toggle Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) CharcoalCard else AppTheme.colors.surfaceElevated,
                        border = BorderStroke(1.dp, if (isDark) GlassBorder else AppTheme.colors.cardBorder),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            val isList = state.viewMode == TransactionsViewMode.LIST
                            val isCal = state.viewMode == TransactionsViewMode.CALENDAR

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isList) AppTheme.colors.primaryAccent else Color.Transparent)
                                    .clickable { viewModel.setViewMode(TransactionsViewMode.LIST) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = "List View",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isList) Color.White else AppTheme.colors.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isCal) AppTheme.colors.primaryAccent else Color.Transparent)
                                    .clickable { viewModel.setViewMode(TransactionsViewMode.CALENDAR) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Calendar View",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isCal) Color.White else AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    if (onNavigateToPending != null) {
                        DetectedTransactionsBadge(
                            pendingCount = state.pendingCount,
                            onClick = onNavigateToPending
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search title, merchant, ref no...", color = AppTheme.colors.textTertiary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.textSecondary)
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
                            }
                        }
                        IconButton(onClick = { showVoiceAssistant = true }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Assistant", tint = AppTheme.colors.primaryAccent)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                colors = rupeeFlowTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Transaction Type Filters (All, Expenses, Income, Refunds, Transfers, Ignored)
            val typeFilters = listOf(
                "ALL" to "All",
                "EXPENSE" to "Expenses",
                "INCOME" to "Income",
                "REFUND" to "Refunds",
                "TRANSFER" to "Transfers",
                "IGNORED" to "Ignored"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                typeFilters.forEach { (key, label) ->
                    val isSelected = state.selectedTypeFilter == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            if (isDark) ElectricBlue.copy(alpha = 0.25f) else AppTheme.colors.primaryAccentContainer
                        } else {
                            if (isDark) CharcoalCard else AppTheme.colors.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AppTheme.colors.primaryAccent else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                        ),
                        modifier = Modifier.clickable { viewModel.onTypeFilterSelected(key) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                if (isDark) ElectricBlueLight else Color(0xFF1D4ED8)
                            } else {
                                AppTheme.colors.textSecondary
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (state.viewMode == TransactionsViewMode.CALENDAR) {
                MonthlyTransactionCalendarView(
                    gridData = state.calendarGridData,
                    selectedDateMillis = state.selectedCalendarDateMillis,
                    selectedDaySummary = state.selectedDaySummary,
                    onDateSelected = { viewModel.selectCalendarDate(it) },
                    onPreviousMonth = { viewModel.previousCalendarMonth() },
                    onNextMonth = { viewModel.nextCalendarMonth() },
                    onTodayClick = { viewModel.goToTodayCalendar() },
                    onTransactionClick = { selectedTransaction = it },
                    onAddTransactionForDate = { dateMillis ->
                        fastAddInitialDateMillis = dateMillis
                        showFastAddSheet = true
                    }
                )
            } else {
                // Time Period Filters (All Time, Monthly, Weekly)
                val dateFilters = listOf(
                    "ALL" to "All Time",
                    "MONTHLY" to "Monthly",
                    "WEEKLY" to "Weekly"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    dateFilters.forEach { (key, label) ->
                        val isSelected = state.selectedDateFilter == key
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                if (isDark) ElectricBlue.copy(alpha = 0.25f) else AppTheme.colors.primaryAccentContainer
                            } else {
                                if (isDark) CharcoalCard else AppTheme.colors.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AppTheme.colors.primaryAccent else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                            ),
                            modifier = Modifier.clickable { viewModel.onDateFilterSelected(key) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    if (isDark) ElectricBlueLight else Color(0xFF1D4ED8)
                                } else {
                                    AppTheme.colors.textSecondary
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Date Period Navigator for Monthly / Weekly filter
                if (state.selectedDateFilter == "MONTHLY" || state.selectedDateFilter == "WEEKLY") {
                    Spacer(modifier = Modifier.height(10.dp))
                    PeriodNavigator(
                        label = state.datePeriodLabel,
                        canNavigatePrevious = state.canNavigatePreviousDate,
                        canNavigateNext = state.canNavigateNextDate,
                        onPrevious = { viewModel.previousDatePeriod() },
                        onNext = { viewModel.nextDatePeriod() },
                        showCurrentPill = state.datePeriodOffset != 0,
                        onCurrent = { viewModel.currentDatePeriod() },
                        currentPillText = if (state.selectedDateFilter == "WEEKLY") "This Week" else "This Month",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentDescriptionPrefix = if (state.selectedDateFilter == "WEEKLY") "week" else "month"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Transactions List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.dateGroups.isEmpty()) {
                        item(key = "empty_state") {
                            MascotEmptyState(
                                pose = MascotPose.THINKING,
                                title = "No transactions found",
                                subtitle = if (state.searchQuery.isNotBlank()) {
                                    "No transactions match '${state.searchQuery}'. Try resetting search or filters."
                                } else {
                                    "No transactions recorded for this period yet. Tap + to add one!"
                                }
                            )
                        }
                    } else {
                        state.dateGroups.forEachIndexed { groupIndex, group ->
                            item(
                                key = "header_${group.dateEpochMillis}_${group.heading}",
                                contentType = "header"
                            ) {
                                TransactionDateHeader(
                                    heading = group.heading,
                                    modifier = if (groupIndex > 0) Modifier.padding(top = 8.dp) else Modifier
                                )
                            }
                            items(
                                items = group.items,
                                key = { item ->
                                    when (item) {
                                        is TransactionListItem.Individual -> "txn_${item.transaction.id}"
                                        is TransactionListItem.TripCard -> "trip_${item.expenseGroupId}"
                                    }
                                },
                                contentType = { item ->
                                    when (item) {
                                        is TransactionListItem.Individual -> "individual"
                                        is TransactionListItem.TripCard -> "trip"
                                    }
                                }
                            ) { item ->
                                when (item) {
                                    is TransactionListItem.Individual -> {
                                        TransactionItemCard(
                                            transaction = item.transaction,
                                            onClick = { selectedTransaction = item.transaction }
                                        )
                                    }
                                    is TransactionListItem.TripCard -> {
                                        TripTransactionSummaryCard(
                                            tripCard = item,
                                            onClick = { onNavigateToTripDetail(item.expenseGroupId) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Fast Add Modal Sheet
    if (showFastAddSheet) {
        FastAddExpenseSheet(
            categories = state.categories,
            friends = state.friends,
            groups = state.friendGroups,
            expenseGroups = state.expenseGroups,
            initialDateMillis = fastAddInitialDateMillis,
            onCreateExpenseGroup = { viewModel.createExpenseGroup(it) },
            onDismiss = {
                showFastAddSheet = false
                fastAddInitialDateMillis = null
            },
            onSave = { newTxn -> viewModel.saveTransaction(newTxn) },
            onSaveSettlement = { settlement -> viewModel.recordSettlement(settlement) }
        )
    }

    // Voice Assistant Sheet
    if (showVoiceAssistant) {
        VoiceAssistantSheet(
            onDismiss = { showVoiceAssistant = false }
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
            friends = state.friends,
            groups = state.friendGroups,
            expenseGroups = state.expenseGroups,
            onCreateExpenseGroup = { viewModel.createExpenseGroup(it) },
            onDismiss = { editingTransaction = null },
            onSave = { updated -> viewModel.saveTransaction(updated) }
        )
    }
}

@Composable
fun ExpenseGroupTransactionCard(
    cluster: ExpenseGroupTransactionCluster,
    onToggleExpand: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.isDark
    val groupColor = try {
        Color(android.graphics.Color.parseColor(cluster.colorHex ?: "#3B82F6"))
    } catch (_: Exception) {
        AppTheme.colors.primaryAccent
    }

    RupeeFlowCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (cluster.isExpanded) groupColor.copy(alpha = 0.5f) else AppTheme.colors.cardBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(groupColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (cluster.groupId == null) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Default.CardTravel,
                        contentDescription = null,
                        tint = groupColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cluster.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (cluster.groupId != null) "Trip Expenses • ${cluster.dateRangeLabel}" else cluster.dateRangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (cluster.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (cluster.isExpanded) "Collapse" else "Expand",
                        tint = AppTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isDark) CharcoalCard else AppTheme.colors.surfaceElevated,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${cluster.transactionCount} ${if (cluster.transactionCount == 1) "transaction" else "transactions"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    if (!cluster.isExpanded) {
                        Text(
                            text = "Tap to view transactions",
                            style = MaterialTheme.typography.labelSmall,
                            color = groupColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Personal Expense",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(cluster.totalPersonalExpensePaise),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }
            }

            AnimatedVisibility(visible = cluster.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = AppTheme.colors.divider)
                    Spacer(modifier = Modifier.height(2.dp))
                    cluster.transactions.forEach { txn ->
                        TransactionItemCard(
                            transaction = txn,
                            onClick = { onTransactionClick(txn) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TripTransactionSummaryCard(
    tripCard: TransactionListItem.TripCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = try {
        Color(android.graphics.Color.parseColor(tripCard.colorHex ?: "#3B82F6"))
    } catch (_: Exception) {
        AppTheme.colors.primaryAccent
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppTheme.colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CardTravel,
                        contentDescription = tripCard.name,
                        tint = themeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tripCard.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    val dest = tripCard.destination
                    if (!dest.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AppTheme.colors.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = dest,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = themeColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (tripCard.transactionCount == 1) "1 transaction" else "${tripCard.transactionCount} transactions",
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp
                            )
                        }

                        if (tripCard.dateRangeLabel.isNotBlank()) {
                            Text(
                                text = "•  ${tripCard.dateRangeLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "My Spend",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(tripCard.totalPersonalExpensePaise),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (tripCard.totalPersonalExpensePaise > 0L) CoralRed else AppTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View Trip",
                    tint = AppTheme.colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionDateHeader(
    heading: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textSecondary,
            letterSpacing = 0.3.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = AppTheme.colors.divider.copy(alpha = 0.5f)
        )
    }
}
