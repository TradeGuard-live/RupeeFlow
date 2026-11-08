package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.domain.model.PendingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {
    @Query("SELECT * FROM pending_transactions WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingTransactions(): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions ORDER BY timestamp DESC")
    fun getAllDetectedTransactions(): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions WHERE status = 'PENDING' ORDER BY timestamp DESC")
    suspend fun getPendingTransactionsList(): List<PendingTransactionEntity>

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_transactions WHERE id = :id")
    suspend fun getById(id: Long): PendingTransactionEntity?

    @Query("SELECT * FROM pending_transactions WHERE referenceNumber = :refNumber LIMIT 1")
    suspend fun findByReference(refNumber: String): PendingTransactionEntity?

    @Query("SELECT * FROM pending_transactions WHERE detectedAmountPaise = :amountPaise AND timestamp BETWEEN :startMillis AND :endMillis")
    suspend fun findSimilarRecent(amountPaise: Long, startMillis: Long, endMillis: Long): List<PendingTransactionEntity>

    @Query("SELECT * FROM pending_transactions WHERE detectedAmountPaise = :amountPaise AND timestamp BETWEEN :startMillis AND :endMillis LIMIT 1")
    suspend fun findRecentSimilar(amountPaise: Long, startMillis: Long, endMillis: Long): PendingTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pending: PendingTransactionEntity): Long

    @Update
    suspend fun update(pending: PendingTransactionEntity)

    @Query("UPDATE pending_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: PendingStatus)

    @Query("UPDATE pending_transactions SET status = 'PENDING' WHERE id = :id")
    suspend fun restorePending(id: Long)

    @Delete
    suspend fun delete(pending: PendingTransactionEntity)
}
