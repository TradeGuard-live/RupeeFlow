package com.naman.rupeeflow.ui.screens.friends

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendGroup
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.*
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onNavigateToFriendDetail: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var deletingGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var editingFriend by remember { mutableStateOf<Friend?>(null) }
    var deletingFriend by remember { mutableStateOf<Friend?>(null) }
    var settlingFriend by remember { mutableStateOf<Friend?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFriendDialog = true },
                containerColor = AppTheme.colors.primaryAccent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend", modifier = Modifier.size(26.dp))
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Friends & Ledger",
                    style = MaterialTheme.typography.headlineLarge,
                    color = AppTheme.colors.textPrimary
                )
                // Visible Add Group Action
                FilledTonalButton(
                    onClick = { showCreateGroupDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isDark) VibrantCyan.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer,
                        contentColor = if (isDark) VibrantCyan else Color(0xFF1D4ED8)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Group",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Group", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Summary Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatCard(
                    title = "They Owe Me",
                    amountFormatted = CurrencyUtils.formatPaiseToRupees(state.totalTheyOweMePaise),
                    accentColor = if (isDark) EmeraldMint else Color(0xFF059669),
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    title = "I Owe Them",
                    amountFormatted = CurrencyUtils.formatPaiseToRupees(state.totalIOweThemPaise),
                    accentColor = if (isDark) CoralRed else Color(0xFFDC2626),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Groups Section Header & Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Groups",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.groups.isNotEmpty()) {
                    Text(
                        text = "${state.groups.size} groups",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Horizontally Scrollable Group Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAllSelected = state.selectedGroupId == null
                val activeCount = state.friends.count { !it.isArchived }

                // "All Friends" filter chip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isAllSelected) {
                        if (isDark) ElectricBlue.copy(alpha = 0.25f) else AppTheme.colors.primaryAccentContainer
                    } else {
                        if (isDark) CharcoalCard else AppTheme.colors.surface
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isAllSelected) AppTheme.colors.primaryAccent else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                    ),
                    modifier = Modifier.clickable { viewModel.selectGroup(null) }
                ) {
                    Text(
                        text = "All Friends ($activeCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAllSelected) {
                            if (isDark) ElectricBlueLight else Color(0xFF1D4ED8)
                        } else {
                            AppTheme.colors.textSecondary
                        },
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                // Custom Group Chips
                state.groups.forEach { grp ->
                    val isGrpSelected = state.selectedGroupId == grp.id
                    val grpColor = if (!grp.colorHex.isNullOrBlank()) {
                        try {
                            Color(android.graphics.Color.parseColor(grp.colorHex))
                        } catch (_: Exception) {
                            if (isDark) VibrantCyan else Color(0xFF0891B2)
                        }
                    } else (if (isDark) VibrantCyan else Color(0xFF0891B2))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isGrpSelected) {
                            if (isDark) grpColor.copy(alpha = 0.25f) else grpColor.copy(alpha = 0.15f)
                        } else {
                            if (isDark) CharcoalCard else AppTheme.colors.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isGrpSelected) grpColor else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                        ),
                        modifier = Modifier.clickable { viewModel.selectGroup(grp.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GroupAvatar(
                                group = grp,
                                modifier = Modifier.size(20.dp),
                                fallbackColor = grpColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${grp.name} (${grp.memberCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isGrpSelected) grpColor else AppTheme.colors.textSecondary,
                                fontWeight = if (isGrpSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            IconButton(
                                onClick = { editingGroup = grp },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit ${grp.name}",
                                    tint = if (isGrpSelected) grpColor else AppTheme.colors.textTertiary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search friends by name or phone...", color = AppTheme.colors.textTertiary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.textSecondary)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Tabs (Balance status)
            val filters = listOf(
                "ALL" to "All",
                "THEY_OWE" to "They Owe Me",
                "I_OWE" to "I Owe Them",
                "SETTLED" to "Settled",
                "ARCHIVED" to "Archived"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { (key, label) ->
                    val isSelected = state.filterStatus == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) {
                            if (isDark) ElectricBlue.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer
                        } else {
                            if (isDark) CharcoalCard else AppTheme.colors.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AppTheme.colors.primaryAccent else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                        ),
                        modifier = Modifier.clickable { viewModel.onFilterStatusSelected(key) }
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Group Banner indicator (if a group filter is active)
            val activeGroup = state.groups.firstOrNull { it.id == state.selectedGroupId }
            if (activeGroup != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtered by Group: ${activeGroup.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) VibrantCyan else Color(0xFF0891B2),
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { editingGroup = activeGroup },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Manage Group", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.primaryAccent)
                    }
                }
            }

            // Friends List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.filteredFriends.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.selectedGroupId != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No members in this group yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap 'Manage Group' to add friends to this group.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Text(
                                    text = "No friends found. Tap + to add a friend!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(state.filteredFriends, key = { it.id }) { friend ->
                        FriendItemCard(
                            friend = friend,
                            onClick = { onNavigateToFriendDetail(friend.id) },
                            onEdit = { editingFriend = friend },
                            onToggleArchive = { viewModel.toggleArchive(friend) },
                            onDelete = { deletingFriend = friend }
                        )
                    }
                }
            }
        }
    }

    // Add Friend Dialog
    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = { showAddFriendDialog = false },
            onAdd = { name, phone, email, notes, profileImagePath ->
                viewModel.addFriend(name, phone, email, notes, profileImagePath)
                showAddFriendDialog = false
            }
        )
    }

    // Edit Friend Dialog
    editingFriend?.let { friend ->
        EditFriendDialog(
            friend = friend,
            allFriends = state.friends,
            onDismiss = { editingFriend = null },
            onSave = { updated ->
                viewModel.updateFriend(updated)
                editingFriend = null
            }
        )
    }

    // Create Group Dialog
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            allFriends = state.friends.filter { !it.isArchived },
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, description, icon, color, friendIds, groupImagePath ->
                viewModel.createGroup(name, description, icon, color, friendIds, groupImagePath)
                showCreateGroupDialog = false
            }
        )
    }

    // Edit Group Dialog
    editingGroup?.let { group ->
        EditGroupDialog(
            group = group,
            allFriends = state.friends.filter { !it.isArchived },
            onDismiss = { editingGroup = null },
            onSave = { updatedGroup, friendIds ->
                viewModel.updateGroup(updatedGroup, friendIds)
                editingGroup = null
            },
            onDelete = {
                editingGroup = null
                deletingGroup = group
            }
        )
    }

    // Delete Group Confirmation Dialog
    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("Delete Group?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Delete \"${group.name}\"? Your friends and existing transactions will not be deleted.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup(group.id)
                        deletingGroup = null
                    }
                ) {
                    Text("Delete Group", color = AppTheme.colors.error, fontWeight = FontWeight.Bold)
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

    // Delete Friend Confirmation Dialog
    deletingFriend?.let { friend ->
        AlertDialog(
            onDismissRequest = { deletingFriend = null },
            title = { Text("Delete Friend?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete ${friend.name}?\n\nIf this friend has existing transactions or settlements, their historical records will be preserved and they will be safely archived.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFriend(friend)
                        deletingFriend = null
                    }
                ) {
                    Text("Delete / Archive", color = AppTheme.colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFriend = null }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Settle Up Dialog
    settlingFriend?.let { friend ->
        SettleUpDialog(
            friend = friend,
            onDismiss = { settlingFriend = null },
            onConfirm = { settlement ->
                viewModel.recordSettlement(settlement)
            }
        )
    }
}

@Composable
fun AddFriendDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String?, email: String?, notes: String?, profileImagePath: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var profileImagePath by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = ImageStorageManager.saveProfileImage(context, uri)
                if (path != null) {
                    profileImagePath = path
                }
            }
        }
    }

    val isDark = AppTheme.isDark
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
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
                    Text(
                        text = "Add Friend",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Profile Image Picker
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    val bitmap = rememberBitmapFromPath(profileImagePath)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isDark) ElectricBlue.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isDark) ElectricBlueLight else AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.primaryAccent)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Upload Photo",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friend Name *", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Ronit, Manali", color = AppTheme.colors.textTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. +91 98765 43210", color = AppTheme.colors.textTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)", color = AppTheme.colors.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onAdd(name, phone, email, notes, profileImagePath)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primaryAccent,
                        contentColor = Color.White
                    ),
                    enabled = name.isNotBlank()
                ) {
                    Text(
                        text = "Add Friend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Dialog to create a new Friend Group
@Composable
fun CreateGroupDialog(
    allFriends: List<Friend>,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, icon: String?, color: String?, friendIds: List<Long>, groupImagePath: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var friendSearchQuery by remember { mutableStateOf("") }
    var selectedFriendIds by remember { mutableStateOf(setOf<Long>()) }
    val colors = listOf("#06B6D4", "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899")
    var selectedColorHex by remember { mutableStateOf(colors.first()) }
    var groupImagePath by remember { mutableStateOf<String?>(null) }

    val filteredFriends = remember(allFriends, friendSearchQuery) {
        if (friendSearchQuery.isBlank()) allFriends
        else allFriends.filter {
            it.name.contains(friendSearchQuery, ignoreCase = true) ||
                    (it.phoneNumber?.contains(friendSearchQuery, ignoreCase = true) == true)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = ImageStorageManager.saveGroupImage(context, uri)
                if (path != null) {
                    groupImagePath = path
                }
            }
        }
    }

    val isDark = AppTheme.isDark
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Create Friend Group",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Group Profile Photo Picker
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    val bitmap = rememberBitmapFromPath(groupImagePath)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Group Picture",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isDark) VibrantCyan.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = if (isDark) VibrantCyan else AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.primaryAccent)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Upload Photo",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name *", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Haveli, Sarangpur, Flatmates", color = AppTheme.colors.textTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupDescription,
                    onValueChange = { groupDescription = it },
                    label = { Text("Description (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Shared ledger with close friends", color = AppTheme.colors.textTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Color selector
                Text("Group Color", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colors.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = selectedColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Friends
                OutlinedTextField(
                    value = friendSearchQuery,
                    onValueChange = { friendSearchQuery = it },
                    placeholder = { Text("Search friends...", color = AppTheme.colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary) },
                    trailingIcon = {
                        if (friendSearchQuery.isNotBlank()) {
                            IconButton(onClick = { friendSearchQuery = "" }) {
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

                Spacer(modifier = Modifier.height(8.dp))

                // Select Members Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Members (${selectedFriendIds.size} selected)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    if (filteredFriends.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val currentFilteredIds = filteredFriends.map { it.id }.toSet()
                                selectedFriendIds = if (selectedFriendIds.containsAll(currentFilteredIds)) {
                                    selectedFriendIds - currentFilteredIds
                                } else {
                                    selectedFriendIds + currentFilteredIds
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            val currentFilteredIds = filteredFriends.map { it.id }.toSet()
                            val allFilteredSelected = selectedFriendIds.containsAll(currentFilteredIds)
                            Text(
                                text = if (allFilteredSelected) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.primaryAccent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Members List with checkboxes
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredFriends.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (friendSearchQuery.isNotBlank()) "No matching friends found." else "No friends available to add.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    } else {
                        filteredFriends.forEach { friend ->
                            val isChecked = selectedFriendIds.contains(friend.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedFriendIds = if (isChecked) {
                                            selectedFriendIds - friend.id
                                        } else {
                                            selectedFriendIds + friend.id
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedFriendIds = if (checked) {
                                            selectedFriendIds + friend.id
                                        } else {
                                            selectedFriendIds - friend.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AppTheme.colors.primaryAccent,
                                        uncheckedColor = AppTheme.colors.textSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(friend.name, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.titleSmall)
                    }

                    Button(
                        onClick = {
                            if (groupName.isNotBlank()) {
                                onCreate(
                                    groupName,
                                    groupDescription.ifBlank { null },
                                    "group",
                                    selectedColorHex,
                                    selectedFriendIds.toList(),
                                    groupImagePath
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        ),
                        enabled = groupName.isNotBlank()
                    ) {
                        Text("Create Group", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// Dialog to edit an existing Friend Group or delete it
@Composable
fun EditGroupDialog(
    group: FriendGroup,
    allFriends: List<Friend>,
    onDismiss: () -> Unit,
    onSave: (group: FriendGroup, friendIds: List<Long>) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var groupName by remember { mutableStateOf(group.name) }
    var groupDescription by remember { mutableStateOf(group.description ?: "") }
    var friendSearchQuery by remember { mutableStateOf("") }
    var selectedFriendIds by remember { mutableStateOf(group.memberFriendIds.toSet()) }
    var groupImagePath by remember { mutableStateOf(group.groupImagePath) }

    val filteredFriends = remember(allFriends, friendSearchQuery) {
        if (friendSearchQuery.isBlank()) allFriends
        else allFriends.filter {
            it.name.contains(friendSearchQuery, ignoreCase = true) ||
                    (it.phoneNumber?.contains(friendSearchQuery, ignoreCase = true) == true)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = ImageStorageManager.saveGroupImage(context, uri)
                if (path != null) {
                    groupImagePath = path
                }
            }
        }
    }

    val isDark = AppTheme.isDark
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Friend Group",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Group Profile Photo Picker
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    val bitmap = rememberBitmapFromPath(groupImagePath)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Group Picture",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isDark) VibrantCyan.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = if (isDark) VibrantCyan else AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.primaryAccent)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Upload Photo",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name *", color = AppTheme.colors.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupDescription,
                    onValueChange = { groupDescription = it },
                    label = { Text("Description (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Friends split ledger", color = AppTheme.colors.textTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Search Friends within Group
                OutlinedTextField(
                    value = friendSearchQuery,
                    onValueChange = { friendSearchQuery = it },
                    placeholder = { Text("Search friends...", color = AppTheme.colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary) },
                    trailingIcon = {
                        if (friendSearchQuery.isNotBlank()) {
                            IconButton(onClick = { friendSearchQuery = "" }) {
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

                Spacer(modifier = Modifier.height(8.dp))

                // Select Members Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Group Members (${selectedFriendIds.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    if (filteredFriends.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val currentFilteredIds = filteredFriends.map { it.id }.toSet()
                                selectedFriendIds = if (selectedFriendIds.containsAll(currentFilteredIds)) {
                                    selectedFriendIds - currentFilteredIds
                                } else {
                                    selectedFriendIds + currentFilteredIds
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            val currentFilteredIds = filteredFriends.map { it.id }.toSet()
                            val allFilteredSelected = selectedFriendIds.containsAll(currentFilteredIds)
                            Text(
                                text = if (allFilteredSelected) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.primaryAccent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Members List with checkboxes
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredFriends.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (friendSearchQuery.isNotBlank()) "No matching friends found." else "No friends available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    } else {
                        filteredFriends.forEach { friend ->
                            val isChecked = selectedFriendIds.contains(friend.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedFriendIds = if (isChecked) {
                                            selectedFriendIds - friend.id
                                        } else {
                                            selectedFriendIds + friend.id
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedFriendIds = if (checked) {
                                            selectedFriendIds + friend.id
                                        } else {
                                            selectedFriendIds - friend.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AppTheme.colors.primaryAccent,
                                        uncheckedColor = AppTheme.colors.textSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(friend.name, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Cancel, Save Changes & Delete Group
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Cancel", color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.titleSmall)
                        }

                        Button(
                            onClick = {
                                if (groupName.isNotBlank()) {
                                    onSave(
                                        group.copy(
                                            name = groupName.trim(),
                                            description = groupDescription.trim().ifBlank { null },
                                            groupImagePath = groupImagePath
                                        ),
                                        selectedFriendIds.toList()
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primaryAccent,
                                contentColor = Color.White
                            ),
                            enabled = groupName.isNotBlank()
                        ) {
                            Text(
                                "Save Changes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDark) CoralRed else Color(0xFFDC2626)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) CoralRed else Color(0xFFFCA5A5)
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Group", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}
