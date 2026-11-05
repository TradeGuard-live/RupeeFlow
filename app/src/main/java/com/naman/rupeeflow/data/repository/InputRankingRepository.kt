package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.InputUsageDao
import com.naman.rupeeflow.data.local.entity.InputUsageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.ln
import kotlin.math.max

interface InputRankingRepository {
    suspend fun recordUsage(fieldType: String, optionId: String, preferenceScore: Double = 0.0)
    fun getUsagesFlow(fieldType: String): Flow<List<InputUsageEntity>>
    suspend fun getUsages(fieldType: String): List<InputUsageEntity>
    suspend fun <T> rankOptions(
        fieldType: String,
        options: List<T>,
        idSelector: (T) -> String
    ): List<T>
}

class InputRankingRepositoryImpl(
    private val inputUsageDao: InputUsageDao
) : InputRankingRepository {

    companion object {
        const val FIELD_CATEGORY = "CATEGORY"
        const val FIELD_PAYMENT_METHOD = "PAYMENT_METHOD"
        const val FIELD_FRIEND = "FRIEND"
        const val FIELD_GROUP = "GROUP"
        const val FIELD_DESCRIPTION = "DESCRIPTION"
        const val FIELD_TRANSACTION_TYPE = "TRANSACTION_TYPE"

        // Weights: Frequency (60%), Recency (30%), Preference (10%)
        private const val WEIGHT_FREQUENCY = 0.60
        private const val WEIGHT_RECENCY = 0.30
        private const val WEIGHT_PREFERENCE = 0.10

        private const val MILLIS_PER_DAY = 1000.0 * 60 * 60 * 24
    }

    override suspend fun recordUsage(fieldType: String, optionId: String, preferenceScore: Double) {
        if (optionId.isBlank()) return
        inputUsageDao.recordUsage(fieldType, optionId.trim(), System.currentTimeMillis())
        if (preferenceScore > 0.0) {
            inputUsageDao.updatePreferenceScore(fieldType, optionId.trim(), preferenceScore)
        }
    }

    override fun getUsagesFlow(fieldType: String): Flow<List<InputUsageEntity>> {
        return inputUsageDao.getUsagesForField(fieldType)
    }

    override suspend fun getUsages(fieldType: String): List<InputUsageEntity> {
        return inputUsageDao.getUsagesForFieldList(fieldType)
    }

    override suspend fun <T> rankOptions(
        fieldType: String,
        options: List<T>,
        idSelector: (T) -> String
    ): List<T> {
        if (options.isEmpty()) return options
        val usages = inputUsageDao.getUsagesForFieldList(fieldType).associateBy { it.optionId }
        if (usages.isEmpty()) return options

        val now = System.currentTimeMillis()
        val maxCount = usages.values.maxOfOrNull { it.usageCount }?.toDouble() ?: 1.0

        return options.mapIndexed { index, item ->
            val id = idSelector(item)
            val usage = usages[id]
            val score = if (usage != null) {
                // Normalized Frequency score (log-dampened to prevent monopoly)
                val frequencyScore = ln(1.0 + usage.usageCount) / ln(1.0 + maxCount)

                // Normalized Recency score (decay based on elapsed days)
                val daysElapsed = max(0.0, (now - usage.lastUsedAt) / MILLIS_PER_DAY)
                val recencyScore = 1.0 / (1.0 + 0.1 * daysElapsed)

                // Preference score (0.0 to 1.0)
                val preferenceScore = usage.preferenceScore.coerceIn(0.0, 1.0)

                (frequencyScore * WEIGHT_FREQUENCY) +
                        (recencyScore * WEIGHT_RECENCY) +
                        (preferenceScore * WEIGHT_PREFERENCE)
            } else {
                -1.0 // Unused options get lower priority
            }
            ScoredItem(item, score, index)
        }
            .sortedWith(
                compareByDescending<ScoredItem<T>> { it.score }
                    .thenBy { it.originalIndex } // Preserves original order for unranked options
            )
            .map { it.item }
    }

    private data class ScoredItem<T>(
        val item: T,
        val score: Double,
        val originalIndex: Int
    )
}
