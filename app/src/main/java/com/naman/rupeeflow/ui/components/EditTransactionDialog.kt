package com.naman.rupeeflow.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.naman.rupeeflow.domain.matching.MerchantNormalizer
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.ExpenseAllocationCalculator
import com.naman.rupeeflow.ui.theme.*
import java.util.Calendar
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    categories: List<Category>,
    friends: List<Friend>,
    groups: List<FriendGroup> = emptyList(),
    expenseGroups: List<ExpenseGroup> = emptyList(),
    onCreateExpenseGroup: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onSearchMerchantLocation: (suspend (String) -> TransactionLocation?)? = null,
    onSearchMerchantCategory: (suspend (String) -> Category?)? = null,
    lockToExpenseGroup: Boolean = false
) {
    val context = LocalContext.current

    // 1. Transaction title
    var title by remember { mutableStateOf(transaction.title) }

    // 2. Description
    var description by remember { mutableStateOf(transaction.description) }

    // 3. Amount
    val initialAmountText = if (transaction.amountPaise > 0L) {
        if (transaction.amountPaise % 100L == 0L) {
            (transaction.amountPaise / 100L).toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", transaction.amountPaise / 100.0)
        }
    } else ""
    var amountText by remember { mutableStateOf(initialAmountText) }

    // 4. Category
    var selectedCategory by remember {
        mutableStateOf(categories.firstOrNull { it.id == transaction.categoryId } ?: transaction.category ?: categories.firstOrNull())
    }
    var userManuallySelectedCategory by remember { mutableStateOf(false) }

    var selectedExpenseGroupId by remember { mutableStateOf(transaction.expenseGroupId) }
    var selectedExpenseGroupName by remember { mutableStateOf(transaction.expenseGroupName) }

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
    val preselectedTrip = remember(transaction.expenseGroupId, expenseGroups) {
        if (transaction.expenseGroupId != null) expenseGroups.firstOrNull { it.id == transaction.expenseGroupId } else null
    }
    val defaultFriendIds = remember(preselectedTrip) {
        if (!initialHasParticipants && preselectedTrip != null && preselectedTrip.memberFriendIds.isNotEmpty()) {
            preselectedTrip.memberFriendIds.toSet()
        } else {
            transaction.participants.mapNotNull { it.friendId }.toSet()
        }
    }
    var isSplitEnabled by remember {
        mutableStateOf(initialHasParticipants || (transaction.expenseGroupId != null && defaultFriendIds.isNotEmpty()))
    }
    var isMeIncluded by remember {
        mutableStateOf(
            if (initialHasParticipants) transaction.participants.any { it.friendId == null } else true
        )
    }
    var selectedFriendIds by remember {
        mutableStateOf(defaultFriendIds)
    }
    var selectedGroupFilterId by remember { mutableStateOf(transaction.groupId) }
    // ✅ Preserve edited bill attachment path across biometric prompt and external pickers
    var billImagePath by rememberSaveable { mutableStateOf(transaction.billImagePath) }
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
                val formattedShare = if (p.shareAmountPaise > 0L) {
                    if (p.shareAmountPaise % 100L == 0L) {
                        (p.shareAmountPaise / 100L).toString()
                    } else {
                        String.format(java.util.Locale.US, "%.2f", p.shareAmountPaise / 100.0)
                    }
                } else ""
                p.friendId to formattedShare
            }
        )
    }

    val initialDeliveryItem = transaction.items.firstOrNull {
        it.name.contains("Delivery", ignoreCase = true) || it.name.contains("Handling", ignoreCase = true)
    }
    var deliveryFeeRupees by remember {
        mutableStateOf(
            if (initialDeliveryItem != null && initialDeliveryItem.totalAmountPaise > 0L) {
                if (initialDeliveryItem.totalAmountPaise % 100L == 0L) (initialDeliveryItem.totalAmountPaise / 100L).toString()
                else String.format(java.util.Locale.US, "%.2f", initialDeliveryItem.totalAmountPaise / 100.0)
            } else ""
        )
    }

    var itemEntries by remember {
        mutableStateOf(
            if (transaction.items.isNotEmpty()) {
                val regularItems = transaction.items.filter { it != initialDeliveryItem }
                if (regularItems.isNotEmpty()) {
                    regularItems.map { itm ->
                        val assignedSet = itm.allocations.map { it.friendId }.toSet()
                        ItemWiseEntryState(
                            tempId = itm.id,
                            name = itm.name,
                            quantity = itm.quantity,
                            unitPriceRupees = if (itm.unitPricePaise % 100L == 0L) (itm.unitPricePaise / 100L).toString() else String.format(java.util.Locale.US, "%.2f", itm.unitPricePaise / 100.0),
                            assignedParticipantIds = assignedSet
                        )
                    }
                } else {
                    listOf(
                        ItemWiseEntryState(
                            name = "Item 1",
                            quantity = 1,
                            unitPriceRupees = "",
                            assignedParticipantIds = if (isMeIncluded) setOf(null) else emptySet()
                        )
                    )
                }
            } else {
                listOf(
                    ItemWiseEntryState(
                        name = "Item 1",
                        quantity = 1,
                        unitPriceRupees = "",
                        assignedParticipantIds = if (isMeIncluded) setOf(null) else emptySet()
                    )
                )
            }
        )
    }

    // 14. Merchant
    var merchantName by remember { mutableStateOf(transaction.merchantName ?: "") }

    // 15. Reference number & UPI Reference Number
    var referenceNumber by remember { mutableStateOf(transaction.referenceNumber ?: transaction.upiReferenceNumber ?: "") }
    var upiReferenceNumber by remember { mutableStateOf(transaction.upiReferenceNumber ?: transaction.referenceNumber ?: "") }

    // 16. Expense Spreading across days
    var isSpreadExpense by rememberSaveable { mutableStateOf(transaction.isSpread) }
    var spreadStartDateMillis by rememberSaveable { mutableStateOf(transaction.spreadStartDate ?: transaction.transactionDate) }
    var spreadDurationDays by rememberSaveable { mutableIntStateOf(if (transaction.spreadDurationDays > 1) transaction.spreadDurationDays else 5) }

    // Location state & merchant memory
    var attachedLocation by remember { mutableStateOf<TransactionLocation?>(transaction.location) }
    var merchantSuggestion by remember { mutableStateOf<TransactionLocation?>(null) }

    LaunchedEffect(merchantName, title) {
        if (userManuallySelectedCategory) return@LaunchedEffect
        val candidate = merchantName.trim().ifBlank { title.trim() }
        if (candidate.length >= 2 && onSearchMerchantCategory != null) {
            kotlinx.coroutines.delay(150)
            val suggestedCat = onSearchMerchantCategory(candidate)
            if (suggestedCat != null && !userManuallySelectedCategory) {
                selectedCategory = suggestedCat
            }
        }
    }

    LaunchedEffect(merchantName) {
        val candidate = merchantName.trim()
        if (candidate.isNotBlank() && attachedLocation == null && onSearchMerchantLocation != null) {
            merchantSuggestion = onSearchMerchantLocation(candidate)
        }
    }

    var isSaving by remember { mutableStateOf(false) }
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
        splitMethod, merchantName, referenceNumber, upiReferenceNumber, attachedLocation,
        isSpreadExpense, spreadDurationDays, spreadStartDateMillis
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
                referenceNumber != (transaction.referenceNumber ?: "") ||
                upiReferenceNumber != (transaction.upiReferenceNumber ?: "") ||
                attachedLocation != transaction.location ||
                isSpreadExpense != transaction.isSpread ||
                (isSpreadExpense && (spreadDurationDays != transaction.spreadDurationDays || spreadStartDateMillis != (transaction.spreadStartDate ?: transaction.transactionDate)))
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (hasUnsavedChanges) {
                showDiscardConfirm = true
            } else {
                onDismiss()
            }
        },
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
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary
                )
                IconButton(onClick = {
                    if (hasUnsavedChanges) showDiscardConfirm = true else onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Transaction Type Selector (Expense, Income, Refund, Transfer)
            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.surfaceSecondary)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    TransactionType.EXPENSE to AppTheme.colors.expenseRed,
                    TransactionType.INCOME to AppTheme.colors.incomeGreen,
                    TransactionType.REFUND to AppTheme.colors.info,
                    TransactionType.TRANSFER to AppTheme.colors.primaryAccent
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
                            color = if (isSelected) color else AppTheme.colors.textSecondary,
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
                label = { Text("Title", color = AppTheme.colors.textSecondary) },
                placeholder = { Text("e.g. Dinner, Rent, Swiggy", color = AppTheme.colors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = rupeeFlowTextFieldColors(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Description Input Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Notes (Optional)", color = AppTheme.colors.textSecondary) },
                placeholder = { Text("Additional details about this transaction", color = AppTheme.colors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                colors = rupeeFlowTextFieldColors(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Category Selector Row
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategorySelectorRow(
                categories = categories,
                selectedCategory = selectedCategory,
                onSelect = {
                    selectedCategory = it
                    userManuallySelectedCategory = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Expense Group (Optional) Selector
            ExpenseGroupSelector(
                expenseGroups = expenseGroups,
                selectedExpenseGroupId = selectedExpenseGroupId,
                selectedExpenseGroupName = selectedExpenseGroupName,
                enabled = !lockToExpenseGroup,
                onSelectGroup = { group ->
                    selectedExpenseGroupId = group?.id
                    selectedExpenseGroupName = group?.name
                    if (group != null && group.memberFriendIds.isNotEmpty() && !initialHasParticipants && selectedFriendIds.isEmpty()) {
                        isSplitEnabled = true
                        selectedFriendIds = group.memberFriendIds.toSet()
                        isMeIncluded = true
                    }
                },
                onCreateNewGroup = { newName ->
                    onCreateExpenseGroup?.invoke(newName)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Transaction Date & Time Controls
            Text(
                text = "Transaction Date & Time",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Date Picker Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppTheme.colors.cardBackground,
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
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
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = DateTimeUtils.formatDate(selectedDateTimeMillis, "dd MMM yyyy"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                }

                // Time Picker Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppTheme.colors.cardBackground,
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
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
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = DateTimeUtils.formatTime(selectedDateTimeMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expense Duration Spreading Section (Only for Expenses)
            if (selectedType == TransactionType.EXPENSE) {
                ExpenseSpreadingSection(
                    isSpreadEnabled = isSpreadExpense,
                    onSpreadEnabledChange = { enabled ->
                        isSpreadExpense = enabled
                        if (enabled && spreadStartDateMillis == 0L) {
                            spreadStartDateMillis = selectedDateTimeMillis
                        }
                    },
                    startDateMillis = spreadStartDateMillis,
                    onStartDateChange = { newStart ->
                        spreadStartDateMillis = newStart
                        selectedDateTimeMillis = newStart
                    },
                    durationDays = spreadDurationDays,
                    onDurationDaysChange = { spreadDurationDays = it },
                    amountPaise = amountPaise
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 7. Payment Method Selector Row
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

            // 8. Account Source (Bank / Card / Cash)
            OutlinedTextField(
                value = accountSource,
                onValueChange = { accountSource = it },
                label = { Text("Account Source / Bank (Optional)", color = AppTheme.colors.textSecondary) },
                placeholder = { Text("e.g. HDFC, ICICI, SBI, Cash Wallet", color = AppTheme.colors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = rupeeFlowTextFieldColors(),
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
                    label = { Text("Merchant", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. Swiggy", color = AppTheme.colors.textMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = upiReferenceNumber,
                    onValueChange = {
                        upiReferenceNumber = it
                        referenceNumber = it
                    },
                    label = { Text("UPI Reference / UTR", color = AppTheme.colors.textSecondary) },
                    placeholder = { Text("e.g. 12-digit UTR ref no.", color = AppTheme.colors.textMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = rupeeFlowTextFieldColors(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Attached Bill / Receipt Photo
            BillAttachmentSection(
                billImagePath = billImagePath,
                onImageSelected = { billImagePath = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 11. Paid By Dropdown (Preserves existing transaction payer)
            PayerDropdownSelector(
                friends = friends,
                selectedPayerId = selectedPayerId,
                onPayerSelect = { selectedPayerId = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 12. Split with Friends Section
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

                    // Group-First Multi-Select Split Selector
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
                                SplitMethod.EXACT_AMOUNT to "Custom ₹",
                                SplitMethod.ITEM_WISE to "By Items"
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
                            color = AppTheme.colors.surfaceSecondary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Equal Split (${totalCount} participants" + (if (!isMeIncluded) ", excluding you" else "") + ")",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.incomeGreen,
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
                                            color = if (pId == null) AppTheme.colors.incomeGreen else AppTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = CurrencyUtils.formatPaiseToRupees(sharePaise),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = AppTheme.colors.primaryAccent
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
                                            color = AppTheme.colors.textPrimary
                                        )
                                        if (calculatedSharePaise > 0L) {
                                            Text(
                                                text = "≈ ${CurrencyUtils.formatPaiseToRupees(calculatedSharePaise)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.primaryAccent
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = currentPctStr,
                                        onValueChange = { input ->
                                            val filtered = input.filter { it.isDigit() || it == '.' }
                                            percentageInputs = percentageInputs + (pId to filtered)
                                        },
                                        trailingIcon = { Text("%", color = AppTheme.colors.textSecondary, fontSize = 12.sp) },
                                        placeholder = { Text("0.0", color = AppTheme.colors.textMuted) },
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
                                        "Total: ${String.format(java.util.Locale.US, "%.1f", totalPct)}% (${String.format(java.util.Locale.US, "%.1f", pctDiff)}% left)"
                                    } else {
                                        "Total: ${String.format(java.util.Locale.US, "%.1f", totalPct)}% (Exceeds by ${String.format(java.util.Locale.US, "%.1f", -pctDiff)}%)"
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

                    // D. Item-Wise Expense Split Breakdown
                    if (splitMethod == SplitMethod.ITEM_WISE) {
                        val itemsTotalPaise = itemEntries.sumOf {
                            it.quantity.coerceAtLeast(1) * CurrencyUtils.parseInputToPaise(it.unitPriceRupees)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemEntries.forEachIndexed { itemIdx, itemEntry ->
                                val itemTotalPaise = itemEntry.quantity.coerceAtLeast(1) * CurrencyUtils.parseInputToPaise(itemEntry.unitPriceRupees)
                                val assignedCount = itemEntry.assignedParticipantIds.size
                                val perPersonEstimate = if (assignedCount > 0 && itemTotalPaise > 0L) {
                                    itemTotalPaise / assignedCount
                                } else 0L

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppTheme.colors.surfaceSecondary,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Item #${itemIdx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.primaryAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (itemEntries.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        itemEntries = itemEntries.toMutableList().apply { removeAt(itemIdx) }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove Item",
                                                        tint = CoralRed,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        OutlinedTextField(
                                            value = itemEntry.name,
                                            onValueChange = { newName ->
                                                itemEntries = itemEntries.toMutableList().apply {
                                                    this[itemIdx] = itemEntry.copy(name = newName)
                                                }
                                            },
                                            placeholder = { Text("Item name (e.g. Pizza, Drinks)", color = AppTheme.colors.textMuted, fontSize = 13.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = rupeeFlowTextFieldColors()
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Quantity Selector (- / +)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(AppTheme.colors.surfaceElevated)
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (itemEntry.quantity > 1) {
                                                            itemEntries = itemEntries.toMutableList().apply {
                                                                this[itemIdx] = itemEntry.copy(quantity = itemEntry.quantity - 1)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Text("−", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                                }
                                                Text(
                                                    text = "${itemEntry.quantity}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppTheme.colors.textPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        itemEntries = itemEntries.toMutableList().apply {
                                                            this[itemIdx] = itemEntry.copy(quantity = itemEntry.quantity + 1)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Text("+", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                                }
                                            }

                                            // Unit Price Input
                                            OutlinedTextField(
                                                value = itemEntry.unitPriceRupees,
                                                onValueChange = { priceInput ->
                                                    val filtered = priceInput.filter { it.isDigit() || it == '.' }
                                                    itemEntries = itemEntries.toMutableList().apply {
                                                        this[itemIdx] = itemEntry.copy(unitPriceRupees = filtered)
                                                    }
                                                },
                                                leadingIcon = { Text("₹", color = AppTheme.colors.primaryAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                                placeholder = { Text("0.00", color = AppTheme.colors.textMuted) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = rupeeFlowTextFieldColors()
                                            )

                                            // Item Total
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = CurrencyUtils.formatPaiseToRupees(itemTotalPaise),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                                if (assignedCount > 1 && perPersonEstimate > 0L) {
                                                    Text(
                                                        text = "~${CurrencyUtils.formatPaiseToRupees(perPersonEstimate)}/ea",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = AppTheme.colors.incomeGreen
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Assigned Participants Selector
                                        Text(
                                            text = "Assigned to:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            allParticipantIds.forEach { pId ->
                                                val isAssigned = itemEntry.assignedParticipantIds.contains(pId)
                                                val pName = if (pId == null) "Me" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isAssigned) AppTheme.colors.primaryAccent.copy(alpha = 0.2f) else AppTheme.colors.surfaceElevated,
                                                    border = BorderStroke(1.dp, if (isAssigned) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            val updatedSet = if (isAssigned) {
                                                                itemEntry.assignedParticipantIds - pId
                                                            } else {
                                                                itemEntry.assignedParticipantIds + pId
                                                            }
                                                            itemEntries = itemEntries.toMutableList().apply {
                                                                this[itemIdx] = itemEntry.copy(assignedParticipantIds = updatedSet)
                                                            }
                                                        }
                                                ) {
                                                    Text(
                                                        text = if (isAssigned) "✓ $pName" else pName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isAssigned) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                                                        fontWeight = if (isAssigned) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Add Item Button
                            OutlinedButton(
                                onClick = {
                                    itemEntries = itemEntries + ItemWiseEntryState(
                                        name = "Item ${itemEntries.size + 1}",
                                        quantity = 1,
                                        unitPriceRupees = "",
                                        assignedParticipantIds = if (isMeIncluded) setOf(null) else emptySet()
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AppTheme.colors.primaryAccent.copy(alpha = 0.5f))
                            ) {
                                Text("+ Add Another Item", color = AppTheme.colors.primaryAccent, fontWeight = FontWeight.SemiBold)
                            }

                            // Handling Fee / Delivery Fees Option
                            val includedParticipantIds = allParticipantIds.filter { pId ->
                                itemEntries.any { it.assignedParticipantIds.contains(pId) }
                            }.ifEmpty { allParticipantIds }
                            val deliveryFeePaise = CurrencyUtils.parseInputToPaise(deliveryFeeRupees)
                            val feeSplits = if (deliveryFeePaise > 0L && includedParticipantIds.isNotEmpty()) {
                                LedgerCalculator.calculateEqualSplit(deliveryFeePaise, includedParticipantIds.size)
                            } else emptyList()
                            val feeShareByParticipant = includedParticipantIds.mapIndexed { idx, pId ->
                                pId to feeSplits.getOrElse(idx) { 0L }
                            }.toMap()

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceSecondary,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Delivery / Handling Fee",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.primaryAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (deliveryFeePaise > 0L && includedParticipantIds.isNotEmpty()) {
                                            Text(
                                                text = "+${CurrencyUtils.formatPaiseToRupees(deliveryFeePaise / includedParticipantIds.size)}/person",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppTheme.colors.incomeGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Evenly spread across all ${includedParticipantIds.size} member${if (includedParticipantIds.size != 1) "s" else ""} included in this expense",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = deliveryFeeRupees,
                                        onValueChange = { priceInput ->
                                            deliveryFeeRupees = priceInput.filter { it.isDigit() || it == '.' }
                                        },
                                        leadingIcon = { Text("₹", color = AppTheme.colors.primaryAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                        placeholder = { Text("0.00 (optional delivery / handling fee)", color = AppTheme.colors.textMuted, fontSize = 13.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = rupeeFlowTextFieldColors()
                                    )
                                }
                            }

                            // Summary of calculated participant shares
                            val tempShares = mutableMapOf<Long?, Long>()
                            allParticipantIds.forEach { tempShares[it] = 0L }
                            itemEntries.forEach { itm ->
                                val total = itm.quantity.coerceAtLeast(1) * CurrencyUtils.parseInputToPaise(itm.unitPriceRupees)
                                val assigned = itm.assignedParticipantIds.toList()
                                if (assigned.isNotEmpty() && total > 0L) {
                                    val splits = LedgerCalculator.calculateEqualSplit(total, assigned.size)
                                    assigned.forEachIndexed { i, pId ->
                                        tempShares[pId] = (tempShares[pId] ?: 0L) + splits[i]
                                    }
                                }
                            }
                            // Spread delivery fee to participating members
                            feeShareByParticipant.forEach { (pId, feeShare) ->
                                tempShares[pId] = (tempShares[pId] ?: 0L) + feeShare
                            }

                            val finalCalculatedTotalPaise = itemsTotalPaise + deliveryFeePaise

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surfaceSecondary,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Calculated Total: ${CurrencyUtils.formatPaiseToRupees(finalCalculatedTotalPaise)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = if (deliveryFeePaise > 0L) "${itemEntries.size} items + Fee" else "${itemEntries.size} items",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    allParticipantIds.forEach { pId ->
                                        val pName = if (pId == null) "Me (Your Share)" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                        val share = tempShares[pId] ?: 0L
                                        val personFee = feeShareByParticipant[pId] ?: 0L
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(pName, style = MaterialTheme.typography.bodySmall, color = if (pId == null) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary)
                                                if (personFee > 0L) {
                                                    Text(
                                                        text = "incl. ${CurrencyUtils.formatPaiseToRupees(personFee)} fee",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = AppTheme.colors.textMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                            Text(
                                                CurrencyUtils.formatPaiseToRupees(share),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (share > 0L) (if (pId == null) AppTheme.colors.incomeGreen else AppTheme.colors.primaryAccent) else AppTheme.colors.textMuted
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

        Spacer(modifier = Modifier.height(16.dp))

        // Location Picker (Offline GPS, Coordinates, Maps Link, Merchant Memory)
        TransactionLocationPicker(
            location = attachedLocation,
            onLocationChanged = { attachedLocation = it },
            merchantSuggestion = merchantSuggestion,
            merchantName = merchantName.trim().takeIf { it.isNotBlank() },
            onApplyMerchantSuggestion = {
                attachedLocation = merchantSuggestion?.copy(locationSource = LocationSource.HISTORICAL_LOCATION_SUGGESTION)
                merchantSuggestion = null
            },
            onDismissMerchantSuggestion = { merchantSuggestion = null }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Validation Check for Split
        val isSplitValid = remember(isSplitEnabled, allParticipantIds, splitMethod, amountPaise, percentageInputs, customRupeeInputs, itemEntries) {
            if (!isSplitEnabled || allParticipantIds.isEmpty()) {
                true
            } else when (splitMethod) {
                SplitMethod.EQUAL -> true
                SplitMethod.PERCENTAGE -> {
                    val totalPct = allParticipantIds.sumOf { percentageInputs[it]?.toDoubleOrNull() ?: 0.0 }
                    abs(totalPct - 100.0) <= 0.5
                }
                SplitMethod.EXACT_AMOUNT, SplitMethod.CUSTOM -> {
                    val sumPaise = allParticipantIds.sumOf { CurrencyUtils.parseInputToPaise(customRupeeInputs[it] ?: "") }
                    sumPaise == amountPaise
                }
                SplitMethod.SHARES -> true
                SplitMethod.ITEM_WISE -> {
                    val itemsTotalPaise = itemEntries.sumOf {
                        it.quantity.coerceAtLeast(1) * CurrencyUtils.parseInputToPaise(it.unitPriceRupees)
                    }
                    val deliveryFeePaise = CurrencyUtils.parseInputToPaise(deliveryFeeRupees)
                    itemEntries.isNotEmpty() &&
                    (itemsTotalPaise + deliveryFeePaise) > 0L &&
                    itemEntries.all { it.name.isNotBlank() && CurrencyUtils.parseInputToPaise(it.unitPriceRupees) > 0L && it.assignedParticipantIds.isNotEmpty() }
                }
            }
        }

        if (!isSplitValid && isSplitEnabled && allParticipantIds.isNotEmpty()) {
            Text(
                text = when (splitMethod) {
                    SplitMethod.PERCENTAGE -> "Please adjust percentages so the total equals 100%."
                    SplitMethod.ITEM_WISE -> "Please ensure all items have a name, unit price, and at least one assigned participant."
                    else -> "Please ensure the sum of participant shares strictly equals the total bill."
                },
                style = MaterialTheme.typography.labelSmall,
                color = AmberGold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val deliveryFeePaise = if (isSplitEnabled && splitMethod == SplitMethod.ITEM_WISE) CurrencyUtils.parseInputToPaise(deliveryFeeRupees) else 0L
        val itemsTotalPaise = if (isSplitEnabled && splitMethod == SplitMethod.ITEM_WISE) {
            itemEntries.sumOf { it.quantity.coerceAtLeast(1) * CurrencyUtils.parseInputToPaise(it.unitPriceRupees) }
        } else 0L
        val effectiveAmountPaise = if (isSplitEnabled && splitMethod == SplitMethod.ITEM_WISE && (itemsTotalPaise + deliveryFeePaise) > 0L) (itemsTotalPaise + deliveryFeePaise) else amountPaise

        // Save Changes Button
        Button(
            onClick = {
                if (isSaving || effectiveAmountPaise <= 0L || !isSplitValid) return@Button
                isSaving = true
                val finalTitle = title.ifBlank { selectedCategory?.name ?: selectedType.displayName }
                val categoryId = selectedCategory?.id ?: transaction.categoryId

                // Build participants
                val participants = mutableListOf<TransactionParticipant>()
                val domainItems = mutableListOf<TransactionItem>()

                if (isSplitEnabled && allParticipantIds.isNotEmpty()) {
                    when (splitMethod) {
                        SplitMethod.EQUAL -> {
                            val shares = LedgerCalculator.calculateEqualSplit(effectiveAmountPaise, allParticipantIds.size)
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
                            val shares = LedgerCalculator.calculatePercentageSplit(effectiveAmountPaise, pcts)
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
                        SplitMethod.EXACT_AMOUNT, SplitMethod.CUSTOM -> {
                            allParticipantIds.forEach { pId ->
                                val pName = if (pId == null) "Me" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                val sharePaise = CurrencyUtils.parseInputToPaise(customRupeeInputs[pId] ?: "")
                                val pct = if (effectiveAmountPaise > 0L) (sharePaise.toDouble() / effectiveAmountPaise.toDouble()) * 100.0 else 0.0
                                participants.add(
                                    TransactionParticipant(
                                        transactionId = transaction.id,
                                        friendId = pId,
                                        friendName = pName,
                                        shareAmountPaise = sharePaise,
                                        percentage = pct,
                                        splitMethod = splitMethod
                                    )
                                )
                            }
                        }
                        SplitMethod.SHARES -> {
                            val weights = allParticipantIds.map { 1 }
                            val shares = LedgerCalculator.calculateSharesSplit(effectiveAmountPaise, weights)
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
                                        splitMethod = SplitMethod.SHARES
                                    )
                                )
                            }
                        }
                        SplitMethod.ITEM_WISE -> {
                            val deliveryFeePaise = CurrencyUtils.parseInputToPaise(deliveryFeeRupees)
                            val includedParticipantIds = allParticipantIds.filter { pId ->
                                itemEntries.any { it.assignedParticipantIds.contains(pId) }
                            }.ifEmpty { allParticipantIds }
                            val feeSplits = if (deliveryFeePaise > 0L && includedParticipantIds.isNotEmpty()) {
                                LedgerCalculator.calculateEqualSplit(deliveryFeePaise, includedParticipantIds.size)
                            } else emptyList()
                            val feeShareByParticipant = includedParticipantIds.mapIndexed { idx, pId ->
                                pId to feeSplits.getOrElse(idx) { 0L }
                            }.toMap()

                            val participantSharesMap = mutableMapOf<Long?, Long>()
                            allParticipantIds.forEach { participantSharesMap[it] = 0L }

                            itemEntries.forEach { itemEntry ->
                                val itemTotal = itemEntry.quantity.coerceAtLeast(1) * CurrencyUtils.parseInputToPaise(itemEntry.unitPriceRupees)
                                val assignedList = itemEntry.assignedParticipantIds.toList()
                                val itemSplits = if (assignedList.isNotEmpty() && itemTotal > 0L) {
                                    LedgerCalculator.calculateEqualSplit(itemTotal, assignedList.size)
                                } else emptyList()

                                val allocs = assignedList.mapIndexed { idx, pId ->
                                    val share = itemSplits.getOrElse(idx) { 0L }
                                    participantSharesMap[pId] = (participantSharesMap[pId] ?: 0L) + share
                                    ItemAllocation(
                                        id = 0L,
                                        itemId = 0L,
                                        transactionId = transaction.id,
                                        friendId = pId,
                                        allocatedAmountPaise = share
                                    )
                                }

                                domainItems.add(
                                    TransactionItem(
                                        id = itemEntry.tempId ?: 0L,
                                        transactionId = transaction.id,
                                        name = itemEntry.name.trim().ifBlank { "Item" },
                                        quantity = itemEntry.quantity.coerceAtLeast(1),
                                        unitPricePaise = CurrencyUtils.parseInputToPaise(itemEntry.unitPriceRupees),
                                        totalAmountPaise = itemTotal,
                                        allocations = allocs
                                    )
                                )
                            }

                            // If delivery & handling fee was entered, add as a transaction item and include allocations
                            if (deliveryFeePaise > 0L && includedParticipantIds.isNotEmpty()) {
                                val feeAllocs = includedParticipantIds.mapIndexed { idx, pId ->
                                    ItemAllocation(
                                        id = 0L,
                                        itemId = 0L,
                                        transactionId = transaction.id,
                                        friendId = pId,
                                        allocatedAmountPaise = feeSplits.getOrElse(idx) { 0L }
                                    )
                                }
                                domainItems.add(
                                    TransactionItem(
                                        id = initialDeliveryItem?.id ?: 0L,
                                        transactionId = transaction.id,
                                        name = "Delivery & Handling Fee",
                                        quantity = 1,
                                        unitPricePaise = deliveryFeePaise,
                                        totalAmountPaise = deliveryFeePaise,
                                        allocations = feeAllocs
                                    )
                                )
                            }

                            allParticipantIds.forEach { pId ->
                                val pName = if (pId == null) "Me" else friends.firstOrNull { it.id == pId }?.name ?: "Friend"
                                val pShare = (participantSharesMap[pId] ?: 0L) + (feeShareByParticipant[pId] ?: 0L)
                                val pct = if (effectiveAmountPaise > 0L) (pShare.toDouble() / effectiveAmountPaise.toDouble()) * 100.0 else 0.0
                                participants.add(
                                    TransactionParticipant(
                                        transactionId = transaction.id,
                                        friendId = pId,
                                        friendName = pName,
                                        shareAmountPaise = pShare,
                                        percentage = pct,
                                        splitMethod = SplitMethod.ITEM_WISE
                                    )
                                )
                            }
                        }
                    }
                }

                val cleanMerchant = merchantName.trim().takeIf { it.isNotBlank() }
                val cleanRef = referenceNumber.trim().takeIf { it.isNotBlank() } ?: upiReferenceNumber.trim().takeIf { it.isNotBlank() }
                val cleanUpiRef = upiReferenceNumber.trim().takeIf { it.isNotBlank() } ?: referenceNumber.trim().takeIf { it.isNotBlank() }

                val isSpreadActive = selectedType == TransactionType.EXPENSE && isSpreadExpense && spreadDurationDays > 1
                val spreadStart = if (isSpreadActive) ExpenseAllocationCalculator.normalizeToStartOfDay(spreadStartDateMillis) else null
                val spreadEnd = if (isSpreadActive) ExpenseAllocationCalculator.calculateEndDate(spreadStart!!, spreadDurationDays) else null
                val domainAllocations = if (isSpreadActive) {
                    val calculated = ExpenseAllocationCalculator.calculateAllocations(
                        amountPaise = effectiveAmountPaise,
                        durationDays = spreadDurationDays,
                        startDateMillis = spreadStart!!
                    )
                    calculated.map {
                        ExpenseAllocation(
                            id = 0L,
                            transactionId = transaction.id,
                            allocationDate = it.allocationDate,
                            amountPaise = it.amountPaise
                        )
                    }
                } else emptyList()

                // Preserve transaction.id and transaction.createdAt; update updatedAt
                val updatedTransaction = transaction.copy(
                    title = finalTitle.trim(),
                    description = description.trim(),
                    amountPaise = effectiveAmountPaise,
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
                    merchantName = cleanMerchant,
                    merchantNormalized = cleanMerchant?.let { MerchantNormalizer.normalize(it).takeIf { n -> n.isNotBlank() } },
                    rawMerchant = transaction.rawMerchant ?: cleanMerchant,
                    sourcePackage = transaction.sourcePackage,
                    referenceNumber = cleanRef,
                    upiReferenceNumber = cleanUpiRef,
                    groupId = selectedGroupFilterId,
                    expenseGroupId = selectedExpenseGroupId,
                    expenseGroupName = selectedExpenseGroupName,
                    billImagePath = billImagePath,
                    latitude = attachedLocation?.latitude,
                    longitude = attachedLocation?.longitude,
                    accuracyMeters = attachedLocation?.accuracyMeters,
                    locationCapturedAt = attachedLocation?.locationCapturedAt,
                    locationSource = attachedLocation?.locationSource,
                    locationLabel = attachedLocation?.locationLabel,
                    googleMapsUrl = attachedLocation?.googleMapsUrl,
                    isSpread = isSpreadActive,
                    spreadDurationDays = if (isSpreadActive) spreadDurationDays else 1,
                    spreadStartDate = spreadStart,
                    spreadEndDate = spreadEnd,
                    allocations = domainAllocations,
                    items = if (isSplitEnabled && splitMethod == SplitMethod.ITEM_WISE) domainItems else transaction.items,
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
                containerColor = AppTheme.colors.primaryAccent,
                contentColor = Color.White
            ),
            enabled = !isSaving && effectiveAmountPaise > 0L && isSplitValid
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
        title = { Text("Discard Changes?", color = AppTheme.colors.textPrimary) },
        text = { Text("You have unsaved edits. Are you sure you want to discard them?", color = AppTheme.colors.textSecondary) },
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
                Text("Keep Editing", color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}
}

