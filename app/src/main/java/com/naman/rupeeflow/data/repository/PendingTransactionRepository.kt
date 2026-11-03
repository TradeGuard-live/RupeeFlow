package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.PendingTransactionDao
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.domain.model.PendingStatus
import com.naman.rupeeflow.domain.model.PendingTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PendingTransactionRepository {
    fun getPendingTransactions(): Flow<List<PendingTransaction>>
    fun getAllDetectedTransactions(): Flow<List<PendingTransaction>>
    suspend fun getPendingTransactionsList(): List<PendingTransaction>
    fun getPendingCount(): Flow<Int>
    suspend fun getById(id: Long): PendingTransaction?
    suspend fun addPendingTransaction(pending: PendingTransaction): Long
    suspend fun resolvePendingTransaction(id: Long)
    suspend fun ignorePendingTransaction(id: Long)
    suspend fun deletePendingTransaction(id: Long)
    suspend fun isDuplicate(amountPaise: Long, timestamp: Long, refNumber: String?): Boolean
}

class PendingTransactionRepositoryImpl(
    private val pendingDao: PendingTransactionDao
) : PendingTransactionRepository {

    override fun getPendingTransactions(): Flow<List<PendingTransaction>> {
        return pendingDao.getPendingTransactions().map { list -> list.map { it.toDomain() } }
    }

    override fun getAllDetectedTransactions(): Flow<List<PendingTransaction>> {
        return pendingDao.getAllDetectedTransactions().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getPendingTransactionsList(): List<PendingTransaction> {
        return pendingDao.getPendingTransactionsList().map { it.toDomain() }
    }

    override fun getPendingCount(): Flow<Int> {
        return pendingDao.getPendingCount()
    }

    override suspend fun getById(id: Long): PendingTransaction? {
        return pendingDao.getById(id)?.toDomain()
    }

    override suspend fun addPendingTransaction(pending: PendingTransaction): Long {
        return pendingDao.insert(pending.toEntity())
    }

    override suspend fun resolvePendingTransaction(id: Long) {
        pendingDao.updateStatus(id, PendingStatus.RESOLVED)
    }

    override suspend fun ignorePendingTransaction(id: Long) {
        pendingDao.updateStatus(id, PendingStatus.IGNORED)
    }

    override suspend fun deletePendingTransaction(id: Long) {
        val item = pendingDao.getById(id)
        if (item != null) pendingDao.delete(item)
    }

    override suspend fun isDuplicate(amountPaise: Long, timestamp: Long, refNumber: String?): Boolean {
        // If reference number matches an existing pending transaction
        if (!refNumber.isNullOrBlank()) {
            val existing = pendingDao.findByReference(refNumber)
            if (existing != null) return true
        }
        // Within 10 minutes with same amount
        val window = 10 * 60 * 1000L
        val recent = pendingDao.findRecentSimilar(amountPaise, timestamp - window, timestamp + window)
        return recent != null
    }

    private fun PendingTransactionEntity.toDomain() = PendingTransaction(
        id = id,
        detectedAmountPaise = detectedAmountPaise,
        detectedType = detectedType,
        detectedBank = detectedBank,
        detectedAccount = detectedAccount,
        detectedMerchant = detectedMerchant,
        referenceNumber = referenceNumber,
        rawContent = rawContent,
        timestamp = timestamp,
        status = status,
        source = source
    )

    private fun PendingTransaction.toEntity() = PendingTransactionEntity(
        id = id,
        detectedAmountPaise = detectedAmountPaise,
        detectedType = detectedType,
        detectedBank = detectedBank,
        detectedAccount = detectedAccount,
        detectedMerchant = detectedMerchant,
        referenceNumber = referenceNumber,
        rawContent = rawContent,
        timestamp = timestamp,
        status = status,
        source = source
    )
}
