package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.FriendDao
import com.naman.rupeeflow.data.local.dao.SettlementDao
import com.naman.rupeeflow.data.local.entity.SettlementEntity
import com.naman.rupeeflow.domain.model.Settlement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettlementRepository {
    fun getAllSettlements(): Flow<List<Settlement>>
    suspend fun getAllSettlementsList(): List<Settlement>
    fun getSettlementsForFriend(friendId: Long): Flow<List<Settlement>>
    suspend fun getSettlementsForFriendList(friendId: Long): List<Settlement>
    suspend fun getSettlementById(id: Long): Settlement?
    suspend fun recordSettlement(settlement: Settlement): Long
    // ✅ Edit existing settlement with ledger recalculation
    suspend fun updateSettlement(settlement: Settlement)
    suspend fun reverseSettlement(id: Long)
    suspend fun unreverseSettlement(id: Long)
    suspend fun deleteSettlement(settlement: Settlement)
}

class SettlementRepositoryImpl(
    private val settlementDao: SettlementDao,
    private val friendDao: FriendDao? = null
) : SettlementRepository {

    override fun getAllSettlements(): Flow<List<Settlement>> {
        return settlementDao.getAllSettlements().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllSettlementsList(): List<Settlement> {
        return settlementDao.getAllSettlementsList().map { it.toDomain() }
    }

    override fun getSettlementsForFriend(friendId: Long): Flow<List<Settlement>> {
        return settlementDao.getSettlementsForFriend(friendId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSettlementsForFriendList(friendId: Long): List<Settlement> {
        return settlementDao.getSettlementsForFriendList(friendId).map { it.toDomain() }
    }

    override suspend fun getSettlementById(id: Long): Settlement? {
        return settlementDao.getSettlementById(id)?.toDomain()
    }

    override suspend fun recordSettlement(settlement: Settlement): Long {
        require(settlement.amountPaise > 0L) { "Settlement amount must be greater than zero" }
        require(settlement.payerId != settlement.receiverId) { "Payer and receiver cannot be the same" }
        val hasMe = (settlement.payerId == null) != (settlement.receiverId == null)
        require(hasMe) { "Exactly one side of a settlement must be Me" }

        val friendId = settlement.payerId ?: settlement.receiverId
        if (friendId != null && friendDao != null) {
            val friend = friendDao.getFriendById(friendId)
            require(friend != null) { "Referenced friend does not exist" }
        }

        return settlementDao.insertSettlement(settlement.toEntity())
    }

    // ✅ Update settlement preserving ID, createdAt, and reversal status with strict validations
    override suspend fun updateSettlement(settlement: Settlement) {
        require(settlement.id > 0L) { "Settlement ID must be valid for update" }
        require(settlement.amountPaise > 0L) { "Settlement amount must be greater than zero" }
        require(settlement.payerId != settlement.receiverId) { "Payer and receiver cannot be the same entity" }
        val hasMe = (settlement.payerId == null) != (settlement.receiverId == null)
        require(hasMe) { "Exactly one side of a settlement must be Me" }

        val friendId = settlement.payerId ?: settlement.receiverId
        if (friendId != null && friendDao != null) {
            val friend = friendDao.getFriendById(friendId)
            require(friend != null) { "Referenced friend does not exist" }
        }

        val existing = settlementDao.getSettlementById(settlement.id)
            ?: throw IllegalArgumentException("Settlement with ID ${settlement.id} not found")

        // Preserve createdAt and existing isReversed status
        val safeEntity = settlement.toEntity().copy(
            createdAt = existing.createdAt,
            isReversed = existing.isReversed
        )
        settlementDao.updateSettlement(safeEntity)
    }

    override suspend fun reverseSettlement(id: Long) {
        settlementDao.reverseSettlement(id)
    }

    override suspend fun unreverseSettlement(id: Long) {
        settlementDao.unreverseSettlement(id)
    }

    override suspend fun deleteSettlement(settlement: Settlement) {
        settlementDao.deleteSettlement(settlement.toEntity())
    }

    private fun SettlementEntity.toDomain() = Settlement(
        id = id,
        payerId = payerId,
        receiverId = receiverId,
        amountPaise = amountPaise,
        settlementDate = settlementDate,
        paymentMethod = paymentMethod,
        referenceNumber = referenceNumber,
        notes = notes,
        isReversed = isReversed,
        createdAt = createdAt
    )

    private fun Settlement.toEntity() = SettlementEntity(
        id = id,
        payerId = payerId,
        receiverId = receiverId,
        amountPaise = amountPaise,
        settlementDate = settlementDate,
        paymentMethod = paymentMethod,
        referenceNumber = referenceNumber,
        notes = notes,
        isReversed = isReversed,
        createdAt = createdAt
    )
}
