package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.theme.*
import com.naman.rupeeflow.voice.ui.VoiceAssistantSheet
import kotlin.math.abs
import kotlin.math.roundToLong

enum class SheetEntryType {
    EXPENSE,
    INCOME,
    SETTLEMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastAddExpenseSheet(
    categories: List<Category>,
    friends: List<Friend>,
    groups: List<FriendGroup> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onSaveSettlement: ((Settlement) -> Unit)? = null,
    initialAmountPaise: Long? = null,
    initialTitle: String? = null,
    initialType: TransactionType? = null,
    initialPaymentMethod: PaymentMethod? = null,
    initialPendingId: Long? = null,
    onPendingResolved: ((Long) -> Unit)? = null
) {
    // Mode: Expense, Income, or Settlement
    var selectedEntryType by remember {
        mutableStateOf(
            when (initialType) {
                TransactionType.INCOME -> SheetEntryType.INCOME
                else -> SheetEntryType.EXPENSE
            }
        )
    }

    var title by remember { mutableStateOf(initialTitle ?: "") }
    var description by remember { mutableStateOf("") }
    var amountText by remember {
        mutableStateOf(
            if (initialAmountPaise != null && initialAmountPaise > 0L) {
                (initialAmountPaise / 100.0).toString()
            } else ""
        )
    }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }
    var selectedPaymentMethod by remember { mutableStateOf(initialPaymentMethod ?: PaymentMethod.UPI) }
    var selectedPayerId by remember { mutableStateOf<Long?>(null) } // null = Me

    // Settlement state
    var selectedSettlementFriend by remember { mutableStateOf<Friend?>(friends.firstOrNull()) }
    var isFriendPayingMe by remember {
        mutableStateOf(
            if (selectedSettlementFriend != null) {
                selectedSettlementFriend!!.status == FriendBalanceStatus.THEY_OWE_ME
            } else true
        )
    }
    var settlementRefNumber by remember { mutableStateOf("") }

    // Splitting state
    var isSplitEnabled by remember { mutableStateOf(false) }
    var isMeIncluded by remember { mutableStateOf(true) }
    var selectedFriendIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedGroupFilterId by remember { mutableStateOf<Long?>(null) }
    var splitMethod by remember { mutableStateOf(SplitMethod.EQUAL) }
    var customRupeeInputs by remember { mutableStateOf(mapOf<Long?, String>()) } // null = Me
    var percentageInputs by remember { mutableStateOf(mapOf<Long?, String>()) } // null = Me
    var billImagePath by remember { mutableStateOf<String?>(null) }
    var showVoiceSheet by remember { mutableStateOf(false) }

    val amountPaise = remember(amountText) { CurrencyUtils.parseInputToPaise(amountText) }

    // Participant IDs list (Me included only if isMeIncluded == true)
    val allParticipantIds: List<Long?> = remember(isMeIncluded, selectedFriendIds) {
        val list = mutableListOf<Long?>()
        if (isMeIncluded) list.add(null)
        list.addAll(selectedFriendIds)
        list
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppTheme.colors.cardBorder) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (selectedEntryType) {
                        SheetEntryType.EXPENSE -> "Add Expense"
                        SheetEntryType.INCOME -> "Add Income"
                        SheetEntryType.SETTLEMENT -> "Add Settlement"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showVoiceSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Assistant",
                            tint = AppTheme.colors.primaryAccent
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Entry Type Selector Row (Expense | Income | Settlement) - Removed Transfer / Added Settlement
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.surfaceSecondary)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    SheetEntryType.EXPENSE to "Expense",
                    SheetEntryType.INCOME to "Income",
                    SheetEntryType.SETTLEMENT to "Settlement"
                ).forEach { (type, label) ->
                    val isSelected = selectedEntryType == type
                    val activeColor = when (type) {
                        SheetEntryType.EXPENSE -> CoralRed
                        SheetEntryType.INCOME -> AppTheme.colors.incomeGreen
                        SheetEntryType.SETTLEMENT -> AppTheme.colors.primaryAccent
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedEntryType = type }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) activeColor else AppTheme.colors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== SETTLEMENT SECTION ====================
            if (selectedEntryType == SheetEntryType.SETTLEMENT) {
                if (friends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends added yet. Please add a friend from the Friends tab to record a settlement.",
                            color = AppTheme.colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // Friend Selector
                    Text(
                        text = "Select Friend",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        friends.forEach { friend ->
                            val isSelected = selectedSettlementFriend?.id == friend.id
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) AppTheme.colors.accentContainer else AppTheme.colors.cardBackground,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                                ),
                                modifier = Modifier.clickable {
                                    selectedSettlementFriend = friend
                                    isFriendPayingMe = friend.status == FriendBalanceStatus.THEY_OWE_ME
                                }
                            ) {
                                Text(
                                    text = friend.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Direction Toggle
                    selectedSettlementFriend?.let { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppTheme.colors.surfaceSecondary)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val opt1 = "${friend.name} paid Me"
                            val opt2 = "I paid ${friend.name}"

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isFriendPayingMe) AppTheme.colors.incomeGreen.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable { isFriendPayingMe = true }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt1,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFriendPayingMe) AppTheme.colors.incomeGreen else AppTheme.colors.textSecondary,
                                    fontWeight = if (isFriendPayingMe) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isFriendPayingMe) CoralRed.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable { isFriendPayingMe = false }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt2,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!isFriendPayingMe) CoralRed else AppTheme.colors.textSecondary,
                                    fontWeight = if (!isFriendPayingMe) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Outstanding balance quick fill helper
                        val friendBal = if (friend.status == FriendBalanceStatus.THEY_OWE_ME) friend.totalTheyOweMePaise else friend.totalIOweThemPaise
                        if (friendBal > 0L) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (friend.status == FriendBalanceStatus.THEY_OWE_ME) "${friend.name} owes you ${CurrencyUtils.formatPaiseToRupees(friendBal)}"
                                    else "You owe ${friend.name} ${CurrencyUtils.formatPaiseToRupees(friendBal)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (friend.status == FriendBalanceStatus.THEY_OWE_ME) AppTheme.colors.incomeGreen else CoralRed
                                )
                                TextButton(
                                    onClick = { amountText = (friendBal / 100.0).toString() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Fill Total", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.primaryAccent)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom Amount Input
                    AmountInputField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = "Settlement Amount (Custom)"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Payment Method
                    Text(
                        text = "Payment Method",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PaymentMethodSelectorRow(
                        selectedMethod = selectedPaymentMethod,
                        onSelect = { selectedPaymentMethod = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notes / Reference Number
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Settlement Notes (Optional)", color = AppTheme.colors.textSecondary) },
                        placeholder = { Text("e.g. Cleared dinner bill, Google Pay settlement", color = AppTheme.colors.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = settlementRefNumber,
                        onValueChange = { settlementRefNumber = it },
                        label = { Text("Reference / UPI Transaction ID (Optional)", color = AppTheme.colors.textSecondary) },
                        placeholder = { Text("e.g. 12-digit UTR ref no.", color = AppTheme.colors.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Save Settlement Button
                    Button(
                        onClick = {
                            val targetFriend = selectedSettlementFriend ?: return@Button
                            if (amountPaise <= 0L) return@Button

                            val settlement = Settlement(
                                payerId = if (isFriendPayingMe) targetFriend.id else null,
                                receiverId = if (isFriendPayingMe) null else targetFriend.id,
                                amountPaise = amountPaise,
                                settlementDate = System.currentTimeMillis(),
                                paymentMethod = selectedPaymentMethod,
                                referenceNumber = settlementRefNumber.trim().ifBlank { null },
                                notes = description.trim().ifBlank { null },
                                isReversed = false
                            )
                            onSaveSettlement?.invoke(settlement)
                            if (initialPendingId != null) {
                                onPendingResolved?.invoke(initialPendingId)
                            }
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryAccent,
                            contentColor = Color.White
                        ),
                        enabled = amountPaise > 0L && selectedSettlementFriend != null
                    ) {
                        Text(
                            text = "Save Settlement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ==================== EXPENSE / INCOME SECTION ====================
            if (selectedEntryType != SheetEntryType.SETTLEMENT) {
                // Amount Input
                AmountInputField(
                    value = amountText,
                    onValueChange = { amountText = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Merchant", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Swiggy, Groceries, Salary, Dinner", color = AppTheme.colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description / Notes Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes (Optional)", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("Add extra details, itemized breakdown, or notes...", color = AppTheme.colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category Selector
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                CategorySelectorRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelect = { selectedCategory = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Payment Method
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                PaymentMethodSelectorRow(
                    selectedMethod = selectedPaymentMethod,
                    onSelect = { selectedPaymentMethod = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bill / Receipt Photo Attachment
                BillAttachmentSection(
                    billImagePath = billImagePath,
                    onImageSelected = { billImagePath = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Paid By Dropdown (Single-selection with default = Me)
                if (selectedEntryType == SheetEntryType.EXPENSE) {
                    PayerDropdownSelector(
                        friends = friends,
                        selectedPayerId = selectedPayerId,
                        onPayerSelect = { selectedPayerId = it }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Split with Friends Section (Only when friends exist)
                if (selectedEntryType == SheetEntryType.EXPENSE && friends.isNotEmpty()) {
                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (isSplitEnabled) AppTheme.colors.primaryAccent.copy(alpha = 0.5f) else AppTheme.colors.cardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Split with Friends",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = if (isMeIncluded) "Your personal share will be counted in spendings"
                                    else "You are excluded; full amount owed by friends",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            Switch(
                                checked = isSplitEnabled,
                                onCheckedChange = { isSplitEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppTheme.colors.primaryAccent,
                                    checkedTrackColor = AppTheme.colors.accentContainer
                                )
                            )
                        }

                        if (isSplitEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Group-First Split Participant Selector
                            GroupFirstFriendSelector(
                                groups = groups,
                                allFriends = friends,
                                selectedGroupId = selectedGroupFilterId,
                                onGroupSelect = { selectedGroupFilterId = it },
                                isMultiSelect = true,
                                selectedFriendIds = selectedFriendIds,
                                onSelectionChange = { selectedFriendIds = it },
                                includeMe = isMeIncluded,
                                onIncludeMeChange = { isMeIncluded = it },
                                totalPaise = amountPaise
                            )

                            if (allParticipantIds.isNotEmpty()) {
                                // Split Method Tabs (Equal, Percentage, Custom Rupee)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Split Method",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppTheme.colors.surfaceSecondary)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        SplitMethod.EQUAL to "Equal (=)",
                                        SplitMethod.PERCENTAGE to "Percentage (%)",
                                        SplitMethod.EXACT_AMOUNT to "Custom ₹"
                                    ).forEach { (method, label) ->
                                        val isSelected = splitMethod == method
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) AppTheme.colors.primaryAccent.copy(alpha = 0.2f) else Color.Transparent)
                                                .clickable { splitMethod = method }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 1. EQUAL SPLIT BREAKDOWN
                                if (splitMethod == SplitMethod.EQUAL) {
                                    val totalParticipants = allParticipantIds.size
                                    val shares = if (amountPaise > 0L && totalParticipants > 0) {
                                        LedgerCalculator.calculateEqualSplit(amountPaise, totalParticipants)
                                    } else {
                                        List(totalParticipants) { 0L }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AppTheme.colors.surfaceSecondary,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Equal Split (${totalParticipants} participants" + (if (!isMeIncluded) ", excluding you" else "") + ")",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.incomeGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            allParticipantIds.forEachIndexed { idx, pId ->
                                                val pName = if (pId == null) "Me (Your Expense)" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                                val pShare = shares.getOrElse(idx) { 0L }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 3.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(pName, style = MaterialTheme.typography.bodyMedium, color = if (pId == null) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary)
                                                    Text(
                                                        CurrencyUtils.formatPaiseToRupees(pShare),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = if (pId == null) AppTheme.colors.incomeGreen else AppTheme.colors.primaryAccent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. PERCENTAGE SPLIT BREAKDOWN
                                if (splitMethod == SplitMethod.PERCENTAGE) {
                                    val totalPct = allParticipantIds.sumOf { pId ->
                                        percentageInputs[pId]?.toDoubleOrNull() ?: 0.0
                                    }
                                    val pctDiff = 100.0 - totalPct
                                    val isPctValid = abs(pctDiff) < 0.1

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        allParticipantIds.forEach { pId ->
                                            val pName = if (pId == null) "Me (You)" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                            val currentPctStr = percentageInputs[pId] ?: ""
                                            val currentPct = currentPctStr.toDoubleOrNull() ?: 0.0
                                            val calculatedPaise = if (amountPaise > 0L) {
                                                ((amountPaise * currentPct) / 100.0).roundToLong()
                                            } else 0L

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(pName, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                                                    Text(
                                                        CurrencyUtils.formatPaiseToRupees(calculatedPaise),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (pId == null) AppTheme.colors.incomeGreen else AppTheme.colors.primaryAccent
                                                    )
                                                }

                                                OutlinedTextField(
                                                    value = currentPctStr,
                                                    onValueChange = { input ->
                                                        val filtered = input.filter { it.isDigit() || it == '.' }
                                                        percentageInputs = percentageInputs + (pId to filtered)
                                                    },
                                                    trailingIcon = { Text("%", color = AppTheme.colors.textSecondary, fontSize = 12.sp) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier.width(110.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = rupeeFlowTextFieldColors()
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isPctValid) {
                                                    "Total: 100% ✓"
                                                } else if (pctDiff > 0) {
                                                    "Total: ${String.format("%.1f", totalPct)}% (${String.format("%.1f", pctDiff)}% left)"
                                                } else {
                                                    "Total: ${String.format("%.1f", totalPct)}% (Exceeds by ${String.format("%.1f", -pctDiff)}%)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isPctValid) AppTheme.colors.incomeGreen else if (pctDiff > 0) AmberGold else CoralRed,
                                                fontWeight = FontWeight.Bold
                                            )

                                            if (!isPctValid && pctDiff > 0 && isMeIncluded) {
                                                TextButton(
                                                    onClick = {
                                                        val myCurrent = percentageInputs[null]?.toDoubleOrNull() ?: 0.0
                                                        val newMyPct = String.format(java.util.Locale.US, "%.1f", myCurrent + pctDiff)
                                                        percentageInputs = percentageInputs + (null to newMyPct)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Remaining % to Me", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.primaryAccent)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. CUSTOM RUPEE SPLIT BREAKDOWN
                                if (splitMethod == SplitMethod.EXACT_AMOUNT) {
                                    val sumEnteredPaise = allParticipantIds.sumOf { pId ->
                                        CurrencyUtils.parseInputToPaise(customRupeeInputs[pId] ?: "")
                                    }
                                    val diffPaise = amountPaise - sumEnteredPaise
                                    val isExactMatched = diffPaise == 0L && amountPaise > 0L

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        allParticipantIds.forEach { pId ->
                                            val pName = if (pId == null) "Me (You)" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                            val currentRupeeStr = customRupeeInputs[pId] ?: ""

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = pName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = AppTheme.colors.textPrimary,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                OutlinedTextField(
                                                    value = currentRupeeStr,
                                                    onValueChange = { input ->
                                                        val filtered = input.filter { it.isDigit() || it == '.' }
                                                        customRupeeInputs = customRupeeInputs + (pId to filtered)
                                                    },
                                                    leadingIcon = { Text("₹", color = AppTheme.colors.primaryAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                                    placeholder = { Text("0.00", color = AppTheme.colors.textMuted) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier.width(135.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = rupeeFlowTextFieldColors()
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isExactMatched) {
                                                    "Total matched: ${CurrencyUtils.formatPaiseToRupees(amountPaise)} ✓"
                                                } else if (diffPaise > 0) {
                                                    "${CurrencyUtils.formatPaiseToRupees(diffPaise)} remaining to assign"
                                                } else {
                                                    "Exceeds total by ${CurrencyUtils.formatPaiseToRupees(-diffPaise)}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isExactMatched) AppTheme.colors.incomeGreen else if (diffPaise > 0) AmberGold else CoralRed,
                                                fontWeight = FontWeight.Bold
                                            )

                                            if (diffPaise > 0 && isMeIncluded) {
                                                TextButton(
                                                    onClick = {
                                                        val myCurrentPaise = CurrencyUtils.parseInputToPaise(customRupeeInputs[null] ?: "")
                                                        val newMyPaise = myCurrentPaise + diffPaise
                                                        val formattedRupees = CurrencyUtils.formatPaiseToRupees(newMyPaise, includeSymbol = false)
                                                        customRupeeInputs = customRupeeInputs + (null to formattedRupees)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Remaining ₹ to Me", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.primaryAccent)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Validation Check for Split
                val isSplitValid = remember(isSplitEnabled, allParticipantIds, splitMethod, amountPaise, percentageInputs, customRupeeInputs) {
                    if (!isSplitEnabled || allParticipantIds.isEmpty()) {
                        true
                    } else when (splitMethod) {
                        SplitMethod.EQUAL -> true
                        SplitMethod.PERCENTAGE -> {
                            val totalPct = allParticipantIds.sumOf { percentageInputs[it]?.toDoubleOrNull() ?: 0.0 }
                            abs(totalPct - 100.0) <= 0.5
                        }
                        SplitMethod.EXACT_AMOUNT -> {
                            val sumPaise = allParticipantIds.sumOf { CurrencyUtils.parseInputToPaise(customRupeeInputs[it] ?: "") }
                            sumPaise == amountPaise
                        }
                    }
                }

                if (!isSplitValid && isSplitEnabled && allParticipantIds.isNotEmpty()) {
                    Text(
                        text = if (splitMethod == SplitMethod.PERCENTAGE) {
                            "Please adjust percentages so the total equals 100%."
                        } else {
                            "Please ensure the sum of participant shares strictly equals the total bill."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Save Button
                Button(
                    onClick = {
                        if (amountPaise <= 0L || !isSplitValid) return@Button
                        val finalTitle = title.ifBlank { selectedCategory?.name ?: (if (selectedEntryType == SheetEntryType.INCOME) "Income" else "Expense") }
                        val categoryId = selectedCategory?.id ?: 1L

                        // Build participants
                        val participants = mutableListOf<TransactionParticipant>()
                        if (isSplitEnabled && allParticipantIds.isNotEmpty()) {
                            when (splitMethod) {
                                SplitMethod.EQUAL -> {
                                    val shares = LedgerCalculator.calculateEqualSplit(amountPaise, allParticipantIds.size)
                                    val pct = if (allParticipantIds.isNotEmpty()) 100.0 / allParticipantIds.size else 0.0
                                    allParticipantIds.forEachIndexed { index, pId ->
                                        val pName = if (pId == null) "Me" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                        participants.add(
                                            TransactionParticipant(
                                                friendId = pId,
                                                friendName = pName,
                                                shareAmountPaise = shares.getOrElse(index) { 0L },
                                                percentage = pct,
                                                splitMethod = SplitMethod.EQUAL
                                            )
                                        )
                                    }
                                }
                                SplitMethod.PERCENTAGE -> {
                                    val pcts = allParticipantIds.map { percentageInputs[it]?.toDoubleOrNull() ?: 0.0 }
                                    val shares = LedgerCalculator.calculatePercentageSplit(amountPaise, pcts)
                                    allParticipantIds.forEachIndexed { index, pId ->
                                        val pName = if (pId == null) "Me" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                        participants.add(
                                            TransactionParticipant(
                                                friendId = pId,
                                                friendName = pName,
                                                shareAmountPaise = shares.getOrElse(index) { 0L },
                                                percentage = pcts.getOrElse(index) { 0.0 },
                                                splitMethod = SplitMethod.PERCENTAGE
                                            )
                                        )
                                    }
                                }
                                SplitMethod.EXACT_AMOUNT -> {
                                    allParticipantIds.forEach { pId ->
                                        val pName = if (pId == null) "Me" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                        val sharePaise = CurrencyUtils.parseInputToPaise(customRupeeInputs[pId] ?: "")
                                        val pct = if (amountPaise > 0L) (sharePaise.toDouble() / amountPaise.toDouble()) * 100.0 else 0.0
                                        participants.add(
                                            TransactionParticipant(
                                                friendId = pId,
                                                friendName = pName,
                                                shareAmountPaise = sharePaise,
                                                percentage = pct,
                                                splitMethod = SplitMethod.EXACT_AMOUNT
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        val transactionType = if (selectedEntryType == SheetEntryType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE

                        val newTxn = Transaction(
                            title = finalTitle,
                            description = description.trim(),
                            amountPaise = amountPaise,
                            categoryId = categoryId,
                            category = selectedCategory,
                            transactionDate = System.currentTimeMillis(),
                            paymentMethod = selectedPaymentMethod,
                            paidByFriendId = selectedPayerId,
                            paidByName = if (selectedPayerId == null) "Me" else friends.firstOrNull { it.id == selectedPayerId }?.name ?: "Friend",
                            participants = participants,
                            splitMethod = splitMethod,
                            transactionType = transactionType,
                            transactionStatus = TransactionStatus.ACTIVE,
                            source = TransactionSource.MANUAL,
                            groupId = selectedGroupFilterId,
                            billImagePath = billImagePath
                        )
                        onSave(newTxn)
                        if (initialPendingId != null) {
                            onPendingResolved?.invoke(initialPendingId)
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedEntryType == SheetEntryType.INCOME) AppTheme.colors.incomeGreen else AppTheme.colors.primaryAccent,
                        contentColor = Color.White
                    ),
                    enabled = amountPaise > 0L && isSplitValid
                ) {
                    Text(
                        text = if (selectedEntryType == SheetEntryType.INCOME) "Save Income" else "Save Expense",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showVoiceSheet) {
        VoiceAssistantSheet(onDismiss = { showVoiceSheet = false })
    }
}
