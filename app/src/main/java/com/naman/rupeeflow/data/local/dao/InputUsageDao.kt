package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.InputUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InputUsageDao {

    @Query("SELECT * FROM input_usage WHERE fieldType = :fieldType ORDER BY usageCount DESC, lastUsedAt DESC")
    fun getUsagesForField(fieldType: String): Flow<List<InputUsageEntity>>

    @Query("SELECT * FROM input_usage WHERE fieldType = :fieldType ORDER BY usageCount DESC, lastUsedAt DESC")
    suspend fun getUsagesForFieldList(fieldType: String): List<InputUsageEntity>

    @Query("SELECT * FROM input_usage WHERE fieldType = :fieldType AND optionId = :optionId LIMIT 1")
    suspend fun getUsage(fieldType: String, optionId: String): InputUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: InputUsageEntity): Long

    @Transaction
    suspend fun recordUsage(fieldType: String, optionId: String, timestamp: Long = System.currentTimeMillis()) {
        val existing = getUsage(fieldType, optionId)
        if (existing != null) {
            insertOrUpdate(
                existing.copy(
                    usageCount = existing.usageCount + 1,
                    lastUsedAt = timestamp
                )
            )
        } else {
            insertOrUpdate(
                InputUsageEntity(
                    fieldType = fieldType,
                    optionId = optionId,
                    usageCount = 1,
                    lastUsedAt = timestamp,
                    preferenceScore = 0.0
                )
            )
        }
    }

    @Query("UPDATE input_usage SET preferenceScore = :score WHERE fieldType = :fieldType AND optionId = :optionId")
    suspend fun updatePreferenceScore(fieldType: String, optionId: String, score: Double)

    @Query("DELETE FROM input_usage WHERE fieldType = :fieldType AND optionId = :optionId")
    suspend fun deleteUsage(fieldType: String, optionId: String)

    @Query("DELETE FROM input_usage")
    suspend fun clearAll()

    @Query("SELECT * FROM input_usage ORDER BY lastUsedAt DESC")
    suspend fun getAllUsageList(): List<InputUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsages(entities: List<InputUsageEntity>)
}
