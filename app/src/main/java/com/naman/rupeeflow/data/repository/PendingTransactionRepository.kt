package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.PendingTransactionDao
import com.naman.rupeeflow.data.local.dao.TransactionDao
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
    // Contextual duplicate detection
    suspend fun isDuplicate(
        amountPaise: Long,
        timestamp: Long,
        refNumber: String?,
        merchant: String? = null,
        bank: String? = null,
        accountLast4: String? = null,
        rawContent: String? = null
    ): Boolean
}

class PendingTransactionRepositoryImpl(
    private val pendingDao: PendingTransactionDao,
    private val transactionDao: TransactionDao? = null
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

    /**
     * High-accuracy duplicate detection:
     * 1. If referenceNumber/UTR matches an existing pending or settled transaction -> duplicate!
     * 2. If no reference number is available, never discard a transaction solely because
     *    another transaction has the same amount. Two legitimate transactions of the same amount
     *    are permitted unless merchant/account and tight timestamp match.
     */
    override suspend fun isDuplicate(
        amountPaise: Long,
        timestamp: Long,
        refNumber: String?,
        merchant: String?,
        bank: String?,
        accountLast4: String?,
        rawContent: String?
    ): Boolean {
        // 1. Strict unique bank reference number check
        if (!refNumber.isNullOrBlank()) {
            val existingPending = pendingDao.findByReference(refNumber)
            if (existingPending != null) return true
            val existingConfirmed = transactionDao?.findByReferenceNumber(refNumber)
            if (existingConfirmed != null) return true
        }

        // 2. High-precision contextual check within short 2-minute window
        val window = 2 * 60 * 1000L
        val recentPending = pendingDao.findSimilarRecent(
            amountPaise = amountPaise,
            startMillis = timestamp - window,
            endMillis = timestamp + window
        )

        for (item in recentPending) {
            // Exact raw text match (e.g. duplicate SMS or notification broadcast delivered twice)
            if (rawContent != null && item.rawContent.trim().equals(rawContent.trim(), ignoreCase = true)) return true

            // If both transactions have merchants and they differ, they are distinct legitimate purchases!
            if (!merchant.isNullOrBlank() && !item.detectedMerchant.isNullOrBlank() &&
                !merchant.equals(item.detectedMerchant, ignoreCase = true)) {
                continue
            }

            // Same merchant & amount within window
            val merchantsMatch = !merchant.isNullOrBlank() && merchant.equals(item.detectedMerchant, ignoreCase = true)
            if (merchantsMatch) return true

            // Same bank & account last 4 within window
            val accountsMatch = !accountLast4.isNullOrBlank() && accountLast4 == item.detectedAccount
            val banksMatch = !bank.isNullOrBlank() && bank.equals(item.detectedBank, ignoreCase = true)
            if (accountsMatch && banksMatch) return true
        }

        // Check against confirmed transactions
        if (transactionDao != null) {
            val recentConfirmed = transactionDao.findSimilarRecent(
                amountPaise = amountPaise,
                startMillis = timestamp - window,
                endMillis = timestamp + window
            )
            for (item in recentConfirmed) {
                // If both have merchants and they differ, distinct legitimate purchase!
                if (!merchant.isNullOrBlank() && !item.merchantName.isNullOrBlank() &&
                    !merchant.equals(item.merchantName, ignoreCase = true)) {
                    continue
                }
                if (!merchant.isNullOrBlank() && merchant.equals(item.merchantName, ignoreCase = true)) return true
                if (!accountLast4.isNullOrBlank() && item.accountSource?.endsWith(accountLast4) == true) return true
            }
        }

        return false
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
