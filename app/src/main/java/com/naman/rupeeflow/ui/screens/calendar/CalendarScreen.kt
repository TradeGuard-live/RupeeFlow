package com.naman.rupeeflow.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel
import com.naman.rupeeflow.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: TransactionsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // Current displayed calendar year & month (1-based month)
    var calendarMonth by remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        mutableStateOf(cal)
    }

    // Selected date in millis (defaults to start of today)
    var selectedDateMillis by remember {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        mutableStateOf(today.timeInMillis)
    }

    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Compute month label: e.g. "November 2026"
    val monthTitle = remember(calendarMonth.timeInMillis) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        sdf.format(calendarMonth.time)
    }

    // Today's date components for highlighting
    val todayCal = remember { Calendar.getInstance() }
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    val currentYear = calendarMonth.get(Calendar.YEAR)
    val currentMonth = calendarMonth.get(Calendar.MONTH)
    val daysInMonth = calendarMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Monday-first: Monday is 1, Sunday is 7
    val firstDayOfWeek = remember(calendarMonth.timeInMillis) {
        val cal = calendarMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, ..., Saturday=7
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        // Convert to Monday=0, Tuesday=1, ..., Sunday=6
        (dow + 5) % 7
    }

    // Map dayOfMonth -> total spending paise for the current month
    val spendingPerDay = remember(state.allTransactions, calendarMonth.timeInMillis) {
        val map = mutableMapOf<Int, Long>()
        val cal = Calendar.getInstance()
        for (txn in state.allTransactions) {
            if (txn.isIgnored) continue
            cal.timeInMillis = txn.transactionDate
            if (cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth) {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                if (txn.transactionType == TransactionType.EXPENSE) {
                    val share = PersonalFinancialAggregator.calculatePersonalExpense(txn)
                    map[day] = (map[day] ?: 0L) + share
                }
            }
        }
        map
    }

    // Transactions for the selected date
    val dayTransactions = remember(selectedDateMillis, state.allTransactions) {
        val selCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val selYear = selCal.get(Calendar.YEAR)
        val selMonth = selCal.get(Calendar.MONTH)
        val selDay = selCal.get(Calendar.DAY_OF_MONTH)

        val cal = Calendar.getInstance()
        state.allTransactions.filter { txn ->
            cal.timeInMillis = txn.transactionDate
            cal.get(Calendar.YEAR) == selYear &&
                    cal.get(Calendar.MONTH) == selMonth &&
                    cal.get(Calendar.DAY_OF_MONTH) == selDay
        }
    }

    val selectedDayTotalExpense = remember(dayTransactions) {
        dayTransactions.filter { !it.isIgnored && it.transactionType == TransactionType.EXPENSE }
            .sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }
    }

    val selectedDateLabel = remember(selectedDateMillis) {
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        sdf.format(Date(selectedDateMillis))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Calendar",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.surface
                )
            )
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month navigation card
            item {
                RupeeFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            val monthTotal = spendingPerDay.values.sum()
                            Text(
                                text = "Total spent: ${CurrencyUtils.formatPaiseToRupees(monthTotal)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // "Today" button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AppTheme.colors.primaryAccentContainer,
                                modifier = Modifier.clickable {
                                    val now = Calendar.getInstance()
                                    val resetCal = now.clone() as Calendar
                                    resetCal.set(Calendar.DAY_OF_MONTH, 1)
                                    resetCal.set(Calendar.HOUR_OF_DAY, 0)
                                    resetCal.set(Calendar.MINUTE, 0)
                                    resetCal.set(Calendar.SECOND, 0)
                                    resetCal.set(Calendar.MILLISECOND, 0)
                                    calendarMonth = resetCal

                                    val todayStart = now.clone() as Calendar
                                    todayStart.set(Calendar.HOUR_OF_DAY, 0)
                                    todayStart.set(Calendar.MINUTE, 0)
                                    todayStart.set(Calendar.SECOND, 0)
                                    todayStart.set(Calendar.MILLISECOND, 0)
                                    selectedDateMillis = todayStart.timeInMillis
                                }
                            ) {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.primaryAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    val prev = calendarMonth.clone() as Calendar
                                    prev.add(Calendar.MONTH, -1)
                                    calendarMonth = prev
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Month",
                                    tint = AppTheme.colors.primaryAccent
                                )
                            }

                            IconButton(
                                onClick = {
                                    val next = calendarMonth.clone() as Calendar
                                    next.add(Calendar.MONTH, 1)
                                    calendarMonth = next
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Month",
                                    tint = AppTheme.colors.primaryAccent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Monday-First Day Header: Mon, Tue, Wed, Thu, Fri, Sat, Sun
                    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (d in dayNames) {
                            Text(
                                text = d,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Calendar Grid (weeks)
                    val totalSlots = firstDayOfWeek + daysInMonth
                    val totalRows = (totalSlots + 6) / 7

                    for (row in 0 until totalRows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0..6) {
                                val slotIndex = row * 7 + col
                                val dayNum = slotIndex - firstDayOfWeek + 1

                                if (dayNum in 1..daysInMonth) {
                                    // Check if this day is selected
                                    val cellCal = calendarMonth.clone() as Calendar
                                    cellCal.set(Calendar.DAY_OF_MONTH, dayNum)
                                    val cellMillis = cellCal.timeInMillis

                                    val selCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                                    val isSelected = selCal.get(Calendar.YEAR) == currentYear &&
                                            selCal.get(Calendar.MONTH) == currentMonth &&
                                            selCal.get(Calendar.DAY_OF_MONTH) == dayNum

                                    val isToday = todayYear == currentYear &&
                                            todayMonth == currentMonth &&
                                            todayDay == dayNum

                                    val daySpent = spendingPerDay[dayNum] ?: 0L
                                    val hasExpense = daySpent > 0L

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when {
                                                    isSelected -> AppTheme.colors.primaryAccent
                                                    isToday -> AppTheme.colors.primaryAccentContainer.copy(alpha = 0.5f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(
                                                        1.dp,
                                                        AppTheme.colors.primaryAccent,
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                } else Modifier
                                            )
                                            .clickable {
                                                selectedDateMillis = cellMillis
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> Color.White
                                                    isToday -> AppTheme.colors.primaryAccent
                                                    else -> AppTheme.colors.textPrimary
                                                },
                                                fontSize = 13.sp
                                            )

                                            if (hasExpense) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) Color.White
                                                            else AppTheme.colors.primaryAccent
                                                        )
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Empty slot
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Selected Day Summary Card
            item {
                RupeeFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = AppTheme.colors.surfaceSecondary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedDateLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "${dayTransactions.size} transactions recorded",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        Text(
                            text = CurrencyUtils.formatPaiseToRupees(selectedDayTotalExpense),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primaryAccent
                        )
                    }
                }
            }

            // Transactions on selected date
            if (dayTransactions.isEmpty()) {
                item {
                    MascotEmptyState(
                        pose = MascotPose.SLEEPING,
                        title = "No transactions on this day",
                        subtitle = "Your wallet took a nice cozy nap! Tap + to record an expense."
                    )
                }
            } else {
                items(dayTransactions, key = { it.id }) { txn ->
                    TransactionItemCard(
                        transaction = txn,
                        onClick = { selectedTransaction = txn }
                    )
                }
            }
        }
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
