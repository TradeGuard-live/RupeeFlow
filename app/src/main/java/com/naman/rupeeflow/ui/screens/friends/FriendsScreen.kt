package com.naman.rupeeflow.ui.screens.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.components.FriendItemCard
import com.naman.rupeeflow.ui.components.MiniStatCard
import com.naman.rupeeflow.ui.components.SettleUpDialog
import com.naman.rupeeflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onNavigateToFriendDetail: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var showAddFriendDialog by remember { mutableStateOf(false) }
    var settlingFriend by remember { mutableStateOf<Friend?>(null) }

    Scaffold(
        containerColor = MidnightDark,
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Friends & Ledger",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
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

            Spacer(modifier = Modifier.height(14.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Tabs
            val filters = listOf(
                "ALL" to "All",
                "THEY_OWE" to "They Owe Me",
                "I_OWE" to "I Owe Them",
                "SETTLED" to "Settled"
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

            Spacer(modifier = Modifier.height(10.dp))

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
                            Text(
                                text = "No friends found. Tap + to add a friend!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    items(state.filteredFriends, key = { it.id }) { friend ->
                        FriendItemCard(
                            friend = friend,
                            onClick = { onNavigateToFriendDetail(friend.id) }
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
            onAdd = { name, phone, email, notes ->
                viewModel.addFriend(name, phone, email, notes)
                showAddFriendDialog = false
            }
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
    onAdd: (name: String, phone: String?, email: String?, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

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

                Spacer(modifier = Modifier.height(16.dp))

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
                            onAdd(name, phone, email, notes)
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
