package com.naman.rupeeflow.ui.screens.groups

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendGroup
import com.naman.rupeeflow.ui.components.FriendAvatar
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.components.rememberBitmapFromPath
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCreationWizardDialog(
    friends: List<Friend>,
    friendGroups: List<FriendGroup>,
    onDismiss: () -> Unit,
    onQuickAddFriend: suspend (name: String, phone: String?, email: String?) -> Long,
    onCreateTrip: (
        name: String,
        destination: String?,
        startDate: Long?,
        endDate: Long?,
        description: String?,
        coverImageUri: String?,
        colorHex: String?,
        friendIds: List<Long>
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(1) } // 1 = Details, 2 = Friends, 3 = Review

    // Step 1: Details
    var name by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var description by remember { mutableStateOf("") }
    var coverImageUri by remember { mutableStateOf<String?>(null) }
    val colors = listOf("#3B82F6", "#06B6D4", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899", "#EF4444")
    var selectedColor by remember { mutableStateOf(colors.first()) }

    // Step 2: Friends
    var selectedFriendIds by remember { mutableStateOf(setOf<Long>()) }
    var friendSearchQuery by remember { mutableStateOf("") }
    var showInlineAddFriendDialog by remember { mutableStateOf(false) }

    // Step 3: Double-tap guard
    var isCreating by remember { mutableStateOf(false) }

    // Photo picker for cover image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val savedPath = ImageStorageManager.saveTripCoverImage(context, uri)
                if (savedPath != null) {
                    coverImageUri = savedPath
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Bar with Close button and Step indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Create Trip / Expense Group",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = when (currentStep) {
                                1 -> "Step 1 of 3: Trip Details"
                                2 -> "Step 2 of 3: Add Friends"
                                else -> "Step 3 of 3: Review and Create"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Step Progress Indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (step in 1..3) {
                        val isActive = step == currentStep
                        val isDone = step < currentStep
                        val barColor = if (isDone || isActive) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(barColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Wizard Body
                Box(modifier = Modifier.weight(1f)) {
                    when (currentStep) {
                        1 -> {
                            Step1TripDetails(
                                name = name,
                                onNameChange = { name = it },
                                destination = destination,
                                onDestinationChange = { destination = it },
                                startDate = startDate,
                                onStartDateChange = { startDate = it },
                                endDate = endDate,
                                onEndDateChange = { endDate = it },
                                description = description,
                                onDescriptionChange = { description = it },
                                coverImageUri = coverImageUri,
                                onPickCoverImage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onRemoveCoverImage = { coverImageUri = null },
                                colors = colors,
                                selectedColor = selectedColor,
                                onSelectColor = { selectedColor = it }
                            )
                        }

                        2 -> {
                            Step2AddFriends(
                                allFriends = friends.filter { !it.isArchived },
                                friendGroups = friendGroups,
                                selectedFriendIds = selectedFriendIds,
                                onToggleFriend = { friendId ->
                                    selectedFriendIds = if (selectedFriendIds.contains(friendId)) {
                                        selectedFriendIds - friendId
                                    } else {
                                        selectedFriendIds + friendId
                                    }
                                },
                                onSelectGroupMembers = { groupFriendIds ->
                                    selectedFriendIds = selectedFriendIds + groupFriendIds
                                },
                                onSelectAll = { allIds ->
                                    selectedFriendIds = allIds.toSet()
                                },
                                onClearAll = {
                                    selectedFriendIds = emptySet()
                                },
                                searchQuery = friendSearchQuery,
                                onSearchQueryChange = { friendSearchQuery = it },
                                onOpenAddFriendDialog = { showInlineAddFriendDialog = true }
                            )
                        }

                        3 -> {
                            Step3ReviewAndCreate(
                                name = name,
                                destination = destination,
                                startDate = startDate,
                                endDate = endDate,
                                description = description,
                                coverImageUri = coverImageUri,
                                colorHex = selectedColor,
                                selectedFriends = friends.filter { selectedFriendIds.contains(it.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back", color = AppTheme.colors.textSecondary)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = AppTheme.colors.textSecondary)
                        }
                    }

                    if (currentStep < 3) {
                        Button(
                            onClick = {
                                if (currentStep == 1 && name.isNotBlank()) {
                                    currentStep = 2
                                } else if (currentStep == 2) {
                                    currentStep = 3
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primaryAccent,
                                contentColor = Color.White
                            ),
                            enabled = if (currentStep == 1) name.isNotBlank() else true
                        ) {
                            Text(if (currentStep == 1) "Next: Add Friends" else "Next: Review", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!isCreating && name.isNotBlank()) {
                                    isCreating = true
                                    onCreateTrip(
                                        name.trim(),
                                        destination.trim().ifBlank { null },
                                        startDate,
                                        endDate,
                                        description.trim().ifBlank { null },
                                        coverImageUri,
                                        selectedColor,
                                        selectedFriendIds.toList()
                                    )
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primaryAccent,
                                contentColor = Color.White
                            ),
                            enabled = !isCreating && name.isNotBlank()
                        ) {
                            Text(if (isCreating) "Creating..." else "Create Trip", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Inline Add Friend Dialog
    if (showInlineAddFriendDialog) {
        InlineAddFriendDialog(
            onDismiss = { showInlineAddFriendDialog = false },
            onAddFriendAsync = { newName, newPhone, newEmail ->
                val newId = onQuickAddFriend(newName, newPhone, newEmail)
                selectedFriendIds = selectedFriendIds + newId
                showInlineAddFriendDialog = false
            }
        )
    }
}

@Composable
private fun Step1TripDetails(
    name: String,
    onNameChange: (String) -> Unit,
    destination: String,
    onDestinationChange: (String) -> Unit,
    startDate: Long?,
    onStartDateChange: (Long?) -> Unit,
    endDate: Long?,
    onEndDateChange: (Long?) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    coverImageUri: String?,
    onPickCoverImage: () -> Unit,
    onRemoveCoverImage: () -> Unit,
    colors: List<String>,
    selectedColor: String,
    onSelectColor: (String) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cover Image Section
        Text(
            text = "Trip Cover Photo (Optional)",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )

        val coverBitmap = rememberBitmapFromPath(coverImageUri)
        val themeColor = try {
            Color(android.graphics.Color.parseColor(selectedColor))
        } catch (_: Exception) {
            AppTheme.colors.primaryAccent
        }

        if (coverBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, themeColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                androidx.compose.foundation.Image(
                    bitmap = coverBitmap,
                    contentDescription = "Cover Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPickCoverImage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.7f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = onRemoveCoverImage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CoralRed.copy(alpha = 0.85f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", fontSize = 12.sp)
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = themeColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, themeColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clickable { onPickCoverImage() }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add Cover Photo",
                        tint = themeColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose Cover Photo from Gallery",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColor
                    )
                    Text(
                        text = "Visual banner for your trip and expense group",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        }

        // Trip Name (Required)
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Trip / Expense Group Name *", color = AppTheme.colors.textSecondary) },
            placeholder = { Text("e.g. Goa Trip, College Reunion, Manali Tour") },
            leadingIcon = {
                Icon(Icons.Default.CardTravel, contentDescription = null, tint = AppTheme.colors.primaryAccent)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = rupeeFlowTextFieldColors(),
            shape = RoundedCornerShape(12.dp)
        )

        // Destination (Optional)
        OutlinedTextField(
            value = destination,
            onValueChange = onDestinationChange,
            label = { Text("Destination (Optional)", color = AppTheme.colors.textSecondary) },
            placeholder = { Text("e.g. Goa, India or Tokyo, Japan") },
            leadingIcon = {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = AppTheme.colors.textSecondary)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = rupeeFlowTextFieldColors(),
            shape = RoundedCornerShape(12.dp)
        )

        // Dates Row (Start Date & End Date)
        Text(
            text = "Trip Dates (Optional)",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Start Date
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colors.surfaceElevated,
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        val cal = Calendar.getInstance()
                        startDate?.let { cal.timeInMillis = it }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selected = Calendar.getInstance().apply {
                                    set(y, m, d, 0, 0, 0)
                                }
                                onStartDateChange(selected.timeInMillis)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = AppTheme.colors.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start Date", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                        Text(
                            text = startDate?.let { dateFormat.format(it) } ?: "Set date",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (startDate != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (startDate != null) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (startDate != null) {
                        IconButton(
                            onClick = { onStartDateChange(null) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // End Date
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colors.surfaceElevated,
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        val cal = Calendar.getInstance()
                        endDate?.let { cal.timeInMillis = it }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selected = Calendar.getInstance().apply {
                                    set(y, m, d, 23, 59, 59)
                                }
                                onEndDateChange(selected.timeInMillis)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = AppTheme.colors.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("End Date", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                        Text(
                            text = endDate?.let { dateFormat.format(it) } ?: "Set date",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (endDate != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (endDate != null) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (endDate != null) {
                        IconButton(
                            onClick = { onEndDateChange(null) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Description (Optional)
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Trip Description (Optional)", color = AppTheme.colors.textSecondary) },
            placeholder = { Text("e.g. Hotel, flight bookings, food and car rental") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = rupeeFlowTextFieldColors(),
            shape = RoundedCornerShape(12.dp)
        )

        // Color Theme
        Text(
            text = "Color Theme",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { hex ->
                val color = Color(android.graphics.Color.parseColor(hex))
                val isSelected = selectedColor == hex
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onSelectColor(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Step2AddFriends(
    allFriends: List<Friend>,
    friendGroups: List<FriendGroup>,
    selectedFriendIds: Set<Long>,
    onToggleFriend: (Long) -> Unit,
    onSelectGroupMembers: (List<Long>) -> Unit,
    onSelectAll: (List<Long>) -> Unit,
    onClearAll: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenAddFriendDialog: () -> Unit
) {
    val filteredFriends = remember(allFriends, searchQuery) {
        if (searchQuery.isBlank()) allFriends
        else allFriends.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    (it.phoneNumber?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search & Add friend inline button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search friends...", color = AppTheme.colors.textTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = rupeeFlowTextFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = onOpenAddFriendDialog,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Selection header & actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AppTheme.colors.primaryAccentContainer
            ) {
                Text(
                    text = "${selectedFriendIds.size} Members Selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.primaryAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onSelectAll(filteredFriends.map { it.id }) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Select All", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.primaryAccent)
                }

                if (selectedFriendIds.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall, color = CoralRed)
                    }
                }
            }
        }

        // Quick add from existing groups chips
        if (friendGroups.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Quick Add from Existing Group",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    friendGroups.forEach { grp ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AppTheme.colors.surfaceElevated,
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            modifier = Modifier.clickable {
                                onSelectGroupMembers(grp.memberFriendIds)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primaryAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${grp.name} (${grp.memberCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Friends List
        if (filteredFriends.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = null,
                        tint = AppTheme.colors.textTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching friends found." else "No friends saved yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenAddFriendDialog) {
                        Text("+ Add New Friend")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredFriends, key = { it.id }) { friend ->
                    val isSelected = selectedFriendIds.contains(friend.id)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AppTheme.colors.primaryAccentContainer.copy(alpha = 0.45f) else AppTheme.colors.surfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleFriend(friend.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FriendAvatar(friend = friend, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friend.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                                if (!friend.phoneNumber.isNullOrBlank()) {
                                    Text(
                                        text = friend.phoneNumber!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleFriend(friend.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AppTheme.colors.primaryAccent
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step3ReviewAndCreate(
    name: String,
    destination: String,
    startDate: Long?,
    endDate: Long?,
    description: String,
    coverImageUri: String?,
    colorHex: String,
    selectedFriends: List<Friend>
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val themeColor = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        AppTheme.colors.primaryAccent
    }
    val coverBitmap = rememberBitmapFromPath(coverImageUri)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary Trip Banner Card
        RupeeFlowCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = themeColor.copy(alpha = 0.5f)
        ) {
            if (coverBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = coverBitmap,
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CardTravel,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    if (destination.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = destination,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            if (startDate != null || endDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val datesText = when {
                    startDate != null && endDate != null -> "${dateFormat.format(startDate)} — ${dateFormat.format(endDate)}"
                    startDate != null -> "From ${dateFormat.format(startDate)}"
                    else -> "Until ${dateFormat.format(endDate!!)}"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppTheme.colors.surfaceElevated
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(datesText, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textPrimary)
                    }
                }
            }

            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary
                )
            }
        }

        // Automatic Friend Group Creation Notice Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppTheme.colors.primaryAccentContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, AppTheme.colors.primaryAccent.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = AppTheme.colors.primaryAccent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Automatic Friend Group Creation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.primaryAccent
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "A corresponding custom friend group \"$name\" with ${selectedFriends.size} member(s) will be automatically created in Friends -> Custom Groups.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textPrimary
                    )
                }
            }
        }

        // Members Summary
        Text(
            text = "Trip Members (${selectedFriends.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary
        )

        if (selectedFriends.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colors.surfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No friends added to this trip yet. You can add expenses and friends anytime after creating the trip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedFriends.forEach { friend ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppTheme.colors.surfaceElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FriendAvatar(friend = friend, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = friend.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                                if (!friend.phoneNumber.isNullOrBlank()) {
                                    Text(
                                        text = friend.phoneNumber!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InlineAddFriendDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String?, email: String?) -> Unit = { _, _, _ -> },
    onAddFriendAsync: (suspend (name: String, phone: String?, email: String?) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add New Friend",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friend Name *", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Sukrut") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. +91 98765 43210") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. sukrut@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = AppTheme.colors.textSecondary)
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && !isSaving) {
                                isSaving = true
                                if (onAddFriendAsync != null) {
                                    coroutineScope.launch {
                                        onAddFriendAsync(
                                            name.trim(),
                                            phone.trim().ifBlank { null },
                                            email.trim().ifBlank { null }
                                        )
                                    }
                                } else {
                                    onAdd(
                                        name.trim(),
                                        phone.trim().ifBlank { null },
                                        email.trim().ifBlank { null }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent),
                        enabled = name.isNotBlank() && !isSaving
                    ) {
                        Text(if (isSaving) "Saving..." else "Add Friend", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditTripDetailsDialog(
    group: ExpenseGroup,
    friends: List<Friend>,
    friendGroups: List<FriendGroup>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        destination: String?,
        startDate: Long?,
        endDate: Long?,
        description: String?,
        coverImageUri: String?,
        colorHex: String?,
        friendIds: List<Long>
    ) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var name by remember { mutableStateOf(group.name) }
    var destination by remember { mutableStateOf(group.destination ?: "") }
    var startDate by remember { mutableStateOf(group.startDate) }
    var endDate by remember { mutableStateOf(group.endDate) }
    var description by remember { mutableStateOf(group.description ?: "") }
    var coverImageUri by remember { mutableStateOf(group.coverImageUri) }
    val colors = listOf("#3B82F6", "#06B6D4", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899", "#EF4444")
    var selectedColor by remember { mutableStateOf(group.colorHex ?: colors.first()) }
    var selectedFriendIds by remember { mutableStateOf(group.memberFriendIds.toSet()) }

    val coroutineScope = rememberCoroutineScope()
    var isManagingMembers by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val savedPath = ImageStorageManager.saveTripCoverImage(context, uri)
                if (savedPath != null) {
                    coverImageUri = savedPath
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
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
                        text = if (isManagingMembers) "Manage Trip Members" else "Edit Trip",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (isManagingMembers) {
                        Step2AddFriends(
                            allFriends = friends.filter { !it.isArchived },
                            friendGroups = friendGroups,
                            selectedFriendIds = selectedFriendIds,
                            onToggleFriend = { friendId ->
                                selectedFriendIds = if (selectedFriendIds.contains(friendId)) {
                                    selectedFriendIds - friendId
                                } else {
                                    selectedFriendIds + friendId
                                }
                            },
                            onSelectGroupMembers = { groupFriendIds ->
                                selectedFriendIds = selectedFriendIds + groupFriendIds
                            },
                            onSelectAll = { allIds ->
                                selectedFriendIds = allIds.toSet()
                            },
                            onClearAll = {
                                selectedFriendIds = emptySet()
                            },
                            searchQuery = "",
                            onSearchQueryChange = {},
                            onOpenAddFriendDialog = {}
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Step1TripDetails(
                                name = name,
                                onNameChange = { name = it },
                                destination = destination,
                                onDestinationChange = { destination = it },
                                startDate = startDate,
                                onStartDateChange = { startDate = it },
                                endDate = endDate,
                                onEndDateChange = { endDate = it },
                                description = description,
                                onDescriptionChange = { description = it },
                                coverImageUri = coverImageUri,
                                onPickCoverImage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onRemoveCoverImage = { coverImageUri = null },
                                colors = colors,
                                selectedColor = selectedColor,
                                onSelectColor = { selectedColor = it }
                            )

                            // Manage Members Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceElevated,
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isManagingMembers = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Group, contentDescription = null, tint = AppTheme.colors.primaryAccent)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Trip Members", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)
                                            Text("${selectedFriendIds.size} friends in this trip", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppTheme.colors.textSecondary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isManagingMembers) {
                        Button(
                            onClick = { isManagingMembers = false },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                        ) {
                            Text("Done Managing Members", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = AppTheme.colors.textSecondary)
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSave(
                                        name.trim(),
                                        destination.trim().ifBlank { null },
                                        startDate,
                                        endDate,
                                        description.trim().ifBlank { null },
                                        coverImageUri,
                                        selectedColor,
                                        selectedFriendIds.toList()
                                    )
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1.5f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent),
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
