package com.naman.rupeeflow.ui.screens.splitpro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.GroupActivityType
import com.naman.rupeeflow.domain.model.GroupType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitProScreen(
    viewModel: SplitProViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGroupDetail: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToReportGenerator: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showUpiSettlementDialog by remember { mutableStateOf(false) }
    var selectedGroupForExpense by remember { mutableStateOf<ExpenseGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Split Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VibrantCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = VibrantCyan,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToReportGenerator) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Report", tint = AppTheme.colors.primaryAccent)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AppTheme.colors.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = AppTheme.colors.primaryAccent,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create Group", fontWeight = FontWeight.Bold) },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Metric Overview Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // You are owed Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = AppTheme.colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "You are owed",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatPaiseToRupees(state.totalOthersOweMePaise),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldMint
                            )
                        }
                    }

                    // You owe Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = AppTheme.colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "You owe",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatPaiseToRupees(state.totalIOweOthersPaise),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CoralRed
                            )
                        }
                    }
                }
            }

            // Summary row: Total groups, Total expenses, Upcoming transfers
            item {
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Groups", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                            Text("${state.totalGroupsCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                        }
                        Divider(modifier = Modifier.height(28.dp).width(1.dp), color = AppTheme.colors.divider)
                        Column {
                            Text("Total Spending", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                            Text(CurrencyUtils.formatPaiseToRupees(state.totalGroupExpensesPaise), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                        }
                        Divider(modifier = Modifier.height(28.dp).width(1.dp), color = AppTheme.colors.divider)
                        Column(
                            modifier = Modifier.clickable { showUpiSettlementDialog = true }
                        ) {
                            Text("Settlements Due", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                            Text("${state.upcomingSettlementsCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AmberGold)
                        }
                    }
                }
            }

            // 2. Action Buttons Row: Create Group, Add Expense, Pay with UPI, View Archive
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Create", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            if (state.groups.isNotEmpty()) {
                                selectedGroupForExpense = state.groups.firstOrNull { !it.isArchived } ?: state.groups.first()
                                showAddExpenseDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = state.groups.isNotEmpty()
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Expense", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { showUpiSettlementDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = EmeraldMint.copy(alpha = 0.18f),
                            contentColor = EmeraldMint
                        )
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Pay with UPI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.setShowArchived(!state.showArchived) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (state.showArchived) Icons.Default.VisibilityOff else Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (state.showArchived) "Active" else "Archive", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. Search Bar
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search groups by name, description...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary) },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = rupeeFlowTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // 4. Group Type Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedGroupType == null,
                            onClick = { viewModel.setFilterGroupType(null) },
                            label = { Text("All Types") }
                        )
                    }
                    items(GroupType.values()) { type ->
                        FilterChip(
                            selected = state.selectedGroupType == type,
                            onClick = { viewModel.setFilterGroupType(type) },
                            label = { Text(type.displayName) }
                        )
                    }
                }
            }

            // 5. Groups Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.showArchived) "Archived Groups (${state.filteredGroups.size})" else "Active Groups (${state.filteredGroups.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }
            }

            // 6. Groups List
            if (state.filteredGroups.isEmpty()) {
                item {
                    MascotEmptyState(
                        pose = MascotPose.FRIENDLY,
                        title = "No groups yet",
                        subtitle = if (state.searchQuery.isNotBlank()) {
                            "No groups match your search query."
                        } else {
                            "Create a group to easily split bills with friends and simplify debts!"
                        },
                        actionLabel = "Create Group",
                        onAction = { showCreateGroupDialog = true }
                    )
                }
            } else {
                items(state.filteredGroups, key = { "group_${it.id}" }) { group ->
                    val summary = state.groupSummaries[group.id]
                    val myNet = summary?.myNetBalancePaise ?: 0L

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToGroupDetail(group.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = AppTheme.colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                    ) {
                        val badgeBg = when {
                            myNet > 0L -> EmeraldMint.copy(alpha = 0.15f)
                            myNet < 0L -> CoralRed.copy(alpha = 0.15f)
                            else -> AppTheme.colors.surface
                        }
                        val badgeColor = when {
                            myNet > 0L -> EmeraldMint
                            myNet < 0L -> CoralRed
                            else -> AppTheme.colors.textSecondary
                        }
                        val badgeText = when {
                            myNet > 0L -> "+${CurrencyUtils.formatPaiseToRupees(myNet)}"
                            myNet < 0L -> "-${CurrencyUtils.formatPaiseToRupees(SafeMath.safeAbs(myNet))}"
                            else -> "Settled"
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    // Group Icon / Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AppTheme.colors.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val icon = when (group.groupType) {
                                            GroupType.TRIP -> Icons.Default.Flight
                                            GroupType.HOME -> Icons.Default.Home
                                            GroupType.FRIENDS -> Icons.Default.Group
                                            GroupType.COUPLE -> Icons.Default.Favorite
                                            GroupType.FAMILY -> Icons.Default.FamilyRestroom
                                            GroupType.OFFICE -> Icons.Default.Work
                                            GroupType.CUSTOM -> Icons.Default.Dashboard
                                        }
                                        Icon(icon, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = group.groupType.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.primaryAccent,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            val groupSummary = state.groupSummaries[group.id]
                                            val activeCount = if (groupSummary != null && groupSummary.memberBalances.isNotEmpty()) {
                                                groupSummary.memberBalances.size
                                            } else {
                                                (group.memberCount + 1).coerceAtLeast(1)
                                            }
                                            Text(
                                                text = if (activeCount == 1) " • 1 member" else " • $activeCount members",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = AppTheme.colors.divider.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total: ${CurrencyUtils.formatPaiseToRupees(summary?.totalExpensePaise ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = if (myNet > 0L) "you are owed" else if (myNet < 0L) "you owe" else "settled",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeColor
                                )
                            }
                        }
                    }
                }
            }

            // 7. Recent Group Activity Section
            if (state.recentActivities.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recent Group Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }

                items(state.recentActivities.take(6), key = { "act_${it.id}_${it.timestamp}_${it.hashCode()}" }) { act ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                            Icon(icon, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = act.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = DateTimeUtils.formatDate(act.timestamp, "dd MMM, hh:mm a"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            if (act.amountPaise != null && act.amountPaise > 0L) {
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(act.amountPaise),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create Split Group
    if (showCreateGroupDialog) {
        CreateSplitGroupDialog(
            friends = state.friends,
            onDismiss = { showCreateGroupDialog = false },
            onQuickAddFriend = { name, cb ->
                viewModel.quickAddFriend(name, onAdded = cb)
            },
            onConfirm = { name, type, desc, cover, color, friendIds ->
                viewModel.createGroup(
                    name = name,
                    type = type,
                    description = desc,
                    coverImageUri = cover,
                    colorHex = color,
                    friendIds = friendIds,
                    onCreated = { newGroupId ->
                        showCreateGroupDialog = false
                        onNavigateToGroupDetail(newGroupId)
                    }
                )
            }
        )
    }

    // Unified Add Transaction Sheet
    if (showAddExpenseDialog && selectedGroupForExpense != null) {
        FastAddExpenseSheet(
            categories = state.categories,
            friends = state.friends,
            groups = emptyList(),
            expenseGroups = state.groups,
            initialExpenseGroupId = selectedGroupForExpense!!.id,
            lockToExpenseGroup = true,
            onDismiss = {
                showAddExpenseDialog = false
                selectedGroupForExpense = null
            },
            onPayWithUpi = { targetFriend, amountPaise ->
                showAddExpenseDialog = false
                selectedGroupForExpense = null
                showUpiSettlementDialog = true
            },
            onSave = { newTxn ->
                viewModel.saveGroupExpense(newTxn)
                showAddExpenseDialog = false
                selectedGroupForExpense = null
            },
            onSearchMerchantCategory = { merchant -> viewModel.getSuggestedCategoryForMerchant(merchant) }
        )
    }

    // Direct UPI Settlement Dialog
    if (showUpiSettlementDialog) {
        UpiSettlementDialog(
            availableFriends = state.friends,
            upiSettlementManager = viewModel.upiSettlementManager,
            onFriendUpdated = { updated -> viewModel.updateFriend(updated) },
            onDismiss = { showUpiSettlementDialog = false },
            onSettlementCompleted = {
                showUpiSettlementDialog = false
            }
        )
    }
}

