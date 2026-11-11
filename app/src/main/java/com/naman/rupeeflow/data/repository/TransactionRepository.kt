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
    suspend fun getLatestLocationByMerchant(merchantName: String): TransactionLocation?
    suspend fun clearAllLocations()
    // Contextual duplicate check: does NOT reject transactions based solely on matching amount
    suspend fun isDuplicate(
        amountPaise: Long,
        timestamp: Long,
        refNumber: String?,
        merchant: String? = null,
        title: String? = null
    ): Boolean
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

    // Atomic save/edit preserving id and createdAt, updating updatedAt
    override suspend fun saveTransaction(transaction: Transaction): Long {
        val existing = if (transaction.id > 0L) transactionDao.getTransactionById(transaction.id)?.transaction else null
        val createdAt = existing?.createdAt ?: if (transaction.createdAt > 0L) transaction.createdAt else System.currentTimeMillis()
        val entity = transaction.toEntity().copy(
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        val participantEntities = transaction.participants.map { it.toEntity(transaction.id) }
        return transactionDao.saveTransactionWithParticipants(entity, participantEntities)
    }

    override suspend fun updateTransactionStatus(id: Long, status: TransactionStatus) {
        transactionDao.updateTransactionStatus(id, status)
    }

    override suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteTransactionById(id)
    }

    override suspend fun getLatestLocationByMerchant(merchantName: String): TransactionLocation? {
        if (merchantName.isBlank()) return null
        val match = transactionDao.findLatestLocationByMerchant(merchantName.trim())
        val txn = match?.transaction ?: return null
        return if (txn.latitude != null && txn.longitude != null) {
            TransactionLocation(
                latitude = txn.latitude,
                longitude = txn.longitude,
                accuracyMeters = txn.accuracyMeters,
                locationCapturedAt = txn.locationCapturedAt ?: txn.createdAt,
                locationSource = txn.locationSource ?: LocationSource.HISTORICAL_LOCATION_SUGGESTION,
                locationLabel = txn.locationLabel ?: merchantName,
                googleMapsUrl = txn.googleMapsUrl
            )
        } else null
    }

    override suspend fun clearAllLocations() {
        transactionDao.clearAllLocations()
    }

    // Context-aware duplicate check: prefers exact reference/UTR and matches merchant or title,
    // never rejecting legitimate purchases with identical amounts
    override suspend fun isDuplicate(
        amountPaise: Long,
        timestamp: Long,
        refNumber: String?,
        merchant: String?,
        title: String?
    ): Boolean {
        if (!refNumber.isNullOrBlank()) {
            val existing = transactionDao.findByReferenceNumber(refNumber)
            if (existing != null) return true
        }

        val window = 2 * 60 * 1000L
        val recent = transactionDao.findSimilarRecent(amountPaise, timestamp - window, timestamp + window)

        for (item in recent) {
            // Immediate duplicate notification/broadcast within 5 seconds with identical merchant or title
            if (kotlin.math.abs(item.transactionDate - timestamp) < 5_000L) {
                if (!merchant.isNullOrBlank() && !item.merchantName.isNullOrBlank()) {
                    if (merchant.equals(item.merchantName, ignoreCase = true)) return true
                } else if (!title.isNullOrBlank() && !item.title.isNullOrBlank()) {
                    if (title.equals(item.title, ignoreCase = true)) return true
                } else if (merchant.isNullOrBlank() && item.merchantName.isNullOrBlank()) {
                    return true
                }
            }

            // Same merchant & amount within window
            if (!merchant.isNullOrBlank() && merchant.equals(item.merchantName, ignoreCase = true)) {
                return true
            }

            // Same title & amount within window
            if (!title.isNullOrBlank() && title.equals(item.title, ignoreCase = true)) {
                return true
            }
        }

        return false
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
            groupId = transaction.groupId,
            billImagePath = transaction.billImagePath,
            latitude = transaction.latitude,
            longitude = transaction.longitude,
            accuracyMeters = transaction.accuracyMeters,
            locationCapturedAt = transaction.locationCapturedAt,
            locationSource = transaction.locationSource,
            locationLabel = transaction.locationLabel,
            googleMapsUrl = transaction.googleMapsUrl,
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
        groupId = groupId,
        billImagePath = billImagePath,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        locationCapturedAt = locationCapturedAt,
        locationSource = locationSource,
        locationLabel = locationLabel,
        googleMapsUrl = googleMapsUrl,
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
