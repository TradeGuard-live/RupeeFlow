package com.naman.rupeeflow.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Polished dialog to edit an existing Friend record in RupeeFlow.
 *
 * Prepopulates current name, phone, email, notes, and profile image.
 * Validates inputs (non-empty name, phone format, duplicate detection against other friends),
 * and preserves the immutable friend ID, createdAt timestamp, and historical ledger references.
 */
@Composable
fun EditFriendDialog(
    friend: Friend,
    allFriends: List<Friend> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Friend) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = AppTheme.isDark

    var name by remember { mutableStateOf(friend.name) }
    var phone by remember { mutableStateOf(friend.phoneNumber ?: "") }
    var email by remember { mutableStateOf(friend.email ?: "") }
    var notes by remember { mutableStateOf(friend.notes ?: "") }
    var profileImagePath by remember { mutableStateOf(friend.profileImagePath) }
    var isSaving by remember { mutableStateOf(false) }

    // Validation error states
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = ImageStorageManager.saveProfileImage(context, uri)
                if (path != null) {
                    profileImagePath = path
                }
            }
        }
    }

    fun validate(): Boolean {
        var isValid = true
        val trimmedName = name.trim()

        // 1. Name validation
        if (trimmedName.isBlank()) {
            nameError = "Friend name cannot be empty"
            isValid = false
        } else {
            // Duplicate check against other friends
            val isDuplicate = allFriends.any {
                it.id != friend.id && it.name.trim().equals(trimmedName, ignoreCase = true)
            }
            if (isDuplicate) {
                nameError = "A friend named '$trimmedName' already exists"
                isValid = false
            } else {
                nameError = null
            }
        }

        // 2. Phone validation (optional field, but if present must be 7-15 digits)
        val trimmedPhone = phone.trim()
        if (trimmedPhone.isNotBlank()) {
            val digitsOnly = trimmedPhone.filter { it.isDigit() }
            val phoneRegex = Regex("""^[+]?[0-9\s\-()]{7,18}$""")
            if (digitsOnly.length < 7 || digitsOnly.length > 15 || !phoneRegex.matches(trimmedPhone)) {
                phoneError = "Please enter a valid phone number (7–15 digits)"
                isValid = false
            } else {
                phoneError = null
            }
        } else {
            phoneError = null
        }

        // 3. Email validation (optional field, but if present must be valid email format)
        val trimmedEmail = email.trim()
        if (trimmedEmail.isNotBlank()) {
            val emailRegex = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")
            if (!emailRegex.matches(trimmedEmail)) {
                emailError = "Please enter a valid email address"
                isValid = false
            } else {
                emailError = null
            }
        } else {
            emailError = null
        }

        return isValid
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Dialog Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Friend",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Profile Image Picker & Clear
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
                            contentDescription = "Friend Profile Picture",
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(if (isDark) ElectricBlue.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isDark) ElectricBlueLight else AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // Camera Icon Overlay
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.primaryAccent)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (profileImagePath != null) {
                    TextButton(
                        onClick = { profileImagePath = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = CoralRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Remove Photo",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoralRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Friend Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) validate()
                    },
                    label = { Text("Friend Name *", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Jay, Priya, સુકૃત", color = AppTheme.colors.textTertiary) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = CoralRed) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phone Number Field
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        if (phoneError != null) validate()
                    },
                    label = { Text("Phone Number (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. +91 98765 43210", color = AppTheme.colors.textTertiary) },
                    isError = phoneError != null,
                    supportingText = phoneError?.let { { Text(it, color = CoralRed) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        if (emailError != null) validate()
                    },
                    label = { Text("Email (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. friend@example.com", color = AppTheme.colors.textTertiary) },
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(it, color = CoralRed) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. College roommate, Flatmate", color = AppTheme.colors.textTertiary) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.cardBorder)
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.titleSmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    Button(
                        onClick = {
                            if (validate()) {
                                isSaving = true
                                val updated = friend.copy(
                                    name = name.trim(),
                                    phoneNumber = phone.trim().takeIf { it.isNotBlank() },
                                    email = email.trim().takeIf { it.isNotBlank() },
                                    notes = notes.trim().takeIf { it.isNotBlank() },
                                    profileImagePath = profileImagePath
                                )
                                onSave(updated)
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
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Save Changes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

