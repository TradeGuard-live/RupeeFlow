package com.naman.rupeeflow.ui.screens.savings

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryViewMode {
    LIST,
    CALENDAR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalDetailScreen(
    goalId: Long,
    viewModel: SavingsGoalDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val goal = state.goal

    var showAddSavingsDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<SavingsEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<SavingsEntry?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(HistoryViewMode.LIST) }

    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val goalColor = remember(goal?.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(goal?.colorHex ?: "#FFAD91")) }
            .getOrDefault(Color(0xFFFFAD91))
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = goal?.name ?: "Goal Detail",
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Goal Options",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Goal") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showOptionsMenu = false
                                showEditGoalDialog = true
                            }
                        )
                        if (goal?.status == SavingsGoalStatus.ACTIVE) {
                            DropdownMenuItem(
                                text = { Text("Pause Goal") },
                                leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.setGoalStatus(SavingsGoalStatus.PAUSED)
                                }
                            )
                        } else if (goal?.status == SavingsGoalStatus.PAUSED) {
                            DropdownMenuItem(
                                text = { Text("Resume Goal") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.setGoalStatus(SavingsGoalStatus.ACTIVE)
                                }
                            )
                        }
                        if (goal?.status != SavingsGoalStatus.ARCHIVED) {
                            DropdownMenuItem(
                                text = { Text("Archive Goal") },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.setGoalStatus(SavingsGoalStatus.ARCHIVED)
                                }
                            )
                        }
                        if (goal?.status != SavingsGoalStatus.COMPLETED) {
                            DropdownMenuItem(
                                text = { Text("Mark as Completed") },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.setGoalStatus(SavingsGoalStatus.COMPLETED)
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Goal", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                            onClick = {
                                showOptionsMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.surface,
                    titleContentColor = AppTheme.colors.textPrimary
                )
            )
        }
    ) { paddingValues ->
        if (goal == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppTheme.colors.deepPeach)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Goal Hero Header Card
                item {
                    GoalDetailHeaderCard(
                        goal = goal,
                        goalColor = goalColor,
                        onAddSavings = { showAddSavingsDialog = true },
                        onWithdraw = { showWithdrawDialog = true }
                    )
                }

                // Celebration Banner (when goal reaches target)
                if (state.isCelebrationVisible) {
                    item {
                        GoalCelebrationBanner(
                            onDismiss = { viewModel.dismissCelebration() }
                        )
                    }
                }

                // Cumulative Totals Chips Row
                item {
                    CumulativeTotalsRow(
                        dailyTotal = state.dailyTotalPaise,
                        weeklyTotal = state.weeklyTotalPaise,
                        monthlyTotal = state.monthlyTotalPaise,
                        allTimeTotal = state.allTimeTotalPaise
                    )
                }

                // Monthly Contribution Summary Card
                if (state.monthlyContributions.isNotEmpty()) {
                    item {
                        MonthlyContributionsCard(
                            contributions = state.monthlyContributions,
                            accentColor = goalColor
                        )
                    }
                }

                // Section Title & View Switcher (List vs Calendar)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SAVINGS HISTORY",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )

                        // Mode Switcher Tabs
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppTheme.colors.surfaceSecondary,
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                FilterChip(
                                    selected = viewMode == HistoryViewMode.LIST,
                                    onClick = { viewMode = HistoryViewMode.LIST },
                                    label = { Text("List") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppTheme.colors.deepPeach,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = viewMode == HistoryViewMode.CALENDAR,
                                    onClick = { viewMode = HistoryViewMode.CALENDAR },
                                    label = { Text("Calendar") },
                                    leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppTheme.colors.deepPeach,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Mode-specific Content
                if (viewMode == HistoryViewMode.LIST) {
                    // Search & Date Filter Bar
                    item {
                        HistoryFilterBar(
                            searchQuery = state.searchQuery,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            startDate = state.startDateFilter,
                            endDate = state.endDateFilter,
                            onDateRangeChange = { start, end -> viewModel.setDateFilter(start, end) }
                        )
                    }

                    // Grouped Daily Entries
                    val groups = state.filteredDailyGroups
                    if (groups.isEmpty()) {
                        item {
                            EmptyHistoryPlaceholder(
                                isFiltered = state.searchQuery.isNotBlank() || state.startDateFilter != null,
                                onAddSavings = { showAddSavingsDialog = true }
                            )
                        }
                    } else {
                        items(
                            items = groups,
                            key = { it.dateMillis }
                        ) { group ->
                            DailySavingsGroupCard(
                                group = group,
                                onEditEntry = { editingEntry = it },
                                onDeleteEntry = { entryToDelete = it }
                            )
                        }
                    }
                } else {
                    // Calendar Interactive View
                    item {
                        SavingsCalendarView(
                            entries = state.entries,
                            selectedDateMillis = state.selectedCalendarDate,
                            onDateSelected = { viewModel.selectCalendarDate(it) },
                            accentColor = goalColor
                        )
                    }

                    // Selected Date Savings Detail
                    item {
                        SelectedCalendarDayDetailCard(
                            selectedDateMillis = state.selectedCalendarDate,
                            entries = state.selectedCalendarEntries,
                            totalPaise = state.selectedCalendarTotalPaise,
                            onEditEntry = { editingEntry = it },
                            onDeleteEntry = { entryToDelete = it },
                            onAddForDate = { showAddSavingsDialog = true }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }

    // Dialogs
    if (showAddSavingsDialog && goal != null) {
        AddSavingsDialog(
            goalName = goal.name,
            onDismiss = { showAddSavingsDialog = false },
            onConfirm = { amount, date, desc, paymentMethod, note ->
                viewModel.addDeposit(
                    amountPaise = amount,
                    date = date,
                    description = desc,
                    paymentMethod = paymentMethod,
                    note = note,
                    onSuccess = { showAddSavingsDialog = false }
                )
            }
        )
    }

    if (showWithdrawDialog && goal != null) {
        WithdrawSavingsDialog(
            goalName = goal.name,
            availableBalancePaise = goal.currentSavedPaise,
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amount, date, desc, paymentMethod, note ->
                viewModel.withdraw(
                    amountPaise = amount,
                    date = date,
                    description = desc,
                    paymentMethod = paymentMethod,
                    note = note,
                    onSuccess = { showWithdrawDialog = false }
                )
            }
        )
    }

    if (showEditGoalDialog && goal != null) {
        CreateEditSavingsGoalDialog(
            goalToEdit = goal,
            onDismiss = { showEditGoalDialog = false },
            onSave = { name, target, _, targetDate, cat, desc, col, priority ->
                viewModel.updateGoal(
                    goal.copy(
                        name = name,
                        targetAmountPaise = target,
                        targetDate = targetDate,
                        category = cat,
                        description = desc,
                        colorHex = col,
                        priority = priority
                    ),
                    onSuccess = { showEditGoalDialog = false }
                )
            }
        )
    }

    if (editingEntry != null) {
        EditSavingsEntryDialog(
            entry = editingEntry!!,
            onDismiss = { editingEntry = null },
            onConfirm = { updated ->
                viewModel.updateEntry(updated, onSuccess = { editingEntry = null })
            }
        )
    }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Saving Record?") },
            text = {
                Text(
                    "Are you sure you want to delete this saving entry of ${CurrencyUtils.formatPaiseToRupees(entryToDelete!!.amountPaise)}? " +
                            "Your goal balance will be recalculated automatically."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEntry(entryToDelete!!.id)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog && goal != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Savings Goal?") },
            text = {
                Text(
                    "Deleting '${goal.name}' will remove this goal and its ${state.entries.size} savings history entries from your active records.\n\n" +
                            "This action cannot be undone. To keep your history without seeing this goal, you can choose 'Archive' instead."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteGoal(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GoalDetailHeaderCard(
    goal: SavingsGoal,
    goalColor: Color,
    onAddSavings: () -> Unit,
    onWithdraw: () -> Unit
) {
    val shortDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Illustration & Category/Countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(goalColor.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = goal.category.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = goalColor,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.textPrimary
                    )

                    if (!goal.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = goal.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }

                // Large Mascot Illustration
                if (goal.isCompleted) {
                    GoalCelebrationIllustration(modifier = Modifier.size(86.dp))
                } else {
                    PiggyBankIllustration(modifier = Modifier.size(86.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Amount Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Current Saved Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(goal.currentSavedPaise),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.textPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Target Amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(goal.targetAmountPaise),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { (goal.progressPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = goalColor,
                trackColor = goalColor.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Stats: % completed, remaining, target date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", goal.progressPercentage)}% completed",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = goalColor
                )

                if (goal.remainingPaise > 0L) {
                    Text(
                        text = "${CurrencyUtils.formatPaiseToRupees(goal.remainingPaise)} remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = if (goal.isExceeded) "Exceeded by ${CurrencyUtils.formatPaiseToRupees(goal.currentSavedPaise - goal.targetAmountPaise)}!" else "Target fully achieved!",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (goal.targetDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val daysLeft = ((goal.targetDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                val targetLabel = if (daysLeft > 0) "$daysLeft days left • Target: ${shortDateFormat.format(Date(goal.targetDate))}"
                else if (daysLeft == 0) "Target date is today!"
                else "Target date passed (${shortDateFormat.format(Date(goal.targetDate))})"

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (daysLeft < 0) Color(0xFFEF4444) else AppTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = targetLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (daysLeft < 0) Color(0xFFEF4444) else AppTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Actions: Add Savings & Withdraw
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAddSavings,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = goalColor,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Savings", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onWithdraw,
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFEF4444)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Withdraw", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun GoalCelebrationBanner(
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFDCFCE7),
        border = BorderStroke(1.dp, Color(0xFF86EFAC))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GoalCelebrationIllustration(modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Congratulations! Target Achieved!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "You reached your savings goal! You can continue saving or keep this as your trophy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF15803D)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF166534))
            }
        }
    }
}

@Composable
fun CumulativeTotalsRow(
    dailyTotal: Long,
    weeklyTotal: Long,
    monthlyTotal: Long,
    allTimeTotal: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TotalStatBadge(label = "Today", amount = dailyTotal)
        TotalStatBadge(label = "This Week", amount = weeklyTotal)
        TotalStatBadge(label = "This Month", amount = monthlyTotal)
        TotalStatBadge(label = "All Time", amount = allTimeTotal)
    }
}

@Composable
fun TotalStatBadge(
    label: String,
    amount: Long
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppTheme.colors.surfaceSecondary,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = CurrencyUtils.formatPaiseToRupees(amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (amount >= 0L) AppTheme.colors.textPrimary else Color(0xFFEF4444)
            )
        }
    }
}

@Composable
fun MonthlyContributionsCard(
    contributions: List<MonthlyContribution>,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Monthly Contribution Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            contributions.take(6).forEach { mc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mc.monthYearLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.colors.textPrimary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+${CurrencyUtils.formatPaiseToRupees(mc.depositAmountPaise)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF16A34A)
                        )
                        if (mc.withdrawalAmountPaise > 0L) {
                            Text(
                                text = "-${CurrencyUtils.formatPaiseToRupees(mc.withdrawalAmountPaise)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFDC2626)
                            )
                        }
                        Text(
                            text = CurrencyUtils.formatPaiseToRupees(mc.netAmountPaise),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                }
                HorizontalDivider(color = AppTheme.colors.cardBorder.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun HistoryFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    startDate: Long?,
    endDate: Long?,
    onDateRangeChange: (Long?, Long?) -> Unit
) {
    val context = LocalContext.current
    val shortDateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search by description or source...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = AppTheme.colors.textSecondary)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.colors.deepPeach,
                unfocusedBorderColor = AppTheme.colors.cardBorder
            )
        )

        // Date Range Quick Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAll = startDate == null && endDate == null
            FilterChip(
                selected = isAll,
                onClick = { onDateRangeChange(null, null) },
                label = { Text("All Time") },
                shape = RoundedCornerShape(10.dp)
            )

            // This Month
            FilterChip(
                selected = false,
                onClick = {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    val end = cal.timeInMillis
                    onDateRangeChange(start, end)
                },
                label = { Text("This Month") },
                shape = RoundedCornerShape(10.dp)
            )

            // Last 30 Days
            FilterChip(
                selected = false,
                onClick = {
                    val now = System.currentTimeMillis()
                    val thirtyDaysAgo = now - (30L * 24L * 60L * 60L * 1000L)
                    onDateRangeChange(thirtyDaysAgo, now)
                },
                label = { Text("Last 30 Days") },
                shape = RoundedCornerShape(10.dp)
            )

            // Custom Range Picker Chip
            val customLabel = if (startDate != null && endDate != null) {
                "${shortDateFormat.format(Date(startDate))} - ${shortDateFormat.format(Date(endDate))}"
            } else "Custom Range"

            AssistChip(
                onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val s = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }.timeInMillis
                            DatePickerDialog(
                                context,
                                { _, y2, m2, d2 ->
                                    val e = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59) }.timeInMillis
                                    onDateRangeChange(s, e)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                label = { Text(customLabel) },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
fun DailySavingsGroupCard(
    group: DailySavingsGroup,
    onEditEntry: (SavingsEntry) -> Unit,
    onDeleteEntry: (SavingsEntry) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Date Header with Daily Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = AppTheme.colors.deepPeach,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateFormat.format(Date(group.dateMillis)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (group.totalSavedPaise >= 0L) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                ) {
                    Text(
                        text = "Total saved: ${CurrencyUtils.formatPaiseToRupees(group.totalSavedPaise)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (group.totalSavedPaise >= 0L) Color(0xFF16A34A) else Color(0xFFDC2626),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = AppTheme.colors.cardBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Individual Records beneath header
            group.entries.forEach { entry ->
                SavingsEntryRow(
                    entry = entry,
                    onEdit = { onEditEntry(entry) },
                    onDelete = { onDeleteEntry(entry) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun SavingsEntryRow(
    entry: SavingsEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDeposit = entry.entryType == SavingsEntryType.DEPOSIT
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppTheme.colors.surfaceSecondary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isDeposit) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDeposit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isDeposit) Color(0xFF16A34A) else Color(0xFFDC2626),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeFormat.format(Date(entry.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        if (!entry.paymentMethod.isNullOrBlank()) {
                            Text(
                                text = " • ${entry.paymentMethod}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.deepPeach
                            )
                        }
                    }

                    if (!entry.note.isNullOrBlank()) {
                        Text(
                            text = entry.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isDeposit) "+${CurrencyUtils.formatPaiseToRupees(entry.amountPaise)}"
                    else "-${CurrencyUtils.formatPaiseToRupees(entry.amountPaise)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDeposit) Color(0xFF16A34A) else Color(0xFFDC2626)
                )

                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit entry",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete entry",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SavingsCalendarView(
    entries: List<SavingsEntry>,
    selectedDateMillis: Long?,
    onDateSelected: (Long) -> Unit,
    accentColor: Color
) {
    var calendarMonthOffset by remember { mutableStateOf(0) }
    val currentMonthCal = remember(calendarMonthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, calendarMonthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    val monthNameFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    // Map days in this month to total saved amount
    val daysWithSavings = remember(entries, calendarMonthOffset) {
        val map = mutableMapOf<Int, Long>()
        val cal = Calendar.getInstance()
        val targetMonth = currentMonthCal.get(Calendar.MONTH)
        val targetYear = currentMonthCal.get(Calendar.YEAR)

        for (e in entries) {
            cal.timeInMillis = e.entryDate
            if (cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear) {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val current = map[day] ?: 0L
                val delta = if (e.entryType == SavingsEntryType.DEPOSIT) e.amountPaise else -e.amountPaise
                map[day] = current + delta
            }
        }
        map
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Month Header with Nav Arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { calendarMonthOffset-- }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }

                Text(
                    text = monthNameFormat.format(currentMonthCal.time),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )

                IconButton(onClick = { calendarMonthOffset++ }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Day of Week Labels
            val weekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                weekDays.forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid
            val firstDayOfWeek = currentMonthCal.get(Calendar.DAY_OF_WEEK) - 1 // 0-indexed
            val daysInMonth = currentMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

            for (week in 0 until (totalCells / 7)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (dayIndex in 0 until 7) {
                        val cellNumber = week * 7 + dayIndex
                        val dayOfMonth = cellNumber - firstDayOfWeek + 1

                        if (dayOfMonth in 1..daysInMonth) {
                            val cellCal = (currentMonthCal.clone() as Calendar).apply {
                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            }
                            val cellMillis = cellCal.timeInMillis
                            val isSelected = selectedDateMillis != null && isSameDay(selectedDateMillis, cellMillis)
                            val hasSaving = daysWithSavings.containsKey(dayOfMonth)
                            val daySavingAmount = daysWithSavings[dayOfMonth] ?: 0L

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) AppTheme.colors.deepPeach
                                        else if (hasSaving) AppTheme.colors.mint.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .clickable { onDateSelected(cellMillis) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$dayOfMonth",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (hasSaving || isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else AppTheme.colors.textPrimary
                                    )
                                    if (hasSaving) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else AppTheme.colors.deepPeach)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedCalendarDayDetailCard(
    selectedDateMillis: Long?,
    entries: List<SavingsEntry>,
    totalPaise: Long,
    onEditEntry: (SavingsEntry) -> Unit,
    onDeleteEntry: (SavingsEntry) -> Unit,
    onAddForDate: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (selectedDateMillis == null) {
                Text(
                    text = "Tap any date on the calendar to view its exact recorded savings history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = dateFormat.format(Date(selectedDateMillis)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = if (entries.isNotEmpty()) "Total saved on this date: ${CurrencyUtils.formatPaiseToRupees(totalPaise)}"
                            else "No savings recorded on this date",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (totalPaise > 0L) Color(0xFF16A34A) else AppTheme.colors.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onAddForDate,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.deepPeach)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (entries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = AppTheme.colors.cardBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    entries.forEach { entry ->
                        SavingsEntryRow(
                            entry = entry,
                            onEdit = { onEditEntry(entry) },
                            onDelete = { onDeleteEntry(entry) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryPlaceholder(
    isFiltered: Boolean,
    onAddSavings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptySavingsHistoryIllustration(modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isFiltered) "No matching savings records found" else "No savings recorded yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isFiltered) "Try adjusting your search query or date range filters."
                else "Add your first saving entry to start building up this goal!",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (!isFiltered) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAddSavings,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.deepPeach)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add First Saving")
                }
            }
        }
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}
