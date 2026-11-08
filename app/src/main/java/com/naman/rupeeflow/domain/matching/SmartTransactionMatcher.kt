package com.naman.rupeeflow.domain.matching

import com.naman.rupeeflow.data.repository.MerchantPreferenceRepository
import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.PendingTransaction
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import java.util.Locale

/**
 * Intelligent Historical Transaction Matching Engine.
 *
 * Pipeline:
 * DetectedTransaction
 *    ↓
 * NormalizeMerchant
 *    ↓
 * FindHistoricalCandidates
 *    ↓
 * CalculateSimilarity
 *    ↓
 * RankCandidates
 *    ↓
 * ApplyConfidenceThreshold
 *    ↓
 * ShowSuggestion
 */
class SmartTransactionMatcher(
    private val merchantPreferenceRepository: MerchantPreferenceRepository? = null,
    private val historyWindowMillis: Long = 90L * 24 * 3600 * 1000L // Default 90 days
) {

    suspend fun findSimilarTransactions(
        detected: PendingTransaction,
        historicalTransactions: List<Transaction>,
        categories: List<Category>
    ): List<HistoricalCandidateMatch> {
        val detectedMerchant = detected.detectedMerchant ?: detected.detectedBank
        if (detectedMerchant.isNullOrBlank()) return emptyList()

        val normDetected = MerchantNormalizer.normalize(detectedMerchant)
        if (normDetected.isBlank()) return emptyList()

        val detectedVpa = MerchantNormalizer.extractVpa(detected.rawContent)
            ?: MerchantNormalizer.extractVpa(detected.detectedMerchant)

        val now = System.currentTimeMillis()
        val windowStart = now - historyWindowMillis
        val extendedWindowStart = now - (historyWindowMillis * 2) // 180 days for strong VPA/exact matches

        val categoryMap = categories.associateBy { it.id }

        val candidateMatches = mutableListOf<HistoricalCandidateMatch>()

        for (history in historicalTransactions) {
            // Must be compatible transaction type (e.g. Expense vs Expense)
            if (history.transactionType != detected.detectedType) continue

            // Must have non-blank title
            if (history.title.isBlank()) continue

            // Calculate merchant match score
            val historyMerchant = history.merchantName ?: history.title
            val historyVpa = MerchantNormalizer.extractVpa(history.referenceNumber)
                ?: MerchantNormalizer.extractVpa(history.merchantName)

            val isVpaMatch = detectedVpa != null && historyVpa != null && detectedVpa.equals(historyVpa, ignoreCase = true)
            val isExactNormMatch = MerchantNormalizer.normalize(historyMerchant) == normDetected
            val isSameMerchant = isVpaMatch || isExactNormMatch || MerchantNormalizer.isSameMerchant(detectedMerchant, historyMerchant)

            if (!isSameMerchant) continue

            // Check time window: allow up to 180 days for exact VPA or exact merchant matches, else 90 days
            val allowedWindowStart = if (isVpaMatch || isExactNormMatch) extendedWindowStart else windowStart
            if (history.transactionDate < allowedWindowStart) continue

            val merchantScore = when {
                isVpaMatch -> 1.0
                isExactNormMatch -> 0.95
                else -> 0.70
            }

            // Calculate amount similarity
            val amountResult = AmountSimilarityCalculator.compare(
                amountAPaise = detected.detectedAmountPaise,
                amountBPaise = history.amountPaise
            )
            val amountScore = amountResult.similarityScore

            // Calculate recency score
            val ageMillis = (now - history.transactionDate).coerceAtLeast(0L)
            val ageDays = ageMillis / (24 * 3600 * 1000L)
            val recencyScore = when {
                ageDays <= 7 -> 1.0
                ageDays <= 30 -> 0.8
                ageDays <= 90 -> 0.6
                else -> 0.4
            }

            // Check learned user feedback
            val isRejected = merchantPreferenceRepository?.isClassificationRejected(
                rawMerchant = detectedMerchant,
                categoryId = history.categoryId,
                title = history.title
            ) ?: false

            if (isRejected) {
                // User explicitly rejected this candidate previously; do not interrupt user with it
                continue
            }

            val hasLearnedBoost = merchantPreferenceRepository?.getLearnedPreferences(detectedMerchant)
                ?.any { it.categoryId == history.categoryId && it.title.equals(history.title, ignoreCase = true) && !it.isRejected } ?: false

            val totalScore = (
                (merchantScore * 0.45) +
                (amountScore * 0.40) +
                (recencyScore * 0.15) +
                (if (hasLearnedBoost) 0.10 else 0.0)
            ).coerceIn(0.0, 1.0)

            // Assign confidence level
            val confidence = when {
                merchantScore >= 0.90 && (amountResult.isExact || amountResult.diffPaise <= AmountSimilarityCalculator.TIGHT_PAISE_TOLERANCE || amountResult.percentageDiff <= AmountSimilarityCalculator.PERCENT_TOLERANCE_TIGHT) ->
                    SimilarityConfidence.HIGH
                merchantScore >= 0.70 && amountScore >= 0.50 ->
                    SimilarityConfidence.MEDIUM
                else ->
                    SimilarityConfidence.LOW
            }

            // Never suggest LOW confidence matches
            if (confidence == SimilarityConfidence.LOW) continue

            val matchReasons = mutableListOf<String>()
            if (isVpaMatch) matchReasons.add("Exact UPI VPA match")
            else if (isExactNormMatch) matchReasons.add("Exact merchant name match")
            else matchReasons.add("Similar merchant name")

            if (amountResult.isExact) {
                matchReasons.add("Exact amount match (${CurrencyUtils.formatPaiseToRupees(history.amountPaise)})")
            } else if (amountResult.diffPaise <= AmountSimilarityCalculator.TIGHT_PAISE_TOLERANCE) {
                matchReasons.add("Very close amount (diff ${CurrencyUtils.formatPaiseToRupees(amountResult.diffPaise)})")
            } else {
                matchReasons.add("Amount difference ${String.format(Locale.ROOT, "%.1f", amountResult.percentageDiff)}%")
            }

            if (hasLearnedBoost) {
                matchReasons.add("Matches previously confirmed preference")
            }

            val matchedCategory = categoryMap[history.categoryId] ?: history.category

            candidateMatches.add(
                HistoricalCandidateMatch(
                    candidateTransaction = history,
                    similarityScore = totalScore,
                    confidence = confidence,
                    merchantMatchScore = merchantScore,
                    amountMatchScore = amountScore,
                    timeRecencyScore = recencyScore,
                    matchReasons = matchReasons,
                    suggestedTitle = history.title,
                    suggestedCategory = matchedCategory,
                    suggestedPaymentMethod = history.paymentMethod
                )
            )
        }

        // Deduplicate candidates that share identical (title, categoryId)
        val deduplicated = candidateMatches
            .groupBy { "${it.suggestedTitle.trim().lowercase(Locale.ROOT)}_${it.candidateTransaction.categoryId}" }
            .values
            .mapNotNull { group ->
                // Keep the one with highest score, or most recent if scores are identical
                group.maxWithOrNull(
                    compareBy<HistoricalCandidateMatch> { it.confidence == SimilarityConfidence.HIGH }
                        .thenBy { it.similarityScore }
                        .thenBy { it.candidateTransaction.transactionDate }
                )
            }

        // Rank by confidence (HIGH first), similarityScore descending, and recency descending
        return deduplicated
            .sortedWith(
                compareByDescending<HistoricalCandidateMatch> { it.confidence == SimilarityConfidence.HIGH }
                    .thenByDescending { it.similarityScore }
                    .thenByDescending { it.candidateTransaction.transactionDate }
            )
            .take(3)
    }
}
