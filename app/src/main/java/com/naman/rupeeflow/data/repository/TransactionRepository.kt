package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.CategoryDao
import com.naman.rupeeflow.data.local.dao.FriendDao
import com.naman.rupeeflow.data.local.dao.TransactionDao
import com.naman.rupeeflow.data.local.dao.TransactionWithParticipants
import com.naman.rupeeflow.data.local.entity.TransactionEntity
import com.naman.rupeeflow.data.local.entity.TransactionParticipantEntity
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    suspend fun getAllTransactionsList(): List<Transaction>
    fun getTransactionsByStatus(status: TransactionStatus): Flow<List<Transaction>>
    suspend fun getTransactionById(id: Long): Transaction?
    fun getTransactionByIdFlow(id: Long): Flow<Transaction?>
    suspend fun saveTransaction(transaction: Transaction): Long
    suspend fun updateTransactionStatus(id: Long, status: TransactionStatus)
    suspend fun deleteTransaction(id: Long)
    suspend fun isDuplicate(amountPaise: Long, timestamp: Long, refNumber: String?): Boolean
}

class TransactionRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val friendDao: FriendDao
) : TransactionRepository {

    override fun getAllTransactions(): Flow<List<Transaction>> {
        return combine(
            transactionDao.getAllTransactions(),
            categoryDao.getAllCategories(),
            friendDao.getAllFriends()
        ) { rawTxns, categories, friends ->
            val catMap = categories.associateBy { it.id }
            val friendMap = friends.associateBy { it.id }
            rawTxns.map { it.toDomain(catMap, friendMap) }
        }
    }

    override suspend fun getAllTransactionsList(): List<Transaction> {
        val rawTxns = transactionDao.getAllTransactionsList()
        val catMap = categoryDao.getAllCategoriesList().associateBy { it.id }
        val friendMap = friendDao.getAllFriendsList().associateBy { it.id }
        return rawTxns.map { it.toDomain(catMap, friendMap) }
    }

    override fun getTransactionsByStatus(status: TransactionStatus): Flow<List<Transaction>> {
        return combine(
            transactionDao.getTransactionsByStatus(status),
            categoryDao.getAllCategories(),
            friendDao.getAllFriends()
        ) { rawTxns, categories, friends ->
            val catMap = categories.associateBy { it.id }
            val friendMap = friends.associateBy { it.id }
            rawTxns.map { it.toDomain(catMap, friendMap) }
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        val raw = transactionDao.getTransactionById(id) ?: return null
        val cat = categoryDao.getCategoryById(raw.transaction.categoryId)
        val friendMap = friendDao.getAllFriendsList().associateBy { it.id }
        val catMap = if (cat != null) mapOf(cat.id to cat) else emptyMap()
        return raw.toDomain(catMap, friendMap)
    }

    override fun getTransactionByIdFlow(id: Long): Flow<Transaction?> {
        return combine(
            transactionDao.getTransactionByIdFlow(id),
            categoryDao.getAllCategories(),
            friendDao.getAllFriends()
        ) { raw, categories, friends ->
            raw?.let {
                val catMap = categories.associateBy { it.id }
                val friendMap = friends.associateBy { it.id }
                it.toDomain(catMap, friendMap)
            }
        }
    }

    override suspend fun saveTransaction(transaction: Transaction): Long {
        val entity = transaction.toEntity()
        val participantEntities = transaction.participants.map { it.toEntity(transaction.id) }
        return transactionDao.saveTransactionWithParticipants(entity, participantEntities)
    }

    override suspend fun updateTransactionStatus(id: Long, status: TransactionStatus) {
        transactionDao.updateTransactionStatus(id, status)
    }

    override suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteTransactionById(id)
    }

    override suspend fun isDuplicate(amountPaise: Long, timestamp: Long, refNumber: String?): Boolean {
        if (!refNumber.isNullOrBlank()) {
            val existing = transactionDao.findByReferenceNumber(refNumber)
            if (existing != null) return true
        }
        val window = 2 * 60 * 1000L
        val recent = transactionDao.findSimilarRecent(amountPaise, timestamp - window, timestamp + window)
        return recent.isNotEmpty()
    }

    private fun TransactionWithParticipants.toDomain(
        categoryMap: Map<Long, com.naman.rupeeflow.data.local.entity.CategoryEntity>,
        friendMap: Map<Long, com.naman.rupeeflow.data.local.entity.FriendEntity>
    ): Transaction {
        val catEntity = categoryMap[transaction.categoryId]
        val categoryDomain = catEntity?.let {
            Category(it.id, it.name, it.iconName, it.colorHex, it.isDefault)
        }

        val payerName = transaction.paidByFriendId?.let { friendMap[it]?.name } ?: "Me"

        val participantDomains = participants.map { p ->
            val pName = p.friendId?.let { friendMap[it]?.name } ?: "Me"
            TransactionParticipant(
                id = p.id,
                transactionId = p.transactionId,
                friendId = p.friendId,
                friendName = pName,
                shareAmountPaise = p.shareAmountPaise,
                percentage = p.percentage,
                splitMethod = p.splitMethod
            )
        }

        return Transaction(
            id = transaction.id,
            title = transaction.title,
            description = transaction.description,
            amountPaise = transaction.amountPaise,
            categoryId = transaction.categoryId,
            category = categoryDomain,
            transactionDate = transaction.transactionDate,
            paymentMethod = transaction.paymentMethod,
            accountSource = transaction.accountSource,
            paidByFriendId = transaction.paidByFriendId,
            paidByName = payerName,
            participants = participantDomains,
            splitMethod = transaction.splitMethod,
            transactionType = transaction.transactionType,
            transactionStatus = transaction.transactionStatus,
            source = transaction.source,
            merchantName = transaction.merchantName,
            referenceNumber = transaction.referenceNumber,
            createdAt = transaction.createdAt,
            updatedAt = transaction.updatedAt
        )
    }

    private fun Transaction.toEntity() = TransactionEntity(
        id = id,
        title = title,
        description = description,
        amountPaise = amountPaise,
        categoryId = categoryId,
        transactionDate = transactionDate,
        paymentMethod = paymentMethod,
        accountSource = accountSource,
        paidByFriendId = paidByFriendId,
        splitMethod = splitMethod,
        transactionType = transactionType,
        transactionStatus = transactionStatus,
        source = source,
        merchantName = merchantName,
        referenceNumber = referenceNumber,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis()
    )

    private fun TransactionParticipant.toEntity(txnId: Long) = TransactionParticipantEntity(
        id = id,
        transactionId = txnId,
        friendId = friendId,
        shareAmountPaise = shareAmountPaise,
        percentage = percentage,
        splitMethod = splitMethod
    )
}
