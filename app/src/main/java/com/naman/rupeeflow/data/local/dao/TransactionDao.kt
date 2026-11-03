package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.TransactionEntity
import com.naman.rupeeflow.data.local.entity.TransactionParticipantEntity
import com.naman.rupeeflow.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

data class TransactionWithParticipants(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val participants: List<TransactionParticipantEntity>
)

@Dao
interface TransactionDao {

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionWithParticipants>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, id DESC")
    suspend fun getAllTransactionsList(): List<TransactionWithParticipants>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE transactionStatus = :status ORDER BY transactionDate DESC, id DESC")
    fun getTransactionsByStatus(status: TransactionStatus): Flow<List<TransactionWithParticipants>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionWithParticipants?

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getTransactionByIdFlow(id: Long): Flow<TransactionWithParticipants?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionEntity(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipantEntities(participants: List<TransactionParticipantEntity>)

    @Update
    suspend fun updateTransactionEntity(transaction: TransactionEntity)

    @Query("DELETE FROM transaction_participants WHERE transactionId = :transactionId")
    suspend fun deleteParticipantsForTransaction(transactionId: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("UPDATE transactions SET transactionStatus = :status WHERE id = :id")
    suspend fun updateTransactionStatus(id: Long, status: TransactionStatus)

    @Query("SELECT * FROM transactions WHERE referenceNumber = :refNumber LIMIT 1")
    suspend fun findByReferenceNumber(refNumber: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE amountPaise = :amountPaise AND transactionDate BETWEEN :startMillis AND :endMillis")
    suspend fun findSimilarRecent(amountPaise: Long, startMillis: Long, endMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE amountPaise = :amountPaise AND transactionDate BETWEEN :startMillis AND :endMillis LIMIT 1")
    suspend fun findDuplicate(amountPaise: Long, startMillis: Long, endMillis: Long): TransactionEntity?

    @androidx.room.Transaction
    suspend fun saveTransactionWithParticipants(
        transaction: TransactionEntity,
        participants: List<TransactionParticipantEntity>
    ): Long {
        val txnId = if (transaction.id == 0L) {
            insertTransactionEntity(transaction)
        } else {
            updateTransactionEntity(transaction)
            deleteParticipantsForTransaction(transaction.id)
            transaction.id
        }

        if (participants.isNotEmpty()) {
            val updatedParticipants = participants.map { it.copy(transactionId = txnId) }
            insertParticipantEntities(updatedParticipants)
        }

        return txnId
    }
}
