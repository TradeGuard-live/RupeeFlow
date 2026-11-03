package com.naman.rupeeflow.data.repository

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
    suspend fun recordSettlement(settlement: Settlement): Long
    suspend fun reverseSettlement(id: Long)
    suspend fun unreverseSettlement(id: Long)
    suspend fun deleteSettlement(settlement: Settlement)
}

class SettlementRepositoryImpl(
    private val settlementDao: SettlementDao
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

    override suspend fun recordSettlement(settlement: Settlement): Long {
        return settlementDao.insertSettlement(settlement.toEntity())
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
