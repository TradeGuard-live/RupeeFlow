package com.naman.rupeeflow.voice.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.AppViewModelProvider
import com.naman.rupeeflow.ui.theme.LocalAppColors
import com.naman.rupeeflow.voice.model.*
import com.naman.rupeeflow.voice.viewmodel.VoiceAssistantUiState
import com.naman.rupeeflow.voice.viewmodel.VoiceAssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantSheet(
    onDismiss: () -> Unit,
    viewModel: VoiceAssistantViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = LocalAppColors.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            viewModel.refreshAvailability()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.cancelCurrentSession()
            onDismiss()
        },
        containerColor = colors.surfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Offline Badge, TTS Toggle, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "100% Offline",
                        tint = colors.success,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "100% Offline & Private",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.success
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.toggleTts() }) {
                        Icon(
                            imageVector = if (uiState.isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (uiState.isTtsEnabled) "Mute TTS" else "Enable TTS",
                            tint = colors.textSecondary
                        )
                    }
                    IconButton(onClick = {
                        viewModel.cancelCurrentSession()
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-Language & Language Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto Detect Chip (Recommended / Default)
                FilterChip(
                    selected = uiState.isAutoDetect,
                    onClick = { viewModel.enableAutoDetect() },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto Language Detection",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (uiState.isAutoDetect) "Auto: ${uiState.detectedLanguage.displayName}" else "Auto Detect",
                            fontWeight = if (uiState.isAutoDetect) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primaryAccentContainer,
                        selectedLabelColor = colors.primaryAccent
                    )
                )

                VoiceLanguage.entries.forEach { lang ->
                    val isSelected = !uiState.isAutoDetect && uiState.selectedLanguage == lang
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectLanguage(lang) },
                        label = { Text(lang.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primaryAccentContainer,
                            selectedLabelColor = colors.primaryAccent
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Offline Language Pack / Permission Status Warning
            if (uiState.availability == SpeechRecognitionAvailability.PERMISSION_REQUIRED) {
                StatusAlertCard(
                    title = "Microphone Permission Required",
                    description = "Grant audio recording permission to use offline voice commands.",
                    actionLabel = "Grant Permission",
                    onAction = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else if (uiState.availability == SpeechRecognitionAvailability.LANGUAGE_PACK_DOWNLOAD_REQUIRED) {
                StatusAlertCard(
                    title = "Offline Model Not Installed",
                    description = "Your device doesn't have the offline voice pack for ${uiState.selectedLanguage.displayName}. You can use manual text input below.",
                    actionLabel = if (!uiState.isManualInputMode) "Type Command" else null,
                    onAction = { viewModel.toggleManualInput() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Central Animated Mic Visualizer or Manual Input
            if (uiState.isManualInputMode) {
                ManualTextInputCard(
                    uiState = uiState,
                    onTextChange = { viewModel.updateTranscriptionInput(it) },
                    onSubmit = { viewModel.submitManualText(it) },
                    onSwitchToMic = { viewModel.toggleManualInput() }
                )
            } else {
                MicPulsingVisualizer(
                    isListening = uiState.status == VoiceAssistantStatus.LISTENING,
                    rmsLevel = uiState.rmsLevel,
                    onClick = {
                        if (uiState.status == VoiceAssistantStatus.LISTENING) {
                            viewModel.stopListening()
                        } else {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                viewModel.startListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Live Transcription Box
                TranscriptionDisplay(
                    transcription = uiState.transcription,
                    partialTranscription = uiState.partialTranscription,
                    status = uiState.status,
                    onEditClick = { viewModel.toggleManualInput() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-Turn Context Cards: PREVIEW, CLARIFICATION, QUERY RESULT, ERROR, SUCCESS
            when (uiState.status) {
                VoiceAssistantStatus.PREVIEW -> {
                    uiState.currentPreview?.let { preview ->
                        ActionPreviewCard(
                            preview = preview,
                            onConfirm = { viewModel.confirmPendingAction() },
                            onCancel = { viewModel.cancelCurrentSession() }
                        )
                    }
                }
                VoiceAssistantStatus.NEEDS_CLARIFICATION -> {
                    uiState.clarificationPrompt?.let { prompt ->
                        ClarificationCard(
                            prompt = prompt,
                            onOptionSelected = { viewModel.selectClarificationOption(it) },
                            onFriendSelected = { viewModel.selectClarificationFriend(it) }
                        )
                    }
                }
                VoiceAssistantStatus.SUCCESS -> {
                    SuccessResultCard(
                        message = uiState.successMessage ?: "Action completed.",
                        details = (uiState.currentPreview as? VoiceActionPreview.QueryResultPreview)?.details ?: emptyList(),
                        onDone = {
                            viewModel.cancelCurrentSession()
                            onDismiss()
                        }
                    )
                }
                VoiceAssistantStatus.ERROR -> {
                    ErrorResultCard(
                        message = uiState.errorMessage ?: "Something went wrong.",
                        onRetry = { viewModel.startListening() },
                        onUseText = { viewModel.toggleManualInput() }
                    )
                }
                else -> {
                    // Quick suggestion hints when idle
                    if (uiState.status == VoiceAssistantStatus.IDLE || uiState.status == VoiceAssistantStatus.LISTENING) {
                        SuggestionHints(onSelectHint = { viewModel.submitManualText(it) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MicPulsingVisualizer(
    isListening: Boolean,
    rmsLevel: Float,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(110.dp)
    ) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(colors.primaryAccent.copy(alpha = 0.2f))
            )
        }

        Surface(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            color = if (isListening) colors.primaryAccent else colors.surfaceSecondary,
            shape = CircleShape,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                    contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                    tint = if (isListening) colors.onPrimaryAccent else colors.primaryAccent,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

    if (isListening) {
        Spacer(modifier = Modifier.height(6.dp))
        // Animated RMS Waveform
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(24.dp)
        ) {
            val barCount = 7
            for (i in 0 until barCount) {
                val extra = (20f * rmsLevel * ((i % 3 + 1) / 3f)).dp
                val height = (12.dp + extra).coerceIn(8.dp, 28.dp)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(height)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.primaryAccent)
                )
            }
        }
    }
}

@Composable
fun TranscriptionDisplay(
    transcription: String,
    partialTranscription: String,
    status: VoiceAssistantStatus,
    onEditClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val displayText = when {
        partialTranscription.isNotBlank() -> partialTranscription
        transcription.isNotBlank() -> transcription
        status == VoiceAssistantStatus.LISTENING -> "Listening... Speak clearly"
        else -> "Tap the mic and speak"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayText,
                fontSize = 15.sp,
                fontWeight = if (transcription.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                color = if (transcription.isNotBlank() || partialTranscription.isNotBlank()) colors.textPrimary else colors.textTertiary,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit with keyboard",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ManualTextInputCard(
    uiState: VoiceAssistantUiState,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSwitchToMic: () -> Unit
) {
    val colors = LocalAppColors.current
    var textInput by remember { mutableStateOf(uiState.transcription) }

    // Sync with external updates
    LaunchedEffect(uiState.transcription) {
        if (uiState.transcription != textInput) {
            textInput = uiState.transcription
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = textInput,
                onValueChange = {
                    textInput = it
                    onTextChange(it)
                },
                placeholder = { Text("e.g. Add friend Sukrut or Add 300 for dinner") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSwitchToMic) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Use Voice")
                }
                Button(
                    onClick = { onSubmit(textInput) },
                    enabled = textInput.isNotBlank()
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Process")
                }
            }
        }
    }
}

@Composable
fun ActionPreviewCard(
    preview: VoiceActionPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalAppColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(1.dp, colors.primaryAccent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colors.primaryAccent
                )
                Text(
                    text = "Confirm Action",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (preview) {
                is VoiceActionPreview.TransactionPreview -> {
                    val txn = preview.transaction
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(txn.amountPaise),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.success
                    )
                    Text(
                        text = txn.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(txn.category?.name ?: "General") }
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Paid by: ${txn.paidByName}") }
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text(txn.paymentMethod.displayName) }
                        )
                    }

                    if (txn.participants.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Split (${txn.participants.size} people):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        txn.participants.forEach { p ->
                            Text(
                                text = "• ${p.friendName}: ${CurrencyUtils.formatPaiseToRupees(p.shareAmountPaise)}",
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
                is VoiceActionPreview.SettlementPreview -> {
                    Text(
                        text = CurrencyUtils.formatPaiseToRupees(preview.settlement.amountPaise),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.success
                    )
                    Text(
                        text = "${preview.directionLabel}: ${preview.friendName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }
                is VoiceActionPreview.FriendPreview -> {
                    val friend = preview.friend
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.primaryAccentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = friend.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = colors.primaryAccent
                            )
                        }
                        Column {
                            Text(
                                text = friend.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            if (!friend.phoneNumber.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.textSecondary
                                    )
                                    Text(
                                        text = friend.phoneNumber,
                                        fontSize = 14.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (preview.isDelete) "Delete Friend" else if (preview.isEdit) "Edit Friend" else "New Friend",
                                fontWeight = FontWeight.SemiBold,
                                color = if (preview.isDelete) colors.error else colors.success
                            )
                        }
                    )
                }
                is VoiceActionPreview.GroupPreview -> {
                    val group = preview.group
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.primaryAccentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = colors.primaryAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = group.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            if (preview.addedMemberNames.isNotEmpty()) {
                                Text(
                                    text = "Members: ${preview.addedMemberNames.joinToString(", ")}",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (preview.isDelete) "Delete Group" else if (preview.isAddMember) "Add Members" else if (preview.isRemoveMember) "Remove Member" else "New Group",
                                fontWeight = FontWeight.SemiBold,
                                color = if (preview.isDelete) colors.error else colors.success
                            )
                        }
                    )
                }
                is VoiceActionPreview.NavigationPreview -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = colors.primaryAccent
                        )
                        Text(
                            text = "Open ${preview.destinationTitle}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }
                is VoiceActionPreview.BackupPreview -> {
                    Text(
                        text = if (preview.isRestore) "Restore Backup" else "Create Backup",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
                is VoiceActionPreview.QueryResultPreview -> {
                    Text(text = preview.formattedAnswer, fontSize = 16.sp, color = colors.textPrimary)
                    if (preview.details.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        preview.details.forEach { d ->
                            Text(text = "• $d", fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isDestructive = when (preview) {
                is VoiceActionPreview.FriendPreview -> preview.isDelete
                is VoiceActionPreview.GroupPreview -> preview.isDelete
                is VoiceActionPreview.TransactionPreview -> preview.isDelete
                is VoiceActionPreview.SettlementPreview -> preview.isDelete || preview.isReverse
                is VoiceActionPreview.BackupPreview -> preview.isRestore
                else -> false
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDestructive) colors.error else colors.success
                    ),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text(
                        text = if (isDestructive) "Confirm Delete" else "Confirm & Save",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ClarificationCard(
    prompt: VoiceClarificationPrompt,
    onOptionSelected: (String) -> Unit,
    onFriendSelected: (com.naman.rupeeflow.domain.model.Friend) -> Unit
) {
    val colors = LocalAppColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = prompt.question,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.textPrimary
            )

            if (prompt.candidateFriends.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    prompt.candidateFriends.forEach { f ->
                        SuggestionChip(
                            onClick = { onFriendSelected(f) },
                            label = { Text(f.name, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            if (prompt.options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    prompt.options.forEach { opt ->
                        SuggestionChip(
                            onClick = { onOptionSelected(opt) },
                            label = { Text(opt) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuccessResultCard(
    message: String,
    details: List<String>,
    onDone: () -> Unit
) {
    val colors = LocalAppColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(1.dp, colors.success.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colors.success
                )
                Text(
                    text = message,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            if (details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                details.forEach { detail ->
                    Text(
                        text = "• $detail",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
fun ErrorResultCard(
    message: String,
    onRetry: () -> Unit,
    onUseText: () -> Unit
) {
    val colors = LocalAppColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = colors.error
                )
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onUseText, modifier = Modifier.weight(1f)) {
                    Text("Use Keyboard")
                }
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Text("Try Again")
                }
            }
        }
    }
}

@Composable
fun StatusAlertCard(
    title: String,
    description: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    val colors = LocalAppColors.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.warning.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.warning)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, fontSize = 12.sp, color = colors.textSecondary)
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.warning),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SuggestionHints(onSelectHint: (String) -> Unit) {
    val colors = LocalAppColors.current
    val hints = listOf(
        "Add 300 for dinner with Jay",
        "Jay paid 500 for lunch",
        "How much does Jay owe me?",
        "Total spent this month",
        "Settle 500 with Priya"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Try saying:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textTertiary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            hints.forEach { hint ->
                SuggestionChip(
                    onClick = { onSelectHint(hint) },
                    label = { Text(hint, fontSize = 11.sp) }
                )
            }
        }
    }
}
