package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.ui.theme.AppTheme
import com.naman.rupeeflow.ui.theme.ElectricBlue
import com.naman.rupeeflow.ui.theme.ElectricBlueLight

/**
 * Dedicated single-selection Payer Dropdown for Add & Edit Transaction screens.
 *
 * Requirements:
 * 1. Default Payer = "Me" (selectedPayerId == null)
 * 2. Visibly locked and displayed: [ Paid by: Me ✓ ▼ ]
 * 3. Replaces checkboxes with a proper single-selection dropdown menu
 * 4. Shows Me and active friends (with search for large friend lists)
 * 5. Preserves existing transaction's actual historical payer when editing
 * 6. Completely separate from participant split selections
 */
@Composable
fun PayerDropdownSelector(
    friends: List<Friend>,
    selectedPayerId: Long?,
    onPayerSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val isDark = AppTheme.isDark

    // Identify selected payer friend if not Me
    val selectedFriend = remember(selectedPayerId, friends) {
        if (selectedPayerId != null) {
            friends.firstOrNull { it.id == selectedPayerId }
        } else null
    }

    // Filter active friends for selection, but retain selected historical friend even if archived
    val selectableFriends = remember(friends, searchQuery, selectedPayerId) {
        val activeOrSelected = friends.filter { !it.isArchived || it.id == selectedPayerId }
        if (searchQuery.isBlank()) {
            activeOrSelected
        } else {
            val q = searchQuery.trim().lowercase()
            activeOrSelected.filter {
                it.name.lowercase().contains(q) || (it.phoneNumber != null && it.phoneNumber.contains(q))
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Paid By",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Selected Payer Display Box (Triggers Dropdown Menu)
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (expanded) AppTheme.colors.surfaceSecondary else AppTheme.colors.cardBackground,
                border = BorderStroke(
                    1.dp,
                    if (expanded) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = enabled) {
                        searchQuery = ""
                        expanded = !expanded
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Payer Avatar / Badge
                    if (selectedPayerId == null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDark) ElectricBlue.copy(alpha = 0.25f) else Color(0xFFDBEAFE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ME",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) ElectricBlueLight else Color(0xFF1D4ED8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        val initial = selectedFriend?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "F"
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.accentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.primaryAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Payer Name Label
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (selectedPayerId == null) "Me" else (selectedFriend?.name ?: "Friend"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            if (selectedPayerId == null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppTheme.colors.incomeGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Default",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.incomeGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 10.sp
                                    )
                                }
                            } else if (selectedFriend?.isArchived == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppTheme.colors.cardBorder
                                ) {
                                    Text(
                                        text = "Archived",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (selectedPayerId == null) "You paid for this transaction" else "Friend paid on your behalf",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Checkmark indicator
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = AppTheme.colors.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Dropdown indicator
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand Payer Dropdown",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Dropdown Menu Popover
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(AppTheme.colors.surfaceElevated)
            ) {
                // Search bar for large friend lists (> 5 friends)
                if (friends.size > 5) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search friends...", color = AppTheme.colors.textMuted, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppTheme.colors.primaryAccent,
                                unfocusedBorderColor = AppTheme.colors.cardBorder,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    HorizontalDivider(color = AppTheme.colors.cardBorder, thickness = 0.5.dp)
                }

                // 1. "Me" Option (Always at the top)
                val isMeSelected = selectedPayerId == null
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) ElectricBlue.copy(alpha = 0.25f) else Color(0xFFDBEAFE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) ElectricBlueLight else Color(0xFF1D4ED8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Me",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isMeSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isMeSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Default (You paid)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            if (isMeSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = AppTheme.colors.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        onPayerSelect(null)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (isMeSelected) AppTheme.colors.accentContainer.copy(alpha = 0.4f) else Color.Transparent
                    )
                )

                HorizontalDivider(color = AppTheme.colors.cardBorder, thickness = 0.5.dp)

                // 2. Active Friends List
                if (selectableFriends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No friends matching \"$searchQuery\"" else "No active friends added yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textTertiary
                        )
                    }
                } else {
                    selectableFriends.forEach { friend ->
                        val isFriendSelected = selectedPayerId == friend.id
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val initial = friend.name.firstOrNull()?.uppercaseChar()?.toString() ?: "F"
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(if (isFriendSelected) AppTheme.colors.accentContainer else AppTheme.colors.surfaceSecondary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initial,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isFriendSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = friend.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isFriendSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isFriendSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textPrimary
                                            )
                                            if (friend.isArchived) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "(Archived)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AppTheme.colors.textMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        if (!friend.phoneNumber.isNullOrBlank()) {
                                            Text(
                                                text = friend.phoneNumber,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.textSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    if (isFriendSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = AppTheme.colors.primaryAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onPayerSelect(friend.id)
                                expanded = false
                            },
                            modifier = Modifier.background(
                                if (isFriendSelected) AppTheme.colors.accentContainer.copy(alpha = 0.4f) else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}

