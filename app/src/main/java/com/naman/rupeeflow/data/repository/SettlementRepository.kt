package com.naman.rupeeflow.data.repository

import android.content.Context
import com.naman.rupeeflow.data.local.dao.FriendDao
import com.naman.rupeeflow.data.local.dao.SettlementDao
import com.naman.rupeeflow.data.local.entity.SettlementEntity
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.widget.RupeeFlowWidgetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettlementRepository {
    fun getAllSettlements(): Flow<List<Settlement>>
    suspend fun getAllSettlementsList(): List<Settlement>
    fun getSettlementsForFriend(friendId: Long): Flow<List<Settlement>>
    suspend fun getSettlementsForFriendList(friendId: Long): List<Settlement>
    fun getSettlementsForGroup(groupId: Long): Flow<List<Settlement>> = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun getSettlementsForGroupList(groupId: Long): List<Settlement> = emptyList()
    suspend fun getSettlementById(id: Long): Settlement?
    suspend fun recordSettlement(settlement: Settlement): Long
    // Edit existing settlement with ledger recalculation
    suspend fun updateSettlement(settlement: Settlement)
    suspend fun reverseSettlement(id: Long): Boolean
    suspend fun unreverseSettlement(id: Long): Boolean
    suspend fun deleteSettlement(settlement: Settlement)
}

class SettlementRepositoryImpl(
    private val settlementDao: SettlementDao,
    private val friendDao: FriendDao? = null,
    private val context: Context? = null
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

    override fun getSettlementsForGroup(groupId: Long): Flow<List<Settlement>> {
        return settlementDao.getSettlementsForGroup(groupId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSettlementsForGroupList(groupId: Long): List<Settlement> {
        return settlementDao.getSettlementsForGroupList(groupId).map { it.toDomain() }
    }

    override suspend fun getSettlementById(id: Long): Settlement? {
        return settlementDao.getSettlementById(id)?.toDomain()
    }

    override suspend fun recordSettlement(settlement: Settlement): Long {
        require(settlement.amountPaise > 0L) { "Settlement amount must be greater than zero" }
        require(settlement.payerId != settlement.receiverId) { "Payer and receiver cannot be the same" }
        if (settlement.groupId == null) {
            val hasMe = (settlement.payerId == null) != (settlement.receiverId == null)
            require(hasMe) { "Exactly one side of a settlement must be Me" }
        }

        val friendIds = listOfNotNull(settlement.payerId, settlement.receiverId)
        if (friendDao != null) {
            for (fId in friendIds) {
                val friend = friendDao.getFriendById(fId)
                require(friend != null) { "Referenced friend does not exist" }
            }
        }

        val id = settlementDao.insertSettlement(settlement.toEntity())
        context?.let { RupeeFlowWidgetManager.updateAllWidgets(it) }
        return id
    }

    // Update settlement preserving ID, createdAt, and reversal status with strict validations
    override suspend fun updateSettlement(settlement: Settlement) {
        require(settlement.id > 0L) { "Settlement ID must be valid for update" }
        require(settlement.amountPaise > 0L) { "Settlement amount must be greater than zero" }
        require(settlement.payerId != settlement.receiverId) { "Payer and receiver cannot be the same entity" }
        if (settlement.groupId == null) {
            val hasMe = (settlement.payerId == null) != (settlement.receiverId == null)
            require(hasMe) { "Exactly one side of a settlement must be Me" }
        }

        val friendIds = listOfNotNull(settlement.payerId, settlement.receiverId)
        if (friendDao != null) {
            for (fId in friendIds) {
                val friend = friendDao.getFriendById(fId)
                require(friend != null) { "Referenced friend does not exist" }
            }
        }

        val existing = settlementDao.getSettlementById(settlement.id)
            ?: throw IllegalArgumentException("Settlement with ID ${settlement.id} not found")

        // Preserve createdAt and existing isReversed status
        val safeEntity = settlement.toEntity().copy(
            createdAt = existing.createdAt,
            isReversed = existing.isReversed
        )
        settlementDao.updateSettlement(safeEntity)
        context?.let { RupeeFlowWidgetManager.updateAllWidgets(it) }
    }

    override suspend fun reverseSettlement(id: Long): Boolean {
        val affected = settlementDao.reverseSettlement(id)
        if (affected > 0) {
            context?.let { RupeeFlowWidgetManager.updateAllWidgets(it) }
            return true
        }
        return false
    }

    override suspend fun unreverseSettlement(id: Long): Boolean {
        val affected = settlementDao.unreverseSettlement(id)
        if (affected > 0) {
            context?.let { RupeeFlowWidgetManager.updateAllWidgets(it) }
            return true
        }
        return false
    }

    override suspend fun deleteSettlement(settlement: Settlement) {
        settlementDao.deleteSettlement(settlement.toEntity())
        context?.let { RupeeFlowWidgetManager.updateAllWidgets(it) }
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
        groupId = groupId,
        proofImagePath = proofImagePath,
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
        groupId = groupId,
        proofImagePath = proofImagePath,
        isReversed = isReversed,
        createdAt = createdAt
    )
}
