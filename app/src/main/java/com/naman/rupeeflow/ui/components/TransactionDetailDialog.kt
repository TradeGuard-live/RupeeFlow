package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.ExpenseAllocationCalculator
import com.naman.rupeeflow.location.LocationIntentHelper
import com.naman.rupeeflow.location.LocationNameResolver
import com.naman.rupeeflow.ui.theme.*

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onToggleIgnore: (Transaction) -> Unit,
    onDuplicate: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onEdit: (Transaction) -> Unit,
    onSaveTransaction: ((Transaction) -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRemoveLocationConfirm by remember { mutableStateOf(false) }
    var showEditLocationDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val category = transaction.category
    val catColor = CategoryIconHelper.parseColor(category?.colorHex)
    val catIcon = CategoryIconHelper.getIcon(category?.iconName)

    val isIncomeOrRefund = transaction.transactionType == TransactionType.INCOME ||
            transaction.transactionType == TransactionType.REFUND
    val amountPrefix = if (isIncomeOrRefund) "+" else "-"
    val amountColor = if (isIncomeOrRefund) AppTheme.colors.incomeGreen else AppTheme.colors.textPrimary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(catColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = category?.name ?: "Expense",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                Text(
                    text = "$amountPrefix${CurrencyUtils.formatPaiseToRupees(transaction.amountPaise)}",
                    style = MaterialTheme.typography.displayLarge,
                    color = amountColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary
                )

                if (transaction.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata cards
                RupeeFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp
                ) {
                    DetailRow("Date", DateTimeUtils.formatDate(transaction.transactionDate, "dd MMMM yyyy"))
                    DetailRow("Time", DateTimeUtils.formatTime(transaction.transactionDate))
                    DetailRow("Type", transaction.transactionType.displayName)
                    DetailRow("Payment Method", transaction.paymentMethod.displayName)
                    DetailRow("Paid By", transaction.paidByName)
                    if (transaction.isSplit) {
                        DetailRow("Split Method", transaction.splitMethod.displayName)
                        DetailRow("Your Share (Counted)", CurrencyUtils.formatPaiseToRupees(transaction.userExpensePaise))
                        val friendsShare = (transaction.amountPaise - transaction.userExpensePaise).coerceAtLeast(0L)
                        DetailRow("Friends' Share (Debt/Credit)", CurrencyUtils.formatPaiseToRupees(friendsShare))
                    }
                    if (!transaction.merchantName.isNullOrBlank()) {
                        DetailRow("Merchant", transaction.merchantName)
                    }
                    val upiRef = transaction.upiReferenceNumber ?: transaction.referenceNumber
                    if (!upiRef.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "UPI Reference Number", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = upiRef, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("UPI Reference Number", upiRef)
                                        clipboard?.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "UPI Reference Number copied", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy UPI Reference Number",
                                        tint = AppTheme.colors.primaryAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else if (!transaction.referenceNumber.isNullOrBlank()) {
                        DetailRow("Reference", transaction.referenceNumber)
                    }
                    if (transaction.hasExpenseGroup) {
                        DetailRow("Expense Group", transaction.expenseGroupName ?: "Group")
                    }
                    if (transaction.hasLocation) {
                        val readableLocation = LocationNameResolver.resolveReadableLocation(
                            context = context,
                            locationLabel = transaction.locationLabel,
                            latitude = transaction.latitude,
                            longitude = transaction.longitude
                        )
                        DetailRow("Payment Location", readableLocation)
                    }
                }

                // Expense Allocation Details (When expense is spread across days)
                if (transaction.isSpread && transaction.spreadDurationDays > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = AppTheme.colors.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Expense Allocation (Daily Spreading)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                        }

                        DetailRow("Original Amount", CurrencyUtils.formatPaiseToRupees(transaction.amountPaise))
                        DetailRow("Purchase Date", DateTimeUtils.formatDate(transaction.transactionDate, "dd MMMM yyyy"))
                        DetailRow("Duration", "${transaction.spreadDurationDays} days")
                        val startDateFormatted = transaction.spreadStartDate?.let { DateTimeUtils.formatDate(it, "dd MMM yyyy") } ?: DateTimeUtils.formatDate(transaction.transactionDate, "dd MMM yyyy")
                        val endDateFormatted = transaction.spreadEndDate?.let { DateTimeUtils.formatDate(it, "dd MMM yyyy") } ?: "N/A"
                        DetailRow("Spread Period", "$startDateFormatted — $endDateFormatted")

                        val dailyBasePaise = transaction.amountPaise / transaction.spreadDurationDays
                        DetailRow("Daily Allocation", "~${CurrencyUtils.formatPaiseToRupees(dailyBasePaise)} / day")

                        val totalAllocated = if (transaction.allocations.isNotEmpty()) {
                            transaction.allocations.sumOf { it.amountPaise }
                        } else {
                            transaction.amountPaise
                        }
                        DetailRow("Total Allocated", CurrencyUtils.formatPaiseToRupees(totalAllocated))

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Daily Breakdown",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val displayAllocations = if (transaction.allocations.isNotEmpty()) {
                            transaction.allocations.map {
                                ExpenseAllocationCalculator.CalculatedAllocation(it.allocationDate, it.amountPaise)
                            }
                        } else {
                            val start = transaction.spreadStartDate ?: transaction.transactionDate
                            ExpenseAllocationCalculator.calculateAllocations(transaction.amountPaise, transaction.spreadDurationDays, start)
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AppTheme.colors.surfaceSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                displayAllocations.forEachIndexed { index, alloc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ExpenseAllocationCalculator.formatPreviewDate(alloc.allocationDate),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = CurrencyUtils.formatPaiseToRupees(alloc.amountPaise),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                    }
                                    if (index < displayAllocations.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            color = AppTheme.colors.cardBorder.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (transaction.hasLocation && transaction.latitude != null && transaction.longitude != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            LocationIntentHelper.openLocation(
                                context = context,
                                latitude = transaction.latitude,
                                longitude = transaction.longitude,
                                label = transaction.locationLabel ?: transaction.merchantName ?: transaction.title
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppTheme.colors.primaryAccent
                        ),
                        border = BorderStroke(1.dp, AppTheme.colors.primaryAccent.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View on Google Maps",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditLocationDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppTheme.colors.primaryAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Edit Location",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.primaryAccent
                            )
                        }

                        OutlinedButton(
                            onClick = { showRemoveLocationConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = CoralRed
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Remove Location",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = CoralRed
                            )
                        }
                    }
                }

                // Attached Bill / Receipt Photo Preview (Image or PDF)
                if (!transaction.billImagePath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Attached Bill / Receipt",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    var showFullscreenReceipt by remember { mutableStateOf(false) }
                    val isPdf = com.naman.rupeeflow.data.local.ImageStorageManager.isPdfAttachment(transaction.billImagePath)
                    val receiptBitmap = if (!isPdf) rememberBitmapFromPath(transaction.billImagePath) else null

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.cardBackground,
                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPdf) {
                                    openPdfAttachment(context, transaction.billImagePath)
                                } else {
                                    showFullscreenReceipt = true
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPdf) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF Document",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else if (receiptBitmap != null) {
                                Image(
                                    bitmap = receiptBitmap,
                                    contentDescription = "Receipt Preview",
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppTheme.colors.surfaceSecondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = AppTheme.colors.primaryAccent)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPdf) com.naman.rupeeflow.data.local.ImageStorageManager.getAttachmentFileName(transaction.billImagePath) else "View Attached Receipt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isPdf) "PDF Document • ${com.naman.rupeeflow.data.local.ImageStorageManager.getAttachmentFileSizeFormatted(transaction.billImagePath)} • Tap to open" else "Tap to view in fullscreen with zoom",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.primaryAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (isPdf) Icons.Default.OpenInNew else Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = AppTheme.colors.textSecondary
                            )
                        }
                    }

                    if (showFullscreenReceipt && !isPdf) {
                        FullScreenReceiptDialog(
                            imagePath = transaction.billImagePath,
                            title = "${transaction.title} - Receipt",
                            onDismiss = { showFullscreenReceipt = false }
                        )
                    }
                }

                // Item-wise Breakdown (When transaction was split by items)
                if (transaction.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Items Breakdown (${transaction.items.size} items)",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp
                    ) {
                        transaction.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    val qtyStr = if (item.quantity > 1) "${item.quantity} × ${CurrencyUtils.formatPaiseToRupees(item.unitPricePaise)}" else null
                                    val allocSummary = if (item.allocations.isNotEmpty()) {
                                        val names = item.allocations.map { a ->
                                            if (a.friendId == null) "Me" else {
                                                transaction.participants.firstOrNull { it.friendId == a.friendId }?.friendName ?: "Friend"
                                            }
                                        }.joinToString(", ")
                                        "Assigned to: $names"
                                    } else null
                                    val subText = listOfNotNull(qtyStr, allocSummary).joinToString(" • ")
                                    if (subText.isNotBlank()) {
                                        Text(
                                            text = subText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(item.totalAmountPaise),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // Participants / Split breakdown (strictly omitting ₹0 share participants)
                val activeParticipants = transaction.participants.filter { it.shareAmountPaise > 0L }
                if (activeParticipants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Participants & Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    RupeeFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp
                    ) {
                        activeParticipants.forEach { part ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (part.friendId == null) "Me (You)" else part.friendName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textPrimary,
                                        fontWeight = if (part.friendId == null) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (part.percentage > 0.0) {
                                        Text(
                                            text = "${part.percentage}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(part.shareAmountPaise),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (part.friendId == null) AppTheme.colors.incomeGreen else AppTheme.colors.primaryAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Responsive 2-column, 2-row action button layout
                // Responsive Action Button Layout (Phase 3: Zero Text Clipping, Two-Column / Vertical Layout)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isNarrow = maxWidth < 280.dp
                    val ignoreColor = if (transaction.isIgnored) AppTheme.colors.incomeGreen else AmberGold

                    val editButton: @Composable (Modifier) -> Unit = { modifier ->
                        Button(
                            onClick = {
                                onDismiss()
                                onEdit(transaction)
                            },
                            modifier = modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primaryAccent,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val copyButton: @Composable (Modifier) -> Unit = { modifier ->
                        OutlinedButton(
                            onClick = {
                                onDuplicate(transaction)
                                onDismiss()
                            },
                            modifier = modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.primaryAccent),
                            border = BorderStroke(1.dp, AppTheme.colors.primaryAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val ignoreRestoreButton: @Composable (Modifier) -> Unit = { modifier ->
                        OutlinedButton(
                            onClick = {
                                onToggleIgnore(transaction)
                                onDismiss()
                            },
                            modifier = modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ignoreColor),
                            border = BorderStroke(1.dp, ignoreColor)
                        ) {
                            Icon(
                                imageVector = if (transaction.isIgnored) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (transaction.isIgnored) "Restore" else "Ignore",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val deleteButton: @Composable (Modifier) -> Unit = { modifier ->
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                            border = BorderStroke(1.dp, CoralRed)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = CoralRed
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = CoralRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isNarrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            editButton(Modifier.fillMaxWidth())
                            copyButton(Modifier.fillMaxWidth())
                            ignoreRestoreButton(Modifier.fillMaxWidth())
                            deleteButton(Modifier.fillMaxWidth())
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Row 1: Primary actions [ Edit ] [ Copy ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                editButton(Modifier.weight(1f))
                                copyButton(Modifier.weight(1f))
                            }
                            // Row 2: Secondary actions [ Ignore/Restore ] [ Delete ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ignoreRestoreButton(Modifier.weight(1f))
                                deleteButton(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?", color = AppTheme.colors.textPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete '${transaction.title}'? This will permanently remove its accounting records.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(transaction)
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showRemoveLocationConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveLocationConfirm = false },
            title = { Text("Remove Payment Location?", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will remove the saved location from this transaction. Transaction amount and accounting details remain unchanged.",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveLocationConfirm = false
                        val cleared = transaction.copy(
                            latitude = null,
                            longitude = null,
                            accuracyMeters = null,
                            locationCapturedAt = null,
                            locationSource = null,
                            locationLabel = null,
                            googleMapsUrl = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSaveTransaction?.invoke(cleared)
                        onDismiss()
                    }
                ) {
                    Text("Remove", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveLocationConfirm = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showEditLocationDialog) {
        var tempLocation by remember { mutableStateOf(transaction.location) }
        AlertDialog(
            onDismissRequest = { showEditLocationDialog = false },
            title = { Text("Edit Payment Location", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TransactionLocationPicker(
                        location = tempLocation,
                        onLocationChanged = { tempLocation = it },
                        merchantName = transaction.merchantName ?: transaction.title
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditLocationDialog = false
                        val updated = transaction.copy(
                            latitude = tempLocation?.latitude,
                            longitude = tempLocation?.longitude,
                            accuracyMeters = tempLocation?.accuracyMeters,
                            locationCapturedAt = tempLocation?.locationCapturedAt,
                            locationSource = tempLocation?.locationSource,
                            locationLabel = tempLocation?.locationLabel,
                            googleMapsUrl = tempLocation?.googleMapsUrl,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSaveTransaction?.invoke(updated)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLocationDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
    }
}
