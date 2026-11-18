package com.naman.rupeeflow.ui.screens.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.ui.components.EditTransactionDialog
import com.naman.rupeeflow.ui.components.FastAddExpenseSheet
import com.naman.rupeeflow.ui.components.TransactionDetailDialog
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel
import com.naman.rupeeflow.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: TransactionsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var showFastAddSheet by rememberSaveable { mutableStateOf(false) }
    var fastAddInitialDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        containerColor = AppTheme.colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    fastAddInitialDateMillis = state.selectedCalendarDateMillis
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header with back navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppTheme.colors.textPrimary
                    )
                }
                Text(
                    text = "Calendar View",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

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
