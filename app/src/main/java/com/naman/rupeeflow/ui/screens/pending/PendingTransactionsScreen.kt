package com.naman.rupeeflow.ui.screens.pending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.matching.HistoricalCandidateMatch
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.location.LocationIntentHelper
import com.naman.rupeeflow.ui.components.FastAddExpenseSheet
import com.naman.rupeeflow.ui.components.RupeeFlowCard
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Screen for reviewing detected SMS and notification transactions.
 *
 * Requirements:
 * - 5 distinct sections/tabs: Pending, Approved, Ignored, Unknown, All
 * - Clear status badges: Pending (Amber), Added (Green), Ignored (Muted/Coral), Unknown (Gray)
 * - Distinct UPI Lite Top-up UI badge and accounting safeguards
 * - Offline transaction location indicator and maps view
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTransactionsScreen(
    viewModel: PendingTransactionsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedDetectedForAdd by remember { mutableStateOf<PendingTransaction?>(null) }
    var selectedDetectedForDetail by remember { mutableStateOf<PendingTransaction?>(null) }
    var selectedFilter by remember { mutableStateOf("PENDING") } // "PENDING", "APPROVED", "IGNORED", "UNKNOWN", "ALL"
    var showUpiLiteConfirmItem by remember { mutableStateOf<PendingTransaction?>(null) }

    // Intelligent Transaction Matching state
    var matchDetected by remember { mutableStateOf<PendingTransaction?>(null) }
    var matchCandidates by remember { mutableStateOf<List<HistoricalCandidateMatch>>(emptyList()) }
    var selectedMatchDraft by remember { mutableStateOf<HistoricalCandidateMatch?>(null) }

    val handleStartAddExpense: (PendingTransaction) -> Unit = { item ->
        coroutineScope.launch {
            // Check if location capture on review is configured and item has no location yet
            var attachedLoc = item.location
            if (attachedLoc == null && viewModel.getLocationCaptureMode() == LocationCaptureMode.ON_REVIEW) {
                attachedLoc = com.naman.rupeeflow.location.OfflineLocationManager.getLastKnownLocation(context)
            }

            val itemWithLoc = if (attachedLoc != null && !item.hasLocation) {
                item.copy(
                    latitude = attachedLoc.latitude,
                    longitude = attachedLoc.longitude,
                    accuracyMeters = attachedLoc.accuracyMeters,
                    locationCapturedAt = attachedLoc.locationCapturedAt,
                    locationSource = attachedLoc.locationSource,
                    googleMapsUrl = attachedLoc.googleMapsUrl
                )
            } else item

            val candidates = viewModel.getSimilarCandidates(itemWithLoc)
            if (candidates.isNotEmpty()) {
                matchCandidates = candidates
                matchDetected = itemWithLoc
            } else {
                selectedMatchDraft = null
                selectedDetectedForAdd = itemWithLoc
            }
        }
    }

    val onTriggerAddExpense: (PendingTransaction) -> Unit = { item ->
        if (item.detectedType == TransactionType.UPI_LITE_TOPUP) {
            showUpiLiteConfirmItem = item
        } else {
            handleStartAddExpense(item)
        }
    }

    // Search and filter state for Ignored, Unknown & All tabs
    var searchQuery by remember { mutableStateOf("") }

    val pendingCount = state.pendingTransactions.size
    val approvedCount = state.addedTransactions.size
    val ignoredCount = state.ignoredTransactions.size
    val unknownCount = state.unknownTransactions.size
    val totalCount = state.allDetectedTransactions.size

    val displayList = remember(state.allDetectedTransactions, selectedFilter, searchQuery) {
        val baseList = when (selectedFilter) {
            "PENDING" -> state.pendingTransactions
            "APPROVED" -> state.addedTransactions
            "IGNORED" -> state.ignoredTransactions
            "UNKNOWN" -> state.unknownTransactions
            else -> state.allDetectedTransactions
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.trim().lowercase()
            baseList.filter { item ->
                (item.detectedMerchant?.lowercase()?.contains(q) == true) ||
                (item.detectedBank?.lowercase()?.contains(q) == true) ||
                (item.referenceNumber?.lowercase()?.contains(q) == true) ||
                item.rawContent.lowercase().contains(q) ||
                CurrencyUtils.formatPaiseToRupees(item.detectedAmountPaise).contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detected Transactions", color = AppTheme.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 5-Filter Pills Row: Pending, Approved, Ignored, Unknown, All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "PENDING" to "Pending ($pendingCount)",
                    "APPROVED" to "Approved ($approvedCount)",
                    "IGNORED" to "Ignored ($ignoredCount)",
                    "UNKNOWN" to "Unknown ($unknownCount)",
                    "ALL" to "All ($totalCount)"
                ).forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) AppTheme.colors.accentContainer else AppTheme.colors.cardBackground,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                        ),
                        modifier = Modifier.clickable {
                            selectedFilter = key
                            searchQuery = ""
                        }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Search Bar for Ignored, Unknown & All tabs (or when searching any list)
            if (selectedFilter == "IGNORED" || selectedFilter == "UNKNOWN" || selectedFilter == "ALL" || (searchQuery.isNotBlank())) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                when (selectedFilter) {
                                    "IGNORED" -> "Search ignored transactions..."
                                    "UNKNOWN" -> "Search unrecognized messages..."
                                    else -> "Search by merchant, bank or SMS..."
                                },
                                color = AppTheme.colors.textMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.primaryAccent,
                            unfocusedBorderColor = AppTheme.colors.cardBorder,
                            focusedTextColor = AppTheme.colors.textPrimary,
                            unfocusedTextColor = AppTheme.colors.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Empty States according to selected section
            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        val emptyIcon = when (selectedFilter) {
                            "PENDING" -> Icons.Default.CheckCircle
                            "APPROVED" -> Icons.Default.DoneAll
                            "IGNORED" -> Icons.Default.VisibilityOff
                            "UNKNOWN" -> Icons.Default.HelpOutline
                            else -> Icons.Default.Sms
                        }
                        val emptyTint = when (selectedFilter) {
                            "PENDING" -> AppTheme.colors.incomeGreen
                            "APPROVED" -> AppTheme.colors.primaryAccent
                            "IGNORED" -> AppTheme.colors.textMuted
                            "UNKNOWN" -> AmberGold
                            else -> AppTheme.colors.primaryAccent
                        }
                        val emptyTitle = when (selectedFilter) {
                            "PENDING" -> "No pending detected transactions."
                            "APPROVED" -> "No approved transactions yet."
                            "IGNORED" -> "No ignored transactions."
                            "UNKNOWN" -> "No unrecognized messages."
                            else -> "No detected transactions yet."
                        }
                        val emptySubtitle = when (selectedFilter) {
                            "PENDING" -> "All detected SMS & notification transactions have been reviewed."
                            "APPROVED" -> "Transactions approved from SMS detection will appear here."
                            "IGNORED" -> if (searchQuery.isNotBlank()) "No ignored records matched \"$searchQuery\"." else "Transactions you ignore from review will be preserved here."
                            "UNKNOWN" -> if (searchQuery.isNotBlank()) "No unrecognized records matched \"$searchQuery\"." else "Messages or notifications that could not be recognized with high confidence will appear here for manual review."
                            else -> "SMS transaction detection will capture incoming bank and UPI alerts automatically."
                        }

                        Icon(
                            imageVector = emptyIcon,
                            contentDescription = null,
                            tint = emptyTint,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = emptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = emptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(displayList, key = { it.id }) { item ->
                        val isPending = item.status == PendingStatus.PENDING
                        val isApproved = item.status == PendingStatus.RESOLVED
                        val isIgnored = item.status == PendingStatus.IGNORED
                        val isUnknown = item.status == PendingStatus.UNKNOWN
                        val isUpiLiteTopup = item.detectedType == TransactionType.UPI_LITE_TOPUP

                        val cardBorderColor = when {
                            isUpiLiteTopup -> Color(0xFF8B5CF6).copy(alpha = 0.5f)
                            isPending -> AmberGold
                            isApproved -> AppTheme.colors.incomeGreen.copy(alpha = 0.35f)
                            isUnknown -> AppTheme.colors.cardBorder
                            else -> AppTheme.colors.cardBorder
                        }

                        val iconBgColor = when {
                            isUpiLiteTopup -> Color(0xFF8B5CF6).copy(alpha = 0.18f)
                            isPending -> AmberGold.copy(alpha = 0.18f)
                            isApproved -> AppTheme.colors.incomeGreen.copy(alpha = 0.15f)
                            isUnknown -> AppTheme.colors.surfaceSecondary
                            else -> AppTheme.colors.surfaceSecondary
                        }

                        val iconColor = when {
                            isUpiLiteTopup -> Color(0xFF8B5CF6)
                            isPending -> AmberGold
                            isApproved -> AppTheme.colors.incomeGreen
                            isUnknown -> AppTheme.colors.textSecondary
                            else -> AppTheme.colors.textMuted
                        }

                        val iconVector = when {
                            isUpiLiteTopup -> Icons.Default.AccountBalanceWallet
                            isPending -> Icons.Default.Sms
                            isApproved -> Icons.Default.CheckCircle
                            isUnknown -> Icons.Default.HelpOutline
                            else -> Icons.Default.VisibilityOff
                        }

                        RupeeFlowCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDetectedForDetail = item },
                            borderColor = cardBorderColor,
                            backgroundColor = AppTheme.colors.cardBackground
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(iconBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = CurrencyUtils.formatPaiseToRupees(item.detectedAmountPaise),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = if (isApproved || isIgnored) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary
                                            )

                                            // Status Badge
                                            when {
                                                isUpiLiteTopup -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "UPI LITE TOP-UP",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF8B5CF6),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                isPending -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AmberGold.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "Pending",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AmberGold,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                isApproved -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AppTheme.colors.incomeGreen.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "Added ✓",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AppTheme.colors.incomeGreen,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                isUnknown -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AppTheme.colors.surfaceSecondary
                                                    ) {
                                                        Text(
                                                            text = "Unknown",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AmberGold,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                isIgnored -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AppTheme.colors.surfaceSecondary
                                                    ) {
                                                        Text(
                                                            text = "Ignored",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AppTheme.colors.textSecondary,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${item.detectedMerchant ?: item.detectedBank ?: "Bank"} • ${DateTimeUtils.formatRelativeDate(item.timestamp)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AppTheme.colors.textSecondary
                                        )

                                        if (isUpiLiteTopup) {
                                            Text(
                                                text = "Wallet Funding • Not counted as expense",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF8B5CF6),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp
                                            )
                                        }

                                        // Offline Location Badge (if attached)
                                        if (item.hasLocation) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    LocationIntentHelper.openLocation(
                                                        context = context,
                                                        latitude = item.latitude!!,
                                                        longitude = item.longitude!!,
                                                        label = item.locationLabel ?: (item.detectedMerchant ?: item.detectedBank)
                                                    )
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = AppTheme.colors.primaryAccent,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "${String.format(java.util.Locale.US, "%.4f, %.4f", item.latitude, item.longitude)}${if (item.accuracyMeters != null) " (±${item.accuracyMeters?.roundToInt()}m)" else ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AppTheme.colors.primaryAccent,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    // Action Buttons based on Status
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        when {
                                            isPending -> {
                                                // Ignore Action
                                                IconButton(onClick = { viewModel.ignorePending(item.id) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Ignore", tint = AppTheme.colors.textMuted)
                                                }
                                                // Approve Action
                                                Button(
                                                    onClick = { onTriggerAddExpense(item) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isUpiLiteTopup) Color(0xFF8B5CF6) else AppTheme.colors.primaryAccent,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = if (isUpiLiteTopup) "Add Top-up" else "Add Expense",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                            isUnknown -> {
                                                IconButton(onClick = { viewModel.deletePending(item.id) }) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = AppTheme.colors.textMuted)
                                                }
                                                IconButton(onClick = { viewModel.ignorePending(item.id) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Ignore", tint = AppTheme.colors.textMuted)
                                                }
                                                Button(
                                                    onClick = { onTriggerAddExpense(item) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = AppTheme.colors.primaryAccent,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Review & Add", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            isIgnored -> {
                                                // Restore Action
                                                OutlinedButton(
                                                    onClick = { viewModel.restorePending(item.id) },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.primaryAccent),
                                                    border = BorderStroke(1.dp, AppTheme.colors.primaryAccent.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Restore", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            isApproved -> {
                                                // Re-add Action
                                                OutlinedButton(
                                                    onClick = { onTriggerAddExpense(item) },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textSecondary),
                                                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Re-add", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Raw SMS snippet
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.rawContent,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textMuted,
                                    maxLines = 2,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Inspection Modal
    selectedDetectedForDetail?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedDetectedForDetail = null },
            title = {
                Text(
                    text = "Detected Transaction Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Amount:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(
                            CurrencyUtils.formatPaiseToRupees(item.detectedAmountPaise),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(
                            when (item.status) {
                                PendingStatus.PENDING -> "Pending Review"
                                PendingStatus.RESOLVED -> "Approved (Added)"
                                PendingStatus.IGNORED -> "Ignored"
                                PendingStatus.UNKNOWN -> "Unrecognized / Review"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when (item.status) {
                                PendingStatus.PENDING -> AmberGold
                                PendingStatus.RESOLVED -> AppTheme.colors.incomeGreen
                                PendingStatus.UNKNOWN -> AmberGold
                                PendingStatus.IGNORED -> AppTheme.colors.textSecondary
                            }
                        )
                    }
                    if (item.detectedType == TransactionType.UPI_LITE_TOPUP) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Transaction Type:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text("UPI Lite Top-up (Wallet Funding)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        }
                    }
                    if (!item.detectedMerchant.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Merchant / Entity:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text(item.detectedMerchant, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                        }
                    }
                    if (!item.detectedBank.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bank / Institution:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text(item.detectedBank, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                        }
                    }
                    if (!item.referenceNumber.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reference / UTR:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text(item.referenceNumber, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                        }
                    }
                    if (item.hasLocation) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Coordinates:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Text(
                                "${String.format(java.util.Locale.US, "%.5f, %.5f", item.latitude, item.longitude)}${if (item.accuracyMeters != null) " (±${item.accuracyMeters?.roundToInt()}m)" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primaryAccent
                            )
                        }
                        if (item.locationSource != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Location Source:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                                Text(item.locationSource.name, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                LocationIntentHelper.openLocation(
                                    context = context,
                                    latitude = item.latitude!!,
                                    longitude = item.longitude!!,
                                    label = item.locationLabel ?: (item.detectedMerchant ?: item.detectedBank)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = AppTheme.colors.primaryAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Google Maps", color = AppTheme.colors.primaryAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Detection Source:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(item.source.name, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Detected On:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(DateTimeUtils.formatRelativeDate(item.timestamp), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Original Message / Notification:", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppTheme.colors.surfaceSecondary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.rawContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                when (item.status) {
                    PendingStatus.PENDING, PendingStatus.UNKNOWN -> {
                        Button(
                            onClick = {
                                onTriggerAddExpense(item)
                                selectedDetectedForDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.detectedType == TransactionType.UPI_LITE_TOPUP) Color(0xFF8B5CF6) else AppTheme.colors.primaryAccent
                            )
                        ) {
                            Text(if (item.detectedType == TransactionType.UPI_LITE_TOPUP) "Add Top-up" else "Approve & Add")
                        }
                    }
                    PendingStatus.IGNORED -> {
                        Button(
                            onClick = {
                                viewModel.restorePending(item.id)
                                selectedDetectedForDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                        ) {
                            Text("Restore to Pending")
                        }
                    }
                    PendingStatus.RESOLVED -> {
                        TextButton(onClick = { selectedDetectedForDetail = null }) {
                            Text("Close")
                        }
                    }
                }
            },
            dismissButton = {
                if (item.status == PendingStatus.PENDING || item.status == PendingStatus.UNKNOWN) {
                    OutlinedButton(
                        onClick = {
                            viewModel.ignorePending(item.id)
                            selectedDetectedForDetail = null
                        }
                    ) {
                        Text("Ignore")
                    }
                } else {
                    TextButton(onClick = { selectedDetectedForDetail = null }) {
                        Text("Close")
                    }
                }
            },
            containerColor = AppTheme.colors.surfaceElevated
        )
    }

    // UPI Lite Confirmation Dialog
    showUpiLiteConfirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { showUpiLiteConfirmItem = null },
            title = {
                Text(
                    text = "Confirm UPI Lite Top-up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Amount: ${CurrencyUtils.formatPaiseToRupees(item.detectedAmountPaise)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B5CF6)
                    )
                    Text(
                        text = "This records a wallet top-up into your UPI Lite balance.\n\nIn accordance with financial accounting principles, wallet funding transfers funds from your bank into UPI Lite and is NOT counted as an expense in your spending reports. Individual purchases made with UPI Lite balance will be tracked as expenses.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toAdd = showUpiLiteConfirmItem!!
                        showUpiLiteConfirmItem = null
                        handleStartAddExpense(toAdd)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Text("Confirm Top-up", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpiLiteConfirmItem = null }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Historical Match Suggestion Dialog
    matchDetected?.let { detectedItem ->
        if (matchCandidates.isNotEmpty()) {
            SimilarTransactionDialog(
                detected = detectedItem,
                candidates = matchCandidates,
                onAcceptMatch = { candidate ->
                    viewModel.recordMatchDecision(detectedItem, candidate.candidateTransaction, accepted = true)
                    selectedMatchDraft = candidate
                    selectedDetectedForAdd = detectedItem
                    matchDetected = null
                    matchCandidates = emptyList()
                },
                onRejectMatch = { candidate ->
                    if (candidate != null) {
                        viewModel.recordMatchDecision(detectedItem, candidate.candidateTransaction, accepted = false)
                    }
                    selectedMatchDraft = null
                    selectedDetectedForAdd = detectedItem
                    matchDetected = null
                    matchCandidates = emptyList()
                },
                onDismiss = {
                    matchDetected = null
                    matchCandidates = emptyList()
                }
            )
        }
    }

    // Opens Add Expense Sheet (FastAddExpenseSheet) prefilled with detected data!
    selectedDetectedForAdd?.let { item ->
        FastAddExpenseSheet(
            categories = state.categories,
            friends = state.friends,
            groups = state.friendGroups,
            onDismiss = {
                selectedDetectedForAdd = null
                selectedMatchDraft = null
            },
            initialAmountPaise = item.detectedAmountPaise,
            initialTitle = selectedMatchDraft?.suggestedTitle ?: (item.detectedMerchant ?: item.detectedBank),
            initialCategory = selectedMatchDraft?.suggestedCategory,
            initialType = item.detectedType,
            initialPaymentMethod = if (item.detectedType == TransactionType.UPI_LITE_TOPUP) PaymentMethod.UPI_LITE else (selectedMatchDraft?.suggestedPaymentMethod ?: PaymentMethod.UPI),
            initialPendingId = item.id,
            initialLocation = item.location,
            onSearchMerchantLocation = { merchant -> viewModel.getMerchantLatestLocation(merchant) },
            suggestedFromPrevious = selectedMatchDraft != null,
            originalDetectedMerchant = item.detectedMerchant ?: item.detectedBank,
            onSave = { newTxn ->
                viewModel.saveTransaction(newTxn, item.id)
                selectedDetectedForAdd = null
                selectedMatchDraft = null
            },
            onSaveSettlement = { settlement ->
                viewModel.saveSettlement(settlement, item.id)
                selectedDetectedForAdd = null
                selectedMatchDraft = null
            },
            onPendingResolved = { id ->
                viewModel.resolvePending(id)
                selectedDetectedForAdd = null
                selectedMatchDraft = null
            }
        )
    }
}
