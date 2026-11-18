package com.naman.rupeeflow.ui.screens.savings

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
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(
    viewModel: SavingsGoalsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGoalDetail: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var quickAddGoalTarget by remember { mutableStateOf<SavingsGoal?>(null) }

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Savings Goals",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
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
                    IconButton(onClick = { showCreateGoalDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "New Goal",
                            tint = AppTheme.colors.deepPeach
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.surface,
                    titleContentColor = AppTheme.colors.textPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateGoalDialog = true },
                containerColor = AppTheme.colors.deepPeach,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Goal")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Goal", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Overview Card (Cute Peachy Banner)
            item {
                SavingsHeroSummaryCard(
                    summary = state.overviewSummary
                )
            }

            // Status Filter Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SavingsGoalStatus.values().forEach { tab ->
                        val isSelected = state.selectedStatusTab == tab
                        val count = when (tab) {
                            SavingsGoalStatus.ACTIVE -> state.activeGoals.size
                            SavingsGoalStatus.COMPLETED -> state.completedGoals.size
                            SavingsGoalStatus.PAUSED -> state.pausedGoals.size
                            SavingsGoalStatus.ARCHIVED -> state.archivedGoals.size
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tab) },
                            label = { Text("${tab.displayName} ($count)") },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppTheme.colors.deepPeach,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Goals List
            if (state.displayedGoals.isEmpty()) {
                item {
                    EmptySavingsGoalsCard(
                        status = state.selectedStatusTab,
                        onCreateClick = { showCreateGoalDialog = true }
                    )
                }
            } else {
                items(
                    items = state.displayedGoals,
                    key = { it.id }
                ) { goal ->
                    SavingsGoalItemCard(
                        goal = goal,
                        onClick = { onNavigateToGoalDetail(goal.id) },
                        onQuickAddClick = { quickAddGoalTarget = goal }
                    )
                }
            }

            // Recent Savings Activity Section
            if (state.recentEntries.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Savings Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(
                    items = state.recentEntries,
                    key = { "recent_${it.id}" }
                ) { entry ->
                    RecentSavingsActivityRow(entry = entry)
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }

    if (showCreateGoalDialog) {
        CreateEditSavingsGoalDialog(
            onDismiss = { showCreateGoalDialog = false },
            onSave = { name, target, initial, targetDate, cat, desc, col, priority ->
                viewModel.createGoal(
                    name = name,
                    targetAmountPaise = target,
                    initialSavedAmountPaise = initial,
                    targetDate = targetDate,
                    category = cat,
                    description = desc,
                    colorHex = col,
                    priority = priority,
                    onSuccess = { showCreateGoalDialog = false }
                )
            }
        )
    }

    if (quickAddGoalTarget != null) {
        val target = quickAddGoalTarget!!
        AddSavingsDialog(
            goalName = target.name,
            onDismiss = { quickAddGoalTarget = null },
            onConfirm = { amount, date, desc, paymentMethod, note ->
                viewModel.addDeposit(
                    goalId = target.id,
                    amountPaise = amount,
                    entryDate = date,
                    description = desc,
                    paymentMethod = paymentMethod,
                    note = note,
                    onSuccess = { quickAddGoalTarget = null }
                )
            }
        )
    }
}

@Composable
fun SavingsHeroSummaryCard(
    summary: SavingsOverviewSummary
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Active Savings",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(summary.totalSavedPaise),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+${CurrencyUtils.formatPaiseToRupees(summary.savingsAddedThisMonthPaise)} added this month",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Cute Bunny Illustration
                BunnySavingCoinsIllustration(
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Overall Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overall Goal Progress",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", summary.overallProgressPercentage)}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.deepPeach
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { (summary.overallProgressPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AppTheme.colors.deepPeach,
                trackColor = AppTheme.colors.primaryAccentContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = AppTheme.colors.surfaceSecondary,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Active Goals",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Text(
                            text = "${summary.activeGoalsCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = AppTheme.colors.surfaceSecondary,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Text(
                            text = "${summary.completedGoalsCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavingsGoalItemCard(
    goal: SavingsGoal,
    onClick: () -> Unit,
    onQuickAddClick: () -> Unit
) {
    val goalColor = runCatching { Color(android.graphics.Color.parseColor(goal.colorHex)) }
        .getOrDefault(AppTheme.colors.deepPeach)

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Category Badge, Name, Status/Priority
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(goalColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (goal.category) {
                            SavingsGoalCategory.EMERGENCY_FUND -> Icons.Default.Shield
                            SavingsGoalCategory.ELECTRONICS -> Icons.Default.Laptop
                            SavingsGoalCategory.TRAVEL -> Icons.Default.Flight
                            SavingsGoalCategory.EDUCATION -> Icons.Default.School
                            SavingsGoalCategory.VEHICLE -> Icons.Default.DirectionsCar
                            SavingsGoalCategory.SHOPPING -> Icons.Default.ShoppingBag
                            SavingsGoalCategory.PERSONAL -> Icons.Default.Person
                            SavingsGoalCategory.CUSTOM -> Icons.Default.Star
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = goal.category.displayName,
                            tint = goalColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = goal.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }

                // Priority / Status Badge
                if (goal.isCompleted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDCFCE7))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "COMPLETED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            fontSize = 10.sp
                        )
                    }
                } else if (goal.priority == SavingsGoalPriority.HIGH) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEE2E2))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "HIGH PRIORITY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Amount Row: Saved / Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(goal.currentSavedPaise),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Target",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(goal.targetAmountPaise),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { (goal.progressPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = goalColor,
                trackColor = goalColor.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Percentage, Remaining & Target Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", goal.progressPercentage)}% completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = goalColor,
                    fontWeight = FontWeight.Bold
                )

                if (goal.remainingPaise > 0L) {
                    Text(
                        text = "${CurrencyUtils.formatPaiseToRupees(goal.remainingPaise)} remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                } else {
                    Text(
                        text = "Target reached!",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (goal.targetDate != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val daysLeft = ((goal.targetDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                    val targetLabel = if (daysLeft > 0) "$daysLeft days left (${dateFormat.format(Date(goal.targetDate))})"
                    else if (daysLeft == 0) "Target is today!"
                    else "Passed target date (${dateFormat.format(Date(goal.targetDate))})"

                    Text(
                        text = targetLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (daysLeft < 0) Color(0xFFEF4444) else AppTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySavingsGoalsCard(
    status: SavingsGoalStatus,
    onCreateClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptySavingsHistoryIllustration(
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = when (status) {
                    SavingsGoalStatus.ACTIVE -> "No active savings goals yet"
                    SavingsGoalStatus.COMPLETED -> "No completed goals yet"
                    SavingsGoalStatus.PAUSED -> "No paused goals"
                    SavingsGoalStatus.ARCHIVED -> "No archived goals"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Set a target for a trip, laptop, emergency fund or shopping, and track every rupee saved!",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (status == SavingsGoalStatus.ACTIVE) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onCreateClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.deepPeach,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Your First Goal", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RecentSavingsActivityRow(
    entry: SavingsEntry
) {
    val isDeposit = entry.entryType == SavingsEntryType.DEPOSIT
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppTheme.colors.surface,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDeposit) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDeposit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isDeposit) Color(0xFF16A34A) else Color(0xFFDC2626),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateFormat.format(Date(entry.entryDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            Text(
                text = if (isDeposit) "+${CurrencyUtils.formatPaiseToRupees(entry.amountPaise)}"
                else "-${CurrencyUtils.formatPaiseToRupees(entry.amountPaise)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDeposit) Color(0xFF16A34A) else Color(0xFFDC2626)
            )
        }
    }
}
