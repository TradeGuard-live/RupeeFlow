package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.InputUsageDao
import com.naman.rupeeflow.data.local.entity.InputUsageEntity
import com.naman.rupeeflow.domain.matching.MerchantNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LearnedMerchantPreference(
    val normalizedMerchant: String,
    val categoryId: Long,
    val title: String,
    val usageCount: Long,
    val isRejected: Boolean,
    val lastUsedAt: Long
)

interface MerchantPreferenceRepository {
    suspend fun recordUserDecision(
        rawMerchant: String?,
        categoryId: Long,
        title: String,
        accepted: Boolean
    )

    suspend fun isClassificationRejected(
        rawMerchant: String?,
        categoryId: Long,
        title: String
    ): Boolean

    suspend fun getLearnedPreferences(rawMerchant: String?): List<LearnedMerchantPreference>

    suspend fun clearLearnedPreference(rawMerchant: String?, categoryId: Long, title: String)

    suspend fun clearAllLearnedPreferences()
}

class MerchantPreferenceRepositoryImpl(
    private val inputUsageDao: InputUsageDao
) : MerchantPreferenceRepository {

    private fun buildFieldType(normalizedMerchant: String): String {
        return "MERCHANT_MATCH:$normalizedMerchant"
    }

    private fun buildOptionId(categoryId: Long, title: String): String {
        return "$categoryId|${title.trim().lowercase()}"
    }

    override suspend fun recordUserDecision(
        rawMerchant: String?,
        categoryId: Long,
        title: String,
        accepted: Boolean
    ) {
        val norm = MerchantNormalizer.normalize(rawMerchant)
        if (norm.isBlank() || title.isBlank()) return

        val fieldType = buildFieldType(norm)
        val optionId = buildOptionId(categoryId, title)

        val existing = inputUsageDao.getUsage(fieldType, optionId)
        val timestamp = System.currentTimeMillis()

        if (accepted) {
            val newCount = (existing?.usageCount ?: 0L) + 1L
            val newScore = if (existing != null && existing.preferenceScore >= 0.0) {
                existing.preferenceScore + 1.0
            } else {
                1.0
            }
            inputUsageDao.insertOrUpdate(
                InputUsageEntity(
                    id = existing?.id ?: 0L,
                    fieldType = fieldType,
                    optionId = optionId,
                    usageCount = newCount,
                    lastUsedAt = timestamp,
                    preferenceScore = newScore
                )
            )
        } else {
            // Negative score marks explicit user rejection
            val newCount = (existing?.usageCount ?: 0L) + 1L
            inputUsageDao.insertOrUpdate(
                InputUsageEntity(
                    id = existing?.id ?: 0L,
                    fieldType = fieldType,
                    optionId = optionId,
                    usageCount = newCount,
                    lastUsedAt = timestamp,
                    preferenceScore = -5.0
                )
            )
        }
    }

    override suspend fun isClassificationRejected(
        rawMerchant: String?,
        categoryId: Long,
        title: String
    ): Boolean {
        val norm = MerchantNormalizer.normalize(rawMerchant)
        if (norm.isBlank() || title.isBlank()) return false

        val fieldType = buildFieldType(norm)
        val optionId = buildOptionId(categoryId, title)
        val usage = inputUsageDao.getUsage(fieldType, optionId)
        return usage != null && usage.preferenceScore < 0.0
    }

    override suspend fun getLearnedPreferences(rawMerchant: String?): List<LearnedMerchantPreference> {
        val norm = MerchantNormalizer.normalize(rawMerchant)
        if (norm.isBlank()) return emptyList()

        val fieldType = buildFieldType(norm)
        val list = inputUsageDao.getUsagesForFieldList(fieldType)

        return list.mapNotNull { item ->
            val parts = item.optionId.split("|")
            if (parts.size >= 2) {
                val catId = parts[0].toLongOrNull() ?: return@mapNotNull null
                val title = parts[1]
                LearnedMerchantPreference(
                    normalizedMerchant = norm,
                    categoryId = catId,
                    title = title,
                    usageCount = item.usageCount,
                    isRejected = item.preferenceScore < 0.0,
                    lastUsedAt = item.lastUsedAt
                )
            } else null
        }
    }

    override suspend fun clearLearnedPreference(rawMerchant: String?, categoryId: Long, title: String) {
        val norm = MerchantNormalizer.normalize(rawMerchant)
        if (norm.isBlank() || title.isBlank()) return
        val fieldType = buildFieldType(norm)
        val optionId = buildOptionId(categoryId, title)
        inputUsageDao.deleteUsage(fieldType, optionId)
    }

    override suspend fun clearAllLearnedPreferences() {
        val all = inputUsageDao.getAllUsageList()
        for (item in all) {
            if (item.fieldType.startsWith("MERCHANT_MATCH:")) {
                inputUsageDao.deleteUsage(item.fieldType, item.optionId)
            }
        }
    }
}
