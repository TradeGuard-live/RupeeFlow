package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CardTravel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.ui.theme.AppTheme

@Composable
fun ExpenseGroupSelector(
    expenseGroups: List<ExpenseGroup>,
    selectedExpenseGroupId: Long?,
    selectedExpenseGroupName: String?,
    onSelectGroup: (ExpenseGroup?) -> Unit,
    onCreateNewGroup: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    val selectedGroup = remember(selectedExpenseGroupId, expenseGroups) {
        if (selectedExpenseGroupId != null) expenseGroups.firstOrNull { it.id == selectedExpenseGroupId } else null
    }
    val currentGroupName = remember(selectedExpenseGroupId, selectedExpenseGroupName, expenseGroups) {
        if (selectedExpenseGroupId != null) {
            selectedGroup?.name ?: selectedExpenseGroupName ?: "Trip Group"
        } else if (!selectedExpenseGroupName.isNullOrBlank()) {
            selectedExpenseGroupName
        } else {
            "No Group"
        }
    }
    val hasSelectedGroup = selectedExpenseGroupId != null || !selectedExpenseGroupName.isNullOrBlank()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Expense Group (Optional)",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedGroup != null) {
                        TripCoverAvatar(
                            group = selectedGroup,
                            modifier = Modifier.size(24.dp),
                            cornerRadius = 6.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CardTravel,
                            contentDescription = null,
                            tint = if (hasSelectedGroup) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentGroupName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (hasSelectedGroup) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (hasSelectedGroup) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            DropdownMenuItem(
                text = { Text("No Group") },
                onClick = {
                    onSelectGroup(null)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Close, contentDescription = null, tint = AppTheme.colors.textSecondary)
                }
            )

            expenseGroups.filter { !it.isArchived }.forEach { eg ->
                DropdownMenuItem(
                    text = { Text(eg.name) },
                    onClick = {
                        onSelectGroup(eg)
                        expanded = false
                    },
                    leadingIcon = {
                        TripCoverAvatar(
                            group = eg,
                            modifier = Modifier.size(24.dp),
                            cornerRadius = 6.dp
                        )
                    }
                )
            }

            HorizontalDivider(color = AppTheme.colors.divider)

            DropdownMenuItem(
                text = {
                    Text("+ Create New Group", color = AppTheme.colors.primaryAccent, fontWeight = FontWeight.Bold)
                },
                onClick = {
                    expanded = false
                    showCreateDialog = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AppTheme.colors.primaryAccent)
                }
            )
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Expense Group / Trip", color = AppTheme.colors.textPrimary) },
            text = {
                Column {
                    Text("Enter group name (e.g. Goa Trip, College Tour):", color = AppTheme.colors.textSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            onCreateNewGroup(newGroupName.trim())
                            showCreateDialog = false
                            newGroupName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primaryAccent,
                        contentColor = Color.White
                    ),
                    enabled = newGroupName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

