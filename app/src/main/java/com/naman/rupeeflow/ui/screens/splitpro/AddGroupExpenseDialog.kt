package com.naman.rupeeflow.ui.screens.splitpro

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.components.FriendAvatar
import com.naman.rupeeflow.ui.components.rupeeFlowTextFieldColors
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupExpenseDialog(
    group: ExpenseGroup,
    friends: List<Friend>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaveExpense: (Transaction) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var amountRupeesInput by remember { mutableStateOf("") }
    val amountPaise = remember(amountRupeesInput) {
        CurrencyUtils.parseInputToPaise(amountRupeesInput)
    }

    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 1L) }
    var selectedDateTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    // Paid By: null = Me, or friendId
    var paidByMemberId by remember { mutableStateOf<Long?>(null) }

    // Group members: null (Me) + all friends in group
    val allGroupMemberIds = remember(group.memberFriendIds) {
        listOf<Long?>(null) + group.memberFriendIds
    }
    val friendsMap = remember(friends) { friends.associateBy { it.id } }

    fun getMemberName(mId: Long?): String {
        return if (mId == null) "You" else friendsMap[mId]?.name ?: "Friend #$mId"
    }

    // Participants selection
    var selectedParticipantIds by remember { mutableStateOf(allGroupMemberIds.toSet()) }

    // 5 Split Methods
    var selectedSplitMethod by remember { mutableStateOf(SplitMethod.EQUAL) }

    // Split method specific inputs: memberId -> String
    val exactInputs = remember { mutableStateMapOf<Long?, String>() }
    val percentageInputs = remember { mutableStateMapOf<Long?, String>() }
    val sharesInputs = remember { mutableStateMapOf<Long?, String>() }
    val customInputs = remember { mutableStateMapOf<Long?, String>() }

    var notes by remember { mutableStateOf("") }
    var billAttachmentPath by remember { mutableStateOf<String?>(null) }

    // Bill Picker (Image or PDF)
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedFilename = ImageStorageManager.saveBillAttachment(context, uri)
                if (savedFilename != null) {
                    billAttachmentPath = savedFilename
                }
            }
        }
    }

    // Calculate live participant shares and validation
    val activeParticipants = remember(selectedParticipantIds) {
        allGroupMemberIds.filter { selectedParticipantIds.contains(it) }
    }

    val calculatedShares = remember(
        amountPaise,
        selectedSplitMethod,
        activeParticipants,
        exactInputs.toMap(),
        percentageInputs.toMap(),
        sharesInputs.toMap(),
        customInputs.toMap()
    ) {
        if (amountPaise <= 0L || activeParticipants.isEmpty()) {
            emptyMap<Long?, Long>()
        } else {
            when (selectedSplitMethod) {
                SplitMethod.EQUAL -> {
                    val shares = LedgerCalculator.calculateEqualSplit(amountPaise, activeParticipants.size)
                    activeParticipants.mapIndexed { index, mId -> mId to shares.getOrElse(index) { 0L } }.toMap()
                }
                SplitMethod.EXACT_AMOUNT -> {
                    activeParticipants.associateWith { mId ->
                        CurrencyUtils.parseInputToPaise(exactInputs[mId] ?: "")
                    }
                }
                SplitMethod.PERCENTAGE -> {
                    val pcts = activeParticipants.map { mId ->
                        percentageInputs[mId]?.toDoubleOrNull() ?: (100.0 / activeParticipants.size)
                    }
                    if (LedgerCalculator.validatePercentages(pcts)) {
                        try {
                            val shares = LedgerCalculator.calculatePercentageSplit(amountPaise, pcts)
                            activeParticipants.mapIndexed { index, mId -> mId to shares.getOrElse(index) { 0L } }.toMap()
                        } catch (_: Exception) {
                            emptyMap()
                        }
                    } else {
                        emptyMap()
                    }
                }
                SplitMethod.SHARES -> {
                    try {
                        val weights = activeParticipants.map { mId ->
                            sharesInputs[mId]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        }
                        val shares = LedgerCalculator.calculateSharesSplit(amountPaise, weights)
                        activeParticipants.mapIndexed { index, mId -> mId to shares.getOrElse(index) { 0L } }.toMap()
                    } catch (_: Exception) {
                        emptyMap()
                    }
                }
                SplitMethod.CUSTOM -> {
                    activeParticipants.associateWith { mId ->
                        CurrencyUtils.parseInputToPaise(customInputs[mId] ?: "")
                    }
                }
            }
        }
    }

    val calculatedSum = remember(calculatedShares) {
        calculatedShares.values.sum()
    }

    val isSplitValid = remember(amountPaise, calculatedShares, calculatedSum, activeParticipants) {
        amountPaise > 0L &&
                activeParticipants.isNotEmpty() &&
                calculatedShares.isNotEmpty() &&
                calculatedSum == amountPaise &&
                calculatedShares.values.none { it < 0L }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
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
                    Column {
                        Text(
                            text = "Add Group Expense",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Expense Title *") },
                            placeholder = { Text("e.g. Dinner at Cafe, Grocery, Taxi") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            colors = rupeeFlowTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // Amount & Category
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = amountRupeesInput,
                                onValueChange = { amountRupeesInput = it },
                                label = { Text("Total Amount *") },
                                placeholder = { Text("₹ 0.00") },
                                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = rupeeFlowTextFieldColors(),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(14.dp)
                            )

                            // Category Selector
                            var categoryDropdownExpanded by remember { mutableStateOf(false) }
                            val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull()

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { categoryDropdownExpanded = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = selectedCategory?.name ?: "Category",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                DropdownMenu(
                                    expanded = categoryDropdownExpanded,
                                    onDismissRequest = { categoryDropdownExpanded = false }
                                ) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name) },
                                            onClick = {
                                                selectedCategoryId = cat.id
                                                categoryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Paid By Selector
                    item {
                        Text(
                            text = "Paid By",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allGroupMemberIds) { mId ->
                                val isSelected = paidByMemberId == mId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { paidByMemberId = mId },
                                    label = { Text(getMemberName(mId)) },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    },
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

                    // Participants Selection Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Participants (${activeParticipants.size}/${allGroupMemberIds.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textSecondary
                            )
                            TextButton(
                                onClick = {
                                    selectedParticipantIds = if (selectedParticipantIds.size == allGroupMemberIds.size) {
                                        setOf(paidByMemberId) // keep payer
                                    } else {
                                        allGroupMemberIds.toSet()
                                    }
                                }
                            ) {
                                Text(
                                    text = if (selectedParticipantIds.size == allGroupMemberIds.size) "Deselect All" else "Select All",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Horizontal participant chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allGroupMemberIds) { mId ->
                                val isSelected = selectedParticipantIds.contains(mId)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedParticipantIds = if (isSelected) {
                                            if (selectedParticipantIds.size > 1) selectedParticipantIds - mId else selectedParticipantIds
                                        } else {
                                            selectedParticipantIds + mId
                                        }
                                    },
                                    label = { Text(getMemberName(mId)) },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Split Method Selector (5 Split Methods)
                    item {
                        Text(
                            text = "Split Method",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(SplitMethod.values()) { method ->
                                val isSelected = selectedSplitMethod == method
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedSplitMethod = method },
                                    label = { Text(method.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VibrantCyan,
                                        selectedLabelColor = Color.Black,
                                        containerColor = AppTheme.colors.surface,
                                        labelColor = AppTheme.colors.textSecondary
                                    )
                                )
                            }
                        }
                    }

                    // Dynamic Split Inputs & Live Preview
                    item {
                        Text(
                            text = "Live Split Preview",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppTheme.colors.surface, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeParticipants.forEach { mId ->
                                val name = getMemberName(mId)
                                val sharePaise = calculatedShares[mId] ?: 0L
                                val sharePct = if (amountPaise > 0L) (sharePaise.toDouble() / amountPaise.toDouble()) * 100.0 else 0.0

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = AppTheme.colors.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Method input field if needed
                                    when (selectedSplitMethod) {
                                        SplitMethod.EXACT_AMOUNT -> {
                                            OutlinedTextField(
                                                value = exactInputs[mId] ?: "",
                                                onValueChange = { exactInputs[mId] = it },
                                                placeholder = { Text("₹ 0") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors = rupeeFlowTextFieldColors(),
                                                modifier = Modifier.width(100.dp),
                                                singleLine = true
                                            )
                                        }
                                        SplitMethod.PERCENTAGE -> {
                                            OutlinedTextField(
                                                value = percentageInputs[mId] ?: "",
                                                onValueChange = { percentageInputs[mId] = it },
                                                placeholder = { Text("%") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors = rupeeFlowTextFieldColors(),
                                                modifier = Modifier.width(80.dp),
                                                singleLine = true
                                            )
                                        }
                                        SplitMethod.SHARES -> {
                                            OutlinedTextField(
                                                value = sharesInputs[mId] ?: "1",
                                                onValueChange = { sharesInputs[mId] = it },
                                                placeholder = { Text("shares") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = rupeeFlowTextFieldColors(),
                                                modifier = Modifier.width(80.dp),
                                                singleLine = true
                                            )
                                        }
                                        SplitMethod.CUSTOM -> {
                                            OutlinedTextField(
                                                value = customInputs[mId] ?: "",
                                                onValueChange = { customInputs[mId] = it },
                                                placeholder = { Text("₹ 0") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors = rupeeFlowTextFieldColors(),
                                                modifier = Modifier.width(100.dp),
                                                singleLine = true
                                            )
                                        }
                                        else -> {}
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = CurrencyUtils.formatPaiseToRupees(sharePaise),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f%%", sharePct),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                                Divider(color = AppTheme.colors.divider.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            // Summary balance check
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Allocated",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = "${CurrencyUtils.formatPaiseToRupees(calculatedSum)} / ${CurrencyUtils.formatPaiseToRupees(amountPaise)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSplitValid) EmeraldMint else AmberGold
                                )
                            }
                        }
                    }

                    // Notes
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes (Optional)") },
                            maxLines = 2,
                            colors = rupeeFlowTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // Receipt Attachment (Image or PDF)
                    item {
                        OutlinedButton(
                            onClick = { attachmentPickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = if (billAttachmentPath?.endsWith(".pdf", ignoreCase = true) == true) Icons.Default.PictureAsPdf else Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (billAttachmentPath != null) {
                                    "Attached: ${billAttachmentPath?.substringAfterLast('/') ?: "Receipt"}"
                                } else {
                                    "Attach Bill / Receipt (Image or PDF)"
                                },
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
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
                            if (isSplitValid && title.isNotBlank()) {
                                val participantsList = activeParticipants.map { mId ->
                                    val share = calculatedShares[mId] ?: 0L
                                    val pct = if (amountPaise > 0L) (share.toDouble() / amountPaise.toDouble()) * 100.0 else 0.0
                                    TransactionParticipant(
                                        friendId = mId,
                                        friendName = getMemberName(mId),
                                        shareAmountPaise = share,
                                        percentage = pct,
                                        splitMethod = selectedSplitMethod
                                    )
                                }

                                val txn = Transaction(
                                    title = title.trim(),
                                    description = notes.trim(),
                                    amountPaise = amountPaise,
                                    categoryId = selectedCategoryId,
                                    transactionDate = selectedDateTimeMillis,
                                    paymentMethod = PaymentMethod.UPI,
                                    paidByFriendId = paidByMemberId,
                                    paidByName = getMemberName(paidByMemberId),
                                    participants = participantsList,
                                    splitMethod = selectedSplitMethod,
                                    transactionType = TransactionType.EXPENSE,
                                    expenseGroupId = group.id,
                                    expenseGroupName = group.name,
                                    billImagePath = billAttachmentPath,
                                    createdAt = System.currentTimeMillis()
                                )

                                onSaveExpense(txn)
                                onDismiss()
                            }
                        },
                        enabled = isSplitValid && title.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Save Expense", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

