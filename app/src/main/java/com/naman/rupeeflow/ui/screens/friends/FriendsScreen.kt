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
    var deletingFriend by remember { mutableStateOf<Friend?>(null) }
    var settlingFriend by remember { mutableStateOf<Friend?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFriendDialog = true },
                containerColor = ElectricBlue,
                contentColor = MidnightDark,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend", modifier = Modifier.size(26.dp))
            }
        }
    ) { paddingValues ->
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
                    color = TextPrimary
                )
                // Visible Add Group Action
                FilledTonalButton(
                    onClick = { showCreateGroupDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VibrantCyan.copy(alpha = 0.2f),
                        contentColor = VibrantCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Group", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Summary Stats Row (Strict Color: EmeraldMint for They Owe Me, CoralRed for I Owe Them)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatCard(
                    title = "They Owe Me",
                    amountFormatted = CurrencyUtils.formatPaiseToRupees(state.totalTheyOweMePaise),
                    accentColor = EmeraldMint,
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    title = "I Owe Them",
                    amountFormatted = CurrencyUtils.formatPaiseToRupees(state.totalIOweThemPaise),
                    accentColor = CoralRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ Groups Section Header & Filters
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
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.groups.isNotEmpty()) {
                    Text(
                        text = "${state.groups.size} groups",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
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
                    color = if (isAllSelected) ElectricBlue.copy(alpha = 0.25f) else CharcoalCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isAllSelected) ElectricBlue else GlassBorder),
                    modifier = Modifier.clickable { viewModel.selectGroup(null) }
                ) {
                    Text(
                        text = "All Friends ($activeCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAllSelected) ElectricBlueLight else TextSecondary,
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
                            VibrantCyan
                        }
                    } else VibrantCyan

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isGrpSelected) grpColor.copy(alpha = 0.25f) else CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isGrpSelected) grpColor else GlassBorder),
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
                                color = if (isGrpSelected) grpColor else TextSecondary,
                                fontWeight = if (isGrpSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            IconButton(
                                onClick = { editingGroup = grp },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit ${grp.name}",
                                    tint = if (isGrpSelected) grpColor else TextMuted,
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
                placeholder = { Text("Search friends by name or phone...", color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = CharcoalCard,
                    unfocusedContainerColor = CharcoalCard
                )
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
                        color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ElectricBlue else GlassBorder
                        ),
                        modifier = Modifier.clickable { viewModel.onFilterStatusSelected(key) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) ElectricBlueLight else TextSecondary,
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
                        color = VibrantCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { editingGroup = activeGroup },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Manage Group", style = MaterialTheme.typography.labelSmall, color = ElectricBlueLight)
                    }
                }
            }

            // Friends List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap 'Manage Group' to add friends to this group.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Text(
                                    text = "No friends found. Tap + to add a friend!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(state.filteredFriends, key = { it.id }) { friend ->
                        FriendItemCard(
                            friend = friend,
                            onClick = { onNavigateToFriendDetail(friend.id) },
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

    // Create Group Dialog
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            allFriends = state.friends.filter { !it.isArchived },
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, icon, color, friendIds, groupImagePath ->
                viewModel.createGroup(name, icon, color, friendIds, groupImagePath)
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
            title = { Text("Delete Group?", color = TextPrimary) },
            text = {
                Text(
                    "Delete \"${group.name}\"? Your friends and existing transactions will not be deleted.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup(group.id)
                        deletingGroup = null
                    }
                ) {
                    Text("Delete Group", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGroup = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete Friend Confirmation Dialog
    deletingFriend?.let { friend ->
        AlertDialog(
            onDismissRequest = { deletingFriend = null },
            title = { Text("Delete Friend?", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete ${friend.name}?\n\nIf this friend has existing transactions or settlements, their historical records will be preserved and they will be safely archived.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFriend(friend)
                        deletingFriend = null
                    }
                ) {
                    Text("Delete / Archive", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFriend = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CharcoalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
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
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
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
                                .background(ElectricBlue.copy(alpha = 0.2f))
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = ElectricBlueLight,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(VibrantCyan)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Upload Photo",
                            tint = MidnightDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friend Name *", color = TextSecondary) },
                    placeholder = { Text("e.g. Ronit, Manali", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)", color = TextSecondary) },
                    placeholder = { Text("e.g. +91 98765 43210", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
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
                        containerColor = ElectricBlue,
                        contentColor = MidnightDark
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

// ✅ Dialog to create a new Friend Group
@Composable
fun CreateGroupDialog(
    allFriends: List<Friend>,
    onDismiss: () -> Unit,
    onCreate: (name: String, icon: String?, color: String?, friendIds: List<Long>, groupImagePath: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var groupName by remember { mutableStateOf("") }
    var selectedFriendIds by remember { mutableStateOf(setOf<Long>()) }
    val colors = listOf("#06B6D4", "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899")
    var selectedColorHex by remember { mutableStateOf(colors.first()) }
    var groupImagePath by remember { mutableStateOf<String?>(null) }

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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CharcoalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
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
                        text = "Create Group",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
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
                                .background(VibrantCyan.copy(alpha = 0.2f))
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = VibrantCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(VibrantCyan)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Upload Photo",
                            tint = MidnightDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name *", color = TextSecondary) },
                    placeholder = { Text("e.g. Hostel Friends, Roommates, Travel", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VibrantCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Color selector
                Text("Group Color", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MidnightDark, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Select Members Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Friends (${selectedFriendIds.size} selected)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    if (allFriends.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedFriendIds = if (selectedFriendIds.size == allFriends.size) {
                                    emptySet()
                                } else {
                                    allFriends.map { it.id }.toSet()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (selectedFriendIds.size == allFriends.size) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelSmall,
                                color = VibrantCyan
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
                    if (allFriends.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No friends available to add.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    } else {
                        allFriends.forEach { friend ->
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
                                    colors = CheckboxDefaults.colors(checkedColor = VibrantCyan, uncheckedColor = TextSecondary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(friend.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            onCreate(groupName, "group", selectedColorHex, selectedFriendIds.toList(), groupImagePath)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan, contentColor = MidnightDark),
                    enabled = groupName.isNotBlank()
                ) {
                    Text("Create Group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ✅ Dialog to edit an existing Friend Group or delete it
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
    var selectedFriendIds by remember { mutableStateOf(group.memberFriendIds.toSet()) }
    var groupImagePath by remember { mutableStateOf(group.groupImagePath) }

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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CharcoalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
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
                        text = "Edit Group",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
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
                                .background(VibrantCyan.copy(alpha = 0.2f))
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = VibrantCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(VibrantCyan)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Upload Photo",
                            tint = MidnightDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name *", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VibrantCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Select Members Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Group Members (${selectedFriendIds.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    if (allFriends.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedFriendIds = if (selectedFriendIds.size == allFriends.size) {
                                    emptySet()
                                } else {
                                    allFriends.map { it.id }.toSet()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (selectedFriendIds.size == allFriends.size) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelSmall,
                                color = VibrantCyan
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
                    allFriends.forEach { friend ->
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
                                colors = CheckboxDefaults.colors(checkedColor = VibrantCyan, uncheckedColor = TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(friend.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Save & Delete Group
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (groupName.isNotBlank()) {
                                onSave(group.copy(name = groupName.trim(), groupImagePath = groupImagePath), selectedFriendIds.toList())
                            }
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan, contentColor = MidnightDark),
                        enabled = groupName.isNotBlank()
                    ) {
                        Text("Save Changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
