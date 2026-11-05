package com.naman.rupeeflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendGroup
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.theme.*

/**
 * Reusable Group-First Selection Component for both:
 * 1. "Split With Friends" (multi-select members with live participant count & share)
 * 2. "Paid By" (single payer select: Me or a specific friend)
 */
@Composable
fun GroupFirstFriendSelector(
    groups: List<FriendGroup>,
    allFriends: List<Friend>,
    selectedGroupId: Long?,
    onGroupSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    // Multi-select mode parameters (Split with friends)
    isMultiSelect: Boolean = false,
    selectedFriendIds: Set<Long> = emptySet(),
    onSelectionChange: ((Set<Long>) -> Unit)? = null,
    includeMe: Boolean = true,
    onIncludeMeChange: ((Boolean) -> Unit)? = null,
    totalPaise: Long = 0L,
    // Single-select mode parameters (Paid By)
    selectedPayerId: Long? = null,
    onPayerSelect: ((Long?) -> Unit)? = null
) {
    var isViewingAllFriends by remember { mutableStateOf(selectedGroupId == null) }

    // Sync viewing state when selectedGroupId changes
    LaunchedEffect(selectedGroupId) {
        if (selectedGroupId == null) {
            isViewingAllFriends = true
        }
    }

    val activeGroup = remember(selectedGroupId, groups) {
        groups.firstOrNull { it.id == selectedGroupId }
    }

    // Determine displayed friends based on current view mode
    val displayedFriends = remember(isViewingAllFriends, activeGroup, allFriends) {
        if (!isViewingAllFriends && activeGroup != null) {
            allFriends.filter { it.id in activeGroup.memberFriendIds }
        } else {
            allFriends
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {

        // ==================== STEP 1: GROUP CARDS ROW ====================
        if (groups.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isMultiSelect) "1. Select Group or Split Members" else "Select Group or Payer",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )

                // Prominent "View All Friends" button
                TextButton(
                    onClick = {
                        isViewingAllFriends = !isViewingAllFriends
                        if (isViewingAllFriends) {
                            onGroupSelect(null)
                        } else if (groups.isNotEmpty()) {
                            onGroupSelect(groups.first().id)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isViewingAllFriends && activeGroup != null) Icons.Default.Groups else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = VibrantCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isViewingAllFriends && activeGroup != null) "Back to ${activeGroup.name}" else "View All Friends",
                        style = MaterialTheme.typography.labelSmall,
                        color = VibrantCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontally Scrollable Rich Group Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "All Friends" Card
                val isAllActive = isViewingAllFriends && selectedGroupId == null
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isAllActive) ElectricBlue.copy(alpha = 0.2f) else CharcoalCard,
                    border = BorderStroke(1.dp, if (isAllActive) ElectricBlue else GlassBorder),
                    modifier = Modifier.clickable {
                        isViewingAllFriends = true
                        onGroupSelect(null)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ElectricBlue.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = ElectricBlueLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "All Friends",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isAllActive) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isAllActive) ElectricBlueLight else TextPrimary
                            )
                            Text(
                                text = "${allFriends.size} friends",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Custom Group Cards with Picture, Name, Count & Member Avatars
                groups.forEach { group ->
                    val isSelected = !isViewingAllFriends && selectedGroupId == group.id
                    val cardBorderColor = if (isSelected) VibrantCyan else GlassBorder
                    val cardBgColor = if (isSelected) VibrantCyan.copy(alpha = 0.15f) else CharcoalCard

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, cardBorderColor),
                        modifier = Modifier.clickable {
                            isViewingAllFriends = false
                            onGroupSelect(group.id)
                            if (isMultiSelect) {
                                // Auto-select this group's members in multi-select mode
                                val memberIds = group.memberFriendIds.filter { mId ->
                                    allFriends.any { it.id == mId }
                                }.toSet()
                                onSelectionChange?.invoke(memberIds)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Group profile picture or fallback avatar
                            GroupAvatar(
                                group = group,
                                modifier = Modifier.size(34.dp),
                                fallbackColor = VibrantCyan
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) VibrantCyan else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${group.memberCount} members",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Mini member avatars
                                    GroupMemberAvatarRow(
                                        memberIds = group.memberFriendIds,
                                        allFriends = allFriends,
                                        avatarSize = 18.dp,
                                        maxAvatars = 3
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // ==================== STEP 2: MEMBER SELECTION ====================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val scopeTitle = if (!isViewingAllFriends && activeGroup != null) {
                "${activeGroup.name} Members"
            } else {
                "All Friends (${displayedFriends.size})"
            }

            Text(
                text = scopeTitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )

            if (isMultiSelect) {
                // Quick Select / Deselect All within current view
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val allIdsInScope = displayedFriends.map { it.id }.toSet()
                            onSelectionChange?.invoke(selectedFriendIds + allIdsInScope)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Select All", style = MaterialTheme.typography.labelSmall, color = VibrantCyan)
                    }
                    TextButton(
                        onClick = {
                            val inScope = displayedFriends.map { it.id }.toSet()
                            onSelectionChange?.invoke(selectedFriendIds - inScope)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall, color = CoralRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Me (User) Row
        if (isMultiSelect && onIncludeMeChange != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (includeMe) ElectricBlue.copy(alpha = 0.15f) else CharcoalCard,
                border = BorderStroke(1.dp, if (includeMe) ElectricBlue else GlassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIncludeMeChange(!includeMe) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ME", style = MaterialTheme.typography.labelSmall, color = ElectricBlueLight, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Me (You)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (includeMe) "Your share counts in your spending" else "Excluded (others owe full amount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (includeMe) ElectricBlueLight else TextMuted
                        )
                    }
                    Checkbox(
                        checked = includeMe,
                        onCheckedChange = { onIncludeMeChange(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ElectricBlue,
                            uncheckedColor = TextSecondary
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Single-select Me (Default Payer) Row
        if (!isMultiSelect && onPayerSelect != null) {
            val isMePayer = selectedPayerId == null
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMePayer) ElectricBlue.copy(alpha = 0.15f) else CharcoalCard,
                border = BorderStroke(1.dp, if (isMePayer) ElectricBlue else GlassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPayerSelect(null) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ME", style = MaterialTheme.typography.labelSmall, color = ElectricBlueLight, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Me (Default Payer)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "You paid for this expense",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    RadioButton(
                        selected = isMePayer,
                        onClick = { onPayerSelect(null) },
                        colors = RadioButtonDefaults.colors(selectedColor = ElectricBlue)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Member List in Current Scope
        if (displayedFriends.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (activeGroup != null && !isViewingAllFriends) {
                        "No friends assigned to this group yet."
                    } else {
                        "No friends added yet. Add friends from the Friends tab."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                displayedFriends.forEach { friend ->
                    val isSelected = if (isMultiSelect) {
                        selectedFriendIds.contains(friend.id)
                    } else {
                        selectedPayerId == friend.id
                    }

                    val rowBgColor = if (isSelected) {
                        if (isMultiSelect) VibrantCyan.copy(alpha = 0.12f) else ElectricBlue.copy(alpha = 0.15f)
                    } else CharcoalCard

                    val rowBorderColor = if (isSelected) {
                        if (isMultiSelect) VibrantCyan.copy(alpha = 0.6f) else ElectricBlue
                    } else GlassBorder

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = rowBgColor,
                        border = BorderStroke(1.dp, rowBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isMultiSelect) {
                                    val newSet = if (isSelected) {
                                        selectedFriendIds - friend.id
                                    } else {
                                        selectedFriendIds + friend.id
                                    }
                                    onSelectionChange?.invoke(newSet)
                                } else {
                                    onPayerSelect?.invoke(friend.id)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Friend profile picture or initials
                            FriendAvatar(
                                friend = friend,
                                modifier = Modifier.size(36.dp),
                                fallbackColor = if (isSelected) VibrantCyan else ElectricBlue
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friend.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = TextPrimary
                                )
                                if (!friend.phoneNumber.isNullOrBlank()) {
                                    Text(
                                        text = friend.phoneNumber,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (isMultiSelect) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        val newSet = if (checked) {
                                            selectedFriendIds + friend.id
                                        } else {
                                            selectedFriendIds - friend.id
                                        }
                                        onSelectionChange?.invoke(newSet)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = VibrantCyan,
                                        uncheckedColor = TextSecondary
                                    )
                                )
                            } else {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onPayerSelect?.invoke(friend.id) },
                                    colors = RadioButtonDefaults.colors(selectedColor = ElectricBlue)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== STEP 4: PARTICIPANT COUNTER & SHARE SUMMARY ====================
        if (isMultiSelect) {
            val totalParticipants = (if (includeMe) 1 else 0) + selectedFriendIds.size

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CharcoalCardHover,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Selected Participants",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (totalParticipants == 0) {
                                "None selected"
                            } else if (includeMe) {
                                "$totalParticipants ($totalParticipants - Me + ${selectedFriendIds.size} friends)"
                            } else {
                                "$totalParticipants friends"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = VibrantCyan
                        )
                    }

                    if (totalPaise > 0L && totalParticipants > 0) {
                        val equalShares = remember(totalPaise, totalParticipants) {
                            LedgerCalculator.calculateEqualSplit(totalPaise, totalParticipants)
                        }
                        val perPersonPaise = equalShares.firstOrNull() ?: 0L

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Share per person",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = CurrencyUtils.formatPaiseToRupees(perPersonPaise),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldMint
                            )
                        }
                    }
                }
            }
        }
    }
}

