package com.naman.rupeeflow.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.theme.*
import java.util.Calendar
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    categories: List<Category>,
    friends: List<Friend>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val context = LocalContext.current

    // 1. Transaction title
    var title by remember { mutableStateOf(transaction.title) }

    // 2. Description
    var description by remember { mutableStateOf(transaction.description) }

    // 3. Amount
    var amountText by remember {
        mutableStateOf(
            if (transaction.amountPaise > 0L) {
                (transaction.amountPaise / 100.0).toString()
            } else ""
        )
    }

    // 4. Category
    var selectedCategory by remember {
        mutableStateOf(categories.firstOrNull { it.id == transaction.categoryId } ?: transaction.category ?: categories.firstOrNull())
    }

    // 5. Transaction type
    var selectedType by remember { mutableStateOf(transaction.transactionType) }

    // 6. Transaction date & time
    var selectedDateTimeMillis by remember { mutableStateOf(transaction.transactionDate) }

    // 7. Payment method
    var selectedPaymentMethod by remember { mutableStateOf(transaction.paymentMethod) }

    // 8. Account source
    var accountSource by remember { mutableStateOf(transaction.accountSource ?: "") }

    // 9. Paid by (Me or Friend)
    var selectedPayerId by remember { mutableStateOf(transaction.paidByFriendId) }

    // 10. Split method & 11. Participants & Shares
    val initialHasParticipants = transaction.participants.isNotEmpty()
    var isSplitEnabled by remember { mutableStateOf(initialHasParticipants) }
    var isMeIncluded by remember {
        mutableStateOf(
            if (initialHasParticipants) transaction.participants.any { it.friendId == null } else true
        )
    }
    var selectedFriendIds by remember {
        mutableStateOf(
            transaction.participants.mapNotNull { it.friendId }.toSet()
        )
    }
    var splitMethod by remember { mutableStateOf(transaction.splitMethod) }

    // Participant inputs for Percentage & Custom ₹
    var percentageInputs by remember {
        mutableStateOf(
            transaction.participants.associate { p ->
                p.friendId to (if (p.percentage > 0.0) String.format(java.util.Locale.US, "%.1f", p.percentage) else "")
            }
        )
    }
    var customRupeeInputs by remember {
        mutableStateOf(
            transaction.participants.associate { p ->
                p.friendId to (if (p.shareAmountPaise > 0L) (p.shareAmountPaise / 100.0).toString() else "")
            }
        )
    }

    // 14. Merchant
    var merchantName by remember { mutableStateOf(transaction.merchantName ?: "") }

    // 15. Reference number
    var referenceNumber by remember { mutableStateOf(transaction.referenceNumber ?: "") }

    var showDiscardConfirm by remember { mutableStateOf(false) }

    val amountPaise = remember(amountText) { CurrencyUtils.parseInputToPaise(amountText) }

    // Participant IDs list (Me included only if isMeIncluded == true)
    val allParticipantIds: List<Long?> = remember(isMeIncluded, selectedFriendIds) {
        val list = mutableListOf<Long?>()
        if (isMeIncluded) list.add(null)
        list.addAll(selectedFriendIds)
        list
    }

    // Has user changed anything?
    val hasUnsavedChanges = remember(
        title, description, amountText, selectedCategory, selectedType,
        selectedDateTimeMillis, selectedPaymentMethod, accountSource,
        selectedPayerId, isSplitEnabled, isMeIncluded, selectedFriendIds,
        splitMethod, merchantName, referenceNumber
    ) {
        title != transaction.title ||
                description != transaction.description ||
                amountPaise != transaction.amountPaise ||
                selectedCategory?.id != transaction.categoryId ||
                selectedType != transaction.transactionType ||
                selectedDateTimeMillis != transaction.transactionDate ||
                selectedPaymentMethod != transaction.paymentMethod ||
                accountSource != (transaction.accountSource ?: "") ||
                selectedPayerId != transaction.paidByFriendId ||
                isSplitEnabled != initialHasParticipants ||
                merchantName != (transaction.merchantName ?: "") ||
                referenceNumber != (transaction.referenceNumber ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (hasUnsavedChanges) {
                showDiscardConfirm = true
            } else {
                onDismiss()
            }
        },
        containerColor = CharcoalSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                IconButton(onClick = {
                    if (hasUnsavedChanges) showDiscardConfirm = true else onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Transaction Type Selector (Expense, Income, Refund, Transfer)
            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CharcoalCard)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    TransactionType.EXPENSE to CoralRed,
                    TransactionType.INCOME to EmeraldMint,
                    TransactionType.REFUND to VibrantCyan,
                    TransactionType.TRANSFER to ElectricBlue
                ).forEach { (type, color) ->
                    val isSelected = selectedType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) color.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { selectedType = type }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) color else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Amount Input Field
            AmountInputField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "Amount"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Title Input Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title", color = TextSecondary) },
                placeholder = { Text("e.g. Dinner, Rent, Swiggy", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = CharcoalCard,
                    unfocusedContainerColor = CharcoalCard
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Description Input Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Notes (Optional)", color = TextSecondary) },
                placeholder = { Text("Additional details about this transaction", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = CharcoalCard,
                    unfocusedContainerColor = CharcoalCard
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Category Selector Row
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategorySelectorRow(
                categories = categories,
                selectedCategory = selectedCategory,
                onSelect = { selectedCategory = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Transaction Date & Time Controls
            Text(
                text = "Transaction Date & Time",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Date Picker Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CharcoalCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimeMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val updated = Calendar.getInstance().apply {
                                        timeInMillis = selectedDateTimeMillis
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    selectedDateTimeMillis = updated.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = DateTimeUtils.formatDate(selectedDateTimeMillis, "dd MMM yyyy"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }

                // Time Picker Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CharcoalCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimeMillis }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val updated = Calendar.getInstance().apply {
                                        timeInMillis = selectedDateTimeMillis
                                        set(Calendar.HOUR_OF_DAY, hour)
                                        set(Calendar.MINUTE, minute)
                                    }
                                    selectedDateTimeMillis = updated.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = DateTimeUtils.formatTime(selectedDateTimeMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Payment Method Selector Row
            Text(
                text = "Payment Method",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            PaymentMethodSelectorRow(
                selectedMethod = selectedPaymentMethod,
                onSelect = { selectedPaymentMethod = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 8. Account Source (Bank / Card / Cash)
            OutlinedTextField(
                value = accountSource,
                onValueChange = { accountSource = it },
                label = { Text("Account Source / Bank (Optional)", color = TextSecondary) },
                placeholder = { Text("e.g. HDFC, ICICI, SBI, Cash Wallet", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = CharcoalCard,
                    unfocusedContainerColor = CharcoalCard
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 9. Merchant & 10. Reference Number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    label = { Text("Merchant", color = TextSecondary) },
                    placeholder = { Text("e.g. Swiggy", color = TextMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("Reference / UTR", color = TextSecondary) },
                    placeholder = { Text("e.g. UPI Ref", color = TextMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 11. Paid By (Who paid for the expense)
            Text(
                text = "Paid By",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "Me" pill
                val isMeSelected = selectedPayerId == null
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isMeSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isMeSelected) ElectricBlue else GlassBorder),
                    modifier = Modifier.clickable { selectedPayerId = null }
                ) {
                    Text(
                        text = "Me (Default)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMeSelected) ElectricBlueLight else TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                friends.forEach { friend ->
                    val isSelected = selectedPayerId == friend.id
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ElectricBlue else GlassBorder),
                        modifier = Modifier.clickable { selectedPayerId = friend.id }
                    ) {
                        Text(
                            text = friend.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) ElectricBlueLight else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 12. Split with Friends Section
            RupeeFlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (isSplitEnabled) VibrantCyan.copy(alpha = 0.5f) else GlassBorder
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
                            color = TextPrimary
                        )
                        Text(
                            text = if (isMeIncluded) "Your personal share will be counted in spendings"
                            else "You are excluded; full amount owed by friends",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isSplitEnabled,
                        onCheckedChange = { isSplitEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VibrantCyan,
                            checkedTrackColor = CharcoalCard
                        )
                    )
                }

                if (isSplitEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Select Participants to Split With",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Checkbox for "Me (You)"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isMeIncluded = !isMeIncluded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isMeIncluded,
                            onCheckedChange = { isMeIncluded = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = VibrantCyan,
                                uncheckedColor = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Me (You)",
                                color = if (isMeIncluded) TextPrimary else TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isMeIncluded) "Include me in this expense" else "I am OUT of this expense (0 share)",
                                color = if (isMeIncluded) EmeraldMint else AmberGold,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))

                    // Participant checkboxes for Friends
                    friends.forEach { friend ->
                        val isChecked = selectedFriendIds.contains(friend.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedFriendIds = if (isChecked) {
                                        selectedFriendIds - friend.id
                                    } else {
                                        selectedFriendIds + friend.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedFriendIds = if (checked) {
                                        selectedFriendIds + friend.id
                                    } else {
                                        selectedFriendIds - friend.id
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = VibrantCyan,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(friend.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (allParticipantIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Split Method",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MidnightDark)
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
                                        .background(if (isSelected) VibrantCyan.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable { splitMethod = method }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) VibrantCyan else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // A. Equal Split Breakdown
                        if (splitMethod == SplitMethod.EQUAL) {
                            val totalCount = allParticipantIds.size
                            val shares = if (amountPaise > 0L && totalCount > 0) {
                                LedgerCalculator.calculateEqualSplit(amountPaise, totalCount)
                            } else {
                                List(totalCount) { 0L }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MidnightDark,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Equal Split (${totalCount} participants" + (if (!isMeIncluded) ", excluding you" else "") + ")",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldMint,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    allParticipantIds.forEachIndexed { index, pId ->
                                        val pName = if (pId == null) "Me (You)" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                        val sharePaise = shares.getOrElse(index) { 0L }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = pName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (pId == null) EmeraldMint else TextPrimary
                                            )
                                            Text(
                                                text = CurrencyUtils.formatPaiseToRupees(sharePaise),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = VibrantCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // B. Percentage Split Breakdown
                        if (splitMethod == SplitMethod.PERCENTAGE) {
                            val totalPct = allParticipantIds.sumOf { pId ->
                                percentageInputs[pId]?.toDoubleOrNull() ?: 0.0
                            }
                            val pctDiff = 100.0 - totalPct
                            val isPctValid = abs(pctDiff) <= 0.01

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                allParticipantIds.forEach { pId ->
                                    val pName = if (pId == null) "Me (You)" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                    val currentPctStr = percentageInputs[pId] ?: ""
                                    val parsedPct = currentPctStr.toDoubleOrNull() ?: 0.0
                                    val calculatedSharePaise = if (amountPaise > 0L) {
                                        (amountPaise * parsedPct / 100.0).toLong()
                                    } else 0L

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary
                                            )
                                            if (calculatedSharePaise > 0L) {
                                                Text(
                                                    text = "≈ ${CurrencyUtils.formatPaiseToRupees(calculatedSharePaise)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = VibrantCyan
                                                )
                                            }
                                        }

                                        OutlinedTextField(
                                            value = currentPctStr,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() || it == '.' }
                                                percentageInputs = percentageInputs + (pId to filtered)
                                            },
                                            trailingIcon = { Text("%", color = TextSecondary, fontSize = 12.sp) },
                                            placeholder = { Text("0.0", color = TextMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.width(110.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = VibrantCyan,
                                                unfocusedBorderColor = GlassBorder,
                                                focusedContainerColor = CharcoalCard,
                                                unfocusedContainerColor = CharcoalCard
                                            )
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
                                            "Total: ${String.format(java.util.Locale.US, "%.1f", totalPct)}% (${String.format(java.util.Locale.US, "%.1f", pctDiff)}% left)"
                                        } else {
                                            "Total: ${String.format(java.util.Locale.US, "%.1f", totalPct)}% (Exceeds by ${String.format(java.util.Locale.US, "%.1f", -pctDiff)}%)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isPctValid) EmeraldMint else if (pctDiff > 0) AmberGold else CoralRed,
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
                                            Text("Remaining % to Me", style = MaterialTheme.typography.labelSmall, color = ElectricBlueLight)
                                        }
                                    }
                                }
                            }
                        }

                        // C. Custom Rupee Split Breakdown
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
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )

                                        OutlinedTextField(
                                            value = currentRupeeStr,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() || it == '.' }
                                                customRupeeInputs = customRupeeInputs + (pId to filtered)
                                            },
                                            leadingIcon = { Text("₹", color = ElectricBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                            placeholder = { Text("0.00", color = TextMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.width(135.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = VibrantCyan,
                                                unfocusedBorderColor = GlassBorder,
                                                focusedContainerColor = CharcoalCard,
                                                unfocusedContainerColor = CharcoalCard
                                            )
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
                                        color = if (isExactMatched) EmeraldMint else if (diffPaise > 0) AmberGold else CoralRed,
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
                                            Text("Remaining ₹ to Me", style = MaterialTheme.typography.labelSmall, color = ElectricBlueLight)
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
                        abs(totalPct - 100.0) <= 0.01
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

            // Save Changes Button
            Button(
                onClick = {
                    if (amountPaise <= 0L || !isSplitValid) return@Button
                    val finalTitle = title.ifBlank { selectedCategory?.name ?: selectedType.displayName }
                    val categoryId = selectedCategory?.id ?: transaction.categoryId

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
                                            transactionId = transaction.id,
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
                                            transactionId = transaction.id,
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
                                            transactionId = transaction.id,
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

                    // ✅ Preserve transaction.id and transaction.createdAt; update updatedAt
                    val updatedTransaction = transaction.copy(
                        title = finalTitle.trim(),
                        description = description.trim(),
                        amountPaise = amountPaise,
                        categoryId = categoryId,
                        category = selectedCategory,
                        transactionDate = selectedDateTimeMillis,
                        paymentMethod = selectedPaymentMethod,
                        accountSource = accountSource.trim().takeIf { it.isNotBlank() },
                        paidByFriendId = selectedPayerId,
                        paidByName = if (selectedPayerId == null) "Me" else friends.firstOrNull { it.id == selectedPayerId }?.name ?: "Friend",
                        participants = participants,
                        splitMethod = splitMethod,
                        transactionType = selectedType,
                        merchantName = merchantName.trim().takeIf { it.isNotBlank() },
                        referenceNumber = referenceNumber.trim().takeIf { it.isNotBlank() },
                        updatedAt = System.currentTimeMillis()
                    )

                    onSave(updatedTransaction)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = MidnightDark
                ),
                enabled = amountPaise > 0L && isSplitValid
            ) {
                Text(
                    text = "Save Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Confirmation dialog for discarding unsaved changes
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard Changes?", color = TextPrimary) },
            text = { Text("You have unsaved edits. Are you sure you want to discard them?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    }
                ) {
                    Text("Discard", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep Editing", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

