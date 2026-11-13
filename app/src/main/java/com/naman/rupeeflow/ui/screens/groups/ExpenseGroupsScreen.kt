package com.naman.rupeeflow.ui.screens.groups

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
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.components.TripCoverAvatar
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseGroupsScreen(
    viewModel: ExpenseGroupViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGroupDetail: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var showCreateWizard by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<ExpenseGroup?>(null) }
    var deletingGroup by remember { mutableStateOf<ExpenseGroup?>(null) }

    val displayedGroups = if (state.selectedTab == 0) state.activeGroups else state.archivedGroups
    val filteredGroups = remember(displayedGroups, state.searchQuery) {
        if (state.searchQuery.isBlank()) displayedGroups
        else displayedGroups.filter {
            it.name.contains(state.searchQuery, ignoreCase = true) ||
                    (it.destination?.contains(state.searchQuery, ignoreCase = true) == true) ||
                    (it.description?.contains(state.searchQuery, ignoreCase = true) == true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Expense Groups / Trips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
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
                    IconButton(onClick = { showCreateWizard = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Expense Group",
                            tint = AppTheme.colors.primaryAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
            )
        },
        containerColor = AppTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search groups & trips...", color = AppTheme.colors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = AppTheme.colors.textSecondary)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                colors = rupeeFlowTextFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // TabRow: Active vs Archived
            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = AppTheme.colors.surfaceElevated,
                contentColor = AppTheme.colors.primaryAccent,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = {
                        Text(
                            text = "Active (${state.activeGroups.size})",
                            fontWeight = if (state.selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = {
                        Text(
                            text = "Archived (${state.archivedGroups.size})",
                            fontWeight = if (state.selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CardTravel,
                            contentDescription = null,
                            tint = AppTheme.colors.textTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.selectedTab == 0) "No active expense groups" else "No archived expense groups",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create groups for trips (e.g. Goa Trip, College Tour) or events to organize your expenses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCreateWizard = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Trip / Group", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredGroups, key = { it.id }) { group ->
                        ExpenseGroupCard(
                            group = group,
                            onClick = { onNavigateToGroupDetail(group.id) },
                            onEdit = { editingGroup = group },
                            onToggleArchive = { viewModel.toggleArchive(group) },
                            onDelete = { deletingGroup = group }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Trip Creation Wizard Dialog
    if (showCreateWizard) {
        TripCreationWizardDialog(
            friends = state.friends,
            friendGroups = state.friendGroups,
            onDismiss = { showCreateWizard = false },
            onQuickAddFriend = { name, phone, email ->
                var createdId = 0L
                viewModel.quickAddFriend(name, phone, email) { id ->
                    createdId = id
                }
                createdId
            },
            onCreateTrip = { name, destination, startDate, endDate, description, coverImageUri, colorHex, friendIds ->
                viewModel.createTrip(
                    name = name,
                    destination = destination,
                    startDate = startDate,
                    endDate = endDate,
                    description = description,
                    coverImageUri = coverImageUri,
                    colorHex = colorHex,
                    friendIds = friendIds
                )
                showCreateWizard = false
            }
        )
    }

    // Edit Trip Dialog
    editingGroup?.let { group ->
        EditTripDetailsDialog(
            group = group,
            friends = state.friends,
            friendGroups = state.friendGroups,
            onDismiss = { editingGroup = null },
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
                editingGroup = null
            }
        )
    }

    // Delete Confirmation Dialog
    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("Delete Expense Group / Trip?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Delete \"${group.name}\"?\n\nDeleting this expense group will not delete any transactions, settlements, or friends from your financial ledger. All transactions and balances will be completely preserved safely.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(group.id)
                        deletingGroup = null
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
                TextButton(onClick = { deletingGroup = null }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun ExpenseGroupCard(
    group: ExpenseGroup,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    RupeeFlowCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Trip Cover Avatar (Rounded Corner Rectangular, distinct from circular friend avatars)
            TripCoverAvatar(
                group = group,
                modifier = Modifier.size(52.dp),
                cornerRadius = 14.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    if (group.isArchived) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AppTheme.colors.cardBorder
                        ) {
                            Text(
                                text = "ARCHIVED",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                if (!group.destination.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = AppTheme.colors.primaryAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = group.destination!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                            maxLines = 1
                        )
                    }
                } else if (!group.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = group.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        maxLines = 1
                    )
                }

                if (group.startDate != null || group.endDate != null) {
                    val dateStr = when {
                        group.startDate != null && group.endDate != null -> "${dateFormat.format(group.startDate!!)} - ${dateFormat.format(group.endDate!!)}"
                        group.startDate != null -> "From ${dateFormat.format(group.startDate!!)}"
                        else -> "Until ${dateFormat.format(group.endDate!!)}"
                    }
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textTertiary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${group.memberCount} members • ${group.transactionCount} transactions",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textTertiary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyUtils.formatPaiseToRupees(group.totalSpendingPaise),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )

                if (group.grossSpendingPaise > 0L && group.grossSpendingPaise != group.totalSpendingPaise) {
                    Text(
                        text = "Total: ${CurrencyUtils.formatPaiseToRupees(group.grossSpendingPaise)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Trip") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (group.isArchived) "Unarchive Trip" else "Archive Trip") },
                            onClick = {
                                showMenu = false
                                onToggleArchive()
                            },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Trip", color = CoralRed) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CoralRed) }
                        )
                    }
                }
            }
        }
    }
}
