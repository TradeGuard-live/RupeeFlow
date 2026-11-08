package com.naman.rupeeflow.ui.screens.pending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.matching.HistoricalCandidateMatch
import com.naman.rupeeflow.domain.matching.SimilarityConfidence
import com.naman.rupeeflow.domain.model.PendingTransaction
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.ui.theme.AmberGold
import com.naman.rupeeflow.ui.theme.AppTheme

@Composable
fun SimilarTransactionDialog(
    detected: PendingTransaction,
    candidates: List<HistoricalCandidateMatch>,
    onAcceptMatch: (HistoricalCandidateMatch) -> Unit,
    onRejectMatch: (HistoricalCandidateMatch?) -> Unit,
    onDismiss: () -> Unit
) {
    if (candidates.isEmpty()) {
        onRejectMatch(null)
        return
    }

    var selectedIndex by remember { mutableStateOf(0) }
    var showOtherMatchesList by remember { mutableStateOf(false) }

    val activeCandidate = candidates.getOrNull(selectedIndex) ?: candidates.first()
    val merchantDisplay = detected.detectedMerchant ?: detected.detectedBank ?: "Merchant"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AppTheme.colors.primaryAccent,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Similar Transaction Found!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showOtherMatchesList) {
                    Text(
                        text = "Select matching previous transaction:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textSecondary
                    )

                    candidates.forEachIndexed { idx, candidate ->
                        val isSelected = selectedIndex == idx
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AppTheme.colors.accentContainer else AppTheme.colors.surfaceSecondary,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AppTheme.colors.primaryAccent else AppTheme.colors.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedIndex = idx }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.suggestedTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "${candidate.suggestedCategory?.name ?: "Expense"} • ${CurrencyUtils.formatPaiseToRupees(candidate.candidateTransaction.amountPaise)} • ${DateTimeUtils.formatRelativeDate(candidate.candidateTransaction.transactionDate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AppTheme.colors.primaryAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // "None of these" option
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppTheme.colors.surfaceSecondary,
                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRejectMatch(activeCandidate) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "None of these (Enter as new)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = AppTheme.colors.textSecondary
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = AppTheme.colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = { showOtherMatchesList = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Back to comparison", color = AppTheme.colors.primaryAccent)
                    }
                } else {
                    // Comparison View: Previous Transaction vs New Detected Transaction
                    Text(
                        text = "Is this the same type of expense as your previous $merchantDisplay transaction?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textPrimary
                    )

                    // Previous Transaction Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppTheme.colors.surfaceSecondary,
                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PREVIOUS TRANSACTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.primaryAccent,
                                    fontSize = 11.sp
                                )
                                if (activeCandidate.confidence == SimilarityConfidence.HIGH) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AppTheme.colors.incomeGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "High Match",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppTheme.colors.incomeGreen,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = activeCandidate.suggestedTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(activeCandidate.candidateTransaction.amountPaise),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text("•", color = AppTheme.colors.textMuted)
                                Text(
                                    text = activeCandidate.suggestedCategory?.name ?: "Expense",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text("•", color = AppTheme.colors.textMuted)
                                Text(
                                    text = activeCandidate.suggestedPaymentMethod.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = DateTimeUtils.formatRelativeDate(activeCandidate.candidateTransaction.transactionDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textMuted
                            )
                        }
                    }

                    // New Detected Transaction Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppTheme.colors.cardBackground,
                        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "NEW DETECTED TRANSACTION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = merchantDisplay,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = CurrencyUtils.formatPaiseToRupees(detected.detectedAmountPaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.primaryAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Detected today via ${detected.source.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textMuted
                            )
                        }
                    }

                    // Other matches toggle if more candidates exist
                    if (candidates.size > 1) {
                        TextButton(
                            onClick = { showOtherMatchesList = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = "View other matches (${candidates.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.primaryAccent
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAcceptMatch(activeCandidate) },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("YES, USE PREVIOUS DETAILS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onRejectMatch(activeCandidate) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("NO, ENTER AS NEW", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.surfaceElevated
    )
}
