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
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.GroupBalanceSummary
import com.naman.rupeeflow.domain.model.GroupType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.FriendAvatar
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun EditSplitGroupDialog(
    group: ExpenseGroup,
    friends: List<Friend>,
    groupSummary: GroupBalanceSummary?,
    onDismiss: () -> Unit,
    onSave: (ExpenseGroup, List<Long>) -> Unit,
    onArchiveToggle: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groupName by remember { mutableStateOf(group.name) }
    var selectedType by remember { mutableStateOf(group.groupType) }
    var groupDescription by remember { mutableStateOf(group.description ?: "") }
    var coverImageUri by remember { mutableStateOf(group.coverImageUri) }
    val coverBitmap = com.naman.rupeeflow.ui.components.rememberBitmapFromPath(coverImageUri)
    var selectedFriendIds by remember { mutableStateOf(group.memberFriendIds.toSet()) }

    var blockedMemberName by remember { mutableStateOf<String?>(null) }
    var blockedMemberBalance by remember { mutableStateOf(0L) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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
                        text = "Edit Group",
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
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Upload Group Image",
                                        tint = AppTheme.colors.primaryAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = groupName,
                                onValueChange = { groupName = it },
                                label = { Text("Group Name *") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                colors = rupeeFlowTextFieldColors(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    // Group Type
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
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppTheme.colors.primaryAccent,
                                        selectedLabelColor = Color.White,
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
                            label = { Text("Description") },
                            maxLines = 2,
                            colors = rupeeFlowTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // Members Header
                    item {
                        Text(
                            text = "Group Members (${selectedFriendIds.size} friends)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    // Friends selection with active balance protection
                    items(friends, key = { "friend_${it.id}" }) { friend ->
                        val isSelected = selectedFriendIds.contains(friend.id)
                        val memberBalance = groupSummary?.memberBalances?.firstOrNull { it.memberId == friend.id }?.netBalancePaise ?: 0L

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppTheme.colors.primaryAccent.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable {
                                    if (isSelected) {
                                        // Guard: check if member has active balance
                                        if (memberBalance != 0L) {
                                            blockedMemberName = friend.name
                                            blockedMemberBalance = memberBalance
                                        } else {
                                            selectedFriendIds = selectedFriendIds - friend.id
                                        }
                                    } else {
                                        selectedFriendIds = selectedFriendIds + friend.id
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (!checked && memberBalance != 0L) {
                                        blockedMemberName = friend.name
                                        blockedMemberBalance = memberBalance
                                    } else {
                                        selectedFriendIds = if (checked) {
                                            selectedFriendIds + friend.id
                                        } else {
                                            selectedFriendIds - friend.id
                                        }
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AppTheme.colors.primaryAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FriendAvatar(friend = friend, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friend.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = AppTheme.colors.textPrimary
                                )
                                if (memberBalance != 0L) {
                                    val balStr = CurrencyUtils.formatPaiseToRupees(kotlin.math.abs(memberBalance))
                                    val status = if (memberBalance > 0L) "Is owed $balStr" else "Owes $balStr"
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (memberBalance > 0L) EmeraldMint else CoralRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Archive & Delete Section
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onArchiveToggle()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (group.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (group.isArchived) "Unarchive" else "Archive")
                            }

                            OutlinedButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete")
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
                                onSave(
                                    group.copy(
                                        name = groupName.trim(),
                                        groupType = selectedType,
                                        description = groupDescription.trim().ifBlank { null },
                                        coverImageUri = coverImageUri
                                    ),
                                    selectedFriendIds.toList()
                                )
                                onDismiss()
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
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Guard Alert: Member cannot be removed with active balance
    blockedMemberName?.let { memberName ->
        AlertDialog(
            onDismissRequest = { blockedMemberName = null },
            title = { Text("Cannot Remove Member", color = AppTheme.colors.textPrimary) },
            text = {
                val amtStr = CurrencyUtils.formatPaiseToRupees(kotlin.math.abs(blockedMemberBalance))
                val reason = if (blockedMemberBalance > 0L) {
                    "$memberName is owed $amtStr in this group."
                } else {
                    "$memberName owes $amtStr in this group."
                }
                Text(
                    text = "$reason\n\nYou cannot remove a member while they have an unsettled balance. Please settle up all outstanding balances before removing them.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { blockedMemberName = null }) {
                    Text("OK", color = AppTheme.colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Safe Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Group?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    text = "Deleting \"${group.name}\" will remove the group container. All historical financial transactions, receipts, and settlements will remain safely preserved in your personal ledger.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteGroup()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete Group", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

