package com.naman.rupeeflow.domain.matching

import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.PendingTransaction
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionType

enum class SimilarityConfidence {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Encapsulates a candidate historical transaction that matches a detected pending transaction.
 */
data class HistoricalCandidateMatch(
    val candidateTransaction: Transaction,
    val similarityScore: Double,
    val confidence: SimilarityConfidence,
    val merchantMatchScore: Double,
    val amountMatchScore: Double,
    val timeRecencyScore: Double,
    val matchReasons: List<String>,
    val suggestedTitle: String,
    val suggestedCategory: Category?,
    val suggestedPaymentMethod: PaymentMethod
)

/**
 * Prepared transaction draft after user approval of historical metadata.
 * Retains detected payment reality (actual amount, date, reference, source)
 * while safely copying approved descriptive metadata (title, category, payment method).
 */
data class PreparedTransactionDraft(
    val title: String,
    val amountPaise: Long,
    val categoryId: Long,
    val category: Category?,
    val transactionDate: Long,
    val paymentMethod: PaymentMethod,
    val merchantName: String?,
    val referenceNumber: String?,
    val transactionType: TransactionType,
    val sourcePendingId: Long
) {
    companion object {
        fun from(
            pending: PendingTransaction,
            candidate: Transaction,
            suggestedCategory: Category?
        ): PreparedTransactionDraft {
            return PreparedTransactionDraft(
                title = candidate.title,
                amountPaise = pending.detectedAmountPaise,
                categoryId = candidate.categoryId,
                category = suggestedCategory ?: candidate.category,
                transactionDate = pending.timestamp,
                paymentMethod = candidate.paymentMethod,
                merchantName = pending.detectedMerchant ?: candidate.merchantName,
                referenceNumber = pending.referenceNumber,
                transactionType = pending.detectedType,
                sourcePendingId = pending.id
            )
        }
    }
}
