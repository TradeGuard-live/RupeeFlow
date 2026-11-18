package com.naman.rupeeflow.ui.screens.splitpro

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.GroupType
import com.naman.rupeeflow.ui.components.FriendAvatar
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CreateSplitGroupDialog(
    friends: List<Friend>,
    onDismiss: () -> Unit,
    onQuickAddFriend: (String, (Long) -> Unit) -> Unit,
    onConfirm: (
        name: String,
        type: GroupType,
        description: String?,
        coverImageUri: String?,
        colorHex: String?,
        friendIds: List<Long>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groupName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(GroupType.TRIP) }
    var groupDescription by remember { mutableStateOf("") }
    var coverImageUri by remember { mutableStateOf<String?>(null) }
    val coverBitmap = com.naman.rupeeflow.ui.components.rememberBitmapFromPath(coverImageUri)
    var selectedFriendIds by remember { mutableStateOf(setOf<Long>()) }

    var showQuickAddFriend by remember { mutableStateOf(false) }
    var newFriendName by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedFilename = ImageStorageManager.saveGroupImage(context, uri)
                if (savedFilename != null) {
                    coverImageUri = savedFilename
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Create Split Pro Group",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Group Photo & Name Row
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.surface)
                                    .clickable { photoPickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverBitmap != null) {
                                    Image(
                                        bitmap = coverBitmap,
                                        contentDescription = "Group Picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Upload Group Image",
                                            tint = AppTheme.colors.primaryAccent,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "Photo",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = groupName,
                                onValueChange = { groupName = it },
                                label = { Text("Group Name *") },
                                placeholder = { Text("e.g. Manali Trip, Flat 402") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                colors = rupeeFlowTextFieldColors(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    // Group Type Selector
                    item {
                        Text(
                            text = "Group Type",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(GroupType.values()) { type ->
                                val isSelected = selectedType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedType = type },
                                    label = { Text(type.displayName) },
                                    leadingIcon = {
                                        val icon = when (type) {
                                            GroupType.TRIP -> Icons.Default.Flight
                                            GroupType.HOME -> Icons.Default.Home
                                            GroupType.FRIENDS -> Icons.Default.Group
                                            GroupType.COUPLE -> Icons.Default.Favorite
                                            GroupType.FAMILY -> Icons.Default.FamilyRestroom
                                            GroupType.OFFICE -> Icons.Default.Work
                                            GroupType.CUSTOM -> Icons.Default.Dashboard
                                        }
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppTheme.colors.primaryAccent,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = AppTheme.colors.surface,
                                        labelColor = AppTheme.colors.textSecondary
                                    )
                                )
                            }
                        }
                    }

                    // Description
                    item {
                        OutlinedTextField(
                            value = groupDescription,
                            onValueChange = { groupDescription = it },
                            label = { Text("Description (Optional)") },
                            placeholder = { Text("e.g. Shared expenses for our apartment") },
                            maxLines = 2,
                            colors = rupeeFlowTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // Members Header & Quick Add
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Members (${selectedFriendIds.size} selected)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textSecondary
                            )
                            TextButton(onClick = { showQuickAddFriend = true }) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Friend", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Quick Add Friend Form
                    if (showQuickAddFriend) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppTheme.colors.surface, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newFriendName,
                                    onValueChange = { newFriendName = it },
                                    label = { Text("Friend Name") },
                                    singleLine = true,
                                    colors = rupeeFlowTextFieldColors(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Button(
                                    onClick = {
                                        if (newFriendName.isNotBlank()) {
                                            onQuickAddFriend(newFriendName.trim()) { newId ->
                                                selectedFriendIds = selectedFriendIds + newId
                                                newFriendName = ""
                                                showQuickAddFriend = false
                                            }
                                        }
                                    },
                                    enabled = newFriendName.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }

                    // Friends List
                    if (friends.isEmpty()) {
                        item {
                            Text(
                                text = "No friends added yet. Tap \"New Friend\" to add members.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(friends, key = { "friend_${it.id}" }) { friend ->
                            val isSelected = selectedFriendIds.contains(friend.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) AppTheme.colors.primaryAccent.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        selectedFriendIds = if (isSelected) {
                                            selectedFriendIds - friend.id
                                        } else {
                                            selectedFriendIds + friend.id
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedFriendIds = if (checked) {
                                            selectedFriendIds + friend.id
                                        } else {
                                            selectedFriendIds - friend.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = AppTheme.colors.primaryAccent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FriendAvatar(friend = friend, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = friend.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    if (!friend.phoneNumber.isNullOrBlank()) {
                                        Text(
                                            text = friend.phoneNumber,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", color = AppTheme.colors.textSecondary)
                    }

                    Button(
                        onClick = {
                            if (groupName.isNotBlank()) {
                                onConfirm(
                                    groupName.trim(),
                                    selectedType,
                                    groupDescription.trim().ifBlank { null },
                                    coverImageUri,
                                    "#3B82F6",
                                    selectedFriendIds.toList()
                                )
                            }
                        },
                        enabled = groupName.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Create Group", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

