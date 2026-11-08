package com.naman.rupeeflow.domain.matching

import kotlin.math.abs
import kotlin.math.max

/**
 * Evaluates amount similarity between two transactions using configurable absolute (paise)
 * and relative (percentage) tolerances with zero overflow risk.
 */
object AmountSimilarityCalculator {

    const val EXACT_PAISE_TOLERANCE = 0L
    const val TIGHT_PAISE_TOLERANCE = 500L     // ₹5.00
    const val MODERATE_PAISE_TOLERANCE = 1000L  // ₹10.00

    const val PERCENT_TOLERANCE_TIGHT = 3.0    // 3%
    const val PERCENT_TOLERANCE_MODERATE = 5.0 // 5%
    const val PERCENT_TOLERANCE_LOOSE = 10.0   // 10%
    const val PERCENT_TOLERANCE_MAX = 20.0     // 20%

    data class AmountComparisonResult(
        val diffPaise: Long,
        val percentageDiff: Double,
        val similarityScore: Double, // 0.0 to 1.0
        val isExact: Boolean,
        val isClose: Boolean
    )

    /**
     * Calculates similarity between amountA and amountB in integer paise.
     */
    fun compare(amountAPaise: Long, amountBPaise: Long): AmountComparisonResult {
        if (amountAPaise <= 0L || amountBPaise <= 0L) {
            return AmountComparisonResult(
                diffPaise = abs(amountAPaise - amountBPaise),
                percentageDiff = 100.0,
                similarityScore = 0.0,
                isExact = false,
                isClose = false
            )
        }

        val diffPaise = abs(amountAPaise - amountBPaise)
        if (diffPaise == 0L) {
            return AmountComparisonResult(
                diffPaise = 0L,
                percentageDiff = 0.0,
                similarityScore = 1.0,
                isExact = true,
                isClose = true
            )
        }

        val maxPaise = max(amountAPaise, amountBPaise)
        val pctDiff = (diffPaise.toDouble() / maxPaise.toDouble()) * 100.0

        val score = when {
            diffPaise <= TIGHT_PAISE_TOLERANCE -> 0.95
            diffPaise <= MODERATE_PAISE_TOLERANCE -> 0.85
            pctDiff <= PERCENT_TOLERANCE_TIGHT -> 0.80
            pctDiff <= PERCENT_TOLERANCE_MODERATE -> 0.70
            pctDiff <= PERCENT_TOLERANCE_LOOSE -> 0.50
            pctDiff <= PERCENT_TOLERANCE_MAX -> 0.30
            else -> 0.0
        }

        val isClose = diffPaise <= TIGHT_PAISE_TOLERANCE || pctDiff <= PERCENT_TOLERANCE_TIGHT

        return AmountComparisonResult(
            diffPaise = diffPaise,
            percentageDiff = pctDiff,
            similarityScore = score,
            isExact = false,
            isClose = isClose
        )
    }
}
