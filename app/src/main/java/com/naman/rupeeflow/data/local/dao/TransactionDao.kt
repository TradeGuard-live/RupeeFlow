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

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE expenseGroupId = :expenseGroupId ORDER BY transactionDate DESC, id DESC")
    fun getTransactionsForExpenseGroup(expenseGroupId: Long): Flow<List<TransactionWithParticipants>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE expenseGroupId = :expenseGroupId ORDER BY transactionDate DESC, id DESC")
    suspend fun getTransactionsForExpenseGroupList(expenseGroupId: Long): List<TransactionWithParticipants>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionEntity(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipantEntities(participants: List<TransactionParticipantEntity>)

    @Update
    suspend fun updateTransactionEntity(transaction: TransactionEntity): Int

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
    @Query("SELECT * FROM transactions WHERE merchantName = :merchantName AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY transactionDate DESC LIMIT 1")
    suspend fun findLatestLocationByMerchant(merchantName: String): TransactionWithParticipants?

    @Query("UPDATE transactions SET latitude = NULL, longitude = NULL, accuracyMeters = NULL, locationCapturedAt = NULL, locationSource = NULL, locationLabel = NULL, googleMapsUrl = NULL")
    suspend fun clearAllLocations()

    // Atomic save/update inside a single Room transaction
    // Enforces strict share balance validation, duplicate participant rejection,
    // and safe participant replacement.
    @androidx.room.Transaction
    suspend fun saveTransactionWithParticipants(
        transaction: TransactionEntity,
        participants: List<TransactionParticipantEntity>
    ): Long {
        require(transaction.amountPaise > 0L) { "Transaction amount must be greater than zero" }

        if (participants.isNotEmpty()) {
            val totalSharePaise = participants.sumOf { it.shareAmountPaise }
            require(totalSharePaise == transaction.amountPaise) {
                "Participant shares ($totalSharePaise paise) must strictly equal total amount (${transaction.amountPaise} paise)"
            }
            val friendIds = participants.map { it.friendId }
            require(friendIds.distinct().size == friendIds.size) {
                "Duplicate participants are not allowed"
            }
            require(participants.all { it.shareAmountPaise >= 0L }) {
                "Participant shares cannot be negative"
            }
        }

        val txnId = if (transaction.id == 0L) {
            insertTransactionEntity(transaction)
        } else {
            deleteParticipantsForTransaction(transaction.id)
            val rows = updateTransactionEntity(transaction)
            if (rows == 0) {
                insertTransactionEntity(transaction)
            } else {
                transaction.id
            }
        }

        if (participants.isNotEmpty()) {
            val updatedParticipants = participants.map { it.copy(id = 0L, transactionId = txnId) }
            insertParticipantEntities(updatedParticipants)
        }

        return txnId
    }
}
